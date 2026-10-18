package com.fieldscanner.app.display

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.fieldscanner.app.display.zones.DeviceIndicators

/** Reads the system's sticky battery broadcast without requiring a runtime permission. */
internal class DeviceIndicatorSource(
    private val context: Context
) : DeviceIndicatorProvider {
    override var onIndicatorsChanged: (DeviceIndicators) -> Unit = {}
    private var registered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = publish(intent)
    }

    override fun start() {
        if (registered) return
        try {
            context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(::publish)
            registered = true
        } catch (_: SecurityException) {
            onIndicatorsChanged(DeviceIndicators())
        }
    }

    override fun stop() {
        if (!registered) return
        try { context.unregisterReceiver(receiver) } catch (_: IllegalArgumentException) { }
        registered = false
    }

    private fun publish(intent: Intent) {
        val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else null
        onIndicatorsChanged(DeviceIndicators(batteryPercent = percent))
    }
}
