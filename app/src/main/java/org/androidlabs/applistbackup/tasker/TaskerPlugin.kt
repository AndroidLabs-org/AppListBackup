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
import android.widget.CheckBox
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
    Custom("Custom");

    companion object {
        fun fromString(value: String): TaskerBackupFormat {
            return if (value.contains(",") || value == BackupFormat.HTML.value ||
                value == BackupFormat.CSV.value || value == BackupFormat.Markdown.value
            ) {
                Custom
            } else {
                TaskerBackupFormat.entries.find { it.value == value } ?: System
            }
        }

        fun isSystem(value: String): Boolean {
            return value == System.value
        }

        fun getSelectedFormats(value: String): List<String> {
            return if (isSystem(value)) {
                emptyList()
            } else {
                value.split(",")
                    .filter { it.isNotEmpty() && BackupFormat.fromStringOptional(it) != null }
            }
        }

        fun formatFromSelected(formats: List<String>): String {
            return if (formats.isEmpty()) {
                System.value
            } else {
                formats.joinToString(",")
            }
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
    private lateinit var formatsContainer: LinearLayout
    private lateinit var saveButton: Button
    private val formatCheckboxes = mutableMapOf<String, CheckBox>()
    private val formatButtonIds = mutableMapOf<TaskerBackupFormat, Int>()

    private var currentInput: TaskerInput<BackupFormatInput>? = null

    override fun assignFromInput(input: TaskerInput<BackupFormatInput>) {
        currentInput = input
    }

    override val inputForTasker: TaskerInput<BackupFormatInput>
        get() = currentInput ?: TaskerInput(BackupFormatInput())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action == "com.twofortyfouram.locale.intent.action.EDIT_SETTING") {
            val bundle = intent?.getBundleExtra("com.twofortyfouram.locale.intent.extra.BUNDLE")

            if (bundle != null) {
                val formatValue = bundle.getString("format", "")
                currentInput = TaskerInput(BackupFormatInput(format = formatValue))
            }
        }

        setContentView(R.layout.tasker_layout)

        val isDarkMode =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        val backgroundColor = context.getColor(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (isDarkMode) android.R.color.system_surface_dark else android.R.color.system_surface_light
            } else {
                if (isDarkMode) android.R.color.background_dark else android.R.color.background_light
            }
        )

        val textColor = context.getColor(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (isDarkMode) android.R.color.system_on_surface_dark else android.R.color.system_on_surface_light
            } else {
                if (isDarkMode) android.R.color.primary_text_dark else android.R.color.primary_text_light
            }
        )

        findViewById<LinearLayout>(R.id.container).setBackgroundColor(backgroundColor)

        val container = findViewById<LinearLayout>(R.id.format_container)
        saveButton = findViewById(R.id.save_button)

        createFormatGroup(container, textColor)

        findViewById<Button>(R.id.cancel_button).setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        saveButton.setOnClickListener {
            helper.finishForTasker()
        }

        val savedInput = currentInput?.regular ?: BackupFormatInput()
        updateFormatCheckboxesVisibility(TaskerBackupFormat.fromString(savedInput.format) == TaskerBackupFormat.Custom)

        updateSaveButtonState()
    }

    private fun createFormatGroup(container: LinearLayout, textColor: Int) {
        val formatHeader = findViewById<TextView>(R.id.format)
        formatHeader.setTextColor(textColor)

        formatGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
        }

        formatsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            visibility = View.GONE
        }

        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        val colors = intArrayOf(
            textColor,
            textColor
        )
        val colorStateList = ColorStateList(states, colors)

        TaskerBackupFormat.entries.forEach { format ->
            val button = RadioButton(this).apply {
                id = View.generateViewId()
                text = format.value
                setTextColor(textColor)
                buttonTintList = colorStateList
                layoutParams = RadioGroup.LayoutParams(
                    0,
                    RadioGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    weight = 1f
                }
            }
            formatGroup.addView(button)
            formatButtonIds[format] = button.id
        }

        container.addView(formatGroup)

        val savedInput = currentInput?.regular ?: BackupFormatInput()
        val loadedFormat = savedInput.format
        val selectedBackupFormat = TaskerBackupFormat.fromString(loadedFormat)

        formatButtonIds[selectedBackupFormat]?.let { buttonId ->
            formatGroup.check(buttonId)
        }

        formatGroup.setOnCheckedChangeListener { _, checkedId ->
            val selectedFormat =
                formatButtonIds.entries.find { it.value == checkedId }?.key
                    ?: TaskerBackupFormat.System

            updateFormatCheckboxesVisibility(selectedFormat == TaskerBackupFormat.Custom)

            val formatValue = if (selectedFormat == TaskerBackupFormat.Custom) {
                TaskerBackupFormat.formatFromSelected(getSelectedFormats())
            } else {
                selectedFormat.value
            }

            currentInput = TaskerInput(BackupFormatInput(format = formatValue))

            updateSaveButtonState()
        }

        BackupFormat.entries.forEach { format ->
            val checkbox = CheckBox(this).apply {
                text = format.value
                setTextColor(textColor)
                buttonTintList = colorStateList
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )

                setOnCheckedChangeListener { _, _ ->
                    if (formatGroup.checkedRadioButtonId == formatButtonIds[TaskerBackupFormat.Custom]) {
                        val selectedFormats = getSelectedFormats()
                        val formatValue = TaskerBackupFormat.formatFromSelected(selectedFormats)
                        currentInput = TaskerInput(BackupFormatInput(format = formatValue))

                        updateSaveButtonState()
                    }
                }
            }
            formatCheckboxes[format.value] = checkbox
            formatsContainer.addView(checkbox)
        }

        container.addView(formatsContainer)

        val selectedFormats = TaskerBackupFormat.getSelectedFormats(loadedFormat)
        selectedFormats.forEach { format ->
            formatCheckboxes[format]?.isChecked = true
        }
    }

    private fun updateFormatCheckboxesVisibility(visible: Boolean) {
        formatsContainer.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun getSelectedFormats(): List<String> {
        return formatCheckboxes.filter { it.value.isChecked }.keys.toList()
    }

    private fun updateSaveButtonState() {
        val currentFormat =
            formatButtonIds.entries.find { it.value == formatGroup.checkedRadioButtonId }?.key
                ?: TaskerBackupFormat.System

        saveButton.isEnabled = when (currentFormat) {
            TaskerBackupFormat.System -> true
            TaskerBackupFormat.Custom -> getSelectedFormats().isNotEmpty()
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
                input.regular.format.let {
                    val formats = TaskerBackupFormat.getSelectedFormats(it)
                    if (formats.isNotEmpty()) {
                        putExtra("format", it)
                    }
                }
            }
            context.startForegroundService(intent)
        }
        return TaskerPluginResultSucess()
    }
}
