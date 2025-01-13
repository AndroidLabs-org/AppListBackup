package org.androidlabs.applistbackup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.androidlabs.applistbackup.data.BackupFormat

class BackupReceiver : BroadcastReceiver() {

    private val tag: String = "BackupReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(tag, "receive $intent")

        val format = intent.getStringExtra("format")

        val serviceIntent = Intent(
            context,
            BackupService::class.java
        )

        format?.let {
            BackupFormat.fromStringOptional(it)?.let { formatEnum ->
                serviceIntent.putExtra("format", formatEnum.value)
            }
        }

        context.startForegroundService(serviceIntent)
    }
}