package com.fieldscanner.app

import android.os.Looper
import android.view.View
import androidx.camera.view.PreviewView
import com.fieldscanner.app.support.TestAppContainer
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * App-startup smoke test (TASK-020).
 *
 * `:app:lintDebug` / `:app:assembleDebug` never run the startup path, so a broken
 * `MainActivity.onCreate` — a throwing `AppContainer`, a collapsed screen graph, a
 * failed `activity_main` inflation, or a `findViewById` that returns null — used to
 * surface only on a manual device install. This test brings the real
 * `FieldScannerApplication` and `MainActivity` up on the local JVM with a fake
 * object graph and fails if the scanner screen stops coming up.
 *
 * Covered: `FieldScannerApplication.onCreate`, `MainActivity.onCreate` through
 * `onResume`, `activity_main` inflation, `MainScreenView.bind`, the whole
 * `buildScreen()` graph (coordinator, camera/voice/language controllers,
 * `CallScreenPresenter`, rune routing) and the first `CallCoordinator` render.
 * Not covered: camera/telephony/TTS/recognition runtime behaviour, on-device
 * rendering, anything past the first frame.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainActivityStartupSmokeTest {

    @Test
    fun `scanner screen comes up without touching network, database or geolocation`() {
        val controller: ActivityController<MainActivity> =
            Robolectric.buildActivity(MainActivity::class.java)
        // Swap the real Room/OpenAI/geolocation graph for fakes before onCreate runs.
        controller.get().containerFactory = { TestAppContainer(it) }

        controller.create().start().resume()
        shadowOf(Looper.getMainLooper()).idle()

        val activity = controller.get()
        // activity_main inflated and MainScreenView.bind resolved every view it holds.
        assertTrue(activity.findViewById<View>(R.id.cameraPreview) is PreviewView)
        assertTrue(activity.findViewById<View>(R.id.displayZones) is DisplayZonesView)
        assertNotNull(activity.findViewById<View>(R.id.capturedPhoto))

        controller.pause().stop().destroy()
    }
}
