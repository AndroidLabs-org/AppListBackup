package org.androidlabs.applistbackup.settings.excluded

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import org.androidlabs.applistbackup.R
import org.androidlabs.applistbackup.ui.BackNavigable

class ExcludedAppsActivity : ComponentActivity() {

    private val viewModel: ExcludedAppsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Scaffold(
            ) { padding ->

                BackNavigable(
                    titleResId = R.string.excluded_apps,
                    onBackPressedDispatcher = onBackPressedDispatcher,
                    rightView = {},
                    content = { innerPadding ->
                        ExcludedAppsScreen (
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding),
                            onDismiss = { finish() }
                        )
                    }
                )
            }
        }
    }
}