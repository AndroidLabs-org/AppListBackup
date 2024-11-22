package org.androidlabs.applistbackup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BackupReceiver : BroadcastReceiver() {

    private val tag: String = "BackupReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(tag, "receive $intent")
        val serviceIntent = Intent(
            context,
            BackupService::class.java
        )
        context.startForegroundService(serviceIntent)
    }
}