package com.fieldscanner.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.fieldscanner.app.audio.AudioPlaybackController
import com.fieldscanner.app.calls.*
import com.fieldscanner.app.camera.CameraController
import com.fieldscanner.app.journal.JournalCoordinator
import com.fieldscanner.app.runes.*
import com.fieldscanner.app.voice.LanguageChangeController
import com.fieldscanner.app.voice.VoiceSearchController
import kotlinx.coroutines.launch

/** Thin Android lifecycle and Activity Result host for the scanner screen. */
class MainActivity : AppCompatActivity() {
    private lateinit var screen: MainScreenView
    /** Seam for the startup smoke test (TASK-020); production always builds the real graph. */
    internal var containerFactory: (Context) -> AppContainer = ::AppContainer
    private val container by lazy { containerFactory(applicationContext) }
    private val scanner by lazy { ViewModelProvider(this, ScannerViewModelFactory(container))[ScannerViewModel::class.java] }
    private val bindings = RuneBindings()
    private lateinit var scannerScreen: ScannerScreenCoordinator
    private lateinit var callUi: CallUiController
    private lateinit var callScreen: CallScreenPresenter

    private val requestCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { scannerScreen.onCameraPermissionResult(it) }
    private val requestLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    private val requestAudio = registerForActivityResult(ActivityResultContracts.RequestPermission()) { scannerScreen.onAudioPermissionResult(it) }
    private val requestCall = registerForActivityResult(ActivityResultContracts.RequestPermission()) { callUi.onCallPermissionResult(it) }
    private val requestDialer = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { callUi.onDefaultDialerResult() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        screen = MainScreenView.bind(findViewById(android.R.id.content))
        buildScreen()
        enterFullscreen()
        scannerScreen.initialize()
    }

    /**
     * Builds the scanner screen object graph. Collaborators are constructed in dependency
     * order — `callUi` before `scannerScreen`, `scannerScreen` before `camera`/`voice`/
     * `callScreen` — so no constructor argument or bound method reference reads a `lateinit`
     * field before it is assigned. Keep this order when adding wiring.
     */
    private fun buildScreen() {
        screen.displayZones.setLeftDisplayInputs(container.leftDisplay)
        val audio = AudioPlaybackController()
        val journal = JournalCoordinator(scanner)

        callUi = CallUiController(
            this,
            requestDialer::launch,
            { requestCall.launch(Manifest.permission.CALL_PHONE) },
            ::showPhoneError,
            container.diagnostics
        )

        scannerScreen = ScannerScreenCoordinator(
            this, screen, scanner, bindings, audio, journal, container.diagnostics,
            { requestCamera.launch(Manifest.permission.CAMERA) },
            { requestLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
            { requestAudio.launch(Manifest.permission.RECORD_AUDIO) },
            { callUi.isPickerVisible }
        )

        val camera = CameraController(
            this, this, screen.cameraPreview, screen.displayZones::isCentralCameraVisible,
            { scannerScreen.showCameraUnavailable("camera provider unavailable") },
            { scannerScreen.showCameraUnavailable("rear camera binding failed") },
            scannerScreen::showPhotoReview,
            container.diagnostics
        )
        val voice = VoiceSearchController(this, { container.languagePreference.current() }, scannerScreen::applyVoiceSearch)
        val languageChange = LanguageChangeController.create(this, container.languagePreference, container.speech)
        scannerScreen.attach(camera, voice, languageChange)

        callScreen = CallScreenPresenter(
            screen.displayZones, camera, voice,
            callUi::selectionFor, callUi::closePicker, scannerScreen::refreshRuneHints
        )

        val router = RuneActionRouter(screen, scannerScreen, callUi)
        val dispatcher = RuneInputDispatcher(bindings, scannerScreen::activeRuneContext, router::execute, router::execute)

        lifecycleScope.launch { scanner.state.collect { scannerScreen.render(scannerScreenModel(it)) } }
        lifecycleScope.launch { scanner.effects.collect(scannerScreen::handleEffect) }
        lifecycleScope.launch { CallCoordinator.state.collect(callScreen::render) }
        screen.displayZones.onHintRunePressed = { if (!callScreen.handleRune(it)) dispatcher.onRunePressed(it) }
        screen.displayZones.onHintRuneLongPressed = dispatcher::onRuneLongPressed
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) enterFullscreen() }
    override fun onDestroy() { scannerScreen.destroy(); super.onDestroy() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); callScreen.render(CallCoordinator.state.value) }

    private fun enterFullscreen() = WindowInsetsControllerCompat(window, screen.cameraPreview).apply { hide(WindowInsetsCompat.Type.systemBars()); systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE }
    // Telephony failures stay textless for the child: the reason goes to the owner diagnostics log,
    // the child sees only the shared error pulse in the lower display zone.
    private fun showPhoneError(reason: String) {
        container.diagnostics.error("MainActivity", "Phone call failed: $reason")
        screen.displayZones.playErrorSignal()
    }
    companion object { const val EXTRA_SHOW_CALL_UI = "com.fieldscanner.app.extra.SHOW_CALL_UI" }
}
