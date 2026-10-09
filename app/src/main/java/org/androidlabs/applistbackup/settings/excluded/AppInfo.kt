package org.androidlabs.applistbackup.settings.excluded

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val name: String,
    val icon: Drawable?
)