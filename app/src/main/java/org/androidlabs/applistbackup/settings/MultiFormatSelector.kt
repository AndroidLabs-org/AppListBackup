package org.androidlabs.applistbackup.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.androidlabs.applistbackup.R
import org.androidlabs.applistbackup.data.BackupFormat

@Composable
fun MultiFormatSelector(
    selectedFormats: Set<BackupFormat>,
    onFormatsChanged: (Set<BackupFormat>) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var tempSelectedFormats by remember(selectedFormats) {
        mutableStateOf(selectedFormats)
    }
    var showError by remember { mutableStateOf(false) }

    Box {
        Button(
            onClick = {
                expanded = true
                showError = false
                tempSelectedFormats = selectedFormats.toSet()
            }
        ) {
            Text(
                text = stringResource(R.string.change)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (showError) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_formats_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }

            BackupFormat.entries.forEach { format ->
                val isSelected = tempSelectedFormats.contains(format)

                DropdownMenuItem(
                    text = { Text(format.value) },
                    leadingIcon = {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = null
                        )
                    },
                    onClick = {
                        val updatedFormats = tempSelectedFormats.toMutableSet()
                        if (isSelected) {
                            updatedFormats.remove(format)
                        } else {
                            updatedFormats.add(format)
                        }
                        tempSelectedFormats = updatedFormats
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(
                    onClick = { expanded = false }
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        if (tempSelectedFormats.isNotEmpty()) {
                            onFormatsChanged(tempSelectedFormats)
                            expanded = false
                        } else {
                            showError = true
                        }
                    }
                ) {
                    Text("Apply")
                }
            }
        }
    }
}
