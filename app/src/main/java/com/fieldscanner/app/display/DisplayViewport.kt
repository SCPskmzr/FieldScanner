package com.fieldscanner.app.display

/**
 * Uniform (letterbox) mapping between the fixed virtual artwork space
 * (`DisplayGeometry.WIDTH` x `DisplayGeometry.HEIGHT`) and real view pixels.
 *
 * The same transform is used for drawing (translate + uniform scale) and for
 * mapping `MotionEvent` coordinates back into virtual space, so the artwork and
 * every touch zone stay aligned on any aspect ratio. There is no second copy of
 * the math.
 */
internal data class DisplayTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
) {
    /** Screen px -> virtual x. */
    fun toVirtualX(px: Float): Float = (px - offsetX) / scale

    /** Screen px -> virtual y. */
    fun toVirtualY(py: Float): Float = (py - offsetY) / scale

    /** Virtual x -> screen px. */
    fun toScreenX(vx: Float): Float = vx * scale + offsetX

    /** Virtual y -> screen px. */
    fun toScreenY(vy: Float): Float = vy * scale + offsetY

    /**
     * True when a screen-space point falls on the drawn artwork rather than in
     * the black letterbox margin. Points in the margin map to virtual
     * coordinates outside `0..WIDTH` / `0..HEIGHT` and must not dispatch a
     * gesture.
     */
    fun contains(px: Float, py: Float): Boolean {
        val vx = toVirtualX(px)
        val vy = toVirtualY(py)
        return vx in 0f..DisplayGeometry.WIDTH && vy in 0f..DisplayGeometry.HEIGHT
    }
}

/** Pure builder for [DisplayTransform]; unit-testable without a View. */
internal object DisplayViewport {
    /**
     * Computes the uniform fit of the virtual artwork inside the cutout-safe
     * area of a view of [viewWidth] x [viewHeight] pixels. The safe area is the
     * view rect shrunk by the given insets; the artwork is scaled by
     * `min(safeW / WIDTH, safeH / HEIGHT)` and centred inside it, leaving the
     * surrounding margin for the view's black background.
     */
    fun of(
        viewWidth: Int,
        viewHeight: Int,
        insetLeft: Int = 0,
        insetTop: Int = 0,
        insetRight: Int = 0,
        insetBottom: Int = 0,
    ): DisplayTransform {
        val safeWidth = (viewWidth - insetLeft - insetRight).coerceAtLeast(0)
        val safeHeight = (viewHeight - insetTop - insetBottom).coerceAtLeast(0)
        if (safeWidth == 0 || safeHeight == 0) return DisplayTransform(1f, 0f, 0f)
        val scale = minOf(safeWidth / DisplayGeometry.WIDTH, safeHeight / DisplayGeometry.HEIGHT)
        val offsetX = insetLeft + (safeWidth - DisplayGeometry.WIDTH * scale) / 2f
        val offsetY = insetTop + (safeHeight - DisplayGeometry.HEIGHT * scale) / 2f
        return DisplayTransform(scale, offsetX, offsetY)
    }
}
