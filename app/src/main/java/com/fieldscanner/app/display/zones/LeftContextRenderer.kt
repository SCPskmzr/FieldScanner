package com.fieldscanner.app.display.zones

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

/** Text-free rendering for the left system display; it intentionally owns no bottom-status state. */
internal class LeftContextRenderer(private val zone: Path) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var displayedTemperature: Float? = null
    private var targetTemperature: Float? = null
    private var temperatureChangeStartedAt = 0L

    fun draw(canvas: Canvas, indicators: DeviceIndicators, showTemperature: Boolean, nowMillis: Long) {
        canvas.save()
        canvas.clipPath(zone)
        when (LeftDisplayState.contentFor(indicators, showTemperature)) {
            LeftDisplayContent.BATTERY -> drawBattery(canvas, indicators.batteryPercent ?: 0)
            LeftDisplayContent.TEMPERATURE -> drawTemperatureScale(canvas, indicators.outdoorTemperatureCelsius, nowMillis)
            LeftDisplayContent.TEMPERATURE_WARNING -> drawTemperatureWarning(canvas, nowMillis)
            LeftDisplayContent.UNAVAILABLE -> drawUnavailable(canvas)
        }
        canvas.restore()
    }

    private fun drawBattery(canvas: Canvas, percent: Int) {
        // The physical zone is the battery: its lower edge fills upward with amber energy.
        val zoneBottom = 712.5f
        val zoneHeight = 641f
        val top = zoneBottom - zoneHeight * percent.coerceIn(0, 100) / 100f
        fill.color = if (percent <= 15) 0xFFFF632B.toInt() else 0xFFFF7B19.toInt()
        canvas.drawRect(0f, top, 190f, zoneBottom, fill)
        paint.color = 0xFFFFC06A.toInt()
        paint.strokeWidth = 3f
        canvas.drawLine(20f, top, 185f, top, paint)
        paint.strokeWidth = 5f
    }

    private fun drawTemperatureWarning(canvas: Canvas, nowMillis: Long) {
        val intense = (nowMillis / 500L) % 2L == 0L
        fill.color = if (intense) 0xFFFF493B.toInt() else 0xFFD72720.toInt()
        val triangle = Path().apply { moveTo(92f, 210f); lineTo(40f, 410f); lineTo(144f, 410f); close() }
        canvas.drawPath(triangle, fill)
        paint.color = Color.WHITE
        paint.strokeWidth = 8f
        canvas.drawLine(92f, 265f, 92f, 350f, paint)
        fill.color = Color.WHITE
        canvas.drawCircle(92f, 378f, 7f, fill)
        paint.strokeWidth = 5f
    }

    private fun drawTemperatureScale(canvas: Canvas, temperature: Float?, nowMillis: Long) {
        val scaleTop = 142f
        val scaleBottom = 625f
        val scaleX = 92f
        paint.color = 0xFFFFC06A.toInt()
        paint.strokeWidth = 4f
        canvas.drawLine(scaleX, scaleTop, scaleX, scaleBottom, paint)
        listOf(-10f, 0f, 10f, 20f, 30f, 40f, 45f).forEach { value ->
            val y = temperatureY(value, scaleTop, scaleBottom)
            canvas.drawLine(scaleX - 16f, y, scaleX + 16f, y, paint)
        }
        fill.color = 0xFFFFE4B5.toInt()
        fill.textSize = 24f
        fill.textAlign = Paint.Align.CENTER
        canvas.drawText("+45", scaleX, scaleTop - 18f, fill)
        canvas.drawText("−10", scaleX, scaleBottom + 30f, fill)

        val current = animatedTemperature(temperature, nowMillis)
        if (current != null) {
            val y = temperatureY(current, scaleTop, scaleBottom)
            fill.color = if (LeftDisplayState.isOutdoorTemperatureWarning(current)) 0xFFFF493B.toInt() else 0xFFFF7B19.toInt()
            canvas.drawCircle(scaleX, y, 19f, fill)
            fill.color = Color.WHITE
            fill.textSize = 28f
            canvas.drawText(String.format(java.util.Locale.US, "%+.0f°", current), scaleX, y - 30f, fill)
        } else {
            fill.color = 0xFFB0B0B0.toInt()
            fill.textSize = 24f
            canvas.drawText("—", scaleX, (scaleTop + scaleBottom) / 2f, fill)
        }
        fill.textAlign = Paint.Align.LEFT
    }

    private fun animatedTemperature(temperature: Float?, nowMillis: Long): Float? {
        if (temperature == null) {
            displayedTemperature = null
            targetTemperature = null
            return null
        }
        if (targetTemperature != temperature) {
            displayedTemperature = displayedTemperature ?: temperature
            targetTemperature = temperature
            temperatureChangeStartedAt = nowMillis
        }
        val start = displayedTemperature ?: temperature
        val target = targetTemperature ?: temperature
        val progress = ((nowMillis - temperatureChangeStartedAt).toFloat() / TEMPERATURE_ANIMATION_MILLIS).coerceIn(0f, 1f)
        return (start + (target - start) * progress).also { displayedTemperature = it }
    }

    private fun temperatureY(temperature: Float, top: Float, bottom: Float): Float {
        val fraction = ((temperature - MIN_TEMPERATURE_CELSIUS) / (MAX_TEMPERATURE_CELSIUS - MIN_TEMPERATURE_CELSIUS)).coerceIn(0f, 1f)
        return bottom - fraction * (bottom - top)
    }

    private fun drawUnavailable(canvas: Canvas) {
        paint.color = 0xFFB0B0B0.toInt()
        canvas.drawCircle(92f, 320f, 38f, paint)
        canvas.drawLine(65f, 347f, 119f, 293f, paint)
    }

    private companion object {
        const val MIN_TEMPERATURE_CELSIUS = -10f
        const val MAX_TEMPERATURE_CELSIUS = 45f
        const val TEMPERATURE_ANIMATION_MILLIS = 600f
    }
}
