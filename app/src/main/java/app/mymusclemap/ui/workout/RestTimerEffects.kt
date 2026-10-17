package app.mymusclemap.ui.workout

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import app.mymusclemap.MainActivity
import app.mymusclemap.R
import app.mymusclemap.data.preferences.TimerSoundPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Side effects for one rest interval. The countdown itself stays in the session
 * row. This only schedules a completion notification and plays a short tone when
 * a notification cannot.
 */
interface RestTimerEffects {
    fun schedule(sessionId: Long, deadlineWallClockMillis: Long, generation: Long, nextLabel: String)
    fun cancel(sessionId: Long)
    fun onRestFinished()

    object None : RestTimerEffects {
        override fun schedule(
            sessionId: Long,
            deadlineWallClockMillis: Long,
            generation: Long,
            nextLabel: String
        ) = Unit

        override fun cancel(sessionId: Long) = Unit
        override fun onRestFinished() = Unit
    }
}

class AndroidRestTimerEffects(context: Context) : RestTimerEffects {
    private val appContext = context.applicationContext
    private val sounds = TimerSoundPreferences(appContext)

    override fun schedule(
        sessionId: Long,
        deadlineWallClockMillis: Long,
        generation: Long,
        nextLabel: String
    ) {
        val alarm = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pending = RestTimerAlarm.pendingIntent(appContext, sessionId, generation, nextLabel)
        try {
            val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()
            if (exactAllowed) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadlineWallClockMillis, pending)
            } else {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadlineWallClockMillis, pending)
            }
        } catch (_: SecurityException) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadlineWallClockMillis, pending)
        }
    }

    override fun cancel(sessionId: Long) {
        val alarm = appContext.getSystemService(AlarmManager::class.java) ?: return
        alarm.cancel(RestTimerAlarm.pendingIntent(appContext, sessionId, generation = 0L, nextLabel = ""))
        NotificationManagerCompat.from(appContext).cancel(RestTimerAlarm.NOTIFICATION_ID)
    }

    override fun onRestFinished() {
        val enabled = runBlocking { sounds.enabled.first() }
        if (!enabled || RestTimerAlarm.canPost(appContext)) return
        RestTimerAlarm.playTone()
    }
}

internal object RestTimerAlarm {
    const val ACTION = "app.mymusclemap.action.REST_COMPLETE"
    const val CHANNEL_ID = "rest_timer"
    const val NOTIFICATION_ID = 41002
    const val EXTRA_SESSION_ID = "sessionId"
    const val EXTRA_GENERATION = "generation"
    const val EXTRA_NEXT = "nextLabel"

    fun pendingIntent(
        context: Context,
        sessionId: Long,
        generation: Long,
        nextLabel: String
    ): PendingIntent {
        val intent = Intent(context, RestTimerAlarmReceiver::class.java).apply {
            action = ACTION
            putExtra(EXTRA_SESSION_ID, sessionId)
            putExtra(EXTRA_GENERATION, generation)
            putExtra(EXTRA_NEXT, nextLabel)
        }
        return PendingIntent.getBroadcast(
            context,
            sessionId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun canPost(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    private var tone: ToneGenerator? = null

    fun playTone() {
        val generator = tone ?: ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80).also { tone = it }
        generator.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
    }

    fun show(context: Context, nextLabel: String, playSound: Boolean) {
        if (!canPost(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_rest),
            if (playSound) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = if (nextLabel.isBlank()) {
            context.getString(R.string.notification_rest_complete)
        } else {
            context.getString(R.string.notification_rest_complete_next, nextLabel)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_workout)
            .setContentTitle(context.getString(R.string.notification_rest_complete))
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setSilent(!playSound)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}

class RestTimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RestTimerAlarm.ACTION) return
        val next = intent.getStringExtra(RestTimerAlarm.EXTRA_NEXT).orEmpty()
        val sounds = runBlocking { TimerSoundPreferences(context).enabled.first() }
        RestTimerAlarm.show(context, next, playSound = sounds)
    }
}
