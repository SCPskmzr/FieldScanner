package com.fieldscanner.app.data.analysis

import java.io.File
import com.fieldscanner.app.data.finding.AnalysisResult

/** Contract for the service that derives a finding from a captured photo. */
interface ImageAnalyzer {
    suspend fun analyze(photo: File): AnalysisResult
    fun cancel()
}
