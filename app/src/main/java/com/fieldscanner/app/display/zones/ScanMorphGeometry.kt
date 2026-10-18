package com.fieldscanner.app.display.zones

import com.fieldscanner.app.SignalSample

/**
 * Pure coordinate operations for the result outline morph.
 *
 * Artists author outlines against the shared template
 * (`docs/artwork/bottom-status-outline-template.svg`) whose native artboard is
 * `viewBox="0 0 640 200"`. Those coordinates are consumed directly here: the
 * template artboard is projected onto the physical lower display zone
 * ([com.fieldscanner.app.display.DisplayGeometry.bottomZone]) with no manual
 * rescaling or translation required from the artist.
 */
internal object ScanMorphGeometry {
    /** Native template artboard, matching `viewBox="0 0 640 200"`. */
    const val TEMPLATE_WIDTH = 640f
    const val TEMPLATE_HEIGHT = 200f

    /** Bounding box of `DisplayGeometry.bottomZone` in display-space units. */
    const val TARGET_LEFT = 317.1f
    const val TARGET_TOP = 513.1f
    const val TARGET_WIDTH = 632.8f
    const val TARGET_HEIGHT = 198.2f

    fun projectTemplatePoint(x: Float, y: Float): SignalSample = SignalSample(
        TARGET_LEFT + x / TEMPLATE_WIDTH * TARGET_WIDTH,
        TARGET_TOP + y / TEMPLATE_HEIGHT * TARGET_HEIGHT
    )

    fun interpolate(
        source: List<SignalSample>,
        target: List<SignalSample>,
        progress: Float
    ): List<SignalSample> {
        require(source.size == target.size) { "Morph paths must have the same number of samples." }
        val amount = progress.coerceIn(0f, 1f)
        return source.indices.map { index ->
            val from = source[index]
            val to = target[index]
            SignalSample(
                from.x + (to.x - from.x) * amount,
                from.y + (to.y - from.y) * amount
            )
        }
    }
}
