package org.androidlabs.applistbackup.faq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.androidlabs.applistbackup.BackupService
import org.androidlabs.applistbackup.R
import org.androidlabs.applistbackup.ui.theme.AppListBackupTheme

class InstructionsActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appName =
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0))
                .toString()

        setContent {
            AppListBackupTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("FAQ") },
                            navigationIcon = {
                                IconButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            },
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    BackupInstructionsScreen(
                        BackupService.getReadablePathFromUri(BackupService.getBackupUri(this)),
                        appName,
                        Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun BackupInstructionsScreen(backupPath: String, appName: String, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()

    val instructions = listOf(
        Instruction(
            title = stringResource(id = R.string.backup_location),
            description = "The backup files are saved in a folder chosen by you:",
            details = if (backupPath == "") {
                "Not set. Please, go back and set destination"
            } else {
                backupPath
            }
        ),
        Instruction(
            title = stringResource(id = R.string.backups_after_uninstalling),
            description = """
                Your files will stay in the desired folder even you delete the app.
                If you change the backup destination, the old files stay in the old destination.
            """.trimIndent()
        ),
        Instruction(
            title = stringResource(id = R.string.tasker_integration),
            description = """
                1. Open Tasker and create a new profile.
                2. Add a new task and Press + to add an Action.
                3. Choose the 'Plugin' category and choose '$appName'.
                4. On the screen you are viewing, locate the pencil icon (✎) next to the 'Configuration' section. Tap this button to open the detailed configuration settings.
                5. Press the back button on your device or use the back navigation button on the top left corner of the screen to return to the main Action Edit screen.
                6. Configure any additional parameters of the task as needed.
                7. Save and activate the profile to automate backups.
                
                Alternative:
                1. Open Tasker and create a new profile.
                2. Add a new task and Press + to add an Action.
                3. Choose the 'App' category and select 'Launch App'.
                4. Press and hold '$appName'.
                5. Choose 'RunBackupActivity'.
                6. Press the back button on your device or use the back navigation button on the top left corner of the screen to return to the main Action Edit screen.
                7. Configure any additional parameters of the task as needed.
                8. Save and activate the profile to automate backups.
            """.trimIndent()
        ),
        Instruction(
            title = stringResource(id = R.string.tasker_timeout),
            description = """
                By default, Tasker has a timeout of 60 seconds for tasks when using plugins. If you have a large application list, this default timeout might not be sufficient, as generating the HTML file can take considerable time. For example, with around 200 apps, it can take approximately 20 seconds on a newer device, and significantly longer on older phones.

                To avoid timeouts, we recommend extending the timeout duration. Setting the timeout to at least 10 minutes (600 seconds) is a healthier option for handling large application lists efficiently. You can adjust this setting in Tasker to ensure the task completes without interruptions.

                Steps to adjust the Tasker plugin timeout:

                1. Open Tasker.
                2. Navigate to the task using the plugin.
                3. Edit the task and find the timeout setting.
                4. Set the timeout to a longer duration, such as 600 seconds (10 minutes).
                5. Save and run the task again.

                This adjustment will help ensure that the task runs smoothly, even with a large number of applications to process.
            """.trimIndent()
        ),
        Instruction(
            title = stringResource(id = R.string.automate_integration),
            description = """
                1. Open the Automate app on your device.
                2. Tap on the "+" button to create a new flow.
                3. Press the "+" icon to add a new block.
                4. Navigate to the 'Apps' section and choose 'Plug-in action'.
                5. Tap on the added block to open its settings.
                6. Press 'Pick plug-in' and select '$appName'.
                7. Tap the 'Save' button to confirm your selection.
                8. Configure any additional parameters required for your flow.
                9. Save the flow by pressing the save icon. You can now run this flow to automate backups.
            """.trimIndent()
        ),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        instructions.forEach { instruction ->
            InstructionRow(
                instruction = instruction,
                isDescriptionBold = instruction.title == stringResource(id = R.string.backup_location) && backupPath != ""
            )
        }

        InstructionsIntent(appName)
    }
}