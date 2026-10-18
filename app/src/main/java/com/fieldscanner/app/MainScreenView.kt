package com.fieldscanner.app

import android.view.View
import android.widget.ImageView
import androidx.camera.view.PreviewView

/** Bound widgets for the fullscreen scanner screen; keeps Android view references out of flow code. */
class MainScreenView private constructor(
    val cameraPreview: PreviewView,
    val displayZones: DisplayZonesView,
    val photo: ImageView
) {
    companion object {
        fun bind(root: View) = MainScreenView(
            root.findViewById(R.id.cameraPreview),
            root.findViewById(R.id.displayZones),
            root.findViewById(R.id.capturedPhoto)
        )
    }
}
