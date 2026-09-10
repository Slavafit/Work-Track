package com.example.worktrack.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.worktrack.R
import com.example.worktrack.formatDate
import java.time.LocalDate

@Composable
fun BackupPanel(controller: BackupController) {
    val state by controller.state.collectAsState()
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(controller::export)
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(controller::inspect)
    }
    Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.backup_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (state.lastExportAt > 0) Text(stringResource(R.string.backup_last_export, state.lastExportAt.formatDate()))
    Button(onClick = { exportPicker.launch("WorkTrack-${LocalDate.now()}.zip") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.backup_export))
    }
    OutlinedButton(onClick = { importPicker.launch(arrayOf("application/zip", "application/octet-stream")) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.backup_import))
    }
}

/** Activity-level dialogs keep navigation and writes blocked throughout restore preparation/commit. */
@Composable
fun BackupDialogs(controller: BackupController) {
    val state by controller.state.collectAsState()
    when {
        state.busy -> AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text(stringResource(R.string.backup_working))
            } },
            confirmButton = {}
        )
        state.preview != null -> AlertDialog(
            onDismissRequest = controller::cancelRestore,
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Summary(state.preview!!)
                Text(stringResource(R.string.backup_replace_warning))
            } },
            confirmButton = { TextButton(onClick = controller::confirmRestore) { Text(stringResource(R.string.backup_replace)) } },
            dismissButton = { TextButton(onClick = controller::cancelRestore) { Text(stringResource(R.string.action_cancel)) } }
        )
        state.message != null -> AlertDialog(
            onDismissRequest = controller::clearMessage,
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(state.message!!))
                state.result?.let { Summary(it) }
            } },
            confirmButton = { TextButton(onClick = controller::clearMessage) { Text(stringResource(android.R.string.ok)) } }
        )
    }
}

@Composable
private fun Summary(summary: BackupSummary) {
    Text(stringResource(R.string.payment_count, summary.payments))
    Text(stringResource(R.string.backup_summary, summary.createdAt.formatDate(), summary.objects, summary.days, summary.proposals, summary.photos, summary.missingPhotos))
}
