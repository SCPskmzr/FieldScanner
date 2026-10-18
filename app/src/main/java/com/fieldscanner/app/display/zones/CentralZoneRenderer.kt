package com.fieldscanner.app.display.zones

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.fieldscanner.app.ContactSelection
import com.fieldscanner.app.R
import com.fieldscanner.app.display.DisplayGeometry

/** Draws central artwork and its contact-selection overlay. */
internal class CentralZoneRenderer(context: Context) {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val artworkPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val centralBitmap = android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.central_amber_display)
    private val contactsBitmap = android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.call_contacts_family_tech)

    fun draw(canvas: Canvas, selection: ContactSelection?) {
        if (selection == null) drawDefault(canvas) else drawContacts(canvas, selection)
    }

    fun drawOutline(canvas: Canvas) = canvas.drawPath(DisplayGeometry.centerZone, outlinePaint)

    private fun drawDefault(canvas: Canvas) {
        canvas.save()
        canvas.clipPath(DisplayGeometry.centerZone)
        canvas.drawBitmap(centralBitmap, null, RectF(0f, 0f, DisplayGeometry.WIDTH, DisplayGeometry.HEIGHT), artworkPaint)
        canvas.restore()
        drawOutline(canvas)
    }

    private fun drawContacts(canvas: Canvas, selection: ContactSelection) {
        val artworkBounds = RectF(157f, 34f, 1051f, 593f)
        canvas.save()
        canvas.clipPath(DisplayGeometry.centerZone)
        canvas.drawBitmap(contactsBitmap, null, artworkBounds, artworkPaint)
        val source = if (selection == ContactSelection.MOM) RectF(43f, 120f, 198f, 468f) else RectF(303f, 50f, 501f, 470f)
        val frame = RectF(
            artworkBounds.left + source.left * artworkBounds.width() / 512f,
            artworkBounds.top + source.top * artworkBounds.height() / 512f,
            artworkBounds.left + source.right * artworkBounds.width() / 512f,
            artworkBounds.top + source.bottom * artworkBounds.height() / 512f
        )
        fillPaint.color = 0x14F2A339
        canvas.drawRoundRect(frame, 12f, 12f, fillPaint)
        glowPaint.color = 0xFFF2A339.toInt(); glowPaint.strokeWidth = 4f
        canvas.drawRoundRect(frame, 12f, 12f, glowPaint)
        drawFrameCorners(canvas, frame)
        canvas.restore()
        drawOutline(canvas)
    }

    private fun drawFrameCorners(canvas: Canvas, frame: RectF) {
        val mark = 20f
        glowPaint.strokeWidth = 7f
        canvas.drawLine(frame.left, frame.top + mark, frame.left, frame.top, glowPaint); canvas.drawLine(frame.left, frame.top, frame.left + mark, frame.top, glowPaint)
        canvas.drawLine(frame.right - mark, frame.top, frame.right, frame.top, glowPaint); canvas.drawLine(frame.right, frame.top, frame.right, frame.top + mark, glowPaint)
        canvas.drawLine(frame.left, frame.bottom - mark, frame.left, frame.bottom, glowPaint); canvas.drawLine(frame.left, frame.bottom, frame.left + mark, frame.bottom, glowPaint)
        canvas.drawLine(frame.right - mark, frame.bottom, frame.right, frame.bottom, glowPaint); canvas.drawLine(frame.right, frame.bottom, frame.right, frame.bottom - mark, glowPaint)
    }
}
