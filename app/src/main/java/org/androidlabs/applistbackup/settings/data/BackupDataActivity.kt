package org.androidlabs.applistbackup.settings.data

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.androidlabs.applistbackup.R
import org.androidlabs.applistbackup.data.BackupAppInfo
import org.androidlabs.applistbackup.settings.SettingsRow
import org.androidlabs.applistbackup.ui.BackNavigable

class BackupDataActivity : ComponentActivity() {
    private val viewModel: BackupDataViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val excludeData by viewModel.backupExcludeData.observeAsState(emptyList())
            val isAllSelected = excludeData.size == BackupAppInfo.entries.count()

            BackNavigable(
                titleResId = R.string.backup_data_settings,
                onBackPressedDispatcher = onBackPressedDispatcher,
                rightView = {
                    TextButton(
                        onClick = {
                            if (isAllSelected) {
                                viewModel.selectAll()
                            } else {
                                viewModel.deselectAll()
                            }
                        }
                    ) {
                        Text(text = stringResource(if (isAllSelected) R.string.select_all else R.string.deselect_all))
                    }
                },
                content = { innerPadding ->
                    BackupDataScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            )
        }
    }
}

@Composable
private fun BackupDataScreen(
    viewModel: BackupDataViewModel,
    modifier: Modifier
) {
    val context = LocalContext.current
    val backupExcludeData = viewModel.backupExcludeData.observeAsState(initial = emptyList())

    LaunchedEffect(key1 = true) {
        viewModel.refresh()
    }

    val scrollState = rememberScrollState()

    fun onToggle(item: BackupAppInfo) {
        val newArray = backupExcludeData.value.toMutableList()
        val index = newArray.indexOf(item)
        if (index >= 0) {
            newArray.removeAt(index)
        } else {
            newArray.add(item)
        }
        viewModel.saveBackupExcludeData(newArray)
    }

    Column(modifier = modifier.verticalScroll(scrollState)) {
        BackupAppInfo.entries.forEach { item ->
            SettingsRow(
                title = item.title(context),
                subtitle = null,
                iconView = {
                    Checkbox(
                        checked = !backupExcludeData.value.contains(item),
                        interactionSource = remember { MutableInteractionSource() },
                        onCheckedChange = { onToggle(item) },
                    )
                },
                rightView = {},
                onClick = { onToggle(item) }
            )
        }
    }
}
