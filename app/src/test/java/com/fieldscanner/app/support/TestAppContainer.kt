package com.fieldscanner.app.support

import android.content.Context
import com.fieldscanner.app.AppContainer
import com.fieldscanner.app.data.analysis.ImageAnalyzer
import com.fieldscanner.app.data.diagnostics.DiagnosticsLog
import com.fieldscanner.app.data.finding.AnalysisResult
import com.fieldscanner.app.data.finding.Finding
import com.fieldscanner.app.data.finding.FindingStore
import com.fieldscanner.app.data.finding.ScanVisual
import com.fieldscanner.app.data.speech.SpeechSynthesizer
import com.fieldscanner.app.display.DeviceIndicatorProvider
import com.fieldscanner.app.display.LeftDisplayInputs
import com.fieldscanner.app.display.OutdoorTemperatureProvider
import com.fieldscanner.app.display.zones.DeviceIndicators
import com.fieldscanner.app.voice.AppLanguage
import com.fieldscanner.app.voice.LanguagePreference
import java.io.File

/**
 * Container used by the app-startup smoke test (TASK-020). Every port is a fake,
 * so bringing up `MainActivity` never opens the Room database, calls OpenAI, or
 * touches geolocation. [startBackgroundMaintenance] is overridden to do no disk
 * work.
 */
class TestAppContainer(context: Context) : AppContainer(context) {
    override val diagnostics: DiagnosticsLog = DiagnosticsLog.NONE
    override val findings: FindingStore = FakeFindingStore()
    override val languagePreference: LanguagePreference = FakeLanguagePreference()
    override val analyzer: ImageAnalyzer = FakeImageAnalyzer()
    override val speech: SpeechSynthesizer = FakeSpeechSynthesizer()
    override val leftDisplay: LeftDisplayInputs =
        LeftDisplayInputs(FakeOutdoorTemperatureProvider(), FakeDeviceIndicatorProvider())

    override fun startBackgroundMaintenance() = Unit
}

private class FakeFindingStore : FindingStore {
    override suspend fun savePhotoWithoutAnalysis(findingId: String, sourcePhoto: File): Finding = finding(findingId)
    override suspend fun saveFinding(findingId: String, sourcePhoto: File, analysis: AnalysisResult): Finding = finding(findingId)
    override suspend fun attachAudio(findingId: String, audioFile: File): String = "file:///audio.mp3"
    override suspend fun deleteFinding(findingId: String) = Unit
    override suspend fun loadFindings(): List<Finding> = emptyList()

    private fun finding(id: String) =
        Finding(id, "", "", "", 0.0, ScanVisual.UNKNOWN_OBJECT.id, null, null, null, null, 0L)
}

private class FakeImageAnalyzer : ImageAnalyzer {
    override suspend fun analyze(photo: File): AnalysisResult = AnalysisResult("", "")
    override fun cancel() = Unit
}

private class FakeSpeechSynthesizer : SpeechSynthesizer {
    override suspend fun synthesize(text: String): File = File("")
}

private class FakeLanguagePreference : LanguagePreference {
    private var language = AppLanguage.RUSSIAN
    override fun current(): AppLanguage = language
    override fun update(language: AppLanguage) { this.language = language }
}

private class FakeOutdoorTemperatureProvider : OutdoorTemperatureProvider {
    override var onTemperatureChanged: (Float?) -> Unit = {}
    override fun start() = Unit
    override fun stop() = Unit
    override fun refreshNow() = Unit
}

private class FakeDeviceIndicatorProvider : DeviceIndicatorProvider {
    override var onIndicatorsChanged: (DeviceIndicators) -> Unit = {}
    override fun start() = Unit
    override fun stop() = Unit
}
