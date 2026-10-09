package org.androidlabs.applistbackup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A labelled on/off setting, exposed to accessibility services as a single control.
 *
 * Two problems are solved here.
 *
 * **Accessibility.** The previous pattern — `Row { Checkbox(onCheckedChange = …); Text(…) }`
 * — produced two separate nodes: a control with no name, and a label that could not be
 * activated. TalkBack announced it unnamed, which is the substance of issue #67. Making the
 * row itself toggleable, passing null to the control so it becomes decorative, and naming
 * the node explicitly gives one labelled control with a comfortable touch target.
 *
 * **Appearance.** These are preferences, not selections within a list. Material 3 uses a
 * switch for a setting that takes effect immediately and reserves the checkbox for choosing
 * items in a list, so a screen of large square checkboxes reads as dated. The label now
 * leads and the switch sits at the trailing edge, which is how the platform's own settings
 * are laid out and how the neighbouring rows in this screen already read.
 */
@Composable
fun CheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            // toggleable leaves the label as a sibling node, and merging descendants was not
            // enough on its own: the tree still showed a checkable node with empty text beside
            // a separate label. Naming the node explicitly is what guarantees it.
            .semantics(mergeDescendants = true) { contentDescription = label }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyLarge,
            // The row already names itself via the explicit contentDescription above.
            // Without this, mergeDescendants also pulls this Text's own content in,
            // and TalkBack announces the label twice — "On. User apps. User apps."
            modifier = Modifier
                .weight(1f, fill = false)
                .clearAndSetSemantics {}
        )
        Spacer(modifier = Modifier.width(16.dp))
        // null: the row owns the interaction, so the switch must not be separately actionable.
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
