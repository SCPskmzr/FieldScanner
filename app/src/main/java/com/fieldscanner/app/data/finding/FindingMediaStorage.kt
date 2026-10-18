package com.fieldscanner.app.data.finding

import android.content.Context
import java.io.File

/** Owns durable photo and audio files belonging to findings. */
internal class FindingMediaStorage(context: Context) {
    private val directory = File(context.applicationContext.filesDir, FINDINGS_DIRECTORY)

    fun savePhoto(source: File, findingId: String): File = copyAtomically(source, "$findingId.jpg", "$findingId.tmp")

    fun saveAudio(source: File, findingId: String): File = copyAtomically(source, "$findingId.mp3", "$findingId.audio.tmp")

    fun deleteMedia(findingId: String) {
        File(directory, "$findingId.jpg").delete()
        File(directory, "$findingId.mp3").delete()
    }

    private fun copyAtomically(source: File, targetName: String, temporaryName: String): File {
        require(source.isFile) { "Source media file does not exist." }
        check((directory.exists() || directory.mkdirs()) && directory.isDirectory) { "Could not create findings directory." }
        val target = File(directory, targetName)
        val temporary = File(directory, temporaryName)
        try {
            source.inputStream().use { input -> temporary.outputStream().use(input::copyTo) }
            check(temporary.renameTo(target)) { "Could not finalize media file." }
            return target
        } catch (error: Throwable) {
            temporary.delete(); target.delete(); throw error
        }
    }

    companion object { private const val FINDINGS_DIRECTORY = "findings" }
}
