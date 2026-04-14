package com.ai.assistance.operit.ui.features.announcement

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R

@Composable
fun ChatBindingAnnouncementDialog(
    onAcknowledge: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(text = stringResource(id = R.string.chat_binding_announcement_title)) },
        text = { Text(text = stringResource(id = R.string.chat_binding_announcement_body)) },
        confirmButton = {
            TextButton(onClick = onAcknowledge) {
                Text(text = stringResource(id = R.string.chat_binding_announcement_acknowledge))
            }
        }
    )
}
