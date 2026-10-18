package com.fieldscanner.app.data.finding

import java.io.File

/** Storage boundary used by scanner and journal workflows. */
interface FindingStore {
    suspend fun savePhotoWithoutAnalysis(findingId: String, sourcePhoto: File): Finding
    suspend fun saveFinding(
        findingId: String,
        sourcePhoto: File,
        analysis: AnalysisResult
    ): Finding

    suspend fun attachAudio(findingId: String, audioFile: File): String
    suspend fun deleteFinding(findingId: String)
    suspend fun loadFindings(): List<Finding>
}
