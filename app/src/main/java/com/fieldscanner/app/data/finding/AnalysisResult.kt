package com.fieldscanner.app.data.finding

/** Structured data returned by the image-analysis provider. */
data class AnalysisResult(
    val title: String,
    val description: String,
    val confidence: Double? = null,
    val visualId: String = ScanVisual.UNKNOWN_OBJECT.id
) {
    fun validated(): AnalysisResult {
        require(title.isNotBlank()) { "Analysis title must not be blank." }
        require(description.isNotBlank()) { "Analysis description must not be blank." }
        require(confidence == null || confidence in 0.0..1.0) {
            "Analysis confidence must be between 0.0 and 1.0."
        }
        return copy(
            title = title.trim(),
            description = description.trim(),
            confidence = confidence ?: 0.0,
            visualId = ScanVisual.resolve(visualId).id
        )
    }
}
