package com.fieldscanner.app.data.diagnostics

import com.fieldscanner.app.data.net.HttpClient
import java.io.IOException

enum class AnalysisFailureReason { NOT_CONFIGURED, NETWORK, SERVICE_STATUS, RESPONSE_PARSE, OTHER }

/** Android-free classification used in owner diagnostics and JVM tests. */
fun classifyAnalysisFailure(error: Throwable): AnalysisFailureReason = when {
    error is HttpClient.HttpException -> AnalysisFailureReason.SERVICE_STATUS
    error is IOException -> AnalysisFailureReason.NETWORK
    error.message?.contains("API key is not configured", ignoreCase = true) == true -> AnalysisFailureReason.NOT_CONFIGURED
    error.message?.let { it.contains("response", true) || it.contains("decode", true) || it.contains("text output", true) } == true -> AnalysisFailureReason.RESPONSE_PARSE
    else -> AnalysisFailureReason.OTHER
}
