package org.androidlabs.applistbackup.tasker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import net.dinglisch.android.tasker.TaskerPlugin
import org.androidlabs.applistbackup.BackupWorker

/**
 * Receives the plugin fire intent from Tasker, MacroDroid, Automate and anything
 * else speaking the Locale plugin protocol.
 *
 * This replaces the library's own BroadcastReceiverAction, which is removed in
 * the manifest. That receiver retargets the intent at IntentServiceAction and
 * calls startForegroundService(), and since Android 12 the system refuses a
 * foreground-service start made from a broadcast receiver - the app is at
 * uidState RCVR, which earns no exemption. The library catches the resulting
 * ForegroundServiceStartNotAllowedException and only prints it, having already
 * answered the host with RESULT_CODE_PENDING, so the automation app reports a
 * successful action while no backup ever runs.
 *
 * Enqueueing WorkManager here is the same route BackupReceiver takes for the
 * broadcast API: work started this way is not a foreground-service start and is
 * not subject to that restriction.
 */
class TaskerFireReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE_SETTING) {
            return
        }

        val bundle = intent.getBundleExtra(EXTRA_BUNDLE)
        val stored = bundle?.getString(BackupFormatInput::format.name)

        // Mirror the previous runner: pass the stored value through only when it
        // names at least one format this build knows, otherwise leave it out and
        // let the backup fall back to the configured setting.
        val format = stored?.takeIf { TaskerBackupFormat.getSelectedFormats(it).isNotEmpty() }

        Log.d(TAG, "fire received, format=${format ?: "from settings"}")

        BackupWorker.enqueue(
            context = context,
            source = SOURCE,
            format = format,
            temporary = false,
        )

        // The host is told the action was accepted, which is what the plugin
        // reported before as well - it returned success once the backup had been
        // started, never waiting for it to finish.
        if (TaskerPlugin.Setting.hostSupportsSynchronousExecution(intent.extras)) {
            TaskerPlugin.Setting.signalFinish(
                context, intent, TaskerPlugin.Setting.RESULT_CODE_OK, null
            )
        } else if (isOrderedBroadcast) {
            resultCode = TaskerPlugin.Setting.RESULT_CODE_OK
        }
    }

    companion object {
        private const val TAG = "TaskerFireReceiver"

        /** Recorded as the trigger so the report reads Automatic, not Manual. */
        const val SOURCE = "tasker"

        const val ACTION_FIRE_SETTING = "com.twofortyfouram.locale.intent.action.FIRE_SETTING"
        const val EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE"
    }
}
