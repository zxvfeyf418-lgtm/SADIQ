package dev.shizzi

import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Bundle
import android.service.quicksettings.TileService
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.collectAsState
import androidx.core.view.WindowCompat
import dev.shizzi.ui.Screen
import dev.shizzi.ui.theme.Appearance
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.twon5a.WelcomeIntro
import dev.shizzi.warp.TunnelViewModel
import dev.shizzi.warp.WarpUiState
import dev.shizzi.warp.WarpViewModel
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val viewModel: SessionViewModel by viewModels()

    private val warpViewModel: WarpViewModel by viewModels()

    private val tunnelViewModel: TunnelViewModel by viewModels()

    // The tunnel to start once the system's VPN consent dialog is accepted.
    private var pendingTunnel: String? = null

    private val vpnConsentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val name = pendingTunnel
            pendingTunnel = null

            if (result.resultCode == Activity.RESULT_OK && name != null) {
                tunnelViewModel.connect(name)
            } else {
                tunnelViewModel.onVpnPermissionDenied()
            }
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) tunnelViewModel.importFrom(uri)
        }

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, granted ->
            viewModel.refreshShizukuState()
            viewModel.refreshPermissions()
            onShizukuResult(isGranted = granted == PackageManager.PERMISSION_GRANTED)
        }

    private val binderReceivedListener =
        Shizuku.OnBinderReceivedListener { viewModel.refreshShizukuState() }

    private val binderDeadListener =
        Shizuku.OnBinderDeadListener { viewModel.refreshShizukuState() }

    private var requested: AppPermission? = null

    private var isChaining = false

    private val asked = mutableSetOf<AppPermission>()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.refreshPermissions()
            onPermissionResult()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerShizukuListeners()

        // Long-pressing the quick settings tile lands on settings, like the stock hotspot tile.
        val startScreen = when (intent?.action) {
            TileService.ACTION_QS_TILE_PREFERENCES -> Screen.SETTINGS
            else -> Screen.HOME
        }

        setContent {
            val settings by viewModel.settings.collectAsState()
            val loaded = settings ?: return@setContent

            val appearance = Appearance(
                theme = loaded.theme,
                design = loaded.design,
                accent = loaded.accent,
            )

            ShizziTheme(appearance = appearance) {
                val colors = ShizziTheme.colors

                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView)
                        .isAppearanceLightStatusBars = !colors.isDark
                }

                Surface(color = colors.background) {
                    val state by viewModel.state.collectAsState()
                    val diagnostics by viewModel.diagnosticsState.collectAsState()
                    val compatibility by viewModel.compatibilityState.collectAsState()
                    val permissions by viewModel.permissionState.collectAsState()
                    val warp by warpViewModel.state.collectAsState()
                    val connection by tunnelViewModel.state.collectAsState()
                    var showIntro by rememberSaveable { mutableStateOf(true) }

                    Crossfade(targetState = showIntro, label = "intro") { isIntro ->
                      if (isIntro) {
                        WelcomeIntro(onFinished = { showIntro = false })
                      } else ShizziApp(
                        state = AppState(
                            session = state,
                            settings = loaded,
                            diagnostics = diagnostics,
                            permissions = permissions,
                            warp = warp,
                            connection = connection,
                        ),
                        onboarding = OnboardingEntry(
                            compatibility = compatibility,
                            onCheckCompatibility = viewModel::checkCompatibility,
                            onDownloadTetheringApex = viewModel::downloadTetheringApex,
                            onInstallTetheringApex = viewModel::installTetheringApex,
                            onRebootDevice = viewModel::rebootDevice,
                            onComplete = viewModel::completeOnboarding,
                        ),
                        actions = AppActions(
                            onToggle = viewModel::toggle,
                            onCancel = viewModel::cancel,
                            onRequestPermission = viewModel::requestPermission,
                            onRequestAllPermissions = ::requestAllPermissions,
                            onGrantPermission = ::grantPermission,
                            onShizukuAction = viewModel::actOnShizuku,
                            onSetTheme = viewModel::setTheme,
                            onSetDesign = viewModel::setDesign,
                            onSetAccent = viewModel::setAccent,
                            onAddCustomAccent = viewModel::addCustomAccent,
                            onSetLogging = viewModel::setLogging,
                            onSetVpnMode = viewModel::setVpnMode,
                            onSetUsbTethering = viewModel::setUsbTethering,
                            onRunProbes = viewModel::runProbes,
                            onDismissDiagnostics = viewModel::dismissDiagnostics,
                            onClearLog = viewModel::clearLog,
                            onRestartOnboarding = viewModel::restartOnboarding,
                            onSetAutomation = viewModel::setAutomation,
                            onRegenerateAutomationToken =
                                viewModel::regenerateAutomationToken,
                            onGenerateWarp = warpViewModel::generate,
                            onSetWarpObfuscated = warpViewModel::setObfuscated,
                            onSelectWarpEndpoint = warpViewModel::selectEndpoint,
                            onSaveWarp = warpViewModel::saveToDownloads,
                            onWarpMessage = warpViewModel::showMessage,
                            onSelectConfig = tunnelViewModel::select,
                            onDeleteConfig = tunnelViewModel::delete,
                            onImportConfig = { importLauncher.launch(arrayOf("*/*")) },
                            onConnect = ::connectTunnel,
                            onDisconnect = tunnelViewModel::disconnect,
                            onRunWarpInApp = ::runWarpInApp,
                            onDismissConnectionMessage = tunnelViewModel::dismissMessage,
                        ),
                        startScreen = startScreen,
                      )
                    }
                }
            }
        }

        holdFirstFrameUntilSettingsLoad()
    }

    /** Asks for the system's VPN consent first when it has not been granted yet. */
    private fun connectTunnel(name: String) {
        val consent = VpnService.prepare(this)

        if (consent == null) {
            tunnelViewModel.connect(name)
        } else {
            pendingTunnel = name
            vpnConsentLauncher.launch(consent)
        }
    }

    /** Saves the generated profile as it is currently shown, then starts it. */
    private fun runWarpInApp() {
        val done = warpViewModel.state.value as? WarpUiState.Done ?: return

        tunnelViewModel.saveGenerated(
            rawName = done.profile.name,
            text = done.profile.toConfig(done.isObfuscated),
            onSaved = ::connectTunnel,
        )
    }

    private fun holdFirstFrameUntilSettingsLoad() {
        val content = findViewById<View>(android.R.id.content)

        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (viewModel.settings.value == null) return false

                    content.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
            },
        )
    }

    private fun requestAllPermissions() {
        isChaining = true
        requestNextOutstanding()
    }

    private fun requestNextOutstanding() {
        if (requestShizuku()) return

        val outstanding = viewModel.permissionState.value.firstOrNull { !it.isGranted }
        val permission = outstanding?.permission

        if (permission == null) {
            isChaining = false
            return
        }

        grantPermission(permission)
    }

    // Only PermissionRequired reports back through the Shizuku listener; the other
    // states hand off to another app, so the chain ends there.
    private fun requestShizuku(): Boolean {
        val state = viewModel.state.value.shizukuState
        if (state is ShizukuState.Ready) return false

        if (state !is ShizukuState.PermissionRequired) stopChain()

        viewModel.actOnShizuku()
        return true
    }

    // A permission with no manifest name is granted on a settings screen in
    // another app, so there is no dialog to launch and no result to wait for.
    private fun grantPermission(permission: AppPermission) {
        val name = permission.manifestName

        if (name == null || isDialogSuppressed(permission)) {
            stopChain()
            viewModel.openPermissionSettings(permission)
            return
        }

        requested = permission
        asked += permission
        permissionLauncher.launch(name)
    }

    private fun onPermissionResult() {
        val permission = requested ?: return
        requested = null

        if (!isChaining) return

        if (viewModel.isPermissionGranted(permission)) requestNextOutstanding() else stopChain()
    }

    private fun onShizukuResult(isGranted: Boolean) {
        if (!isChaining) return

        if (isGranted) requestNextOutstanding() else stopChain()
    }

    private fun stopChain() {
        isChaining = false
    }

    // shouldShowRequestPermissionRationale is also false before the first ask, so
    // only a permission this process has already requested can be suppressed.
    private fun isDialogSuppressed(permission: AppPermission): Boolean {
        val name = permission.manifestName ?: return false
        if (permission !in asked) return false

        return !shouldShowRequestPermissionRationale(name)
    }

    private fun registerShizukuListeners() {
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshShizukuState()
        viewModel.refreshPermissions()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        super.onDestroy()
    }
}
