package app.mymusclemap.ui.workout

import java.util.concurrent.ConcurrentHashMap

/**
 * One completion sound per interval, shared by the in-app player and the alarm.
 */
class TimerCompletionLedger {
    private val claimed = ConcurrentHashMap.newKeySet<String>()

    fun tryClaim(key: String): Boolean = claimed.add(key)

    fun claimed(key: String): Boolean = key in claimed

    companion object {
        val process: TimerCompletionLedger = TimerCompletionLedger()
    }
}
