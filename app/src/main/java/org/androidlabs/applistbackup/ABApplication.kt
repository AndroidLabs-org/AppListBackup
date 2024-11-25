package org.androidlabs.applistbackup

import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.os.Bundle

class ABApplication : Application() {
    private var activityReferences = 0
    private var isActivityChangingConfigurations = false
    private var isMainAppInForeground: Boolean = false
    private var activitiesObserver: ActivityLifecycleCallbacks? = null

    override fun onCreate() {
        super.onCreate()
        listenActivities()
    }

    override fun onTerminate() {
        super.onTerminate()
        activitiesObserver?.let {
            unregisterActivityLifecycleCallbacks(it)
        }
    }

    private fun clearNotifications() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancelAll()
    }

    private fun onEnterForeground(lastActivity: Activity) {
        isMainAppInForeground = true
        clearNotifications()
    }

    private fun onEnterBackground(lastActivity: Activity) {
        isMainAppInForeground = false
    }

    private fun listenActivities() {
        activitiesObserver = object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

            override fun onActivityStarted(activity: Activity) {
                if (++activityReferences == 1 && !isActivityChangingConfigurations) {
                    onEnterForeground(activity)
                }
            }

            override fun onActivityResumed(activity: Activity) {}

            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {
                isActivityChangingConfigurations = activity.isChangingConfigurations
                if (--activityReferences == 0 && !isActivityChangingConfigurations) {
                    onEnterBackground(activity)
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {}
        }

        registerActivityLifecycleCallbacks(activitiesObserver)
    }
}