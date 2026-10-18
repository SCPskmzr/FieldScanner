package com.fieldscanner.app

import android.content.Context
import com.fieldscanner.app.data.camera.TemporaryPhotoCleanup
import com.fieldscanner.app.data.finding.FindingRepository
import com.fieldscanner.app.data.diagnostics.DiagnosticsLog
import com.fieldscanner.app.data.diagnostics.remote.AndroidDiagnosticsLog
import com.fieldscanner.app.data.finding.FindingStore
import com.fieldscanner.app.data.analysis.ImageAnalyzer
import com.fieldscanner.app.data.analysis.remote.OpenAiImageAnalyzer
import com.fieldscanner.app.data.location.DeviceLocationProvider
import com.fieldscanner.app.data.speech.remote.OpenAiTtsProxy
import com.fieldscanner.app.data.speech.SpeechSynthesizer
import com.fieldscanner.app.display.DeviceIndicatorSource
import com.fieldscanner.app.display.LeftDisplayInputs
import com.fieldscanner.app.display.OutdoorTemperatureSource
import com.fieldscanner.app.voice.LanguagePreference
import com.fieldscanner.app.voice.remote.SharedPrefsLanguagePreference

/**
 * Object graph for the scanner screen, assembled once per process.
 *
 * The ports are `open` and resolved lazily so a test can subclass this container,
 * replace individual ports with fakes, and never construct the Room database, the
 * OpenAI clients, or the geolocation stack (see the startup smoke test, TASK-020).
 * Production keeps its previous behaviour: every port is built on first access and
 * [startBackgroundMaintenance] still runs at construction.
 */
open class AppContainer(context: Context) {
    protected val appContext: Context = context.applicationContext

    open val diagnostics: DiagnosticsLog by lazy {
        AndroidDiagnosticsLog.getInstance(appContext) { com.fieldscanner.app.BuildConfig.OPENAI_API_KEY }
    }
    open val findings: FindingStore by lazy { FindingRepository.getInstance(appContext, diagnostics) }

    /** Single source of the selected app language; propagated to prompt, TTS, recognition and journal matching (TASK-015). */
    open val languagePreference: LanguagePreference by lazy { SharedPrefsLanguagePreference(appContext) }
    open val analyzer: ImageAnalyzer by lazy {
        OpenAiImageAnalyzer(appContext, diagnostics) { languagePreference.current() }
    }
    open val speech: SpeechSynthesizer by lazy {
        OpenAiTtsProxy(appContext, diagnostics) { languagePreference.current() }
    }

    /** Backend for the independent left system display; injected into DisplayZonesView. */
    internal open val leftDisplay: LeftDisplayInputs by lazy {
        LeftDisplayInputs(
            OutdoorTemperatureSource(DeviceLocationProvider(appContext)),
            DeviceIndicatorSource(appContext)
        )
    }

    init {
        startBackgroundMaintenance()
    }

    /** One-shot startup housekeeping; overridable so a test container performs no disk work. */
    protected open fun startBackgroundMaintenance() {
        TemporaryPhotoCleanup(appContext).removePhotosFromEarlierSessions()
    }
}
