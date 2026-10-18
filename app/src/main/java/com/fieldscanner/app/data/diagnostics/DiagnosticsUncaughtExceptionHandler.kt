package com.fieldscanner.app.data.diagnostics

/** Persists a crash before handing process termination to Android's prior handler. */
class DiagnosticsUncaughtExceptionHandler(
    private val log: DiagnosticsLog,
    private val previous: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, error: Throwable) {
        log.logNow(DiagnosticsLevel.ERROR, "UncaughtException", "Unhandled exception on ${thread.name}", error)
        previous?.uncaughtException(thread, error)
    }
}
