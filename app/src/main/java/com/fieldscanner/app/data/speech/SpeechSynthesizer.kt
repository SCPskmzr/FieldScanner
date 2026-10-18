package com.fieldscanner.app.data.speech

import java.io.File

/** Contract for generating spoken audio from text. */
interface SpeechSynthesizer {
    suspend fun synthesize(text: String): File
}
