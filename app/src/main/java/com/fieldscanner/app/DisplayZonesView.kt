package com.fieldscanner.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.BlurMaskFilter
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Region
import android.os.Build
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import com.fieldscanner.app.display.DisplayGeometry
import com.fieldscanner.app.display.DisplayTransform
import com.fieldscanner.app.display.DisplayViewport
import com.fieldscanner.app.display.LeftDisplayInputs
import com.fieldscanner.app.display.effects.InterferenceEffect
import com.fieldscanner.app.display.zones.BottomStatusRenderer
import com.fieldscanner.app.display.zones.CentralZoneRenderer
import com.fieldscanner.app.display.zones.DeviceIndicators
import com.fieldscanner.app.display.zones.LeftContextRenderer
import com.fieldscanner.app.display.zones.LeftTemperatureGesture
import com.fieldscanner.app.display.zones.RuneHintsZoneRenderer
import com.fieldscanner.app.data.finding.ScanVisual
import com.fieldscanner.app.runes.HintSlotId
import com.fieldscanner.app.runes.RuneBinding
import com.fieldscanner.app.runes.RuneId

/** Coordinates fixed display zones, their animation clocks, and rune-touch emulation. */
class DisplayZonesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    var onHintRunePressed: ((RuneId) -> Unit)? = null
    /** Hold on a rune-hint slot past [RUNE_LONG_PRESS_MILLIS]; used by the language switch (TASK-015). */
    var onHintRuneLongPressed: ((RuneId) -> Unit)? = null

    private var displayState = DisplayState()
    private val centralZoneRenderer = CentralZoneRenderer(context)
    private val runeHintsRenderer = RuneHintsZoneRenderer(context)
    private val bottomStatusRenderer = BottomStatusRenderer(context.applicationContext, DisplayGeometry.bottomZone)
    private val leftContextRenderer = LeftContextRenderer(DisplayGeometry.leftZone)
    private var deviceIndicators = DeviceIndicators()
    /** Left-display backend, assembled in AppContainer and injected from MainActivity. */
    private var leftDisplayInputs: LeftDisplayInputs? = null
    private val interferenceEffect = InterferenceEffect()
    private val hintPager = RuneHintPager()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val leftGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val hintRegions = HintSlotId.values()
        .mapIndexed { index, id -> id to regionFor(DisplayGeometry.hintZones[index]) }
        .toMap()
    private val leftRegion = regionFor(DisplayGeometry.leftZone)
    private var leftTemperaturePressStartedAt: Long? = null
    private var longPressRune: RuneId? = null
    private var longPressSlotBounds: android.graphics.Rect? = null
    private var longPressConsumed = false
    private var runeFlashUntilMillis = 0L
    /** Deadline of the textless "something failed" pulse over the lower zone; the reason goes to the diagnostics log. */
    private var errorPulseUntilMillis = 0L
    private val errorPulsePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var animationRunning = false
    /**
     * Shared uniform (letterbox) transform from the fixed virtual artwork space to
     * real view pixels, recomputed on size / window-insets change. Used by both
     * [onDraw] and [onTouchEvent] so drawing and hit-testing never diverge (TASK-033).
     */
    private var transform = DisplayTransform(1f, 0f, 0f)
    private val rotateHintPage = Runnable {
        if (hintPager.advancePage()) {
            scheduleHintPageRotation()
            invalidate()
        }
    }
    private val runeLongPressRunnable = Runnable {
        val rune = longPressRune ?: return@Runnable
        longPressConsumed = true
        runeFlashUntilMillis = SystemClock.uptimeMillis() + RUNE_FLASH_MILLIS
        invalidate()
        onHintRuneLongPressed?.invoke(rune)
    }
    private val showTemperatureRunnable = Runnable {
        if (leftTemperaturePressStartedAt != null) render(displayState.copy(showOutdoorTemperature = true))
    }
    private val refreshTemperatureRunnable = Runnable {
        if (leftTemperaturePressStartedAt != null) {
            leftDisplayInputs?.refreshTemperatureNow()
        }
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    /** Injects the left-display backend; wiring lives in AppContainer, not in this View. */
    internal fun setLeftDisplayInputs(inputs: LeftDisplayInputs) {
        leftDisplayInputs?.stop()
        inputs.onIndicatorsChanged = { indicators ->
            deviceIndicators = indicators
            postInvalidateOnAnimation()
        }
        leftDisplayInputs = inputs
        if (isAttachedToWindow) inputs.start()
    }

    fun render(state: DisplayState) {
        displayState = state
        invalidate()
    }

    fun toggleCentralCamera(): Boolean {
        render(displayState.copy(showCameraInCenter = !displayState.showCameraInCenter))
        return displayState.showCameraInCenter
    }

    fun showCentralCamera() = render(displayState.copy(showCameraInCenter = true))
    fun hideCentralCamera() = render(displayState.copy(showCameraInCenter = false))
    fun isCentralCameraVisible() = displayState.showCameraInCenter

    fun setAvailableHintBindings(bindings: List<RuneBinding>) {
        hintPager.setBindings(bindings)
        scheduleHintPageRotation()
        invalidate()
    }

    fun notifyRuneInteraction() {
        hintPager.reset()
        scheduleHintPageRotation()
        invalidate()
    }

    fun setJournalDetails(title: String?, latitude: Double?, longitude: Double?) {
        val coordinates = if (latitude != null && longitude != null) {
            String.format(java.util.Locale.US, "%.5f, %.5f", latitude, longitude)
        } else null
        render(displayState.copy(journalTitle = title, journalCoordinates = coordinates))
    }

    fun setCallDetails(title: String?, contactName: String?, phoneNumber: String?) =
        render(displayState.copy(callTitle = title, callContactName = contactName, callPhoneNumber = phoneNumber))

    fun setContactPicker(selection: ContactSelection?) = render(displayState.copy(contactSelection = selection))
    fun showIdleBottomStatus() = setBottomStatus(BottomStatusMode.IDLE)
    fun showCameraBottomStatus() = setBottomStatus(BottomStatusMode.CAMERA)
    fun hideBottomStatus() = setBottomStatus(BottomStatusMode.HIDDEN)
    fun showPhotoReviewBottomStatus() { if (displayState.bottomStatusMode != BottomStatusMode.CAPTURE_FLASH) setBottomStatus(BottomStatusMode.PHOTO_REVIEW, displayState.bottomSignalFrozenAtMillis ?: SystemClock.uptimeMillis()) }
    fun showAnalyzingBottomStatus() = setBottomStatus(BottomStatusMode.ANALYZING, displayState.bottomSignalFrozenAtMillis ?: SystemClock.uptimeMillis())
    fun showDeleteConfirmationBottomStatus() = setBottomStatus(BottomStatusMode.DELETE_CONFIRMATION)
    fun showAnalysisResultBottomStatus(visual: ScanVisual = ScanVisual.UNKNOWN_OBJECT) {
        bottomStatusRenderer.setResultVisual(visual)
        setBottomStatus(BottomStatusMode.ANALYSIS_RESULT, displayState.bottomSignalFrozenAtMillis ?: SystemClock.uptimeMillis())
    }

    /**
     * Textless failure cue: a short red throb over the lower signal zone. It carries no meaning
     * beyond "an action did not complete" — the specific reason is written to the diagnostics log
     * by the caller. Shared by analysis, photo-save, deletion, camera and telephony failures so the
     * child never sees error text (see docs/modules/ui-and-input.md).
     */
    fun playErrorSignal() {
        errorPulseUntilMillis = SystemClock.uptimeMillis() + ERROR_PULSE_MILLIS
        invalidate()
    }

    fun playCaptureFlash() {
        val frozenAtMillis = SystemClock.uptimeMillis()
        bottomStatusRenderer.capture()
        render(displayState.copy(bottomStatusMode = BottomStatusMode.CAPTURE_FLASH, bottomSignalFrozenAtMillis = frozenAtMillis))
        postDelayed({ if (displayState.bottomStatusMode == BottomStatusMode.CAPTURE_FLASH) setBottomStatus(BottomStatusMode.PHOTO_REVIEW, frozenAtMillis) }, CAPTURE_FLASH_DURATION_MILLIS)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.translate(transform.offsetX, transform.offsetY)
        canvas.scale(transform.scale, transform.scale)
        val now = SystemClock.uptimeMillis()
        drawCentralAndOuterZones(canvas, now)
        bottomStatusRenderer.draw(canvas, displayState.bottomStatusMode, displayState.bottomSignalFrozenAtMillis, now)
        // The child-facing UI must never require reading: release builds carry no text.
        // Journal/call identity is conveyed by the finding photo and the MOM/DAD contact
        // icon; these overlays are developer diagnostics only. See docs/modules/ui-and-input.md.
        if (BuildConfig.DEBUG) drawOverlayText(canvas)
        interferenceEffect.draw(canvas, DisplayGeometry.centerZone, now, displayState.showCameraInCenter)
        drawRuneLongPressFlash(canvas, now)
        drawErrorPulse(canvas, now)
        canvas.restore()
        if (animationRunning) postInvalidateDelayed(FRAME_DELAY_MILLIS)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recomputeTransform()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        recomputeTransform()
        invalidate()
        return super.onApplyWindowInsets(insets)
    }

    /** Recomputes the shared letterbox transform from the current size and display-cutout safe insets. */
    private fun recomputeTransform() {
        var insetLeft = 0
        var insetTop = 0
        var insetRight = 0
        var insetBottom = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val cutout = rootWindowInsets?.displayCutout
            if (cutout != null) {
                insetLeft = cutout.safeInsetLeft
                insetTop = cutout.safeInsetTop
                insetRight = cutout.safeInsetRight
                insetBottom = cutout.safeInsetBottom
            }
        }
        transform = DisplayViewport.of(width, height, insetLeft, insetTop, insetRight, insetBottom)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); recomputeTransform(); leftDisplayInputs?.start(); animationRunning = true; interferenceEffect.start(SystemClock.uptimeMillis()); postInvalidateOnAnimation(); scheduleHintPageRotation() }
    override fun onDetachedFromWindow() { animationRunning = false; leftDisplayInputs?.stop(); removeCallbacks(rotateHintPage); cancelLeftTemperatureGesture(); cancelRuneLongPress(); super.onDetachedFromWindow() }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (width == 0 || height == 0) return true
        if (!transform.contains(event.x, event.y)) {
            // Tap in the black letterbox margin: swallow it, dispatch no gesture.
            cancelLeftTemperatureGesture()
            cancelRuneLongPress()
            return true
        }
        val x = transform.toVirtualX(event.x).toInt()
        val y = transform.toVirtualY(event.y).toInt()
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (leftRegion.contains(x, y) && y >= LEFT_TEMPERATURE_AREA_TOP) startLeftTemperatureGesture()
                else startRuneLongPress(x, y)
            }
            MotionEvent.ACTION_MOVE -> if (longPressRune != null && longPressSlotBounds?.contains(x, y) != true) cancelRuneLongPress()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasTemperatureGesture = leftTemperaturePressStartedAt != null
                cancelLeftTemperatureGesture()
                val wasLongPress = longPressConsumed
                cancelRuneLongPress()
                if (wasTemperatureGesture || wasLongPress) return true
                if (event.action == MotionEvent.ACTION_UP) hintRegions.entries.firstOrNull { it.value.contains(x, y) }?.key
                    ?.let { id -> hintPager.slots().firstOrNull { it.id == id }?.binding?.rune }
                    ?.let { onHintRunePressed?.invoke(it) }
            }
        }
        return true
    }
    private fun setBottomStatus(mode: BottomStatusMode, frozenAtMillis: Long? = null) { bottomStatusRenderer.setMode(mode); render(displayState.copy(bottomStatusMode = mode, bottomSignalFrozenAtMillis = frozenAtMillis)) }
    private fun scheduleHintPageRotation() { removeCallbacks(rotateHintPage); if (hintPager.pageCount() > 1) postDelayed(rotateHintPage, RuneHintPager.PAGE_DURATION_MILLIS) }
    private fun drawCentralAndOuterZones(canvas: Canvas, now: Long) { if (displayState.showCameraInCenter) { canvas.saveLayer(0f, 0f, DisplayGeometry.WIDTH, DisplayGeometry.HEIGHT, null); canvas.drawColor(Color.BLACK); canvas.drawPath(DisplayGeometry.centerZone, clearPaint); drawOuterZones(canvas, now); canvas.restore(); centralZoneRenderer.drawOutline(canvas) } else { canvas.drawColor(Color.BLACK); centralZoneRenderer.draw(canvas, displayState.contactSelection); drawOuterZones(canvas, now) } }
    private fun drawOuterZones(canvas: Canvas, now: Long) { fillPaint.color = 0xFF24150B.toInt(); canvas.drawPath(DisplayGeometry.leftZone, fillPaint); leftContextRenderer.draw(canvas, deviceIndicators, displayState.showOutdoorTemperature, now); canvas.drawPath(DisplayGeometry.leftZone, outlinePaint); drawLeftGlow(canvas); fillPaint.color = 0xFF10100E.toInt(); canvas.drawPath(DisplayGeometry.bottomZone, fillPaint); canvas.drawPath(DisplayGeometry.bottomZone, outlinePaint); bottomStatusRenderer.drawGlow(canvas); runeHintsRenderer.draw(canvas, hintPager, now) }
    private fun drawLeftGlow(canvas: Canvas) { leftGlowPaint.color = 0x4CB0B0B0; leftGlowPaint.strokeWidth = 21f; leftGlowPaint.maskFilter = BlurMaskFilter(24f, BlurMaskFilter.Blur.NORMAL); canvas.drawPath(DisplayGeometry.leftZone, leftGlowPaint); leftGlowPaint.maskFilter = null; leftGlowPaint.strokeWidth = 3.5f; canvas.drawPath(DisplayGeometry.leftZone, leftGlowPaint) }
    private fun drawOverlayText(canvas: Canvas) { fillPaint.color = Color.BLACK; fillPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD); displayState.journalTitle?.let { fillPaint.textSize = 26f; canvas.drawText(it.take(28), 500f, 620f, fillPaint) }; displayState.journalCoordinates?.let { fillPaint.textSize = 22f; canvas.drawText(it, 535f, 665f, fillPaint) }; displayState.callTitle?.let { title -> canvas.save(); canvas.clipPath(DisplayGeometry.centerZone); fillPaint.textAlign = Paint.Align.CENTER; fillPaint.textSize = 24f; canvas.drawText(title, 643f, 272f, fillPaint); fillPaint.textSize = 38f; canvas.drawText(displayState.callContactName.orEmpty(), 643f, 352f, fillPaint); fillPaint.textSize = 24f; canvas.drawText(displayState.callPhoneNumber.orEmpty(), 643f, 405f, fillPaint); fillPaint.textAlign = Paint.Align.LEFT; canvas.restore() } }
    private fun regionFor(path: Path): Region { val bounds = android.graphics.RectF(); path.computeBounds(bounds, true); return Region().apply { setPath(path, Region(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())) } }
    private fun startRuneLongPress(x: Int, y: Int) {
        cancelRuneLongPress()
        val slot = hintRegions.entries.firstOrNull { it.value.contains(x, y) } ?: return
        val rune = hintPager.slots().firstOrNull { it.id == slot.key }?.binding?.rune ?: return
        longPressRune = rune
        longPressSlotBounds = slot.value.bounds
        longPressConsumed = false
        postDelayed(runeLongPressRunnable, RUNE_LONG_PRESS_MILLIS)
    }

    private fun cancelRuneLongPress() {
        removeCallbacks(runeLongPressRunnable)
        longPressRune = null
        longPressSlotBounds = null
        longPressConsumed = false
    }

    private fun drawRuneLongPressFlash(canvas: Canvas, now: Long) {
        val bounds = longPressSlotBounds ?: return
        if (now >= runeFlashUntilMillis) return
        val alpha = (255 * (runeFlashUntilMillis - now) / RUNE_FLASH_MILLIS).toInt().coerceIn(0, 255)
        leftGlowPaint.maskFilter = null
        leftGlowPaint.style = Paint.Style.STROKE
        leftGlowPaint.strokeWidth = 8f
        leftGlowPaint.color = Color.argb(alpha, 255, 255, 255)
        canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), minOf(bounds.width(), bounds.height()) / 2f - 6f, leftGlowPaint)
        leftGlowPaint.strokeWidth = 3.5f
    }

    private fun drawErrorPulse(canvas: Canvas, now: Long) {
        val remaining = errorPulseUntilMillis - now
        if (remaining <= 0L) return
        val progress = 1f - remaining.toFloat() / ERROR_PULSE_MILLIS
        val strength = kotlin.math.abs(kotlin.math.sin(progress * Math.PI.toFloat() * ERROR_PULSE_CYCLES)) * (1f - progress)
        canvas.save()
        canvas.clipPath(DisplayGeometry.bottomZone)
        canvas.drawColor(Color.argb((150 * strength).toInt().coerceIn(0, 255), 255, 45, 30))
        canvas.restore()
        errorPulsePaint.style = Paint.Style.STROKE
        errorPulsePaint.strokeWidth = 6f
        errorPulsePaint.maskFilter = null
        errorPulsePaint.color = Color.argb((225 * strength).toInt().coerceIn(0, 255), 255, 80, 60)
        canvas.drawPath(DisplayGeometry.bottomZone, errorPulsePaint)
    }

    private fun startLeftTemperatureGesture() {
        if (leftTemperaturePressStartedAt != null) return
        leftTemperaturePressStartedAt = SystemClock.uptimeMillis()
        postDelayed(showTemperatureRunnable, LeftTemperatureGesture.SHOW_TEMPERATURE_AFTER_MILLIS)
        postDelayed(refreshTemperatureRunnable, LeftTemperatureGesture.REFRESH_TEMPERATURE_AFTER_MILLIS)
    }
    private fun cancelLeftTemperatureGesture() {
        if (leftTemperaturePressStartedAt == null) return
        leftTemperaturePressStartedAt = null
        removeCallbacks(showTemperatureRunnable)
        removeCallbacks(refreshTemperatureRunnable)
        render(displayState.copy(showOutdoorTemperature = false))
    }
    private companion object { const val FRAME_DELAY_MILLIS = 50L; const val CAPTURE_FLASH_DURATION_MILLIS = 1_100L; const val LEFT_TEMPERATURE_AREA_TOP = 561; const val RUNE_LONG_PRESS_MILLIS = 1_000L; const val RUNE_FLASH_MILLIS = 260L; const val ERROR_PULSE_MILLIS = 900L; const val ERROR_PULSE_CYCLES = 2f }
}
