package com.kiwi.island

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

object ChargingSource {
    private const val CHARGING_MS = 6_000L
    private const val LOW_BATTERY_MS = 8_000L

    private var registered = false
    private var lastPlugged: Boolean? = null
    private var lowAnnounced = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (level < 0) return
            val percent = level * 100 / scale
            // Like iOS, charging and low-battery are momentary events, not permanent residents of the island.
            // The first (sticky) broadcast only sets the baseline, so starting the service doesn't flash "charging".
            val previous = lastPlugged
            lastPlugged = plugged
            if (plugged) {
                lowAnnounced = false
                ActivityRepository.remove("battery")
                if (previous == false) ActivityRepository.putFor(ChargingActivity(percent), CHARGING_MS)
                else ActivityRepository.refresh(ChargingActivity(percent))
            } else {
                ActivityRepository.remove("charging")
                if (percent <= 20) {
                    if (!lowAnnounced) {
                        lowAnnounced = true
                        ActivityRepository.putFor(BatteryActivity(percent), LOW_BATTERY_MS)
                    } else {
                        ActivityRepository.refresh(BatteryActivity(percent))
                    }
                } else {
                    lowAnnounced = false
                    ActivityRepository.remove("battery")
                }
            }
        }
    }

    fun start(context: Context) {
        if (registered) return
        ContextCompat.registerReceiver(
            context.applicationContext,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        registered = true
    }

    fun stop(context: Context) {
        if (!registered) return
        runCatching { context.applicationContext.unregisterReceiver(receiver) }
        registered = false
        lastPlugged = null
        lowAnnounced = false
        ActivityRepository.remove("charging")
        ActivityRepository.remove("battery")
    }
}

object CallSource {
    private var caller: String? = null
    private var state = TelephonyManager.CALL_STATE_IDLE
    private var startedAt = 0L
    private var modern: Any? = null
    private var legacy: PhoneStateListener? = null

    fun start(context: Context) {
        if (modern != null || legacy != null) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val telephony = context.getSystemService(TelephonyManager::class.java)
        if (Build.VERSION.SDK_INT >= 31) {
            registerModern(context, telephony)
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) = update(state)
            }
            legacy = listener
            @Suppress("DEPRECATION")
            telephony.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    @RequiresApi(31)
    private fun registerModern(context: Context, telephony: TelephonyManager) {
        val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) = update(state)
        }
        modern = callback
        telephony.registerTelephonyCallback(context.mainExecutor, callback)
    }

    fun stop(context: Context) {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        if (Build.VERSION.SDK_INT >= 31) {
            (modern as? TelephonyCallback)?.let { telephony.unregisterTelephonyCallback(it) }
        } else {
            @Suppress("DEPRECATION")
            legacy?.let { telephony.listen(it, PhoneStateListener.LISTEN_NONE) }
        }
        modern = null
        legacy = null
        state = TelephonyManager.CALL_STATE_IDLE
        ActivityRepository.remove("call")
    }

    fun setCaller(name: String?) {
        caller = name
        if (state != TelephonyManager.CALL_STATE_IDLE) publish()
    }

    private fun update(newState: Int) {
        state = newState
        when (newState) {
            TelephonyManager.CALL_STATE_OFFHOOK -> if (startedAt == 0L) startedAt = SystemClock.elapsedRealtime()
            TelephonyManager.CALL_STATE_IDLE -> {
                startedAt = 0L
                caller = null
            }
        }
        publish()
    }

    private fun publish() {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> ActivityRepository.put(CallActivity(true, caller, 0L))
            TelephonyManager.CALL_STATE_OFFHOOK -> ActivityRepository.put(CallActivity(false, caller, startedAt))
            else -> ActivityRepository.remove("call")
        }
    }
}

object CallActions {
    private fun allowed(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) ==
            PackageManager.PERMISSION_GRANTED

    @Suppress("MissingPermission")
    fun answer(context: Context) {
        if (!allowed(context)) return
        runCatching { context.getSystemService(TelecomManager::class.java).acceptRingingCall() }
    }

    @Suppress("MissingPermission", "DEPRECATION")
    fun end(context: Context) {
        if (!allowed(context)) return
        runCatching { context.getSystemService(TelecomManager::class.java).endCall() }
    }
}
