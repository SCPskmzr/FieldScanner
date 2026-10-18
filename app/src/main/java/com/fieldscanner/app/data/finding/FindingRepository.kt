package com.fieldscanner.app.data.finding

import android.content.Context
import android.net.Uri
import com.fieldscanner.app.data.location.DeviceLocationProvider
import com.fieldscanner.app.data.diagnostics.DiagnosticsLog
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Persists completed image analyses without blocking the UI thread. */
class FindingRepository private constructor(context: Context, private val diagnostics: DiagnosticsLog) : FindingStore {
    private val appContext = context.applicationContext
    private val dao = FindingsDatabase.getInstance(appContext).findingDao()
    private val locationProvider = DeviceLocationProvider(appContext)
    private val mediaStorage = FindingMediaStorage(appContext)

    override suspend fun savePhotoWithoutAnalysis(findingId: String, sourcePhoto: File): Finding = withContext(Dispatchers.IO) {
        val savedPhoto = mediaStorage.savePhoto(sourcePhoto, findingId)
        try {
            Finding(
                id = findingId,
                photoUri = Uri.fromFile(savedPhoto).toString(),
                title = "Фото без анализа",
                description = "Снимок сохранён без распознавания.",
                confidence = 0.0,
                visualId = ScanVisual.UNKNOWN_OBJECT.id,
                audioUri = null,
                latitude = null,
                longitude = null,
                accuracyMeters = null,
                foundAt = System.currentTimeMillis()
            ).also { dao.insertFinding(it.toEntity()) }
        } catch (error: Throwable) {
            savedPhoto.delete()
            diagnostics.error("FindingRepository", "Saving unanalyzed finding failed", error)
            throw error
        }
    }

    override suspend fun saveFinding(
        findingId: String,
        sourcePhoto: File,
        analysis: AnalysisResult
    ): Finding = withContext(Dispatchers.IO) {
                val validAnalysis = analysis.validated()
                val savedPhoto = mediaStorage.savePhoto(sourcePhoto, findingId)
                try {
                    val location = locationProvider.getCurrentLocation()
                    Finding(
                        id = findingId,
                        photoUri = Uri.fromFile(savedPhoto).toString(),
                        title = validAnalysis.title,
                        description = validAnalysis.description,
                        confidence = validAnalysis.confidence ?: 0.0,
                        visualId = validAnalysis.visualId,
                        audioUri = null,
                        latitude = location?.latitude,
                        longitude = location?.longitude,
                        accuracyMeters = location?.accuracyMeters,
                        foundAt = System.currentTimeMillis()
                    ).also { dao.insertFinding(it.toEntity()) }
                } catch (error: Throwable) {
                    savedPhoto.delete()
                    diagnostics.error("FindingRepository", "Saving analyzed finding failed", error)
                    throw error
                }
    }

    /** Persists the generated audio and links it to the finding after the save task. */
    override suspend fun attachAudio(findingId: String, audioFile: File): String = withContext(Dispatchers.IO) {
                val target = mediaStorage.saveAudio(audioFile, findingId)
                try {
                    check(dao.updateAudioUri(findingId, Uri.fromFile(target).toString()) == 1) {
                        "Finding was not saved."
                    }
                    Uri.fromFile(target).toString()
                } catch (error: Throwable) {
                    target.delete()
                    diagnostics.error("FindingRepository", "Attaching audio failed", error)
                    throw error
                }
    }

    override suspend fun loadFindings(): List<Finding> = withContext(Dispatchers.IO) {
        dao.getAllFindings().map(FindingEntity::toDomain)
    }

    override suspend fun deleteFinding(findingId: String) = withContext(Dispatchers.IO) {
        try {
            check(dao.deleteFinding(findingId) == 1) { "Finding was not saved." }
            mediaStorage.deleteMedia(findingId)
        } catch (error: Throwable) {
            diagnostics.error("FindingRepository", "Deleting finding failed", error)
            throw error
        }
    }

    companion object {
        @Volatile
        private var instance: FindingRepository? = null

        fun getInstance(context: Context, diagnostics: DiagnosticsLog = DiagnosticsLog.NONE): FindingRepository = instance ?: synchronized(this) {
            instance ?: FindingRepository(context, diagnostics).also { instance = it }
        }
    }
}
