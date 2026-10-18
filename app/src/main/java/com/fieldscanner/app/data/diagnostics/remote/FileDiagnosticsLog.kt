package com.fieldscanner.app.data.diagnostics.remote

import java.io.File
import java.io.FileOutputStream

/** Two bounded files: current + one previous generation, at most 256 KiB together. */
class FileDiagnosticsLog(private val directory: File) {
    private val current = File(directory, CURRENT_NAME)
    private val previous = File(directory, PREVIOUS_NAME)

    @Synchronized fun append(record: String) {
        directory.mkdirs()
        val encoded = record.toByteArray(Charsets.UTF_8)
        val bytes = encoded.copyOfRange(0, minOf(encoded.size, MAX_RECORD_BYTES))
        if (current.length() + bytes.size > MAX_FILE_BYTES) {
            previous.delete()
            current.renameTo(previous)
        }
        FileOutputStream(current, true).buffered().use { output -> output.write(bytes) }
    }

    fun files(): List<File> = listOf(current, previous)

    companion object {
        const val CURRENT_NAME = "field-scanner-diagnostics.log"
        const val PREVIOUS_NAME = "field-scanner-diagnostics.1.log"
        const val MAX_FILE_BYTES = 128 * 1024
        const val MAX_TOTAL_BYTES = MAX_FILE_BYTES * 2
        private const val MAX_RECORD_BYTES = 4 * 1024
    }
}
