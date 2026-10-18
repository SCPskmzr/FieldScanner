package com.fieldscanner.app.runes

import com.fieldscanner.app.ScannerUiState

fun ScannerUiState.toRuneContext(): RuneContext = when (this) {
    is ScannerUiState.CameraInactive -> RuneContext.CAMERA_INACTIVE
    is ScannerUiState.Camera -> RuneContext.CAMERA
    is ScannerUiState.PhotoReview -> RuneContext.PHOTO_REVIEW
    is ScannerUiState.Analyzing -> RuneContext.ANALYZING
    is ScannerUiState.AnalysisResult -> RuneContext.ANALYSIS_RESULT
    is ScannerUiState.AnalysisResultDeleteConfirmation -> RuneContext.ANALYSIS_RESULT_DELETE_CONFIRMATION
    is ScannerUiState.JournalLoading -> RuneContext.JOURNAL_LOADING
    is ScannerUiState.Journal -> RuneContext.JOURNAL
    is ScannerUiState.JournalDeleteConfirmation -> RuneContext.JOURNAL_DELETE_CONFIRMATION
}
