package org.androidlabs.applistbackup

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity


class RunBackupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = Intent(
            this,
            BackupService::class.java
        )
        intent.putExtra("source", "tasker")
        startForegroundService(intent)

        finish()
    }
}