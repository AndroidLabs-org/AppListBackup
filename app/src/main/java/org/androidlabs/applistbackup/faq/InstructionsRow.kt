package org.androidlabs.applistbackup.faq

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class Instruction(val title: String, val description: String, val details: String? = null)

@Composable
fun InstructionRow(
    instruction: Instruction,
    isDescriptionBold: Boolean
) {
    Column {
        Text(
            text = instruction.title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = instruction.description,
            fontWeight = if (isDescriptionBold) FontWeight.Bold else FontWeight.Normal
        )

        if (instruction.details != null) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = instruction.details,
                fontWeight = FontWeight.Bold
            )
        }
    }
}