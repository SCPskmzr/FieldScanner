package com.fieldscanner.app.data.camera

import android.content.Context
import android.os.Environment
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** Removes scan photos left behind by an earlier app process without touching finding media. */
internal class TemporaryPhotoCleanup(
    context: Context,
    private val executor: Executor = Executors.newSingleThreadExecutor()
) {
    private val appContext = context.applicationContext

    /** Schedules disk traversal away from the main thread. */
    fun removePhotosFromEarlierSessions(sessionStartedAtMillis: Long = System.currentTimeMillis()) {
        executor.execute {
            temporaryPhotoDirectory(appContext).listFiles()?.forEach { photo ->
                if (TemporaryPhotoCleanupRule.shouldDelete(photo.name, photo.lastModified(), sessionStartedAtMillis)) {
                    photo.delete()
                }
            }
        }
    }
}

internal fun temporaryPhotoDirectory(context: Context): File =
    context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir

/** Pure session-boundary rule, kept Android-free so it can be checked by local unit tests. */
internal object TemporaryPhotoCleanupRule {
    fun shouldDelete(fileName: String, lastModifiedMillis: Long, sessionStartedAtMillis: Long): Boolean =
        fileName.startsWith(SCAN_FILE_PREFIX) && fileName.endsWith(JPEG_EXTENSION) && lastModifiedMillis < sessionStartedAtMillis

    private const val SCAN_FILE_PREFIX = "SCAN_"
    private const val JPEG_EXTENSION = ".jpg"
}
