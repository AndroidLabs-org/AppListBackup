package org.androidlabs.applistbackup

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

/**
 * Implementation of App Widget functionality.
 */
class BackupWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    showLoading: Boolean = false
) {
    val intent = Intent(context, BackupReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

    val views = RemoteViews(context.packageName, R.layout.backup_widget)
    views.setOnClickPendingIntent(R.id.widget_layout, pendingIntent)

    views.setViewVisibility(R.id.loading_spinner, if (showLoading) View.VISIBLE else View.GONE)
    views.setViewVisibility(R.id.logo, if (!showLoading) View.VISIBLE else View.GONE)
    val text = if (showLoading)
        context.getString(R.string.appwidget_text_in_progress)
    else
        context.getString(R.string.appwidget_text)
    views.setTextViewText(R.id.textView, text)

    appWidgetManager.updateAppWidget(appWidgetId, views)
}