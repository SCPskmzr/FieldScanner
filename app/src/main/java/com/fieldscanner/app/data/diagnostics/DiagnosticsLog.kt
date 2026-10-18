package com.fieldscanner.app.data.diagnostics

/** Owner-only diagnostics; messages are never rendered in the child UI. */
interface DiagnosticsLog {
    fun log(level: DiagnosticsLevel, tag: String, message: String, error: Throwable? = null)
    fun logNow(level: DiagnosticsLevel, tag: String, message: String, error: Throwable? = null)

    fun error(tag: String, message: String, error: Throwable? = null) =
        log(DiagnosticsLevel.ERROR, tag, message, error)

    companion object {
        val NONE: DiagnosticsLog = object : DiagnosticsLog {
            override fun log(level: DiagnosticsLevel, tag: String, message: String, error: Throwable?) = Unit
            override fun logNow(level: DiagnosticsLevel, tag: String, message: String, error: Throwable?) = Unit
        }
    }
}

enum class DiagnosticsLevel { DEBUG, INFO, WARN, ERROR }
