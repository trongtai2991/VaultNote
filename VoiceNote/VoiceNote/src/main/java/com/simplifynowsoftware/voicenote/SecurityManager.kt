package com.simplifynowsoftware.voicenote

import android.os.SystemClock

object SecurityManager {
    var isUnlocked: Boolean = false
    private var lastActiveTime: Long = 0
    private const val LOCK_TIMEOUT_MS = 5 * 60 * 1000 // 5 phút

    fun onAppForegrounded() {
        if (lastActiveTime != 0L) {
            val idleTime = SystemClock.elapsedRealtime() - lastActiveTime
            if (idleTime > LOCK_TIMEOUT_MS) {
                isUnlocked = false
            }
        }
    }

    fun onAppBackgrounded() {
        lastActiveTime = SystemClock.elapsedRealtime()
    }
}
