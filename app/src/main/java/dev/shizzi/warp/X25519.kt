package dev.shizzi.warp

import java.math.BigInteger
import java.security.SecureRandom
import java.util.Base64

/**
 * Curve25519 key agreement (RFC 7748), just enough to make a WireGuard key pair
 * on the device. Plain BigInteger arithmetic keeps it dependency-free and
 * runnable on a bare JVM; it runs once per generated profile, so speed is moot.
 */
internal object X25519 {

    private val P: BigInteger = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
    private val A24: BigInteger = BigInteger.valueOf(121665)
    private const val KEY_BYTES = 32

    /** The curve's base point: u = 9. */
    private val BASE_POINT = ByteArray(KEY_BYTES).also { it[0] = 9 }

    /** Clears the low three bits and sets bit 254, as RFC 7748 requires of a scalar. */
    fun clamp(scalar: ByteArray): ByteArray {
        require(scalar.size == KEY_BYTES) { "a Curve25519 scalar is 32 bytes" }

        return scalar.copyOf().also {
            it[0] = (it[0].toInt() and 248).toByte()
            it[31] = ((it[31].toInt() and 127) or 64).toByte()
        }
    }

    fun publicKey(privateKey: ByteArray): ByteArray = scalarMult(privateKey, BASE_POINT)

    fun scalarMult(scalar: ByteArray, point: ByteArray): ByteArray {
        require(scalar.size == KEY_BYTES && point.size == KEY_BYTES) { "keys are 32 bytes" }

        val k = decodeLittleEndian(clamp(scalar))
        val x1 = decodeLittleEndian(point).clearBit(255).mod(P)

        var x2 = BigInteger.ONE
        var z2 = BigInteger.ZERO
        var x3 = x1
        var z3 = BigInteger.ONE
        var swap = false

        for (bit in 254 downTo 0) {
            val kBit = k.testBit(bit)
            if (swap != kBit) {
                x2 = x3.also { x3 = x2 }
                z2 = z3.also { z3 = z2 }
            }
            swap = kBit

            val a = x2.add(z2).mod(P)
            val aa = a.multiply(a).mod(P)
            val b = x2.subtract(z2).mod(P)
            val bb = b.multiply(b).mod(P)
            val e = aa.subtract(bb).mod(P)
            val c = x3.add(z3).mod(P)
            val d = x3.subtract(z3).mod(P)
            val da = d.multiply(a).mod(P)
            val cb = c.multiply(b).mod(P)

            val sum = da.add(cb).mod(P)
            val diff = da.subtract(cb).mod(P)

            x3 = sum.multiply(sum).mod(P)
            z3 = x1.multiply(diff.multiply(diff).mod(P)).mod(P)
            x2 = aa.multiply(bb).mod(P)
            z2 = e.multiply(aa.add(A24.multiply(e)).mod(P)).mod(P)
        }

        if (swap) {
            x2 = x3.also { x3 = x2 }
            z2 = z3.also { z3 = z2 }
        }

        val result = x2.multiply(z2.modPow(P.subtract(BigInteger.valueOf(2)), P)).mod(P)
        return encodeLittleEndian(result)
    }

    private fun BigInteger.clearBit(index: Int): BigInteger = if (testBit(index)) flipBit(index) else this

    private fun decodeLittleEndian(bytes: ByteArray): BigInteger =
        BigInteger(1, bytes.reversedArray())

    private fun encodeLittleEndian(value: BigInteger): ByteArray {
        val bigEndian = value.toByteArray()
        val out = ByteArray(KEY_BYTES)

        // toByteArray may carry a leading sign byte, or be shorter than 32 bytes.
        for (i in 0 until minOf(bigEndian.size, KEY_BYTES)) {
            out[i] = bigEndian[bigEndian.size - 1 - i]
        }
        return out
    }
}

/** A WireGuard key pair, both halves in the base64 form config files use. */
data class WireGuardKeys(val privateKey: String, val publicKey: String) {

    companion object {
        fun generate(random: SecureRandom = SecureRandom()): WireGuardKeys {
            val seed = ByteArray(32).also(random::nextBytes)
            val privateKey = X25519.clamp(seed)

            return WireGuardKeys(
                privateKey = Base64.getEncoder().encodeToString(privateKey),
                publicKey = Base64.getEncoder().encodeToString(X25519.publicKey(privateKey)),
            )
        }
    }
}
