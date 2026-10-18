package com.fieldscanner.app

import com.fieldscanner.app.data.finding.Finding
import com.fieldscanner.app.data.finding.ScanVisual
import com.fieldscanner.app.runes.RuneContext
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the pure state -> screen mapping for every [ScannerUiState] subtype (TASK-028).
 * These assertions are the contract the idempotent `ScannerScreenCoordinator.render` relies on.
 */
class ScannerScreenModelTest {

    @Test fun `camera inactive is idle with no feed, no preview and no photo`() {
        val model = scannerScreenModel(ScannerUiState.CameraInactive())

        assertEquals(BottomStatusTarget.Idle, model.bottomStatus)
        assertFalse(model.centerShowsCameraFeed)
        assertFalse(model.cameraPreviewActive)
        assertEquals(PhotoTarget.None, model.photo)
        assertNull(model.journalDetails)
        assertEquals(RuneContext.CAMERA_INACTIVE, model.runeContext)
    }

    @Test fun `camera runs the preview behind an open centre and shows no photo`() {
        val model = scannerScreenModel(ScannerUiState.Camera())

        assertEquals(BottomStatusTarget.Camera, model.bottomStatus)
        assertTrue(model.centerShowsCameraFeed)
        assertTrue(model.cameraPreviewActive)
        assertEquals(PhotoTarget.None, model.photo)
        assertEquals(RuneContext.CAMERA, model.runeContext)
    }

    @Test fun `photo review shows the captured file while the preview keeps running`() {
        val photo = File("SCAN_1.jpg")

        val model = scannerScreenModel(ScannerUiState.PhotoReview(1, photo))

        assertEquals(BottomStatusTarget.PhotoReview, model.bottomStatus)
        assertTrue(model.centerShowsCameraFeed)
        assertTrue(model.cameraPreviewActive)
        assertEquals(PhotoTarget.CapturedFile(photo), model.photo)
        assertNull(model.journalDetails)
        assertEquals(RuneContext.PHOTO_REVIEW, model.runeContext)
    }

    @Test fun `analyzing shows the identical captured file as photo review so render is a no-op`() {
        val photo = File("SCAN_1.jpg")

        val analyzing = scannerScreenModel(ScannerUiState.Analyzing(2, photo))

        assertEquals(BottomStatusTarget.Analyzing, analyzing.bottomStatus)
        assertTrue(analyzing.cameraPreviewActive)
        assertEquals(scannerScreenModel(ScannerUiState.PhotoReview(1, photo)).photo, analyzing.photo)
        assertEquals(RuneContext.ANALYZING, analyzing.runeContext)
    }

    @Test fun `analysis result morphs the bottom status to the finding visual and shows the saved image`() {
        val finding = finding(visualId = ScanVisual.CAT.id, photoUri = "file:///findings/1.jpg")

        val model = scannerScreenModel(ScannerUiState.AnalysisResult(3, finding))

        assertEquals(BottomStatusTarget.AnalysisResult(ScanVisual.CAT), model.bottomStatus)
        assertTrue(model.centerShowsCameraFeed)
        assertTrue(model.cameraPreviewActive)
        assertEquals(PhotoTarget.SavedFinding("file:///findings/1.jpg"), model.photo)
        assertNull(model.journalDetails)
        assertEquals(RuneContext.ANALYSIS_RESULT, model.runeContext)
    }

    @Test fun `an unknown finding visual falls back to UNKNOWN_OBJECT`() {
        val model = scannerScreenModel(ScannerUiState.AnalysisResult(3, finding(visualId = "not-a-real-id")))

        assertEquals(BottomStatusTarget.AnalysisResult(ScanVisual.UNKNOWN_OBJECT), model.bottomStatus)
    }

    @Test fun `analysis-result delete confirmation keeps the same image and only swaps the bottom status`() {
        val finding = finding(photoUri = "file:///findings/1.jpg")

        val model = scannerScreenModel(ScannerUiState.AnalysisResultDeleteConfirmation(4, finding))

        assertEquals(BottomStatusTarget.DeleteConfirmation, model.bottomStatus)
        assertEquals(PhotoTarget.SavedFinding("file:///findings/1.jpg"), model.photo)
        assertTrue(model.centerShowsCameraFeed)
        assertEquals(RuneContext.ANALYSIS_RESULT_DELETE_CONFIRMATION, model.runeContext)
    }

    @Test fun `journal loading hides the bottom status and shows nothing yet`() {
        val model = scannerScreenModel(ScannerUiState.JournalLoading(5))

        assertEquals(BottomStatusTarget.Hidden, model.bottomStatus)
        assertFalse(model.centerShowsCameraFeed)
        assertFalse(model.cameraPreviewActive)
        assertEquals(PhotoTarget.None, model.photo)
        assertNull(model.journalDetails)
        assertEquals(RuneContext.JOURNAL_LOADING, model.runeContext)
    }

    @Test fun `a populated journal shows the finding photo, its overlay and no camera preview`() {
        val finding = finding(photoUri = "file:///findings/9.jpg", title = "Oak", latitude = 1.5, longitude = 2.5)

        val model = scannerScreenModel(ScannerUiState.Journal(6, finding))

        assertEquals(BottomStatusTarget.Hidden, model.bottomStatus)
        assertTrue(model.centerShowsCameraFeed)
        assertFalse(model.cameraPreviewActive)
        assertEquals(PhotoTarget.SavedFinding("file:///findings/9.jpg"), model.photo)
        assertEquals(JournalDetails("Oak", 1.5, 2.5), model.journalDetails)
        assertEquals(RuneContext.JOURNAL, model.runeContext)
    }

    @Test fun `an empty journal filter result hides the photo and the overlay`() {
        val model = scannerScreenModel(ScannerUiState.Journal(6, finding = null))

        assertEquals(PhotoTarget.None, model.photo)
        assertNull(model.journalDetails)
        assertEquals(RuneContext.JOURNAL, model.runeContext)
    }

    @Test fun `journal delete confirmation keeps the entry visible under the trash bottom status`() {
        val finding = finding(photoUri = "file:///findings/9.jpg", title = "Oak", latitude = 1.5, longitude = 2.5)

        val model = scannerScreenModel(ScannerUiState.JournalDeleteConfirmation(7, finding))

        assertEquals(BottomStatusTarget.DeleteConfirmation, model.bottomStatus)
        assertTrue(model.centerShowsCameraFeed)
        assertFalse(model.cameraPreviewActive)
        assertEquals(PhotoTarget.SavedFinding("file:///findings/9.jpg"), model.photo)
        assertEquals(JournalDetails("Oak", 1.5, 2.5), model.journalDetails)
        assertEquals(RuneContext.JOURNAL_DELETE_CONFIRMATION, model.runeContext)
    }

    @Test fun `the model is a value type so an unchanged state renders identically`() {
        val photo = File("SCAN_1.jpg")

        assertEquals(
            scannerScreenModel(ScannerUiState.PhotoReview(1, photo)),
            scannerScreenModel(ScannerUiState.PhotoReview(1, photo))
        )
    }

    private fun finding(
        visualId: String = ScanVisual.UNKNOWN_OBJECT.id,
        photoUri: String = "file:///findings/x.jpg",
        title: String = "",
        latitude: Double? = null,
        longitude: Double? = null
    ) = Finding("id", photoUri, title, "", 1.0, visualId, null, latitude, longitude, null, 0L)
}
