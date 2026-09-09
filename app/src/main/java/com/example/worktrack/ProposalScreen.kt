package com.example.worktrack

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.worktrack.data.Material
import com.example.worktrack.data.ObjectSummary
import com.example.worktrack.data.ProposalSummary
import com.example.worktrack.data.WorkType
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.saveable.rememberSaveable

@Composable
internal fun ProposalScreen(vm: AppViewModel, padding: PaddingValues) {
    val editor = vm.proposalEditor
    val draft by editor.state.collectAsState()
    val objects by vm.objects.collectAsState()
    val types by vm.workTypes.collectAsState()
    val materials by vm.materials.collectAsState()
    val proposals by vm.proposals.collectAsState()
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    val selectedObject = objects.firstOrNull { it.id == draft.objectId }
    val originalObject = proposals.firstOrNull { it.id == draft.proposalId }?.let { p -> objects.firstOrNull { it.id == p.objectId } }
    val readOnly = selectedObject?.isCompleted == true || originalObject?.isCompleted == true
    val editable = !draft.busy && !readOnly
    val referencesAvailable = draft.lines.all { line -> types.any { it.id == line.workTypeId } } &&
        draft.materialLines.all { line -> materials.any { it.id == line.materialId } }
    val valid = draft.valid && referencesAvailable && selectedObject != null && !draft.busy
    var pendingOpen by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    var showNewType by rememberSaveable { mutableStateOf(false) }
    var showNewMaterial by rememberSaveable { mutableStateOf(false) }

    fun open(id: Long) {
        if (draft.saving) return
        if (draft.dirty) pendingOpen = id
        else if (id == 0L) editor.newDraft() else editor.open(id)
    }

    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.section_saved_proposals), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { open(0) }, enabled = !draft.saving) { Text(stringResource(R.string.action_new_proposal)) }
            }
        }
        items(proposals, key = { "saved-${it.id}" }) { proposal ->
            ProposalSummaryCard(proposal, draft.proposalId == proposal.id,
                onOpen = { if (proposal.id != draft.proposalId || !draft.dirty) open(proposal.id) },
                onDelete = { if (!draft.busy) pendingDelete = proposal.id },
                deleteEnabled = !draft.busy && objects.firstOrNull { it.id == proposal.objectId }?.isCompleted == false)
        }
        item {
            if (draft.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            draft.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            if (readOnly) Text(stringResource(R.string.object_read_only), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (draft.dirty) Text(stringResource(R.string.draft_unsaved), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.tab_proposal), style = MaterialTheme.typography.titleLarge)
                    if (!draft.busy && originalObject?.isCompleted != true) {
                        EntityPickerField(stringResource(R.string.report_tab_object), objects, draft.objectId, { it.id }, { "${it.address} - ${it.clientName}" }, editor::selectObject)
                    } else {
                        Text(selectedObject?.address.orEmpty())
                    }
                    selectedObject?.let { Text(stringResource(R.string.report_customer_format, it.clientName)) }
                    Button(onClick = {
                        val first = types.firstOrNull { it.isActive }
                        if (first == null) showNewType = true else editor.addService(first.id)
                    }, enabled = editable, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_add_service)) }
                    Button(onClick = {
                        val first = materials.firstOrNull { it.isActive }
                        if (first == null) showNewMaterial = true else editor.addMaterial(first.id)
                    }, enabled = editable, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_add_material)) }
                }
            }
        }
        if (draft.lines.isNotEmpty()) item { SectionTitle(stringResource(R.string.section_proposal_services)) }
        items(draft.lines, key = { "service-${it.id}" }) { line ->
            ProposalLineCard(line, types, { name, callback -> vm.addWorkType(name, callback) }, editor::updateService,
                { editor.removeService(line.id) }, editable)
        }
        if (draft.materialLines.isNotEmpty()) item { SectionTitle(stringResource(R.string.section_proposal_materials)) }
        items(draft.materialLines, key = { "material-${it.id}" }) { line ->
            ProposalMaterialLineCard(line, materials, { name, callback -> vm.addMaterial(name, callback) }, editor::updateMaterial,
                { editor.removeMaterial(line.id) }, editable)
        }
        item {
            Text(stringResource(R.string.report_total_format, draft.total?.money() ?: "—"), style = MaterialTheme.typography.titleMedium)
            if (!draft.valid && (draft.lines.isNotEmpty() || draft.materialLines.isNotEmpty())) {
                Text(stringResource(R.string.proposal_invalid), color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = editor::save, enabled = valid && editable, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_save_proposal))
            }
            Button(onClick = {
                context.shareText(buildProposalText(
                    context.getString(R.string.proposal_title), context.getString(R.string.report_company_format),
                    context.getString(R.string.report_address_format), context.getString(R.string.report_customer_format),
                    context.getString(R.string.report_total_format), settings.companyName, selectedObject,
                    draft.lines.map { line -> types.first { it.id == line.workTypeId } to requireNotNull(parseAmount(line.amount)) },
                    draft.materialLines.map { line -> materials.first { it.id == line.materialId } to requireNotNull(parseAmount(line.amount)) },
                    context.getString(R.string.section_proposal_services), context.getString(R.string.section_proposal_materials)
                ))
            }, enabled = valid, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_send_to_customer)) }
        }
    }
    pendingOpen?.let { id ->
        ConfirmDialog(stringResource(R.string.discard_draft_title), stringResource(R.string.discard_draft_message), { pendingOpen = null }) {
            pendingOpen = null
            if (id == 0L) editor.newDraft() else editor.open(id)
        }
    }
    pendingDelete?.let { id ->
        ConfirmDialog(stringResource(R.string.delete_proposal_title), stringResource(R.string.delete_proposal_message), { pendingDelete = null }) {
            pendingDelete = null
            editor.delete(id)
        }
    }
    if (showNewType) QuickAddTypeDialog({ showNewType = false }) { name ->
        vm.addWorkType(name) { editor.addService(it); showNewType = false }
    }
    if (showNewMaterial) QuickAddMaterialDialog({ showNewMaterial = false }) { name ->
        vm.addMaterial(name) { editor.addMaterial(it); showNewMaterial = false }
    }
}


@Composable
private fun ProposalSummaryCard(
    proposal: ProposalSummary,
    selected: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    deleteEnabled: Boolean
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().appCardEffect(RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(proposal.address, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(proposal.clientName, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.proposal_items_format, proposal.itemCount, proposal.totalAmount.money()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete, enabled = deleteEnabled) {
                Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete))
            }
        }
    }
}

@Composable
private fun ProposalLineCard(
    line: ProposalLine,
    types: List<WorkType>,
    onAddType: (String, (Long) -> Unit) -> Unit,
    onChange: (ProposalLine) -> Unit,
    onDelete: () -> Unit,
    editable: Boolean
) {
    var showNewType by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().appCardEffect(RoundedCornerShape(8.dp)), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DropdownPickerField(
                label = stringResource(R.string.label_work_type),
                items = types.filter { it.isActive || it.id == line.workTypeId },
                selectedId = line.workTypeId,
                idOf = { it.id },
                titleOf = { it.name },
                onSelect = { onChange(line.copy(workTypeId = it)) }
                ,onAddNew = if (editable) ({ showNewType = true }) else null, enabled = editable
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = line.amount,
                    onValueChange = { onChange(line.copy(amount = it)) },
                    label = { Text(stringResource(R.string.label_amount)) },
                    enabled = editable,
                    isError = parseAmount(line.amount) == null,
                    supportingText = { if (parseAmount(line.amount) == null) Text(stringResource(R.string.amount_invalid)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                IconButton(onClick = onDelete, enabled = editable) {
                    Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete))
                }
            }
        }
    }
    if (showNewType) QuickAddTypeDialog(
        onDismiss = { showNewType = false },
        onSave = { name ->
            onAddType(name) { id -> onChange(line.copy(workTypeId = id)) }
            showNewType = false
        }
    )
}

@Composable
private fun ProposalMaterialLineCard(
    line: ProposalMaterialLine,
    materials: List<Material>,
    onAddMaterial: (String, (Long) -> Unit) -> Unit,
    onChange: (ProposalMaterialLine) -> Unit,
    onDelete: () -> Unit,
    editable: Boolean
) {
    var showNewMaterial by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().appCardEffect(RoundedCornerShape(8.dp)), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DropdownPickerField(
                label = stringResource(R.string.label_material),
                items = materials.filter { it.isActive || it.id == line.materialId },
                selectedId = line.materialId,
                idOf = { it.id },
                titleOf = { it.name },
                onSelect = { onChange(line.copy(materialId = it)) },
                onAddNew = if (editable) ({ showNewMaterial = true }) else null, enabled = editable
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = line.amount,
                    onValueChange = { onChange(line.copy(amount = it)) },
                    label = { Text(stringResource(R.string.label_amount)) },
                    enabled = editable,
                    isError = parseAmount(line.amount) == null,
                    supportingText = { if (parseAmount(line.amount) == null) Text(stringResource(R.string.amount_invalid)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                IconButton(onClick = onDelete, enabled = editable) {
                    Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete))
                }
            }
        }
    }
    if (showNewMaterial) QuickAddMaterialDialog(
        onDismiss = { showNewMaterial = false },
        onSave = { name ->
            onAddMaterial(name) { id -> onChange(line.copy(materialId = id)) }
            showNewMaterial = false
        }
    )
}

private fun buildProposalText(
    proposalTitle: String,
    companyFormat: String,
    addressFormat: String,
    customerFormat: String,
    totalFormat: String,
    companyName: String,
    objectSummary: ObjectSummary?,
    lines: List<Pair<WorkType, Long>>,
    materialLines: List<Pair<Material, Long>>,
    servicesLabel: String,
    materialsLabel: String
): String {
    val total = lines.sumOf { it.second } + materialLines.sumOf { it.second }
    return buildString {
        appendLine(proposalTitle)
        companyName.trim().takeIf { it.isNotEmpty() }?.let {
            appendLine(companyFormat.format(it))
        }
        appendLine(addressFormat.format(objectSummary?.address.orEmpty()))
        appendLine(customerFormat.format(objectSummary?.clientName.orEmpty()))
        appendLine()
        if (lines.isNotEmpty()) {
            appendLine("$servicesLabel:")
            lines.forEach { (type, amount) -> appendLine("- ${type.name}: ${amount.money()}") }
        }
        if (materialLines.isNotEmpty()) {
            if (lines.isNotEmpty()) appendLine()
            appendLine("$materialsLabel:")
            materialLines.forEach { (material, amount) -> appendLine("- ${material.name}: ${amount.money()}") }
        }
        appendLine()
        appendLine(totalFormat.format(total.money()))
    }
}

