package com.trichome.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.trichome.app.R
import com.trichome.app.ui.MainActivity

/**
 * Notification plumbing for all Trichome workers. Uses the monochrome
 * `ic_stat_leaf` status-bar icon.
 */
object ReminderNotifications {

    private const val CHANNEL_ID = "trichome_reminders"
    private const val CHANNEL_NAME = "Recordatorios de Cultivo"
    private const val CHANNEL_DESCRIPTION = "Recordatorios y check-ins de tus plantas"
    private const val CHANNEL_IMPORTANCE = NotificationManager.IMPORTANCE_HIGH

    const val CHECKIN_CHANNEL_ID = "trichome_checkin"
    private const val CHECKIN_CHANNEL_NAME = "Check-in Diario"
    private const val CHECKIN_CHANNEL_DESCRIPTION = "Recordatorios suaves de cuidado diario"

    /** Creates notification channels (idempotent). */
    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, CHANNEL_IMPORTANCE).apply {
                description = CHANNEL_DESCRIPTION
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHECKIN_CHANNEL_ID, CHECKIN_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = CHECKIN_CHANNEL_DESCRIPTION
            }
        )
    }

    fun showReminder(context: Context, notificationId: Int, title: String, message: String) {
        ensureChannels(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_leaf)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    fun showDailyCheckin(context: Context, notificationId: Int, title: String, message: String) {
        ensureChannels(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHECKIN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_leaf)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }
}