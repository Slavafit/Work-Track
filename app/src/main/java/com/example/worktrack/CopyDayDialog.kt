package com.example.worktrack

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource

@Composable
internal fun CopyDayDialog(saving: Boolean, onDismiss: () -> Unit, onCopy: (Long) -> Unit) {
    var date by rememberSaveable { mutableStateOf(todayMillis()) }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.day_copy)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.day_copy_description))
            if (!saving) DateButton(stringResource(R.string.label_date), date) { date = it } else Text(date.formatDate())
        } },
        confirmButton = { Button(enabled = !saving && date > 0, onClick = { onCopy(date) }) { Text(stringResource(R.string.day_copy)) } },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
