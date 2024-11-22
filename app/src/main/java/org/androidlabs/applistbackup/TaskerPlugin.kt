package org.androidlabs.applistbackup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerActionNoOutputOrInput
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelperNoOutputOrInput
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigNoInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess

class TaskerPluginHelper(config: TaskerPluginConfig<Unit>) : TaskerPluginConfigHelperNoOutputOrInput<TaskerPluginRunner>(config) {
    override val runnerClass: Class<TaskerPluginRunner> get() = TaskerPluginRunner::class.java
    override fun addToStringBlurb(input: TaskerInput<Unit>, blurbBuilder: StringBuilder) {
        blurbBuilder.append(context.getString(R.string.tasker_command))
    }
}


class TaskerPlugin : Activity(), TaskerPluginConfigNoInput {
    override val context get() = applicationContext
    private val taskerHelper by lazy { TaskerPluginHelper(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("BackupTaskerPluginRunner", "create")
        taskerHelper.finishForTasker()
    }
}

class TaskerPluginRunner : TaskerPluginRunnerActionNoOutputOrInput() {
    override fun run(context: Context, input: TaskerInput<Unit>): TaskerPluginResult<Unit> {
        Handler(Looper.getMainLooper()).post {
            Log.d("BackupTaskerPluginRunner", "run $input")
            val intent = Intent(context, BackupService::class.java)
            intent.putExtra("source", "tasker")
            context.startForegroundService(intent)
        }
        return TaskerPluginResultSucess()
    }
}