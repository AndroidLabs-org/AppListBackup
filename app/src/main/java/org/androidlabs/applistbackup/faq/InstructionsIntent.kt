package org.androidlabs.applistbackup.faq

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.androidlabs.applistbackup.R
import org.androidlabs.applistbackup.data.BackupFormat

@Composable
fun InstructionsIntent(
    appName: String,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val packageName = "org.androidlabs.applistbackup"
    val action = "$packageName.BACKUP_ACTION"

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.intent_integration),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                painter = painterResource(id = if (isExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                contentDescription = if (isExpanded) "Collapse" else "Expand"
            )
        }

        if (isExpanded) {
            Text(text = stringResource(R.string.intent_integration_description_2, appName))

            BlockView(title = "Intent API") {
                ValueRow(title = "Package:", value = packageName)

                ValueRow(title = "Action:", value = action)

                Text(text = stringResource(R.string.optionally))

                BackupFormat.entries.forEach {
                    ValueRow(title = "Extra (${it.value}):", value = "format:${it.value}")
                }
            }

            Text(text = stringResource(R.string.intent_integration_description_2))

            BlockView(title = "Shell (ADB)") {
                ValueRow(value = "adb shell am broadcast -a $action -n $packageName/.BackupReceiver")

                BackupFormat.entries.forEach {
                    ValueRow(
                        title = "${stringResource(R.string.optionally)} (${it.value}):",
                        value = "--es format ${it.value}"
                    )
                }
            }

            Text(
                text = stringResource(
                    R.string.intent_integration_example,
                    action,
                    packageName
                ).split("\n").joinToString("\n") { line ->
                    var newLine = line.trim()
                    if (newLine.startsWith("-")) {
                        newLine = "    $newLine"
                    }
                    newLine
                })
        }
    }
}

@Composable
fun BlockView(
    title: String,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), shape = shape)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )

            content()
        }
    }
}

@Composable
fun ValueRow(
    title: String? = null,
    value: String
) {
    val context = LocalContext.current

    fun onCopy() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Copied Text", value)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, context.getString(R.string.text_copied), Toast.LENGTH_SHORT).show()
    }

    val annotatedString = buildAnnotatedString {
        title?.let {
            withStyle(style = SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)) {
                append("$it ")
            }
        }

        withStyle(style = SpanStyle(fontSize = 16.sp)) {
            append(value)
        }
    }

    ClickableText(
        text = annotatedString,
        style = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = TextUnit(0.1f, TextUnitType.Sp)
        ),
        onClick = { onCopy() }
    )
}
