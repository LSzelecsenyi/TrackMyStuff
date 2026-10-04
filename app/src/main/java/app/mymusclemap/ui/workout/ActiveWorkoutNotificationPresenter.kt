package app.mymusclemap.ui.workout

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.mymusclemap.MainActivity
import app.mymusclemap.R
import app.mymusclemap.domain.workout.ActiveWorkoutNotificationBody
import app.mymusclemap.domain.workout.ActiveWorkoutNotificationModel
import app.mymusclemap.domain.workout.QuantityParser
import app.mymusclemap.domain.workout.notificationLoadLabel
import app.mymusclemap.domain.workout.notificationValueParts

internal fun shouldRequestPromotedOngoing(
    sdkInt: Int,
    sdkIntFull: Int,
    canPostPromoted: Boolean
): Boolean {
    if (sdkInt < Build.VERSION_CODES.BAKLAVA) return false
    if (sdkIntFull < Build.VERSION_CODES_FULL.BAKLAVA_1) return false
    return canPostPromoted
}

enum class LockScreenDeliveryBlock {
    None,
    Permission,
    Channel
}

internal object ActiveWorkoutNotifications {
    const val CHANNEL_ID = "active_workout"
    const val NOTIFICATION_ID = 41001
    const val ACTION_COMPLETE = "app.mymusclemap.action.COMPLETE_CURRENT_SET"
    const val EXTRA_SESSION_ID = "sessionId"
    const val EXTRA_SET_ID = "setId"

    fun canPost(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_active_workout),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_active_workout_description)
            setShowBadge(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }

    fun publish(
        context: Context,
        model: ActiveWorkoutNotificationModel?,
        notificationId: Int = NOTIFICATION_ID
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (model == null || !canDeliver(context)) {
            manager.cancel(notificationId)
            return
        }
        ensureChannel(context)
        manager.notify(notificationId, build(context, model))
    }

    fun canDeliver(context: Context): Boolean {
        return deliveryBlock(context) == LockScreenDeliveryBlock.None
    }

    fun runtimePermissionGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun deliveryBlock(context: Context): LockScreenDeliveryBlock {
        if (!runtimePermissionGranted(context) || !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return LockScreenDeliveryBlock.Permission
        }
        if (channelBlocked(context)) return LockScreenDeliveryBlock.Channel
        return LockScreenDeliveryBlock.None
    }

    fun appNotificationSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    }

    fun channelSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
            }
        } else {
            appNotificationSettingsIntent(context)
        }
    }

    private fun channelBlocked(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val channel = context.getSystemService(NotificationManager::class.java)
            ?.getNotificationChannel(CHANNEL_ID)
            ?: return false
        return channel.importance == NotificationManager.IMPORTANCE_NONE
    }

    fun cancel(context: Context, notificationId: Int = NOTIFICATION_ID) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    fun build(
        context: Context,
        model: ActiveWorkoutNotificationModel,
        requestPromotion: Boolean = promotionAvailable(context)
    ): android.app.Notification {
        val current = model.body as? ActiveWorkoutNotificationBody.CurrentSet
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_workout)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(openWorkoutIntent(context, model.sessionId))
            .setPublicVersion(publicVersion(context, model.sessionId, current))
            .setSubText(model.workoutName)
        when (val body = model.body) {
            is ActiveWorkoutNotificationBody.CurrentSet -> {
                val setLabel = context.getString(
                    R.string.notification_set_of,
                    body.setNumber,
                    body.setCount
                )
                if (body.completable) {
                    val detail = currentSetLine(setLabel, valueLabel(context, body))
                    builder
                        .setContentTitle(body.exerciseName)
                        .setContentText(detail)
                        .setStyle(
                            NotificationCompat.BigTextStyle()
                                .bigText(detail)
                                .setBigContentTitle(body.exerciseName)
                        )
                } else {
                    val open = context.getString(R.string.notification_open_to_enter_set)
                    builder
                        .setContentTitle(body.exerciseName)
                        .setContentText(open)
                        .setStyle(
                            NotificationCompat.BigTextStyle()
                                .bigText("$setLabel\n$open")
                                .setBigContentTitle(body.exerciseName)
                        )
                }
            }
            ActiveWorkoutNotificationBody.ReadyToFinish -> {
                val title = context.getString(R.string.notification_all_sets_logged)
                val text = context.getString(R.string.notification_finish_in_app)
                builder
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .bigText(text)
                            .setBigContentTitle(title)
                    )
            }
        }
        val actionable = current?.takeIf { it.completable }
        if (actionable != null) {
            builder.addAction(completeAction(context, model.sessionId, actionable.setId))
        }
        if (requestPromotion) {
            builder.setRequestPromotedOngoing(true)
        }
        return builder.build()
    }

    fun promotionAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return false
        return canRequestPromotedOngoing(context)
    }

    @RequiresApi(36)
    private fun canRequestPromotedOngoing(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return shouldRequestPromotedOngoing(
            sdkInt = Build.VERSION.SDK_INT,
            sdkIntFull = Build.VERSION.SDK_INT_FULL,
            canPostPromoted = manager.canPostPromotedNotifications()
        )
    }

    fun completeIntent(context: Context, sessionId: Long, setId: Long): Intent {
        return Intent(context, ActiveWorkoutSetReceiver::class.java).apply {
            action = ACTION_COMPLETE
            data = Uri.parse("app://mymusclemap/complete-set/$sessionId/$setId")
            putExtra(EXTRA_SESSION_ID, sessionId)
            putExtra(EXTRA_SET_ID, setId)
        }
    }

    fun completeRequestCode(setId: Long): Int {
        return (setId xor (setId ushr 32)).toInt()
    }

    fun completionIds(intent: Intent?): Pair<Long, Long>? {
        if (intent?.action != ACTION_COMPLETE) return null
        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
        val setId = intent.getLongExtra(EXTRA_SET_ID, -1L)
        if (sessionId <= 0L || setId <= 0L) return null
        return sessionId to setId
    }

    private fun completeAction(
        context: Context,
        sessionId: Long,
        setId: Long
    ): NotificationCompat.Action {
        val pending = PendingIntent.getBroadcast(
            context,
            completeRequestCode(setId),
            completeIntent(context, sessionId, setId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_notification_workout,
            context.getString(R.string.notification_complete_set),
            pending
        ).build()
    }

    private fun openWorkoutIntent(context: Context, sessionId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("app://mymusclemap/active-workout/$sessionId")
            putExtra(MainActivity.EXTRA_OPEN_ACTIVE_WORKOUT, sessionId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun publicVersion(
        context: Context,
        sessionId: Long,
        current: ActiveWorkoutNotificationBody.CurrentSet?
    ): android.app.Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_workout)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notification_workout_in_progress))
            .setContentIntent(openWorkoutIntent(context, sessionId))
        if (current?.completable == true) {
            builder.addAction(completeAction(context, sessionId, current.setId))
        }
        return builder.build()
    }

    internal fun valueLabel(context: Context, body: ActiveWorkoutNotificationBody.CurrentSet): String {
        if (!body.completable) return ""
        val reps = body.reps?.let { context.getString(R.string.notification_reps, it) }
        val load = body.loadKind?.let { kind ->
            notificationLoadLabel(
                kind,
                body.weightKg,
                context.getString(R.string.set_copy_bodyweight)
            )
        }
        val duration = body.durationSeconds?.let { seconds ->
            val (minutes, remainder) = QuantityParser.fromSeconds(seconds)
            "$minutes:${remainder.toString().padStart(2, '0')}"
        }
        val distance = body.distanceMeters?.let { meters ->
            context.getString(
                R.string.notification_distance_meters,
                QuantityParser.formatDisplay(meters)
            )
        }
        return notificationValueParts(reps, load, duration, distance)
    }

    internal fun currentSetLine(setLabel: String, valueLabel: String): String {
        return if (valueLabel.isBlank()) setLabel else "$setLabel · $valueLabel"
    }
}
