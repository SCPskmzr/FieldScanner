package com.fieldscanner.app.data.diagnostics.remote

import android.content.Context
import android.util.Log
import com.fieldscanner.app.data.diagnostics.DiagnosticsFormatter
import com.fieldscanner.app.data.diagnostics.DiagnosticsLevel
import com.fieldscanner.app.data.diagnostics.DiagnosticsLog

/** File and logcat implementation. Normal records always run on its single I/O worker. */
class AndroidDiagnosticsLog private constructor(context: Context, apiKeyProvider: () -> String) : DiagnosticsLog {
    private val formatter = DiagnosticsFormatter(apiKeyProvider)
    private val fileLog = FileDiagnosticsLog(context.filesDir)

    override fun log(level: DiagnosticsLevel, tag: String, message: String, error: Throwable?) {
        AsyncDiagnosticsWriter.submit { write(level, tag, message, error) }
    }

    override fun logNow(level: DiagnosticsLevel, tag: String, message: String, error: Throwable?) {
        write(level, tag, message, error)
    }

    private fun write(level: DiagnosticsLevel, tag: String, message: String, error: Throwable?) {
        val record = formatter.format(level, tag, message, error)
        fileLog.append(record)
        Log.println(level.toAndroidPriority(), tag, record.trimEnd())
    }

    private fun DiagnosticsLevel.toAndroidPriority() = when (this) {
        DiagnosticsLevel.DEBUG -> Log.DEBUG; DiagnosticsLevel.INFO -> Log.INFO
        DiagnosticsLevel.WARN -> Log.WARN; DiagnosticsLevel.ERROR -> Log.ERROR
    }

    companion object {
        @Volatile private var instance: AndroidDiagnosticsLog? = null
        fun getInstance(context: Context, apiKeyProvider: () -> String): AndroidDiagnosticsLog = instance ?: synchronized(this) {
            instance ?: AndroidDiagnosticsLog(context.applicationContext, apiKeyProvider).also { instance = it }
        }
    }
}
