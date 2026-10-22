package com.fitcal.app.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fitcal.app.MainActivity
import com.fitcal.app.R
import com.fitcal.app.appContext
import com.fitcal.app.data.PreferenceKeyValueStorage
import com.fitcal.app.getCurrentDateString
import com.fitcal.app.ui.screens.progress.WeeklySummaryPrefs
import com.fitcal.app.ui.screens.progress.lastCompletedWeek
import com.fitcal.app.ui.screens.progress.weeklySummaryFor
import com.fitcal.shared.data.LocalMealRepository
import kotlinx.coroutines.runBlocking
import java.util.Calendar

/**
 * Monday 09:00 "Your week in FitCal is ready" (owner decision 2026-10-23). The text never
 * includes numbers, because it shows on the lock screen; tapping opens the app, where the
 * weekly summary card has them. Sent only when something was logged last week and the
 * setting is on (Settings → Weekly summary). Inexact, so it costs no exact-alarm permission.
 */
class WeeklySummaryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        appContext = context.applicationContext
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> sync(context, WeeklySummaryPrefs.notificationsEnabled())
            ACTION_WEEKLY_SUMMARY -> if (WeeklySummaryPrefs.notificationsEnabled() && loggedLastWeek()) show(context)
        }
    }

    private fun loggedLastWeek(): Boolean = try {
        val week = lastCompletedWeek(getCurrentDateString())
        val meals = runBlocking { LocalMealRepository(PreferenceKeyValueStorage()).getAllMeals() }
        week != null && weeklySummaryFor(meals, week, null) != null
    } catch (_: Exception) {
        false
    }

    private fun show(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            REQUEST_CODE + 1,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF10B981.toInt())
            .setContentTitle("Your week in FitCal is ready")
            .setContentText("Tap to see how last week went.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(REQUEST_CODE, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }

    companion object {
        private const val CHANNEL_ID = "fitcal_weekly_summary"
        private const val ACTION_WEEKLY_SUMMARY = "com.fitcal.app.ACTION_WEEKLY_SUMMARY"
        private const val REQUEST_CODE = 25000

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Weekly summary", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "A Monday-morning note that last week's summary is ready."
                    }
                )
            }
        }

        private fun pending(context: Context, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, WeeklySummaryReceiver::class.java).apply { action = ACTION_WEEKLY_SUMMARY },
            flags or PendingIntent.FLAG_IMMUTABLE,
        )

        /** Schedules (or cancels) the weekly alarm. Safe to call often; it replaces the previous one. */
        fun sync(context: Context, enabled: Boolean) {
            val alarms = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            pending(context, PendingIntent.FLAG_NO_CREATE)?.let { alarms.cancel(it); it.cancel() }
            if (!enabled) return
            ensureChannel(context)
            val next = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.WEEK_OF_YEAR, 1)
            }
            val pi = pending(context, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
            alarms.setInexactRepeating(AlarmManager.RTC, next.timeInMillis, AlarmManager.INTERVAL_DAY * 7, pi)
        }
    }
}
