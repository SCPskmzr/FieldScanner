package com.fieldscanner.app.audio

import android.media.MediaPlayer
import java.io.File

/** Releases the previous player before starting a new scanner narration. */
class AudioPlaybackController {
    private var player: MediaPlayer? = null

    fun play(file: File) {
        if (!file.isFile) return
        release()
        player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnPreparedListener { it.start() }
            setOnCompletionListener { release() }
            setOnErrorListener { _, _, _ -> release(); true }
            prepareAsync()
        }
    }

    fun release() {
        player?.release()
        player = null
    }
}
