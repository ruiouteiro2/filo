package com.filo.app.core.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

data class BatteryState(val level: Int, val charging: Boolean)

/**
 * No permission needed: ACTION_BATTERY_CHANGED is sticky, so a null receiver registration
 * hands back the last broadcast immediately.
 */
object BatteryReader {

    const val LOW_THRESHOLD = 15

    fun read(context: Context): BatteryState? {
        val intent: Intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return BatteryState(level = (level * 100) / scale, charging = charging)
    }
}
