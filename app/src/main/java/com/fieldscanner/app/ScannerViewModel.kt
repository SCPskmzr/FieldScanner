package com.fieldscanner.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldscanner.app.data.finding.Finding
import com.fieldscanner.app.data.finding.FindingStore
import com.fieldscanner.app.data.analysis.ImageAnalyzer
import com.fieldscanner.app.data.speech.SpeechSynthesizer
import com.fieldscanner.app.data.diagnostics.DiagnosticsLog
import com.fieldscanner.app.data.diagnostics.classifyAnalysisFailure
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns the mutually exclusive modes of the scanner screen. Every field that decides what the
 * screen shows lives in [ScannerUiState] itself (captured photo, current journal finding), so a
 * single collector can render the screen from the state alone (TASK-028).
 */
class ScannerViewModel(
    private val analyzer: ImageAnalyzer,
    private val store: FindingStore,
    private val speech: SpeechSynthesizer,
    private val diagnostics: DiagnosticsLog = DiagnosticsLog.NONE,
    private val localeProvider: () -> Locale = { Locale("ru", "RU") }
) : ViewModel() {
    private val _state = MutableStateFlow<ScannerUiState>(ScannerUiState.Camera())
    val state: StateFlow<ScannerUiState> = _state
    private var nextOperationId = 0L
    private var allFindings: List<Finding> = emptyList()
    private var journalFindings: List<Finding> = emptyList()
    private var journalIndex = 0
    private val _effects = MutableSharedFlow<ScannerEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<ScannerEffect> = _effects

    fun showCameraInactive() {
        _state.value = ScannerUiState.CameraInactive(state.value.operationId)
    }

    fun showCamera() {
        _state.value = ScannerUiState.Camera(state.value.operationId)
    }

    fun showPhotoReview(photo: File) {
        _state.value = ScannerUiState.PhotoReview(state.value.operationId, photo)
    }

    fun beginAnalysis(photo: File): Long {
        val operationId = ++nextOperationId
        _state.value = ScannerUiState.Analyzing(operationId, photo)
        return operationId
    }

    fun cancelAnalysis() {
        analyzer.cancel()
        val photo = (state.value as? ScannerUiState.Analyzing)?.photo ?: return
        _state.value = ScannerUiState.PhotoReview(++nextOperationId, photo)
    }

    fun isActiveAnalysis(operationId: Long): Boolean =
        state.value is ScannerUiState.Analyzing && state.value.operationId == operationId

    fun beginJournalLoad(): Long {
        val operationId = ++nextOperationId
        _state.value = ScannerUiState.JournalLoading(operationId)
        return operationId
    }

    fun showJournal(operationId: Long) {
        if (state.value is ScannerUiState.JournalLoading && state.value.operationId == operationId) {
            _state.value = ScannerUiState.Journal(operationId, currentJournalFinding())
        }
    }

    fun setJournal(operationId: Long, findings: List<Finding>) {
        allFindings = findings; journalFindings = findings; journalIndex = 0; showJournal(operationId)
    }

    fun currentJournalFinding(): Finding? = journalFindings.getOrNull(journalIndex)
    fun moveJournal(delta: Int): Finding? {
        if (journalFindings.isNotEmpty()) journalIndex = (journalIndex + delta + journalFindings.size) % journalFindings.size
        return publishJournalFinding()
    }
    fun filterJournal(candidates: List<String>): Finding? {
        val locale = localeProvider()
        val queries = candidates.map { it.lowercase(locale).trim() }
        journalFindings = if (queries.any { it in SHOW_ALL_QUERIES }) allFindings else queries.firstNotNullOfOrNull { query ->
            allFindings.filter { it.title.lowercase(locale).contains(query) }.takeIf { it.isNotEmpty() }
        }.orEmpty()
        journalIndex = 0
        return publishJournalFinding()
    }

    /** Re-emits [ScannerUiState.Journal] so the screen collector renders the newly selected finding. */
    private fun publishJournalFinding(): Finding? {
        val finding = currentJournalFinding()
        if (state.value is ScannerUiState.Journal) {
            _state.value = ScannerUiState.Journal(state.value.operationId, finding)
        }
        return finding
    }

    fun isJournalVisible(): Boolean = state.value is ScannerUiState.Journal

    /** Enters the journal-only delete confirmation; no data is removed at this point. */
    fun requestJournalFindingDeletion(): Boolean {
        if (state.value !is ScannerUiState.Journal || currentJournalFinding() == null) return false
        _state.value = ScannerUiState.JournalDeleteConfirmation(++nextOperationId, currentJournalFinding())
        return true
    }

    fun cancelJournalFindingDeletion() {
        if (state.value is ScannerUiState.JournalDeleteConfirmation) {
            _state.value = ScannerUiState.Journal(++nextOperationId, currentJournalFinding())
        }
    }

    /** Enters the analysis-result delete confirmation; the finding is carried so cancel can restore it. */
    fun requestAnalysisResultDeletion(): Boolean {
        val finding = (state.value as? ScannerUiState.AnalysisResult)?.finding ?: return false
        _state.value = ScannerUiState.AnalysisResultDeleteConfirmation(++nextOperationId, finding)
        return true
    }

    fun cancelAnalysisResultDeletion() {
        val current = state.value
        if (current is ScannerUiState.AnalysisResultDeleteConfirmation) {
            _state.value = ScannerUiState.AnalysisResult(++nextOperationId, current.finding)
        }
    }

    fun isJournalLoading(operationId: Long): Boolean =
        state.value is ScannerUiState.JournalLoading && state.value.operationId == operationId

    fun analyze(photo: File) {
        val operationId = beginAnalysis(photo)
        viewModelScope.launch {
            runCatching { analyzer.analyze(photo) }.onFailure { error ->
                diagnostics.error("ScannerViewModel", "Analysis failed: ${classifyAnalysisFailure(error)}", error)
                if (isActiveAnalysis(operationId)) {
                    _state.value = ScannerUiState.PhotoReview(operationId, photo)
                    _effects.tryEmit(ScannerEffect.AnalysisFailed)
                }
            }.onSuccess { result ->
                val findingId = UUID.randomUUID().toString()
                runCatching { store.saveFinding(findingId, photo, result) }.onFailure { error ->
                    diagnostics.error("ScannerViewModel", "Saving analyzed finding failed", error)
                    if (isActiveAnalysis(operationId)) {
                        _state.value = ScannerUiState.PhotoReview(operationId, photo)
                        _effects.tryEmit(ScannerEffect.AnalysisFailed)
                    }
                }.onSuccess { finding ->
                    // The repository has atomically copied the image into files/findings/.
                    photo.delete()
                    runCatching {
                        val audio = speech.synthesize(result.description)
                        audio to store.attachAudio(findingId, audio)
                    }.onSuccess { (audio, audioUri) ->
                        if (isActiveAnalysis(operationId)) {
                            val completedFinding = finding.copy(audioUri = audioUri)
                            showAnalysisResult(operationId, completedFinding)
                            _effects.tryEmit(ScannerEffect.AnalysisResultReady(completedFinding, audio))
                        }
                    }.onFailure { error ->
                        diagnostics.error("ScannerViewModel", "Speech synthesis or audio attachment failed", error)
                        if (isActiveAnalysis(operationId)) {
                            showAnalysisResult(operationId, finding)
                            _effects.tryEmit(ScannerEffect.AnalysisResultReady(finding, null))
                        }
                    }
                }
            }
        }
    }

    fun savePhotoWithoutAnalysis(photo: File) {
        viewModelScope.launch {
            runCatching { store.savePhotoWithoutAnalysis(UUID.randomUUID().toString(), photo) }
                .onFailure { error -> diagnostics.error("ScannerViewModel", "Saving photo without analysis failed", error); _effects.tryEmit(ScannerEffect.PhotoSaveFailed) }
            photo.delete()
        }
    }

    fun deleteCurrentFinding() {
        val finding = when (val currentState = state.value) {
            is ScannerUiState.AnalysisResult -> currentState.finding
            is ScannerUiState.AnalysisResultDeleteConfirmation -> currentState.finding
            is ScannerUiState.JournalDeleteConfirmation -> currentJournalFinding()
            else -> null
        } ?: return
        val deletingFromJournal = state.value is ScannerUiState.JournalDeleteConfirmation
        viewModelScope.launch {
            runCatching { store.deleteFinding(finding.id) }.onSuccess {
                if (!deletingFromJournal) {
                    _state.value = ScannerUiState.CameraInactive(++nextOperationId)
                } else {
                    allFindings = allFindings.filterNot { it.id == finding.id }
                    journalFindings = journalFindings.filterNot { it.id == finding.id }
                    journalIndex = journalIndex.coerceAtMost((journalFindings.size - 1).coerceAtLeast(0))
                    _state.value = if (journalFindings.isEmpty()) ScannerUiState.CameraInactive(++nextOperationId)
                        else ScannerUiState.Journal(++nextOperationId, currentJournalFinding())
                }
            }.onFailure { error -> diagnostics.error("ScannerViewModel", "Deleting finding failed", error); _effects.tryEmit(ScannerEffect.FindingDeleteFailed) }
        }
    }

    private fun showAnalysisResult(operationId: Long, finding: Finding) {
        _state.value = ScannerUiState.AnalysisResult(operationId, finding)
    }

    fun loadJournal() {
        val operationId = beginJournalLoad()
        viewModelScope.launch { runCatching { store.loadFindings() }.onSuccess { findings -> if (isJournalLoading(operationId)) _effects.tryEmit(ScannerEffect.JournalLoaded(operationId, findings)) } }
    }

    override fun onCleared() {
        analyzer.cancel()
        super.onCleared()
    }
}

private val SHOW_ALL_QUERIES = setOf(
    "все", "всё", "показать все", "показать всё", // ru
    "усе", "усё", // uk / be
    "all", "show all", "wszystko", "הכל" // en / pl / he
)

sealed class ScannerEffect {
    object AnalysisFailed : ScannerEffect()
    object PhotoSaveFailed : ScannerEffect()
    object FindingDeleteFailed : ScannerEffect()
    data class AnalysisResultReady(val finding: Finding, val audio: File?) : ScannerEffect()
    data class JournalLoaded(val operationId: Long, val findings: List<Finding>) : ScannerEffect()
}

sealed class ScannerUiState(open val operationId: Long) {
    data class CameraInactive(override val operationId: Long = 0L) : ScannerUiState(operationId)
    data class Camera(override val operationId: Long = 0L) : ScannerUiState(operationId)
    data class PhotoReview(override val operationId: Long, val photo: File) : ScannerUiState(operationId)
    data class Analyzing(override val operationId: Long, val photo: File) : ScannerUiState(operationId)
    data class AnalysisResult(override val operationId: Long, val finding: Finding) : ScannerUiState(operationId)
    data class AnalysisResultDeleteConfirmation(override val operationId: Long, val finding: Finding) : ScannerUiState(operationId)
    data class JournalLoading(override val operationId: Long) : ScannerUiState(operationId)
    data class Journal(override val operationId: Long, val finding: Finding? = null) : ScannerUiState(operationId)
    data class JournalDeleteConfirmation(override val operationId: Long, val finding: Finding? = null) : ScannerUiState(operationId)
}
