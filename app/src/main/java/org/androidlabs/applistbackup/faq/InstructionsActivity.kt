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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.androidlabs.applistbackup.BackupService
import org.androidlabs.applistbackup.R
import org.androidlabs.applistbackup.settings.Settings
import org.androidlabs.applistbackup.ui.theme.AppListBackupTheme

class InstructionsActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appName =
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0))
                .toString()
        val backupPath = BackupService.getReadablePathFromUri(this, Settings.getBackupUri(this))

        val instructions = listOf(
            Instruction(
                title = getString(R.string.backup_location),
                description = getString(R.string.backup_location_details),
                boldDescription = backupPath != "",
                details = if (backupPath == "") {
                    getString(R.string.backup_location_not_set)
                } else {
                    backupPath
                }
            ),
            Instruction(
                title = getString(R.string.backups_after_uninstalling),
                description = getString(R.string.backups_after_uninstalling_description)
            ),
            Instruction(
                title = getString(R.string.tasker_integration),
                description = getString(R.string.tasker_integration_description, appName)
            ),
            Instruction(
                title = getString(R.string.tasker_timeout),
                description = getString(R.string.tasker_timeout_description)
            ),
            Instruction(
                title = getString(R.string.automate_integration),
                description = getString(R.string.automate_integration_description, appName)
            ),
            Instruction(
                title = getString(R.string.macrodroid_integration),
                description = getString(R.string.macrodroid_integration_description, appName)
            )
        ).map {
            it.copy(
                description = it.description.split("\n").joinToString("\n") { line -> line.trim() }
            )
        }

        setContent {
            AppListBackupTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.faq)) },
                            navigationIcon = {
                                IconButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.back)
                                    )
                                }
                            },
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    BackupInstructionsScreen(
                        instructions,
                        appName,
                        Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun BackupInstructionsScreen(
    instructions: List<Instruction>,
    appName: String,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val expandedStates = remember { mutableStateMapOf<Int, Boolean>() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        instructions.forEachIndexed { index, instruction ->
            val isExpanded = expandedStates[index] == true
            InstructionRow(
                instruction = instruction,
                isExpanded = isExpanded,
                onToggle = {
                    expandedStates[index] = !isExpanded
                }
            )
        }

        val intentIndex = instructions.count()
        val isIntentExpanded = expandedStates[intentIndex] == true

        InstructionsIntent(
            appName = appName,
            isExpanded = isIntentExpanded,
            onToggle = {
                expandedStates[intentIndex] = !isIntentExpanded
            }
        )
    }
}