package org.androidlabs.applistbackup.faq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.androidlabs.applistbackup.BackupService
import org.androidlabs.applistbackup.R
import org.androidlabs.applistbackup.ui.theme.AppListBackupTheme

data class Instruction(val title: String, var description: String, val boldDescription: Boolean = false, val details: String? = null)

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
            description = stringResource(R.string.backup_location_details),
            boldDescription = backupPath != "",
            details = if (backupPath == "") {
                stringResource(R.string.backup_location_not_set)
            } else {
                backupPath
            }
        ),
        Instruction(
            title = stringResource(id = R.string.backups_after_uninstalling),
            description = stringResource(R.string.backups_after_uninstalling_description)
        ),
        Instruction(
            title = stringResource(id = R.string.tasker_integration),
            description = stringResource(id = R.string.tasker_integration_description, appName)
        ),
        Instruction(
            title = stringResource(id = R.string.tasker_timeout),
            description = stringResource(R.string.tasker_timeout_description)
        ),
        Instruction(
            title = stringResource(id = R.string.automate_integration),
            description = stringResource(id = R.string.automate_integration_description, appName)
        ),
        Instruction(
            title = stringResource(id = R.string.macrodroid_integration),
            description = stringResource(id = R.string.macrodroid_integration_description, appName)
        )
    ).map {
        it.copy(
            description = it.description.split("\n")
                .map { line -> line.trim() }
                .joinToString("\n")
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        instructions.forEach { instruction ->
            Text(
                text = instruction.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = instruction.description,
                fontWeight = if (instruction.boldDescription) FontWeight.Bold else FontWeight.Normal,
            )

            if (instruction.details != null) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = instruction.details,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}