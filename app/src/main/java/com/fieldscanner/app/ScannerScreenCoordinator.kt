package com.fieldscanner.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.view.View
import androidx.core.content.ContextCompat
import com.fieldscanner.app.audio.AudioPlaybackController
import com.fieldscanner.app.camera.CameraController
import com.fieldscanner.app.data.diagnostics.DiagnosticsLog
import com.fieldscanner.app.data.finding.Finding
import com.fieldscanner.app.journal.JournalCoordinator
import com.fieldscanner.app.runes.RuneAction
import com.fieldscanner.app.runes.RuneBindings
import com.fieldscanner.app.runes.RuneContext
import com.fieldscanner.app.runes.toRuneContext
import com.fieldscanner.app.voice.LanguageChangeController
import com.fieldscanner.app.voice.VoiceSearchController
import java.io.File

/**
 * Owns scanner, photo-review and journal screen flows. Rune actions only push a new
 * [ScannerUiState]; the screen itself is drawn in one place — [render], a pure
 * [ScannerScreenModel] applied to the `View` idempotently and subscribed to
 * `ScannerViewModel.state` in `MainActivity` (TASK-028). Only genuinely one-shot work
 * (audio, shutter flash, error pulse, permission prompts) stays outside [render].
 */
class ScannerScreenCoordinator(
    private val activity: Activity,
    private val screen: MainScreenView,
    private val scanner: ScannerViewModel,
    private val runes: RuneBindings,
    private val audio: AudioPlaybackController,
    private val journal: JournalCoordinator,
    private val diagnostics: DiagnosticsLog,
    private val requestCameraPermission: () -> Unit,
    private val requestLocationPermission: () -> Unit,
    private val requestAudioPermission: () -> Unit,
    private val contactsVisible: () -> Boolean
) {
    private lateinit var camera: CameraController
    private lateinit var voiceSearch: VoiceSearchController
    private lateinit var languageChange: LanguageChangeController

    /** Pending microphone-permission continuation; not screen state, so it is not part of [ScannerUiState]. */
    private var audioPermissionTarget: (() -> Unit)? = null

    /** Last model applied to the `View`; guards [render] against re-applying an unchanged screen. */
    private var renderedModel: ScannerScreenModel? = null

    fun attach(camera: CameraController, voiceSearch: VoiceSearchController, languageChange: LanguageChangeController) {
        this.camera = camera
        this.voiceSearch = voiceSearch
        this.languageChange = languageChange
        // Language mode is an external rune-context source; its end is not a scanner-state change.
        languageChange.onFinished = { refreshRuneHints() }
    }

    fun initialize() {
        scanner.showCameraInactive()
    }

    // ----- state-driven rendering -------------------------------------------------------------

    /** Applies [model] to the screen, touching only what changed since the previous model. */
    fun render(model: ScannerScreenModel) {
        val previous = renderedModel
        if (previous == model) return
        renderedModel = model

        if (previous?.centerShowsCameraFeed != model.centerShowsCameraFeed) {
            if (model.centerShowsCameraFeed) screen.displayZones.showCentralCamera() else screen.displayZones.hideCentralCamera()
        }
        if (previous?.photo != model.photo) {
            val wasHidden = previous?.photo == null || previous.photo is PhotoTarget.None
            applyPhoto(model.photo)
            if (wasHidden && model.photo !is PhotoTarget.None) screen.displayZones.bringToFront()
        }
        if (previous?.journalDetails != model.journalDetails) {
            screen.displayZones.setJournalDetails(model.journalDetails?.title, model.journalDetails?.latitude, model.journalDetails?.longitude)
        }
        if (previous?.bottomStatus != model.bottomStatus) applyBottomStatus(model.bottomStatus)
        // Rune hints are derived from state here; contacts / language-change contexts are refreshed
        // by their own owners because their source is external to the scanner state.
        if (previous?.runeContext != model.runeContext) refreshRuneHints()
        if (previous?.cameraPreviewActive != model.cameraPreviewActive) applyCameraPreview(model.cameraPreviewActive)
    }

    private fun applyPhoto(target: PhotoTarget) = when (target) {
        PhotoTarget.None -> screen.photo.visibility = View.GONE
        is PhotoTarget.CapturedFile -> showPhoto(Uri.fromFile(target.file))
        is PhotoTarget.SavedFinding -> showPhoto(Uri.parse(target.uri))
    }

    private fun showPhoto(uri: Uri) {
        screen.photo.setImageURI(uri)
        screen.photo.visibility = View.VISIBLE
    }

    private fun applyBottomStatus(target: BottomStatusTarget) = when (target) {
        BottomStatusTarget.Idle -> screen.displayZones.showIdleBottomStatus()
        BottomStatusTarget.Camera -> screen.displayZones.showCameraBottomStatus()
        BottomStatusTarget.PhotoReview -> screen.displayZones.showPhotoReviewBottomStatus()
        BottomStatusTarget.Analyzing -> screen.displayZones.showAnalyzingBottomStatus()
        is BottomStatusTarget.AnalysisResult -> screen.displayZones.showAnalysisResultBottomStatus(target.visual)
        BottomStatusTarget.DeleteConfirmation -> screen.displayZones.showDeleteConfirmationBottomStatus()
        BottomStatusTarget.Hidden -> screen.displayZones.hideBottomStatus()
    }

    /** Enter/leave the CameraX preview. The only entry into an active preview is the `Camera` state. */
    private fun applyCameraPreview(active: Boolean) {
        if (active) {
            if (hasPermission(Manifest.permission.CAMERA)) camera.start() else requestCameraPermission()
        } else {
            camera.stop()
        }
    }

    // ----- lifecycle & rune-context glue ----------------------------------------------------

    fun onCameraPermissionResult(granted: Boolean) {
        if (granted && screen.displayZones.isCentralCameraVisible()) camera.start()
        else if (!granted) { screen.displayZones.hideCentralCamera(); showCameraUnavailable("camera permission denied") }
    }

    fun destroy() { camera.stop(); audio.release(); voiceSearch.stop(); languageChange.stop() }
    fun activeRuneContext(): RuneContext = when {
        ::languageChange.isInitialized && languageChange.isActive() -> RuneContext.LANGUAGE_CHANGE
        contactsVisible() -> RuneContext.CONTACTS
        else -> scanner.state.value.toRuneContext()
    }
    fun refreshRuneHints() = screen.displayZones.setAvailableHintBindings(runes.availableHintBindings(activeRuneContext()))

    /** Camera cannot run (permission, provider, rear lens). No text for the child: reason to the log, textless pulse on screen. */
    fun showCameraUnavailable(reason: String) {
        diagnostics.error("ScannerScreenCoordinator", "Camera unavailable: $reason")
        screen.displayZones.playErrorSignal()
    }

    fun execute(action: RuneAction): Boolean {
        if (::languageChange.isInitialized && languageChange.isActive()) { routeLanguageChangeRune(action); return true }
        return executeAction(action)
    }

    private fun routeLanguageChangeRune(action: RuneAction) = when (action) {
        RuneAction.ConfirmLanguageChange -> languageChange.onConfirmKey()
        RuneAction.CycleLanguageChange -> languageChange.onCycleKey()
        RuneAction.CancelLanguageChange -> languageChange.onCancelKey()
        else -> Unit
    }

    private fun executeAction(action: RuneAction): Boolean = when (action) {
        RuneAction.ActivateCamera, RuneAction.ContinueScanning -> { showCameraMode(); true }
        RuneAction.ReturnToInactiveCamera -> { scanner.showCameraInactive(); true }
        RuneAction.OpenJournal -> { scanner.loadJournal(); true }
        RuneAction.TakePhoto -> { takePhoto(); true }
        RuneAction.SavePhotoWithoutAnalysis -> { savePhotoWithoutAnalysis(); true }
        RuneAction.DiscardPhotoAndReturnToInactiveCamera -> { discardPhotoAndReturnToInactiveCamera(); true }
        RuneAction.AnalyzePhoto -> { acceptPhoto(); true }
        RuneAction.CancelAnalysis -> { scanner.cancelAnalysis(); true }
        RuneAction.PlayAnalysisResultAudio -> { playAnalysisResultAudio(); true }
        RuneAction.DeleteCurrentFinding -> { requestDeleteCurrentFinding(); true }
        RuneAction.ConfirmFindingDeletion -> { scanner.deleteCurrentFinding(); true }
        RuneAction.CancelFindingDeletion -> { cancelFindingDeletion(); true }
        RuneAction.MoveJournalPrevious -> { journal.move(-1); true }
        RuneAction.MoveJournalNext -> { journal.move(1); true }
        RuneAction.PlayJournalAudio -> { playJournalAudio(); true }
        RuneAction.StartJournalVoiceSearch -> { startJournalVoiceSearch(); true }
        RuneAction.StartLanguageChange -> { startLanguageChange(); true }
        else -> false
    }

    fun handleEffect(effect: ScannerEffect) = when (effect) {
        ScannerEffect.AnalysisFailed -> screen.displayZones.playErrorSignal()
        ScannerEffect.PhotoSaveFailed -> screen.displayZones.playErrorSignal()
        is ScannerEffect.AnalysisResultReady -> { effect.audio?.let(audio::play); Unit }
        ScannerEffect.FindingDeleteFailed -> screen.displayZones.playErrorSignal()
        is ScannerEffect.JournalLoaded -> showLoadedJournal(effect.operationId, effect.findings)
    }

    fun applyVoiceSearch(candidates: List<String>) { journal.filter(candidates) }

    // ----- rune-action handlers: push state, do one-shot work only -------------------------

    private fun showCameraMode() { audio.release(); scanner.showCamera() }

    private fun takePhoto() {
        screen.displayZones.playCaptureFlash()
        camera.takePhoto(
            { diagnostics.error("ScannerScreenCoordinator", "Photo capture requested before camera was ready"); screen.displayZones.playErrorSignal() },
            { screen.displayZones.playErrorSignal() }
        )
    }

    /** CameraX capture callback: promote the temporary file into the [ScannerUiState.PhotoReview] state. */
    fun showPhotoReview(file: File) { scanner.showPhotoReview(file) }

    private fun acceptPhoto() { if (!hasLocationPermission()) requestLocationPermission(); analyzeAcceptedPhoto() }
    private fun analyzeAcceptedPhoto() { reviewPhoto()?.let(scanner::analyze) }
    private fun savePhotoWithoutAnalysis() { reviewPhoto()?.let(scanner::savePhotoWithoutAnalysis); showCameraMode() }
    private fun discardPhotoAndReturnToInactiveCamera() { reviewPhoto()?.delete(); scanner.showCameraInactive() }
    private fun reviewPhoto(): File? = (scanner.state.value as? ScannerUiState.PhotoReview)?.photo

    private fun showLoadedJournal(operationId: Long, findings: List<Finding>) {
        if (journal.showLoaded(operationId, findings) == null) scanner.showCameraInactive()
    }

    private fun playJournalAudio() { if (journal.isVisible()) journal.current()?.audioUri?.let(Uri::parse)?.path?.let(::File)?.let(audio::play) }
    private fun playAnalysisResultAudio() {
        (scanner.state.value as? ScannerUiState.AnalysisResult)?.finding?.audioUri?.let(Uri::parse)?.path?.let(::File)?.let(audio::play)
    }

    /** Journal and analysis-result deletion both route through the textless bottom-status confirmation state. */
    private fun requestDeleteCurrentFinding() {
        if (!scanner.requestJournalFindingDeletion()) scanner.requestAnalysisResultDeletion()
    }

    private fun cancelFindingDeletion() = when (scanner.state.value) {
        is ScannerUiState.JournalDeleteConfirmation -> scanner.cancelJournalFindingDeletion()
        is ScannerUiState.AnalysisResultDeleteConfirmation -> scanner.cancelAnalysisResultDeletion()
        else -> Unit
    }

    private fun startJournalVoiceSearch() {
        if (!journal.isVisible()) return
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) { audioPermissionTarget = ::startJournalVoiceSearch; requestAudioPermission() } else voiceSearch.start()
    }
    private fun startLanguageChange() {
        if (!journal.isVisible()) return
        // The language-switch mode is a key-driven carousel: no microphone needed. Its rune context
        // is external to the scanner state, so refresh the hints explicitly here.
        languageChange.start()
        refreshRuneHints()
    }
    fun onAudioPermissionResult(granted: Boolean) {
        val target = audioPermissionTarget
        audioPermissionTarget = null
        if (granted) target?.invoke()
    }
    private fun hasLocationPermission() = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    private fun hasPermission(permission: String) = ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
}
