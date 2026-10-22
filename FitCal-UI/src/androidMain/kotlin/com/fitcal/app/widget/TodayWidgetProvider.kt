package com.fitcal.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.fitcal.app.MainActivity
import com.fitcal.app.R
import com.fitcal.app.WidgetSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home-screen widget: today's calories (left, or eaten without targets), the three macros and a
 * Scan button. Plain RemoteViews, no extra library. The app pushes fresh numbers through
 * [save] whenever meals or targets change; on a new day the widget shows zero until the app
 * is opened, rather than yesterday's numbers.
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        const val EXTRA_LAUNCH_ACTION = "com.fitcal.app.LAUNCH_ACTION"
        const val ACTION_SCAN = "scan"

        private const val PREFS = "fitcal_widget"

        fun save(context: Context, s: WidgetSummary) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("date", s.date)
                .putInt("calories", s.calories)
                .putInt("goal", s.calorieGoal)
                .putInt("protein", s.proteinG)
                .putInt("carbs", s.carbsG)
                .putInt("fat", s.fatG)
                .apply()
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
            if (ids.isNotEmpty()) ids.forEach { manager.updateAppWidget(it, views(context)) }
        }

        private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        internal fun views(context: Context): RemoteViews {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val isToday = prefs.getString("date", null) == today()
            val calories = if (isToday) prefs.getInt("calories", 0) else 0
            val goal = prefs.getInt("goal", 0)
            val protein = if (isToday) prefs.getInt("protein", 0) else 0
            val carbs = if (isToday) prefs.getInt("carbs", 0) else 0
            val fat = if (isToday) prefs.getInt("fat", 0) else 0

            val (big, caption) = when {
                goal <= 0 -> "%,d".format(calories) to "kcal eaten today"
                calories <= goal -> "%,d".format(goal - calories) to "kcal left today"
                else -> "%,d".format(calories - goal) to "kcal over today"
            }

            return RemoteViews(context.packageName, R.layout.widget_today).apply {
                setTextViewText(R.id.widget_calories, big)
                setTextViewText(R.id.widget_caption, caption)
                setTextViewText(R.id.widget_macros, "P $protein g  ·  C $carbs g  ·  F $fat g")
                setOnClickPendingIntent(R.id.widget_root, launch(context, null, 0))
                setOnClickPendingIntent(R.id.widget_scan, launch(context, ACTION_SCAN, 1))
            }
        }

        private fun launch(context: Context, action: String?, requestCode: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (action != null) putExtra(EXTRA_LAUNCH_ACTION, action)
            }
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
