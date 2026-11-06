package dev.shizzi.warp

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TunnelStorageTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val sample = """
        [Interface]
        PrivateKey = abc
        Address = 10.0.0.2/32

        [Peer]
        PublicKey = def
        AllowedIPs = 0.0.0.0/0
        Endpoint = 1.2.3.4:2408
    """.trimIndent()

    private fun store() = ConfigStore(File(folder.root, "tunnels"))

    @Test
    fun sanitizeKeepsGoodNamesAndStripsTheExtension() {
        assertEquals("2N5A-WARP-AB12", TunnelNames.sanitize("2N5A-WARP-AB12"))
        assertEquals("office", TunnelNames.sanitize("office.conf"))
        assertEquals("office", TunnelNames.sanitize("Office.CONF".lowercase()))
        assertEquals("a_b", TunnelNames.sanitize("a b.conf"))
    }

    @Test
    fun sanitizeShortensToFifteenCharactersAndStaysValid() {
        val name = TunnelNames.sanitize("a-very-long-file-name-for-a-tunnel.conf")

        assertTrue(name.length <= TunnelNames.MAX_LENGTH)
        assertTrue(TunnelNames.isValid(name))
    }

    @Test
    fun sanitizeFallsBackWhenNothingValidIsLeft() {
        assertEquals("tunnel", TunnelNames.sanitize("ملف.conf"))
        assertEquals("tunnel", TunnelNames.sanitize("   "))
        assertEquals("tunnel", TunnelNames.sanitize("___"))
    }

    @Test
    fun sanitizeDropsFolderPrefixes() {
        assertEquals("home", TunnelNames.sanitize("primary:Download/home.conf"))
    }

    @Test
    fun isValidFollowsTheLibraryRule() {
        assertTrue(TunnelNames.isValid("a"))
        assertTrue(TunnelNames.isValid("0123456789abcde"))
        assertFalse(TunnelNames.isValid(""))
        assertFalse(TunnelNames.isValid("0123456789abcdef"))
        assertFalse(TunnelNames.isValid("has space"))
        assertFalse(TunnelNames.isValid("عربي"))
    }

    @Test
    fun uniqueAddsASuffixWithinTheLimit() {
        assertEquals("home", TunnelNames.unique("home", emptyList()))
        assertEquals("home-2", TunnelNames.unique("home", listOf("home")))
        assertEquals("home-3", TunnelNames.unique("home", listOf("HOME", "home-2")))

        val long = "0123456789abcde"
        val result = TunnelNames.unique(long, listOf(long))
        assertTrue(result.length <= TunnelNames.MAX_LENGTH)
        assertTrue(TunnelNames.isValid(result))
        assertTrue(result != long)
    }

    @Test
    fun storeSavesListsAndReadsBack() {
        val store = store()
        assertTrue(store.list().isEmpty())

        store.save("beta", sample)
        store.save("Alpha", sample + "\n# two")

        assertEquals(listOf("Alpha", "beta"), store.names())
        assertEquals(sample, store.read("beta")?.text)
        assertEquals(sample + "\n# two", store.read("Alpha")?.text)
    }

    @Test
    fun storeOverwritesTheSameNameButSaveAsNewDoesNot() {
        val store = store()

        store.save("home", "one")
        store.save("home", "two")
        assertEquals("two", store.read("home")?.text)

        val second = store.saveAsNew("home.conf", "three")
        assertEquals("home-2", second.name)
        assertEquals("two", store.read("home")?.text)
        assertEquals(2, store.list().size)
    }

    @Test
    fun storeDeletesAndIgnoresStrangers() {
        val store = store()
        store.save("home", sample)
        File(folder.root, "tunnels/notes.txt").writeText("x")
        File(folder.root, "tunnels/bad name.conf").writeText("x")

        assertEquals(listOf("home"), store.names())
        assertTrue(store.delete("home"))
        assertFalse(store.delete("home"))
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun storeRefusesNamesThatCouldEscapeTheFolder() {
        val store = store()

        assertNull(store.read("../secret"))
        assertFalse(store.delete("../secret"))
        assertTrue(runCatching { store.save("../secret", "x") }.isFailure)
    }

    @Test
    fun sanityAcceptsAWellFormedConfig() {
        assertNull(ConfigSanity.check(sample))
    }

    @Test
    fun sanityExplainsWhatIsMissing() {
        assertEquals(ConfigProblem.EMPTY, ConfigSanity.check("  \n"))
        assertEquals(ConfigProblem.NOT_A_CONFIG, ConfigSanity.check("hello world"))
        assertEquals(
            ConfigProblem.NO_PRIVATE_KEY,
            ConfigSanity.check("[Interface]\nAddress = 10.0.0.2/32\n[Peer]\nPublicKey = x"),
        )
        assertEquals(
            ConfigProblem.NO_PEER,
            ConfigSanity.check("[Interface]\nPrivateKey = abc\nAddress = 10.0.0.2/32"),
        )
        assertEquals(ConfigProblem.TOO_LARGE, ConfigSanity.check("x".repeat(ConfigSanity.MAX_BYTES + 1)))
    }

    @Test
    fun sanityIsCaseAndSpacingTolerant() {
        assertNull(ConfigSanity.check("[interface]\nprivatekey=abc\n[PEER]\npublickey=x"))
    }

    @Test
    fun generatedProfilesPassTheSanityCheckInBothModes() {
        val profile = WarpProfile(
            name = "2N5A-WARP-AB12",
            privateKey = "priv",
            publicKey = "pub",
            peerPublicKey = WarpDefaults.PEER_PUBLIC_KEY,
            addressV4 = "172.16.0.2",
            addressV6 = "2606:4700:110::1",
        )

        assertNull(ConfigSanity.check(profile.toConfig(obfuscated = true)))
        assertNull(ConfigSanity.check(profile.toConfig(obfuscated = false)))
        assertTrue(TunnelNames.isValid(TunnelNames.sanitize(profile.name)))
        assertEquals(profile.name, TunnelNames.sanitize(profile.name))
    }

    @Test
    fun formatBytesPicksReadableUnits() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.5 MB", formatBytes(1_572_864))
        assertEquals("2.0 GB", formatBytes(2L * 1024 * 1024 * 1024))
        assertEquals("0 B", formatBytes(-5))
    }
}
