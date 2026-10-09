package org.androidlabs.applistbackup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.androidlabs.applistbackup.data.BackupFormat

class BackupReceiver : BroadcastReceiver() {

    private val tag: String = "BackupReceiver"

    companion object {
        /** Used when a caller triggers a backup without naming itself. */
        const val SOURCE_BROADCAST = "broadcast"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(tag, "receive $intent")

        val format = intent.getStringExtra("format")

        val temporary = intent.getBooleanExtra("temporary", false)

        // The extra may carry several comma-separated formats, e.g. "HTML,CSV" — the syntax the
        // in-app instructions document. Validate each one and forward the ones we recognise.
        val normalisedFormat = format?.let { raw ->
            val formats = raw.split(",")
                .mapNotNull { BackupFormat.fromString(it.trim()) }
                .distinct()

            if (formats.isEmpty()) {
                Log.w(tag, "no recognised format in \"$raw\", falling back to settings")
                null
            } else {
                formats.joinToString(",") { it.value }
            }
        }

        // Not startForegroundService: a receiver's process sits in a restricted background
        // state, and Android refuses a foreground-service start from there
        // (ForegroundServiceStartNotAllowedException), taking the whole app down on every
        // scripted trigger — exactly when the app is closed. BackupWorker runs the same
        // backup without needing to become a foreground service at all.
        BackupWorker.enqueue(
            context = context,
            // Reaching this receiver at all means something outside the UI asked for
            // the backup — a broadcast, the widget, an automation app. The caller may
            // name itself; if it does not, record that it was not a person tapping a
            // button.
            source = intent.getStringExtra("source") ?: SOURCE_BROADCAST,
            format = normalisedFormat,
            temporary = temporary,
        )
    }
}