package com.example.worktrack

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.worktrack.data.ObjectSummary

fun validContactPhone(value: String): Boolean = value.isBlank() || (
    value.count { it in '0'..'9' } in 9..15 &&
    value.trim().let { text -> text.removePrefix("+").all { it in '0'..'9' || it in " ()-." } }
)

@Composable
internal fun ObjectDetailsEditor(vm: AppViewModel, obj: ObjectSummary, onDismiss: () -> Unit) {
    var address by rememberSaveable(obj.id) { mutableStateOf(obj.address) }
    var name by rememberSaveable(obj.id) { mutableStateOf(obj.clientName) }
    var phone by rememberSaveable(obj.id) { mutableStateOf(obj.clientPhone.orEmpty()) }
    var shared by rememberSaveable(obj.id) { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable(obj.id) { mutableStateOf(false) }
    val count by remember(obj.id) { vm.clientObjectCount(obj.id) }.collectAsState(initial = 0)
    val saving by vm.isSaving.collectAsState()
    val error by vm.operationError.collectAsState()
    val dirty = address != obj.address || name != obj.clientName || phone != obj.clientPhone.orEmpty()
    fun dismiss() { if (!saving) { if (dirty) confirmDiscard = true else onDismiss() } }
    AlertDialog(
        onDismissRequest = ::dismiss,
        title = { Text(stringResource(R.string.object_edit_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(address, { address = it }, enabled = !saving, singleLine = true,
                    label = { Text(stringResource(R.string.label_address)) }, isError = address.isBlank())
                OutlinedTextField(name, { name = it }, enabled = !saving, singleLine = true,
                    label = { Text(stringResource(R.string.label_customer)) }, isError = name.isBlank())
                OutlinedTextField(phone, { phone = it }, enabled = !saving, singleLine = true,
                    label = { Text(stringResource(R.string.label_phone)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    isError = !validContactPhone(phone),
                    supportingText = { if (!validContactPhone(phone)) Text(stringResource(R.string.contact_phone_invalid)) })
                if (count > 1) {
                    Text(stringResource(R.string.client_shared_info, count))
                    Row {
                        Checkbox(checked = shared, onCheckedChange = { shared = it }, enabled = !saving)
                        Text(stringResource(R.string.client_update_shared), modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        },
        confirmButton = { Button(enabled = !saving && address.isNotBlank() && name.isNotBlank() && validContactPhone(phone),
            onClick = { vm.editObjectDetails(obj.id, address, name, phone, shared && count > 1) { vm.clearError(); onDismiss() } }) {
            Text(stringResource(R.string.action_save))
        } },
        dismissButton = { TextButton(enabled = !saving, onClick = ::dismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
    if (confirmDiscard) ConfirmDialog(stringResource(R.string.object_edit_discard), stringResource(R.string.object_edit_discard_message),
        { confirmDiscard = false }) { vm.clearError(); onDismiss() }
}
