package org.androidlabs.applistbackup.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsRow(
    title: String,
    subtitle: String?,
    iconView: @Composable () -> Unit,
    rightView: @Composable () -> Unit,
    footerView: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    var modifier = Modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 56.dp)
    onClick?.let {
        modifier = modifier.clickable { it() }
    }

    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(24.dp)) {
                iconView()
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.SemiBold)
                subtitle?.let {
                    Text(text = it)
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            rightView()
        }

        footerView?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                it()
            }
        }
    }
}
