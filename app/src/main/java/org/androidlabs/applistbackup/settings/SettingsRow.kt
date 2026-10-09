package org.androidlabs.applistbackup.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    iconView: @Composable () -> Unit,
    rightView: (@Composable () -> Unit)? = null,
    footerView: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    mergeSemantics: Boolean = true
) {

    val modifier = Modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 56.dp)
        .then(
            if (onClick != null) Modifier.clickable { onClick() }
            else Modifier
        )
        .padding(horizontal = 16.dp, vertical = 10.dp)

    Column(
        modifier = modifier.then(
        if (mergeSemantics) {
            Modifier.semantics(mergeDescendants = true) {}
        } else {
            Modifier
        }
    )) {

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = if (subtitle == null) {
                Alignment.CenterVertically
            } else {
                Alignment.Top
            }
        ) {

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .padding(top = 2.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                iconView()
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold
                )

                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (rightView != null) {
                Spacer(modifier = Modifier.width(12.dp))
                rightView()
            }
        }

        if (footerView != null) {
            Spacer(modifier = Modifier.height(8.dp))
            footerView()
        }
    }
}