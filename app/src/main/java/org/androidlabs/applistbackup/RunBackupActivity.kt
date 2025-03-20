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

        format?.let {
            BackupFormat.fromStringOptional(it)?.let { formatEnum ->
                intent.putExtra("format", formatEnum.value)
            }
        }

        startForegroundService(intent)

        finish()
    }
}