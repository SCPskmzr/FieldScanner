package com.fieldscanner.app.display.zones

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.fieldscanner.app.R
import com.fieldscanner.app.RuneHintPager
import com.fieldscanner.app.display.DisplayGeometry
import com.fieldscanner.app.runes.HintSlotId
import com.fieldscanner.app.runes.RuneCatalog
import com.fieldscanner.app.runes.RuneId

/** Draws the three right-hand rune hint zones and their page-transition glow. */
internal class RuneHintsZoneRenderer(context: Context) {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val runeBitmaps: Map<RuneId, Bitmap> = mapOf(
        RuneId.ASTRA to bitmap(context, R.drawable.rune_astra),
        RuneId.BOREAL to bitmap(context, R.drawable.rune_boreal),
        RuneId.CIPHER to bitmap(context, R.drawable.rune_cipher),
        RuneId.DRIFT to bitmap(context, R.drawable.rune_drift),
        RuneId.EMBER to bitmap(context, R.drawable.rune_ember),
        RuneId.FLUX to bitmap(context, R.drawable.rune_flux)
    )

    fun draw(canvas: Canvas, pager: RuneHintPager, now: Long) {
        val bindingsBySlot = pager.slots().associateBy { it.id }
        val opacity = pager.opacity(now)
        HintSlotId.values().forEachIndexed { index, slotId ->
            val rune = bindingsBySlot[slotId]?.binding?.rune ?: return@forEachIndexed
            drawRune(canvas, rune, DisplayGeometry.hintZones[index], opacity)
        }
    }

    private fun drawRune(canvas: Canvas, rune: RuneId, zone: Path, opacity: Float) {
        val bounds = RectF().also { zone.computeBounds(it, true) }
        bounds.inset(bounds.width() * 0.14f, bounds.height() * 0.14f)
        canvas.save()
        canvas.clipPath(zone)
        bitmapPaint.alpha = (255 * opacity).toInt()
        canvas.drawBitmap(runeBitmaps.getValue(rune), null, bounds, bitmapPaint)
        canvas.restore()
        drawGlow(canvas, zone, withOpacity(dimmed(RuneCatalog.presentation(rune).color.argb), opacity))
    }

    private fun drawGlow(canvas: Canvas, zone: Path, color: Int) {
        canvas.save()
        canvas.clipPath(zone)
        glowPaint.color = withOpacity(color, 0.85f)
        glowPaint.strokeWidth = 21f
        glowPaint.maskFilter = BlurMaskFilter(24f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(zone, glowPaint)
        glowPaint.color = withOpacity(color, 0.65f)
        glowPaint.strokeWidth = 10.5f
        glowPaint.maskFilter = BlurMaskFilter(7.5f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(zone, glowPaint)
        canvas.restore()
        glowPaint.maskFilter = null
        glowPaint.color = color
        glowPaint.strokeWidth = 3.5f
        canvas.drawPath(zone, glowPaint)
    }

    private fun dimmed(color: Int) = Color.argb(95, Color.red(color), Color.green(color), Color.blue(color))
    private fun withOpacity(color: Int, opacity: Float) = Color.argb((Color.alpha(color) * opacity).toInt(), Color.red(color), Color.green(color), Color.blue(color))
    private fun bitmap(context: Context, resourceId: Int) = android.graphics.BitmapFactory.decodeResource(context.resources, resourceId)
}
