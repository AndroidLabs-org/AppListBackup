package org.androidlabs.applistbackup.settings.sort

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.androidlabs.applistbackup.R
import org.androidlabs.applistbackup.data.BackupAppInfo
import org.androidlabs.applistbackup.data.SortBy
import org.androidlabs.applistbackup.data.SortOrder
import org.androidlabs.applistbackup.settings.SettingsRow
import org.androidlabs.applistbackup.ui.BackNavigable

class BackupSortActivity : ComponentActivity() {
    private val viewModel: BackupSortViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val sortBy by viewModel.sortBy.collectAsState()
            val sortOrder by viewModel.sortOrder.collectAsState()

            BackNavigable(
                titleResId = R.string.backup_sort_settings,
                onBackPressedDispatcher = onBackPressedDispatcher,
                content = { innerPadding ->
                    BackupSortScreen(
                        sortBy = sortBy,
                        sortOrder = sortOrder,
                        onSortByChanged = viewModel::setSortBy,
                        onSortOrderChanged = viewModel::setSortOrder,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            )
        }
    }
}

@Composable
private fun BackupSortScreen(
    sortBy: SortBy,
    sortOrder: SortOrder,
    onSortByChanged: (SortBy) -> Unit,
    onSortOrderChanged: (SortOrder) -> Unit,
    modifier: Modifier = Modifier
) {

    Column(modifier = modifier.verticalScroll(rememberScrollState()))
    {

        Text(
            text = stringResource(R.string.sorting),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 12.dp
            )
        )


        SortRadioItem(
            text = stringResource(R.string.sort_by_app_name),
            selected = sortBy == SortBy.APP_NAME
        ) {
            onSortByChanged(SortBy.APP_NAME)
        }

        SortRadioItem(
            text = stringResource(R.string.sort_by_install_time),
            selected = sortBy == SortBy.INSTALL_TIME
        ) {
            onSortByChanged(SortBy.INSTALL_TIME)
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.order),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 12.dp
            )
        )

        SortRadioItem(
            text = stringResource(R.string.order_ascending),
            selected = sortOrder == SortOrder.ASCENDING
        ) {
            onSortOrderChanged(SortOrder.ASCENDING)
        }

        SortRadioItem(
            text = stringResource(R.string.order_descending),
            selected = sortOrder == SortOrder.DESCENDING
        ) {
            onSortOrderChanged(SortOrder.DESCENDING)
        }
    }
}

@Composable
private fun SortRadioItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null
        )

        Spacer(Modifier.width(12.dp))

        Text(text)
    }
}
