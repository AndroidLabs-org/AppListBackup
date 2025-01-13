package org.androidlabs.applistbackup.ui

import androidx.activity.OnBackPressedDispatcher
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import org.androidlabs.applistbackup.R
import org.androidlabs.applistbackup.ui.theme.AppListBackupTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackNavigable(
    @StringRes titleResId: Int,
    onBackPressedDispatcher: OnBackPressedDispatcher,
    content: @Composable (PaddingValues) -> Unit,
    rightView: (@Composable () -> Unit)? = null
) {
    AppListBackupTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(id = titleResId)) },
                    navigationIcon = {
                        IconButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(id = R.string.back)
                            )
                        }
                    },
                    actions = {
                        rightView?.invoke()
                    }
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            content(innerPadding)
        }
    }
}