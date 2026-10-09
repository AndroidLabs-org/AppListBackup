package org.androidlabs.applistbackup

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import org.androidlabs.applistbackup.data.BackupFormat


class RunBackupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val format = intent.dataString?.let { data ->
            val pattern = "%format=(.+)".toRegex()
            pattern.find(data)?.groupValues?.get(1)
        }

        val intent = Intent(
            this,
            BackupService::class.java
        )
        intent.putExtra("source", "tasker")
        intent.putExtra("temporary",false)

        // The shortcut URI may name several formats, e.g. "%format=HTML,CSV" — the same
        // syntax the in-app instructions document for the broadcast trigger. Resolving the
        // whole string with a single exact-match lookup returned null and dropped the
        // caller's choice silently, which is the defect that was fixed in BackupReceiver.
        format?.let { raw ->
            val formats = raw.split(",")
                .mapNotNull { BackupFormat.fromString(it.trim()) }
                .distinct()

            if (formats.isNotEmpty()) {
                intent.putExtra("format", formats.joinToString(",") { it.value })
            }
        }

        startForegroundService(intent)

        finish()
    }
}