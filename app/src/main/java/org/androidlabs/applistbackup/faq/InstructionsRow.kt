package org.androidlabs.applistbackup.faq

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.androidlabs.applistbackup.R

data class Instruction(
    val title: String,
    var description: String,
    val boldDescription: Boolean = false,
    val details: String? = null
)

@Composable
fun InstructionRow(
    instruction: Instruction,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = instruction.title,
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
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = instruction.description,
                fontWeight = if (instruction.boldDescription) FontWeight.Bold else FontWeight.Normal
            )

            instruction.details?.let {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = it,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}