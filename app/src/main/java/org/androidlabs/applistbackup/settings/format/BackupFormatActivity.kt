package org.androidlabs.applistbackup.settings.format

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import org.androidlabs.applistbackup.R
import org.androidlabs.applistbackup.data.BackupFormat
import org.androidlabs.applistbackup.settings.Settings
import org.androidlabs.applistbackup.settings.SettingsRow
import org.androidlabs.applistbackup.ui.BackNavigable

class BackupFormatActivity : ComponentActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

            val context = LocalContext.current
            val noFormatsError = stringResource(R.string.no_formats_error)
            var backupFormat by remember {
                mutableStateOf(Settings.getBackupFormats(context))
            }

            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()

            fun handleToggle(item: BackupFormat) {
                val newSet = if (item in backupFormat) {
                    if (backupFormat.size == 1) {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                noFormatsError
                            )
                        }
                        return
                    }
                    backupFormat - item
                } else {
                    backupFormat + item
                }

                Settings.setBackupFormats(context, newSet)

                backupFormat = newSet
            }

            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { padding ->

                BackNavigable(
                    titleResId = R.string.backup_format,
                    onBackPressedDispatcher = onBackPressedDispatcher,
                    rightView = {},
                    content = { innerPadding ->

                        BackupFormatScreen(
                            formats = backupFormat,
                            onToggle = ::handleToggle,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                )
            }
        }
    }
}
@Composable
private fun BackupFormatScreen(
    formats: Set<BackupFormat>,
    onToggle: (BackupFormat) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {

        BackupFormat.entries.forEach { item ->

            val checked = item in formats

            val toggle = { onToggle(item) }

            SettingsRow(
                title = item.name,
                subtitle = if (item == BackupFormat.HTML) {
                    stringResource(R.string.html_hint)
                } else null,
                iconView = {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { toggle() }
                    )
                },
                rightView = {},
                onClick = toggle
            )
        }
    }
}