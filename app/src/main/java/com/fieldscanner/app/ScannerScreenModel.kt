package com.fieldscanner.app

import com.fieldscanner.app.data.finding.ScanVisual
import com.fieldscanner.app.runes.RuneContext
import com.fieldscanner.app.runes.toRuneContext
import java.io.File

/**
 * Pure description of what the scanner screen must show for a given [ScannerUiState].
 *
 * It contains no Android types and no timestamps, so [scannerScreenModel] is a total function that
 * is unit-tested against every [ScannerUiState] subtype. `ScannerScreenCoordinator.render` applies
 * this model to the `View` idempotently; one-shot actions (audio playback, the shutter flash, the
 * error pulse, permission prompts beyond the camera preview) stay in `ScannerEffect` / rune
 * handlers and never live here — re-applying the same model must have no visible effect.
 */
data class ScannerScreenModel(
    val bottomStatus: BottomStatusTarget,
    /** Whether the central zone punches through to the live camera / captured photo behind it. */
    val centerShowsCameraFeed: Boolean,
    /** Whether the CameraX preview should be bound. Only the `Camera` state can turn this on. */
    val cameraPreviewActive: Boolean,
    val photo: PhotoTarget,
    /** Debug-only journal overlay (title + coordinates); `null` outside a populated journal entry. */
    val journalDetails: JournalDetails?,
    val runeContext: RuneContext
)

sealed interface BottomStatusTarget {
    object Idle : BottomStatusTarget
    object Camera : BottomStatusTarget
    object PhotoReview : BottomStatusTarget
    object Analyzing : BottomStatusTarget
    data class AnalysisResult(val visual: ScanVisual) : BottomStatusTarget
    object DeleteConfirmation : BottomStatusTarget
    object Hidden : BottomStatusTarget
}

sealed interface PhotoTarget {
    object None : PhotoTarget
    /** The temporary capture shown during review/analysis, addressed by file. */
    data class CapturedFile(val file: File) : PhotoTarget
    /** A persisted finding image, addressed by its stored URI string. */
    data class SavedFinding(val uri: String) : PhotoTarget
}

data class JournalDetails(val title: String?, val latitude: Double?, val longitude: Double?)

/** The single mapping from scanner state to screen description. Keep it pure. */
fun scannerScreenModel(state: ScannerUiState): ScannerScreenModel = when (state) {
    is ScannerUiState.CameraInactive -> ScannerScreenModel(
        bottomStatus = BottomStatusTarget.Idle,
        centerShowsCameraFeed = false,
        cameraPreviewActive = false,
        photo = PhotoTarget.None,
        journalDetails = null,
        runeContext = state.toRuneContext()
    )
    is ScannerUiState.Camera -> ScannerScreenModel(
        bottomStatus = BottomStatusTarget.Camera,
        centerShowsCameraFeed = true,
        cameraPreviewActive = true,
        photo = PhotoTarget.None,
        journalDetails = null,
        runeContext = state.toRuneContext()
    )
    is ScannerUiState.PhotoReview -> ScannerScreenModel(
        bottomStatus = BottomStatusTarget.PhotoReview,
        centerShowsCameraFeed = true,
        cameraPreviewActive = true,
        photo = PhotoTarget.CapturedFile(state.photo),
        journalDetails = null,
        runeContext = state.toRuneContext()
    )
    is ScannerUiState.Analyzing -> ScannerScreenModel(
        bottomStatus = BottomStatusTarget.Analyzing,
        centerShowsCameraFeed = true,
        cameraPreviewActive = true,
        photo = PhotoTarget.CapturedFile(state.photo),
        journalDetails = null,
        runeContext = state.toRuneContext()
    )
    is ScannerUiState.AnalysisResult -> ScannerScreenModel(
        bottomStatus = BottomStatusTarget.AnalysisResult(ScanVisual.resolve(state.finding.visualId)),
        centerShowsCameraFeed = true,
        cameraPreviewActive = true,
        photo = PhotoTarget.SavedFinding(state.finding.photoUri),
        journalDetails = null,
        runeContext = state.toRuneContext()
    )
    is ScannerUiState.AnalysisResultDeleteConfirmation -> ScannerScreenModel(
        bottomStatus = BottomStatusTarget.DeleteConfirmation,
        centerShowsCameraFeed = true,
        cameraPreviewActive = true,
        photo = PhotoTarget.SavedFinding(state.finding.photoUri),
        journalDetails = null,
        runeContext = state.toRuneContext()
    )
    is ScannerUiState.JournalLoading -> ScannerScreenModel(
        bottomStatus = BottomStatusTarget.Hidden,
        centerShowsCameraFeed = false,
        cameraPreviewActive = false,
        photo = PhotoTarget.None,
        journalDetails = null,
        runeContext = state.toRuneContext()
    )
    is ScannerUiState.Journal -> ScannerScreenModel(
        bottomStatus = BottomStatusTarget.Hidden,
        centerShowsCameraFeed = true,
        cameraPreviewActive = false,
        photo = state.finding?.let { PhotoTarget.SavedFinding(it.photoUri) } ?: PhotoTarget.None,
        journalDetails = state.finding?.let { JournalDetails(it.title, it.latitude, it.longitude) },
        runeContext = state.toRuneContext()
    )
    is ScannerUiState.JournalDeleteConfirmation -> ScannerScreenModel(
        bottomStatus = BottomStatusTarget.DeleteConfirmation,
        centerShowsCameraFeed = true,
        cameraPreviewActive = false,
        photo = state.finding?.let { PhotoTarget.SavedFinding(it.photoUri) } ?: PhotoTarget.None,
        journalDetails = state.finding?.let { JournalDetails(it.title, it.latitude, it.longitude) },
        runeContext = state.toRuneContext()
    )
}
