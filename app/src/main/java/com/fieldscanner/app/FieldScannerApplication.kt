package com.fieldscanner.app

import android.app.Application
import com.fieldscanner.app.data.diagnostics.DiagnosticsUncaughtExceptionHandler
import com.fieldscanner.app.data.diagnostics.remote.AndroidDiagnosticsLog

class FieldScannerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val diagnostics = AndroidDiagnosticsLog.getInstance(applicationContext) { BuildConfig.OPENAI_API_KEY }
        Thread.setDefaultUncaughtExceptionHandler(
            DiagnosticsUncaughtExceptionHandler(diagnostics, Thread.getDefaultUncaughtExceptionHandler())
        )
    }
}
