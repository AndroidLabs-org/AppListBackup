package org.androidlabs.applistbackup

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs a backup from the background, for callers that cannot rely on a foreground state:
 * [BackupReceiver] and the Tasker/Locale plugin runner. Both used to call
 * `context.startForegroundService()` directly, which Android refuses once the process is in
 * a restricted background state (RCVR for a receiver, similarly for a plugin host callback) -
 * every scripted trigger, since that is exactly when the app is closed. WorkManager's enqueue
 * is never restricted this way, so it is the trigger; this worker then runs the same
 * [BackupService.performBackup] logic without ever asking to become a foreground service,
 * since a backup takes about a second and WorkManager already keeps the process alive for
 * the duration.
 */
class BackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val source = inputData.getString(KEY_SOURCE)
        val format = inputData.getString(KEY_FORMAT)
        val temporary = inputData.getBoolean(KEY_TEMPORARY, false)

        Log.d(TAG, "backup requested by $source")

        if (!BackupService.isRunning.compareAndSet(expect = false, update = true)) {
            Log.w(TAG, "backup already in progress; ignoring duplicate request")
            return@withContext Result.success()
        }

        try {
            BackupService.performBackup(applicationContext, source, format, temporary)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "backup failed", e)
            Result.failure()
        } finally {
            BackupService.isRunning.value = false
        }
    }

    companion object {
        private const val TAG = "BackupWorker"
        private const val WORK_NAME = "app-list-backup"

        const val KEY_SOURCE = "source"
        const val KEY_FORMAT = "format"
        const val KEY_TEMPORARY = "temporary"

        /**
         * Queues a backup. Safe to call from a receiver, the Tasker plugin runner, or anywhere
         * else with no foreground state to lean on.
         */
        fun enqueue(
            context: Context,
            source: String? = null,
            format: String? = null,
            temporary: Boolean = false,
        ) {
            val data = Data.Builder()
                .putString(KEY_SOURCE, source)
                .putString(KEY_FORMAT, format)
                .putBoolean(KEY_TEMPORARY, temporary)
                .build()

            val request = OneTimeWorkRequestBuilder<BackupWorker>()
                .setInputData(data)
                // Ask to run immediately; fall back to a normal job rather than failing
                // outright when the expedited quota is spent.
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
