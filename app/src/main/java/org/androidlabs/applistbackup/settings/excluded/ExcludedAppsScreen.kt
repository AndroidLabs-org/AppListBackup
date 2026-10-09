package org.androidlabs.applistbackup.settings.excluded

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.androidlabs.applistbackup.R

@Composable
fun ExcludedAppsScreen(
    viewModel: ExcludedAppsViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var appList by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    val selectedPackages by viewModel.selectedPackages.collectAsState()

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            scope.launch {
                val installedApps = packageManager.getInstalledApplications(0)
                    .map {
                        AppInfo(
                            packageName = it.packageName,
                            name = packageManager.getApplicationLabel(it).toString(),
                            icon = it.loadIcon(packageManager)
                        )
                    }
                    .sortedBy { it.name }

                scope.launch(Dispatchers.Main) {
                    appList = installedApps
                    isLoading = false
                }
            }
        }
    }


    val filteredList = if (searchQuery.isBlank()) {
        appList
    } else {
        appList.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }


    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.select_excluded_apps),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(bottom = 16.dp)
                .align(Alignment.CenterHorizontally)
        )

        Button(
            onClick = { viewModel.clearAll() },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(stringResource(R.string.clear_all))
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            label = { Text(stringResource(R.string.search)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.search)
                )
            },
            singleLine = true,
            enabled = !isLoading
        )

        if (isLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(stringResource(R.string.loading))
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(filteredList) { app ->

                    val checked = app.packageName in selectedPackages

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.togglePackage(app.packageName) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        app.icon.let {
                            Image(
                                painter = rememberDrawablePainter(it),
                                contentDescription = app.name,
                                modifier = Modifier.size(24.dp)
                            )

                            Spacer(modifier = Modifier.width(16.dp))
                        }

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = app.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                viewModel.togglePackage(app.packageName)
                            }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.cancel))
            }

            Button(
                onClick = {
                    viewModel.save()
                    onDismiss()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}