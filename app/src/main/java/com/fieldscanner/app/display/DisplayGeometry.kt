package com.fieldscanner.app.display

import android.graphics.Path

/** Immutable coordinates for the fixed FieldScanner display artwork. */
internal object DisplayGeometry {
    const val WIDTH = 1286f
    const val HEIGHT = 745f

    val leftZone = path(*DisplayGeometryCoordinates.leftZone)
    val centerZone = path(*DisplayGeometryCoordinates.centerZone)
    val bottomZone = path(*DisplayGeometryCoordinates.bottomZone)
    val hintZones = DisplayGeometryCoordinates.hintZones.map { path(*it) }

    private fun path(vararg points: Float): Path = Path().apply {
        moveTo(points[0], points[1])
        var index = 2
        while (index < points.size) {
            lineTo(points[index], points[index + 1])
            index += 2
        }
        close()
    }
}
