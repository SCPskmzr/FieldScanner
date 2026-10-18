package com.fieldscanner.app.data.diagnostics.remote

import java.util.concurrent.Executors

/** Serializes normal diagnostic I/O away from UI callers. */
object AsyncDiagnosticsWriter {
    private val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "field-scanner-diagnostics") }
    fun submit(write: () -> Unit) = executor.execute(write)
}
