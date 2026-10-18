package com.fieldscanner.app.display.effects

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.fieldscanner.app.display.DisplayGeometry

/** Animated scanlines, noise and short analogue-snow bursts for the central display. */
internal class InterferenceEffect {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val snowBitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
    private val snowPixels = IntArray(640 * 360)
    private val snowPaint = Paint().apply { isFilterBitmap = false }
    private var nextBurstAtMillis = 0L

    fun start(now: Long) { nextBurstAtMillis = now + 30_000L }

    fun draw(canvas: Canvas, centerZone: Path, now: Long, cameraVisible: Boolean) {
        canvas.save(); canvas.clipPath(centerZone)
        val burst = if (cameraVisible) burstStrength(now) else 0f
        val offset = ((now / 24L) % 8L).toFloat()
        paint.color = Color.argb((22 + 46 * burst).toInt(), 255, 148, 45); paint.strokeWidth = 1.4f
        var y = offset
        while (y < DisplayGeometry.HEIGHT) { canvas.drawLine(0f, y, DisplayGeometry.WIDTH, y, paint); y += 8f }
        paint.color = Color.argb(2 + ((now / 160L) % 4L).toInt() + (16 * burst).toInt(), 255, 191, 94)
        canvas.drawRect(0f, 0f, DisplayGeometry.WIDTH, DisplayGeometry.HEIGHT, paint)
        val seed = (now / 50L).toInt()
        if (burst > 0f) drawSnow(canvas, seed, burst) else drawNoise(canvas, seed, cameraVisible)
        if (burst == 0f && seed % 100 == 0) { paint.color = Color.argb(if (cameraVisible) 36 else 24, 255, 91, 28); val glitchY = (hash(seed) and 0x7FFF) % DisplayGeometry.HEIGHT.toInt(); canvas.drawRect(0f, glitchY.toFloat(), DisplayGeometry.WIDTH, glitchY + 1.5f, paint) }
        canvas.restore()
    }

    private fun drawNoise(canvas: Canvas, frameSeed: Int, cameraVisible: Boolean) {
        paint.color = Color.argb(if (cameraVisible) 34 else 24, 255, 149, 48)
        var state = frameSeed xor 0x4F1BBCDC
        repeat(16) { state = hash(state); val y = (state and 0x7FFF) % DisplayGeometry.HEIGHT.toInt(); val x = ((state ushr 8) and 0x7FFF) % DisplayGeometry.WIDTH.toInt(); canvas.drawRect(x.toFloat(), y.toFloat(), (x + 8 + ((state ushr 16) and 0x3F)).toFloat(), y + 1.5f, paint) }
    }

    private fun drawSnow(canvas: Canvas, frameSeed: Int, strength: Float) {
        var index = 0; var state = frameSeed xor 0x6D2B79F5
        while (index < snowPixels.size) { state = hash(state); if ((state and 0xFF) < 128) snowPixels[index] = 0 else { val brightness = 45 + (state and 0xBF); val alpha = ((35 + ((state ushr 24) and 0x3F)) * strength).toInt(); snowPixels[index] = (alpha shl 24) or (brightness + 35).coerceAtMost(255).shl(16) or (brightness * 2 / 3 shl 8) or (brightness / 4) }; index++ }
        snowBitmap.setPixels(snowPixels, 0, 640, 0, 0, 640, 360)
        canvas.drawBitmap(snowBitmap, null, RectF(0f, 0f, DisplayGeometry.WIDTH, DisplayGeometry.HEIGHT), snowPaint)
    }

    private fun burstStrength(now: Long): Float { if (now < nextBurstAtMillis) return 0f; if (now >= nextBurstAtMillis + 5_000L) { nextBurstAtMillis = now + 30_000L; return 0f }; val elapsed = now - nextBurstAtMillis; return when { elapsed < 1_000L -> elapsed / 1_000f; elapsed > 4_000L -> (5_000L - elapsed) / 1_000f; else -> 1f } }
    private fun hash(value: Int): Int { var result = value xor (value shl 13); result = result xor (result ushr 17); return result xor (result shl 5) }
}
