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
import com.example.worktrack.data.CustomerPayment

@Composable
internal fun CustomerPaymentsPanel(vm: AppViewModel, objectId: Long) {
    val pending by remember(objectId) { vm.pendingAmounts(objectId) }.collectAsState(initial = 0)
    val finance by remember(objectId) { vm.objectFinance(objectId) }.collectAsState(initial = null)
    val payments by remember(objectId) { vm.customerPayments(objectId) }.collectAsState(initial = emptyList())
    val saving by vm.isSaving.collectAsState()
    var editingId by rememberSaveable(objectId) { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable(objectId) { mutableStateOf<Long?>(null) }
    var showAll by rememberSaveable(objectId) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.finance_title), style = MaterialTheme.typography.titleMedium)
            if (pending > 0) Text(stringResource(R.string.pending_amounts_warning), color = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.finance_basis), style = MaterialTheme.typography.bodySmall)
            finance?.let {
                Text(stringResource(R.string.finance_work, it.workAmount.money()))
                Text(stringResource(R.string.finance_materials, it.materialAmount.money()))
                Text(stringResource(R.string.report_total_format, it.totalAmount.money()))
                Text(stringResource(R.string.finance_paid, it.paidAmount.money()))
                Text(stringResource(if (it.balance < 0) R.string.finance_credit else R.string.finance_due,
                    (if (it.balance < 0) -it.balance else it.balance).money()), style = MaterialTheme.typography.titleMedium)
            }
            Button(onClick = { editingId = 0 }, enabled = !saving) { Text(stringResource(R.string.payment_add)) }
            if (payments.isEmpty()) Text(stringResource(R.string.payment_empty))
            (if (showAll) payments else payments.take(5)).forEach { payment ->
                HorizontalDivider()
                Text("${payment.date.formatDate()} · ${payment.amount.money()}")
                payment.notes?.let { Text(it) }
                Row {
                    TextButton(onClick = { editingId = payment.id }, enabled = !saving) { Text(stringResource(R.string.action_edit)) }
                    TextButton(onClick = { deletingId = payment.id }, enabled = !saving) { Text(stringResource(R.string.action_delete)) }
                }
            }
            if (payments.size > 5) TextButton(onClick = { showAll = !showAll }) {
                Text(stringResource(if (showAll) R.string.payment_show_less else R.string.payment_show_all))
            }
        }
    }
    editingId?.let { id ->
        val payment = payments.firstOrNull { it.id == id }
        if (id == 0L || payment != null) key(id) {
            PaymentDialog(payment, saving, { editingId = null }) { date, amount, notes ->
                vm.savePayment(id.takeIf { it != 0L }, objectId, date, amount, notes) { editingId = null }
            }
        }
    }
    deletingId?.let { id ->
        AlertDialog(
            onDismissRequest = { if (!saving) deletingId = null },
            title = { Text(stringResource(R.string.payment_delete_title)) },
            text = { Text(stringResource(R.string.payment_delete_message)) },
            confirmButton = { TextButton(enabled = !saving, onClick = { vm.deletePayment(id, objectId) { deletingId = null } }) {
                Text(stringResource(R.string.action_delete))
            } },
            dismissButton = { TextButton(enabled = !saving, onClick = { deletingId = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun PaymentDialog(payment: CustomerPayment?, saving: Boolean, onDismiss: () -> Unit, onSave: (Long, Long, String) -> Unit) {
    var date by rememberSaveable { mutableStateOf(payment?.date ?: todayMillis()) }
    var amount by rememberSaveable { mutableStateOf(payment?.amount?.amountInput().orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(payment?.notes.orEmpty()) }
    val parsed = parseAmount(amount)
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.payment_title)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!saving) DateButton(stringResource(R.string.label_date), date) { date = it }
            else Text(date.formatDate())
            OutlinedTextField(amount, { amount = it }, enabled = !saving, singleLine = true,
                label = { Text(stringResource(R.string.payment_amount)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = amount.isNotEmpty() && (parsed == null || parsed <= 0),
                supportingText = { Text(stringResource(R.string.payment_positive)) })
            OutlinedTextField(notes, { notes = it }, enabled = !saving, label = { Text(stringResource(R.string.label_notes)) })
        } },
        confirmButton = { Button(enabled = !saving && parsed != null && parsed > 0 && date > 0,
            onClick = { onSave(date, requireNotNull(parsed), notes) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
