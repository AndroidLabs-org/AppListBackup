package org.androidlabs.applistbackup.data

import android.graphics.drawable.Drawable

data class BackupAppDetails(
    val packageName: String,
    val name: CharSequence,
    val icon: Drawable,
    val isSystem: Boolean,
    val isEnabled: Boolean,
    val installerName: String,
    val versionName: String,
    val versionCode: Long,
    val targetSdkVersion: Int,
    val minSdkVersion: Int,
    val firstInstallTime: Long,
    val lastUpdateTime: Long
)