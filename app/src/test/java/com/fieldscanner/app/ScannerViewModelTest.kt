package com.fieldscanner.app

import com.fieldscanner.app.data.finding.AnalysisResult
import com.fieldscanner.app.data.finding.Finding
import com.fieldscanner.app.data.finding.FindingStore
import com.fieldscanner.app.data.finding.ScanVisual
import com.fieldscanner.app.data.analysis.ImageAnalyzer
import com.fieldscanner.app.data.speech.SpeechSynthesizer
import java.io.File
import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.After
import org.junit.Before
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }
    private fun viewModel(store: FakeStore = FakeStore()) = ScannerViewModel(FakeAnalyzer(), store, FakeSpeech())
    @Test
    fun `cancelled analysis result is ignored`() {
        val viewModel = viewModel()
        val analysisId = viewModel.beginAnalysis(File("photo.jpg"))

        viewModel.cancelAnalysis()

        assertFalse(viewModel.isActiveAnalysis(analysisId))
    }

    @Test
    fun `stale journal load is ignored`() {
        val viewModel = viewModel()
        val firstLoadId = viewModel.beginJournalLoad()
        val secondLoadId = viewModel.beginJournalLoad()

        viewModel.showJournal(firstLoadId)

        assertFalse(viewModel.isJournalVisible())
        viewModel.showJournal(secondLoadId)
        assertTrue(viewModel.isJournalVisible())
    }

    @Test
    fun `journal filter resets selection to first matching finding`() {
        val viewModel = viewModel()
        val operationId = viewModel.beginJournalLoad()
        viewModel.setJournal(operationId, listOf(finding("Oak"), finding("Pine")))

        val selected = viewModel.filterJournal(listOf("pine"))

        assertEquals("Pine", selected?.title)
    }

    @Test
    fun `journal filter uses the injected locale and still matches titles`() {
        val viewModel = ScannerViewModel(FakeAnalyzer(), FakeStore(), FakeSpeech()) { Locale.ENGLISH }
        val operationId = viewModel.beginJournalLoad()
        viewModel.setJournal(operationId, listOf(finding("Oak"), finding("Pine Cone")))

        assertEquals("Pine Cone", viewModel.filterJournal(listOf("PINE"))?.title)

        // "all" (non-Russian) restores the full list.
        assertEquals("Oak", viewModel.filterJournal(listOf("all"))?.title)
        assertEquals("Pine Cone", viewModel.moveJournal(1)?.title)
    }

    @Test
    fun `journal deletion requires confirmation and cancellation preserves the current finding`() {
        val viewModel = viewModel()
        val operationId = viewModel.beginJournalLoad()
        viewModel.setJournal(operationId, listOf(finding("Oak")))

        assertTrue(viewModel.requestJournalFindingDeletion())
        assertTrue(viewModel.state.value is ScannerUiState.JournalDeleteConfirmation)

        viewModel.cancelJournalFindingDeletion()

        assertTrue(viewModel.state.value is ScannerUiState.Journal)
        assertEquals("Oak", viewModel.currentJournalFinding()?.title)
    }

    @Test
    fun `confirmed journal deletion removes the current finding and selects the next one`() = runTest {
        val store = FakeStore()
        val viewModel = viewModel(store)
        val operationId = viewModel.beginJournalLoad()
        viewModel.setJournal(operationId, listOf(finding("Oak"), finding("Pine")))
        viewModel.requestJournalFindingDeletion()

        viewModel.deleteCurrentFinding()
        runCurrent()

        assertEquals(listOf("Oak"), store.deletedFindingIds)
        assertTrue(viewModel.state.value is ScannerUiState.Journal)
        assertEquals("Pine", viewModel.currentJournalFinding()?.title)
    }

    @Test
    fun `analysis result deletion requires confirmation and cancellation restores the same result`() = runTest {
        val viewModel = viewModel()
        viewModel.analyze(File("photo.jpg"))
        runCurrent()
        val result = viewModel.state.value as ScannerUiState.AnalysisResult

        assertTrue(viewModel.requestAnalysisResultDeletion())
        val confirmation = viewModel.state.value as ScannerUiState.AnalysisResultDeleteConfirmation
        assertEquals(result.finding, confirmation.finding)

        viewModel.cancelAnalysisResultDeletion()

        assertEquals(result.finding, (viewModel.state.value as ScannerUiState.AnalysisResult).finding)
    }

    @Test
    fun `confirmed analysis result deletion removes the finding and leaves the scanner idle`() = runTest {
        val store = FakeStore()
        val viewModel = viewModel(store)
        viewModel.analyze(File("photo.jpg"))
        runCurrent()
        val finding = (viewModel.state.value as ScannerUiState.AnalysisResult).finding
        viewModel.requestAnalysisResultDeletion()

        viewModel.deleteCurrentFinding()
        runCurrent()

        assertEquals(listOf(finding.id), store.deletedFindingIds)
        assertTrue(viewModel.state.value is ScannerUiState.CameraInactive)
    }

    @Test
    fun `analysis result deletion cannot be requested outside the analysis result`() {
        assertFalse(viewModel().requestAnalysisResultDeletion())
    }

    @Test
    fun `analysis error returns to photo review`() = runTest {
        val viewModel = ScannerViewModel(FailingAnalyzer(), FakeStore(), FakeSpeech())
        viewModel.analyze(File("photo.jpg"))
        runCurrent()
        assertTrue(viewModel.state.value is ScannerUiState.PhotoReview)
    }

    @Test
    fun `successful analysis opens a dedicated result context`() = runTest {
        val viewModel = viewModel()

        viewModel.analyze(File("photo.jpg"))
        runCurrent()

        assertTrue(viewModel.state.value is ScannerUiState.AnalysisResult)
    }

    @Test
    fun `successful finding save deletes the temporary photo only after it is stored`() = runTest {
        val photo = File.createTempFile("SCAN_", ".jpg")
        val store = FakeStore()
        val viewModel = viewModel(store)

        viewModel.analyze(photo)
        runCurrent()

        assertTrue(store.findingWasSaved)
        assertFalse(photo.exists())
    }

    @Test
    fun `failed finding save preserves the temporary photo for another attempt`() = runTest {
        val photo = File.createTempFile("SCAN_", ".jpg")
        val viewModel = ScannerViewModel(FakeAnalyzer(), FailingSaveStore(), FakeSpeech())

        viewModel.analyze(photo)
        runCurrent()

        assertTrue(photo.exists())
        photo.delete()
    }

    @Test
    fun `cancelled analysis failure returns to photo review`() = runTest {
        val viewModel = ScannerViewModel(FailingAnalyzer(), FakeStore(), FakeSpeech())
        viewModel.analyze(File("photo.jpg"))

        viewModel.cancelAnalysis()
        runCurrent()

        assertTrue(viewModel.state.value is ScannerUiState.PhotoReview)
    }

    @Test
    fun `journal navigation re-emits the state with the selected finding`() {
        val viewModel = viewModel()
        val operationId = viewModel.beginJournalLoad()
        viewModel.setJournal(operationId, listOf(finding("Oak"), finding("Pine")))

        assertEquals("Oak", (viewModel.state.value as ScannerUiState.Journal).finding?.title)

        viewModel.moveJournal(1)

        assertEquals("Pine", (viewModel.state.value as ScannerUiState.Journal).finding?.title)
    }

    @Test
    fun `an empty journal filter re-emits the state with no finding`() {
        val viewModel = viewModel()
        val operationId = viewModel.beginJournalLoad()
        viewModel.setJournal(operationId, listOf(finding("Oak")))

        viewModel.filterJournal(listOf("no such title"))

        assertEquals(null, (viewModel.state.value as ScannerUiState.Journal).finding)
    }

    @Test
    fun `analysis carries the photo so the screen can be rendered from state alone`() = runTest {
        val viewModel = viewModel()
        val photo = File("photo.jpg")

        val operationId = viewModel.beginAnalysis(photo)

        assertEquals(photo, (viewModel.state.value as ScannerUiState.Analyzing).photo)
        assertTrue(viewModel.isActiveAnalysis(operationId))

        viewModel.cancelAnalysis()

        assertEquals(photo, (viewModel.state.value as ScannerUiState.PhotoReview).photo)
    }

    private fun finding(title: String) = Finding(title, "", title, "", 1.0, ScanVisual.UNKNOWN_OBJECT.id, null, null, null, null, 0L)

    private class FakeAnalyzer : ImageAnalyzer { override suspend fun analyze(photo: File) = AnalysisResult("", ""); override fun cancel() = Unit }
    private open class FakeStore : FindingStore {
        val deletedFindingIds = mutableListOf<String>()
        var findingWasSaved = false
        override suspend fun savePhotoWithoutAnalysis(findingId: String, sourcePhoto: File): Finding = Finding(findingId, "", "", "", 0.0, ScanVisual.UNKNOWN_OBJECT.id, null, null, null, null, 0L)
        override suspend fun saveFinding(findingId: String, sourcePhoto: File, analysis: AnalysisResult): Finding {
            findingWasSaved = true
            return Finding(findingId, "", "", "", 0.0, ScanVisual.resolve(analysis.visualId).id, null, null, null, null, 0L)
        }
        override suspend fun attachAudio(findingId: String, audioFile: File): String = "file:///audio.mp3"
        override suspend fun deleteFinding(findingId: String) { deletedFindingIds += findingId }
        override suspend fun loadFindings(): List<Finding> = emptyList()
    }
    private class FailingSaveStore : FakeStore() {
        override suspend fun saveFinding(findingId: String, sourcePhoto: File, analysis: AnalysisResult): Finding = error("storage unavailable")
    }
    private class FakeSpeech : SpeechSynthesizer { override suspend fun synthesize(text: String): File = File("") }
    private class FailingAnalyzer : ImageAnalyzer { override suspend fun analyze(photo: File): AnalysisResult = error("network failed"); override fun cancel() = Unit }
}
