package org.androidlabs.applistbackup.data

import android.content.Context
import android.os.Build
import java.util.Locale
import java.util.TimeZone

object EnvironmentInfoProvider {

    fun collect(context: Context): BackupEnvironmentInfo {
        return BackupEnvironmentInfo(
            androidVersion = Build.VERSION.RELEASE ?: "Unknown",
            apiLevel = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            device = Build.DEVICE,
            product = Build.PRODUCT,
            buildId = Build.ID,
            fingerprint = Build.FINGERPRINT,
            bootloader = Build.BOOTLOADER,
            hardware = Build.HARDWARE,
            securityPatch = runCatching {
                Build.VERSION.SECURITY_PATCH
            }.getOrNull(),
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            locale = Locale.getDefault().toLanguageTag(),
            timezone = TimeZone.getDefault().id
        )
    }
}