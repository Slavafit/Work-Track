package com.example.worktrack

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun HelpButton(@StringRes title: Int, @StringRes body: Int) {
    var opened by rememberSaveable(title, body) { mutableStateOf(false) }
    val label = stringResource(title)
    IconButton(onClick = { opened = true }, modifier = Modifier.size(48.dp)) {
        Icon(Icons.Outlined.HelpOutline, contentDescription = stringResource(R.string.help_for_section, label),
            tint = MaterialTheme.colorScheme.primary)
    }
    if (opened) AlertDialog(
        onDismissRequest = { opened = false },
        title = { Text(label) },
        text = { Text(stringResource(body), modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { opened = false }) { Text(stringResource(R.string.help_understood)) } }
    )
}

@Composable
internal fun HelpHeading(@StringRes title: Int, @StringRes body: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        HelpButton(title, body)
    }
}
