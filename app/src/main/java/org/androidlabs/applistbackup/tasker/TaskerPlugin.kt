package org.androidlabs.applistbackup.tasker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerActionNoOutput
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelperNoOutput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import org.androidlabs.applistbackup.BackupService
import org.androidlabs.applistbackup.R
import org.androidlabs.applistbackup.data.BackupFormat

enum class TaskerBackupFormat(val value: String) {
    System("System"),
    HTML(BackupFormat.HTML.value),
    Markdown(BackupFormat.Markdown.value);

    companion object {
        fun fromString(value: String): TaskerBackupFormat {
            return TaskerBackupFormat.entries.find { it.value == value } as TaskerBackupFormat
        }
    }
}

@TaskerInputRoot
data class BackupFormatInput @JvmOverloads constructor(
    @field:TaskerInputField("format", labelResIdName = "backup_format")
    var format: String = TaskerBackupFormat.System.value
)

class PluginHelper(config: TaskerPluginConfig<BackupFormatInput>) :
    TaskerPluginConfigHelperNoOutput<BackupFormatInput, PluginRunner>(config) {

    override val inputClass: Class<BackupFormatInput> = BackupFormatInput::class.java
    override val runnerClass: Class<PluginRunner> = PluginRunner::class.java
}

class TaskerPlugin : Activity(), TaskerPluginConfig<BackupFormatInput> {
    override val context: Context get() = applicationContext
    private val helper by lazy { PluginHelper(this) }
    private lateinit var formatGroup: RadioGroup
    private val formatButtonIds = mutableMapOf<TaskerBackupFormat, Int>()

    private var currentInput: TaskerInput<BackupFormatInput>? = null

    override fun assignFromInput(input: TaskerInput<BackupFormatInput>) {
        currentInput = input
    }

    override val inputForTasker: TaskerInput<BackupFormatInput>
        get() = currentInput ?: TaskerInput(BackupFormatInput())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tasker_layout)

        val container = findViewById<LinearLayout>(R.id.container)
        createFormatGroup(container)

        findViewById<Button>(R.id.cancel_button).setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        findViewById<Button>(R.id.save_button).setOnClickListener {
            helper.finishForTasker()
        }
    }

    private fun createFormatGroup(container: LinearLayout) {
        val isDarkMode =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        val textColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (isDarkMode) android.R.color.system_on_surface_dark else android.R.color.system_on_surface_light
        } else {
            if (isDarkMode) android.R.color.primary_text_dark else android.R.color.primary_text_light
        }
        val backgroundColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (isDarkMode) android.R.color.system_surface_dark else android.R.color.system_surface_light
        } else {
            if (isDarkMode) android.R.color.background_dark else android.R.color.background_light
        }

        container.setBackgroundColor(context.getColor(backgroundColor))

        val formatHeader = findViewById<TextView>(R.id.format)
        formatHeader.setTextColor(context.getColor(textColor))

        formatGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        val colors = intArrayOf(
            context.getColor(textColor),
            context.getColor(textColor)
        )
        val colorStateList = ColorStateList(states, colors)

        TaskerBackupFormat.entries.forEach { format ->
            val button = RadioButton(this).apply {
                id = View.generateViewId()
                text = format.value
                setTextColor(context.getColor(textColor))
                buttonTintList = colorStateList
                layoutParams = RadioGroup.LayoutParams(
                    RadioGroup.LayoutParams.MATCH_PARENT,
                    RadioGroup.LayoutParams.WRAP_CONTENT
                )
            }
            formatGroup.addView(button)
            formatButtonIds[format] = button.id
        }

        findViewById<FrameLayout>(R.id.format_container).addView(formatGroup)

        val loadedFormat = intent?.getStringExtra("format") ?: TaskerBackupFormat.System.value

        formatButtonIds[TaskerBackupFormat.fromString(loadedFormat)]?.let { buttonId ->
            formatGroup.check(buttonId)
        }

        formatGroup.setOnCheckedChangeListener { _, checkedId ->
            val selectedFormat =
                formatButtonIds.entries.find { it.value == checkedId }?.key ?: TaskerBackupFormat.System
            currentInput = TaskerInput(BackupFormatInput(format = selectedFormat.value))
        }
    }
}

class PluginRunner : TaskerPluginRunnerActionNoOutput<BackupFormatInput>() {
    override fun run(
        context: Context,
        input: TaskerInput<BackupFormatInput>
    ): TaskerPluginResult<Unit> {
        Handler(Looper.getMainLooper()).post {
            Log.d("BackupTaskerPluginRunner", "run $input")
            val intent = Intent(context, BackupService::class.java).apply {
                putExtra("source", "tasker")
                BackupFormat.fromStringOptional(input.regular.format)?.let {
                    putExtra("format", it.value)
                }
            }
            context.startForegroundService(intent)
        }
        return TaskerPluginResultSucess()
    }
}
