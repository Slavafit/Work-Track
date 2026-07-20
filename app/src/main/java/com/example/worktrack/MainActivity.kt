package com.example.worktrack

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.worktrack.data.Client
import com.example.worktrack.data.EntryDetail
import com.example.worktrack.data.LanguageMode
import com.example.worktrack.data.ObjectSummary
import com.example.worktrack.data.ProposalItem
import com.example.worktrack.data.ProposalSummary
import com.example.worktrack.data.ThemeMode
import com.example.worktrack.data.WorkType
import com.example.worktrack.data.Worker
import com.example.worktrack.license.LicenseGate
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: AppViewModel = viewModel()
            val settings by vm.settings.collectAsState()
            val localizedContext = LocalContext.current.withLanguage(settings.language)
            val localizedConfiguration = localizedContext.resources.configuration
            val dark = when (settings.themeMode) {
                ThemeMode.System -> androidx.compose.foundation.isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfiguration
            ) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                    Surface(Modifier.fillMaxSize()) {
                        LicenseGate {
                            WorkTrackApp(vm)
                        }
                    }
                }
            }
        }
    }
}

private fun Context.withLanguage(language: LanguageMode): Context {
    val locale = when (language) {
        LanguageMode.System -> return this
        LanguageMode.RU -> Locale("ru")
        LanguageMode.EN -> Locale("en")
        LanguageMode.ES -> Locale("es")
    }
    val config = Configuration(resources.configuration)
    config.setLocales(LocaleList(locale))
    return createConfigurationContext(config)
}

private enum class MainTab(@StringRes val titleRes: Int, @StringRes val navLabelRes: Int, val icon: ImageVector) {
    Objects(R.string.tab_objects, R.string.nav_objects, Icons.Outlined.Work),
    Proposal(R.string.tab_proposal, R.string.nav_proposal, Icons.Outlined.Assessment),
    Reports(R.string.tab_reports, R.string.nav_reports, Icons.Outlined.Assessment),
    About(R.string.tab_about, R.string.nav_about, Icons.Outlined.Info)
}

private enum class SettingsSection(@StringRes val titleRes: Int) {
    Workers(R.string.tab_workers),
    Types(R.string.tab_types)
}

private data class ProposalLine(
    val id: Long,
    val workTypeId: Long,
    val amount: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkTrackApp(vm: AppViewModel) {
    var tab by remember { mutableStateOf(MainTab.Objects) }
    var settingsSection by remember { mutableStateOf<SettingsSection?>(null) }
    var objectId by remember { mutableLongStateOf(0L) }
    var dayId by remember { mutableLongStateOf(0L) }
    val title = when {
        dayId != 0L -> stringResource(R.string.title_work_day)
        objectId != 0L -> stringResource(R.string.title_object)
        settingsSection != null -> stringResource(settingsSection!!.titleRes)
        else -> stringResource(tab.titleRes)
    }

    BackHandler(enabled = dayId != 0L || objectId != 0L || settingsSection != null || tab != MainTab.Objects) {
        when {
            dayId != 0L -> dayId = 0L
            objectId != 0L -> objectId = 0L
            settingsSection != null -> settingsSection = null
            tab != MainTab.Objects -> tab = MainTab.Objects
        }
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }) },
        bottomBar = {
            if (objectId == 0L && dayId == 0L) NavigationBar {
                MainTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = {
                            tab = item
                            settingsSection = null
                        },
                        icon = { Icon(item.icon, contentDescription = stringResource(item.titleRes)) },
                        label = {
                            Text(
                                text = stringResource(item.navLabelRes),
                                maxLines = 1,
                                softWrap = false,
                                fontSize = 10.sp,
                                overflow = TextOverflow.Clip
                            )
                        }
                    )
                }
            }
        }
    ) { padding ->
        when {
            dayId != 0L -> WorkDayScreen(vm, dayId, padding, onBack = { dayId = 0L })
            objectId != 0L -> ObjectDetailsScreen(vm, objectId, padding, onBack = { objectId = 0L }, onOpenDay = { dayId = it })
            settingsSection == SettingsSection.Workers -> WorkersScreen(vm, padding, onBack = { settingsSection = null })
            settingsSection == SettingsSection.Types -> WorkTypesScreen(vm, padding, onBack = { settingsSection = null })
            tab == MainTab.Objects -> ObjectsScreen(vm, padding, onOpen = { objectId = it })
            tab == MainTab.Proposal -> ProposalScreen(vm, padding)
            tab == MainTab.Reports -> ReportsScreen(vm, padding)
            tab == MainTab.About -> AboutScreen(
                vm = vm,
                padding = padding,
                onOpenWorkers = { settingsSection = SettingsSection.Workers },
                onOpenTypes = { settingsSection = SettingsSection.Types }
            )
        }
    }
}

@Composable
private fun ObjectsScreen(vm: AppViewModel, padding: PaddingValues, onOpen: (Long) -> Unit) {
    val objects by vm.objects.collectAsState()
    val clients by vm.clients.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val active = objects.filterNot { it.isCompleted }
            val completed = objects.filter { it.isCompleted }
            if (active.isEmpty() && completed.isEmpty()) item { EmptyText(stringResource(R.string.empty_objects)) }
            items(active, key = { it.id }) { ObjectCard(it, onOpen) }
            if (completed.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.section_completed)) }
                items(completed, key = { it.id }) { ObjectCard(it, onOpen) }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { showCreate = true },
            icon = { Icon(Icons.Outlined.Add, null) },
            text = { Text(stringResource(R.string.title_object)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }
    if (showCreate) CreateObjectDialog(clients, onDismiss = { showCreate = false }, onSave = { address, clientId, client, phone ->
        vm.createObject(address, clientId, client, phone)
        showCreate = false
    })
}

@Composable
private fun ObjectCard(item: ObjectSummary, onOpen: (Long) -> Unit) {
    Card(
        onClick = { onOpen(item.id) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.address, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 2)
                if (item.isCompleted) Text(stringResource(R.string.status_completed), color = MaterialTheme.colorScheme.primary)
            }
            Text(stringResource(R.string.customer_format, item.clientName), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.object_total_days_format, item.totalAmount.money(), item.dayCount), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ObjectDetailsScreen(vm: AppViewModel, objectId: Long, padding: PaddingValues, onBack: () -> Unit, onOpenDay: (Long) -> Unit) {
    val objects by vm.objects.collectAsState()
    val daysFlow = remember(objectId) { vm.workDays(objectId) }
    val days by daysFlow.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val obj = objects.firstOrNull { it.id == objectId }
    var showCreateDay by remember { mutableStateOf(false) }
    var confirmComplete by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                Spacer(Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(obj?.address.orEmpty(), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.customer_format, obj?.clientName.orEmpty()))
                        Text(stringResource(R.string.total_format, obj?.totalAmount?.money().orEmpty()), fontWeight = FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { showCreateDay = true },
                                enabled = obj?.isCompleted != true,
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.action_add_day), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            OutlinedButton(
                                onClick = { vm.shareObjectReport(objectId) { context.shareText(it) } },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Share, null)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.action_report), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (obj?.isCompleted != true) {
                            OutlinedButton(onClick = { confirmComplete = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.action_complete_object), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            if (days.isEmpty()) item { EmptyText(stringResource(R.string.empty_work_days)) }
            items(days, key = { it.id }) { day ->
                Card(onClick = { onOpenDay(day.id) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(day.date.formatDate(), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.day_counts_format, day.workerCount, day.entryCount))
                        Text(stringResource(R.string.total_format, day.totalAmount.money()), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
    if (showCreateDay) CreateDayDialog(vm, objectId, onDismiss = { showCreateDay = false }, onCreated = { id ->
        showCreateDay = false
        onOpenDay(id)
    })
    if (confirmComplete) ConfirmDialog(stringResource(R.string.confirm_complete_object_title), stringResource(R.string.confirm_complete_object_message), onDismiss = { confirmComplete = false }) {
        vm.completeObject(objectId)
        confirmComplete = false
    }
}

@Composable
private fun WorkDayScreen(vm: AppViewModel, dayId: Long, padding: PaddingValues, onBack: () -> Unit) {
    val entriesFlow = remember(dayId) { vm.entries(dayId) }
    val workerIdsFlow = remember(dayId) { vm.dayWorkerIds(dayId) }
    val entries by entriesFlow.collectAsState(initial = emptyList())
    val workerIds by workerIdsFlow.collectAsState(initial = emptyList())
    val workers by vm.workers.collectAsState()
    val types by vm.activeWorkTypes.collectAsState()
    val dayWorkers = workers.filter { it.id in workerIds }
    val entriesByWorker = entries.groupBy { it.workerId }
    var entryWorker by remember { mutableStateOf<Worker?>(null) }
    var deleteId by remember { mutableLongStateOf(0L) }
    Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.day_total_format, entries.sumOf { it.amount }.money()), style = MaterialTheme.typography.titleMedium)
            }
            if (dayWorkers.isEmpty()) item { EmptyText(stringResource(R.string.empty_workers)) }
            items(dayWorkers, key = { it.id }) { worker ->
                WorkerServicesCard(
                    worker = worker,
                    entries = entriesByWorker[worker.id].orEmpty(),
                    canAdd = types.isNotEmpty(),
                    onAdd = { entryWorker = worker },
                    onDelete = { deleteId = it }
                )
            }
        }
    }
    entryWorker?.let { worker ->
        AddEntryDialog(
        worker = worker,
        types = types,
        onDismiss = { entryWorker = null },
        onSave = { typeId, amount, notes ->
            vm.addEntry(dayId, worker.id, typeId, amount, notes)
            entryWorker = null
        }
        )
    }
    if (deleteId != 0L) ConfirmDialog(stringResource(R.string.confirm_delete_entry_title), stringResource(R.string.confirm_delete_entry_message), onDismiss = { deleteId = 0L }) {
        vm.deleteEntry(deleteId)
        deleteId = 0L
    }
}

@Composable
private fun WorkerServicesCard(
    worker: Worker,
    entries: List<EntryDetail>,
    canAdd: Boolean,
    onAdd: () -> Unit,
    onDelete: (Long) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(worker.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.total_format, entries.sumOf { it.amount }.money()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = onAdd, enabled = canAdd) {
                    Text(stringResource(R.string.action_add), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (entries.isEmpty()) {
                Text(stringResource(R.string.empty_entries), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                entries.forEach { entry ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.workTypeName, fontWeight = FontWeight.SemiBold)
                            entry.notes?.takeIf { it.isNotBlank() }?.let {
                                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(entry.amount.money(), fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { onDelete(entry.id) }) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkersScreen(vm: AppViewModel, padding: PaddingValues, onBack: () -> Unit) {
    val workers by vm.workers.collectAsState()
    var editing by remember { mutableStateOf<Worker?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { OutlinedButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } }
            if (workers.isEmpty()) item { EmptyText(stringResource(R.string.empty_workers)) }
            items(workers, key = { it.id }) { worker ->
                Card(onClick = { editing = worker }, shape = RoundedCornerShape(8.dp)) {
                    ListItem(
                        headlineContent = { Text(worker.name) },
                        supportingContent = { Text(worker.phone.orEmpty()) },
                        trailingContent = { Text(if (worker.isActive) stringResource(R.string.status_active) else stringResource(R.string.status_hidden)) }
                    )
                }
            }
        }
        FloatingActionButton(onClick = { showAdd = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Outlined.Add, stringResource(R.string.action_add))
        }
    }
    if (showAdd) WorkerDialog(null, onDismiss = { showAdd = false }, onSave = { name, phone, active ->
        vm.addWorker(name, phone)
        showAdd = false
    })
    editing?.let { worker ->
        WorkerDialog(worker, onDismiss = { editing = null }, onSave = { name, phone, active ->
            vm.saveWorker(worker.copy(name = name, phone = phone.ifBlank { null }, isActive = active))
            editing = null
        })
    }
}

@Composable
private fun WorkTypesScreen(vm: AppViewModel, padding: PaddingValues, onBack: () -> Unit) {
    val types by vm.workTypes.collectAsState()
    var editing by remember { mutableStateOf<WorkType?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { OutlinedButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } }
            if (types.isEmpty()) item { EmptyText(stringResource(R.string.empty_work_types)) }
            items(types, key = { it.id }) { type ->
                Card(onClick = { editing = type }, shape = RoundedCornerShape(8.dp)) {
                    ListItem(
                        headlineContent = { Text(type.name) },
                        trailingContent = { Text(if (type.isActive) stringResource(R.string.status_active) else stringResource(R.string.status_hidden)) }
                    )
                }
            }
        }
        FloatingActionButton(onClick = { showAdd = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Outlined.Add, stringResource(R.string.action_add))
        }
    }
    if (showAdd) WorkTypeDialog(null, onDismiss = { showAdd = false }, onSave = { name, active ->
        vm.addWorkType(name)
        showAdd = false
    })
    editing?.let { type ->
        WorkTypeDialog(type, onDismiss = { editing = null }, onSave = { name, active ->
            vm.saveWorkType(type.copy(name = name, isActive = active))
            editing = null
        })
    }
}

@Composable
private fun ProposalScreen(vm: AppViewModel, padding: PaddingValues) {
    val objects by vm.objects.collectAsState()
    val types by vm.activeWorkTypes.collectAsState()
    val proposals by vm.proposals.collectAsState()
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    var selectedProposalId by remember { mutableStateOf<Long?>(null) }
    var objectId by remember { mutableLongStateOf(0L) }
    var nextLineId by remember { mutableLongStateOf(-1L) }
    var lines by remember { mutableStateOf<List<ProposalLine>>(emptyList()) }
    val selectedObject = objects.firstOrNull { it.id == objectId }
    val proposalTitle = stringResource(R.string.proposal_title)
    val companyFormat = stringResource(R.string.report_company_format)
    val addressFormat = stringResource(R.string.report_address_format)
    val customerFormat = stringResource(R.string.report_customer_format)
    val totalFormat = stringResource(R.string.report_total_format)
    val validLines = lines.mapNotNull { line ->
        val type = types.firstOrNull { it.id == line.workTypeId }
        val amount = line.amount.toLongOrNull()
        if (type != null && amount != null && amount > 0L) type to amount else null
    }
    val total = validLines.sumOf { it.second }

    LaunchedEffect(selectedProposalId) {
        val id = selectedProposalId ?: return@LaunchedEffect
        val proposal = proposals.firstOrNull { it.id == id } ?: return@LaunchedEffect
        objectId = proposal.objectId
        vm.loadProposalItems(id) { savedItems ->
            lines = savedItems.map { ProposalLine(it.id, it.workTypeId, it.amount.toString()) }
            nextLineId = -1L
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.section_saved_proposals), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        OutlinedButton(
                            onClick = {
                                selectedProposalId = null
                                objectId = 0L
                                lines = emptyList()
                                nextLineId = -1L
                            }
                        ) {
                            Icon(Icons.Outlined.Add, null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_new_proposal), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (proposals.isEmpty()) {
                        Text(stringResource(R.string.empty_proposals), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        proposals.forEach { proposal ->
                            ProposalSummaryCard(
                                proposal = proposal,
                                selected = selectedProposalId == proposal.id,
                                onOpen = { selectedProposalId = proposal.id },
                                onDelete = {
                                    vm.deleteProposal(proposal.id)
                                    if (selectedProposalId == proposal.id) {
                                        selectedProposalId = null
                                        objectId = 0L
                                        lines = emptyList()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.tab_proposal), style = MaterialTheme.typography.titleLarge)
                    EntityPickerField(
                        label = stringResource(R.string.report_tab_object),
                        items = objects,
                        selectedId = objectId,
                        idOf = { it.id },
                        titleOf = { "${it.address} - ${it.clientName}" },
                        onSelect = { objectId = it }
                    )
                    if (selectedObject != null) {
                        Text(stringResource(R.string.report_customer_format, selectedObject.clientName), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = {
                            val firstType = types.firstOrNull() ?: return@Button
                            lines = lines + ProposalLine(nextLineId, firstType.id, "")
                            nextLineId -= 1
                        },
                        enabled = types.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_add_service))
                    }
                }
            }
        }
        if (lines.isEmpty()) {
            item { EmptyText(stringResource(R.string.empty_proposal_services)) }
        } else {
            items(lines, key = { it.id }) { line ->
                ProposalLineCard(
                    line = line,
                    types = types,
                    onChange = { updated -> lines = lines.map { if (it.id == updated.id) updated else it } },
                    onDelete = { lines = lines.filterNot { it.id == line.id } }
                )
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.report_total_format, total.money()), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Button(
                        onClick = {
                            vm.saveProposal(
                                proposalId = selectedProposalId,
                                objectId = objectId,
                                items = validLines.map { (type, amount) -> ProposalItem(proposalId = 0L, workTypeId = type.id, amount = amount) },
                                onSaved = { selectedProposalId = it }
                            )
                        },
                        enabled = selectedObject != null && validLines.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.action_save_proposal))
                    }
                    Button(
                        onClick = {
                            context.shareText(
                                buildProposalText(
                                    proposalTitle = proposalTitle,
                                    companyFormat = companyFormat,
                                    addressFormat = addressFormat,
                                    customerFormat = customerFormat,
                                    totalFormat = totalFormat,
                                    companyName = settings.companyName,
                                    objectSummary = selectedObject,
                                    lines = validLines
                                )
                            )
                        },
                        enabled = selectedObject != null && validLines.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Share, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_send_to_customer))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProposalSummaryCard(
    proposal: ProposalSummary,
    selected: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
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
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete))
            }
        }
    }
}

@Composable
private fun ProposalLineCard(
    line: ProposalLine,
    types: List<WorkType>,
    onChange: (ProposalLine) -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DropdownPickerField(
                label = stringResource(R.string.label_work_type),
                items = types,
                selectedId = line.workTypeId,
                idOf = { it.id },
                titleOf = { it.name },
                onSelect = { onChange(line.copy(workTypeId = it)) }
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = line.amount,
                    onValueChange = { onChange(line.copy(amount = it.filter(Char::isDigit))) },
                    label = { Text(stringResource(R.string.label_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete))
                }
            }
        }
    }
}

private fun buildProposalText(
    proposalTitle: String,
    companyFormat: String,
    addressFormat: String,
    customerFormat: String,
    totalFormat: String,
    companyName: String,
    objectSummary: ObjectSummary?,
    lines: List<Pair<WorkType, Long>>
): String {
    val total = lines.sumOf { it.second }
    return buildString {
        appendLine(proposalTitle)
        companyName.trim().takeIf { it.isNotEmpty() }?.let {
            appendLine(companyFormat.format(it))
        }
        appendLine(addressFormat.format(objectSummary?.address.orEmpty()))
        appendLine(customerFormat.format(objectSummary?.clientName.orEmpty()))
        appendLine()
        lines.forEach { (type, amount) ->
            appendLine("- ${type.name}: ${amount.money()}")
        }
        appendLine()
        appendLine(totalFormat.format(total.money()))
    }
}

@Composable
private fun ReportsScreen(vm: AppViewModel, padding: PaddingValues) {
    val workers by vm.workers.collectAsState()
    val objects by vm.objects.collectAsState()
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var date by remember { mutableLongStateOf(todayMillis()) }
    var from by remember { mutableLongStateOf(todayMillis()) }
    var to by remember { mutableLongStateOf(todayMillis()) }
    var workerId by remember { mutableLongStateOf(0L) }
    var objectId by remember { mutableLongStateOf(0L) }

    Column(Modifier.fillMaxSize().padding(padding)) {
        TabRow(selectedTabIndex = tab) {
            listOf(stringResource(R.string.report_tab_date), stringResource(R.string.report_tab_worker), stringResource(R.string.report_tab_object)).forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (tab) {
                0 -> {
                    DateButton(stringResource(R.string.label_date), date) { date = it }
                    Button(onClick = { vm.shareDateReport(date) { context.shareText(it) } }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Share, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_share_report))
                    }
                }
                1 -> {
                    EntityChips(workers, workerId, { it.id }, { it.name }) { workerId = it }
                    DateButton(stringResource(R.string.label_from), from) { from = it }
                    DateButton(stringResource(R.string.label_to), to) { to = it }
                    Button(onClick = { vm.shareWorkerReport(workerId, from, to) { context.shareText(it) } }, enabled = workerId != 0L, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Share, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_share_report))
                    }
                }
                2 -> {
                    EntityChips(objects, objectId, { it.id }, { it.address }) { objectId = it }
                    Button(onClick = { vm.shareObjectReport(objectId) { context.shareText(it) } }, enabled = objectId != 0L, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Share, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_share_report))
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateObjectDialog(clients: List<Client>, onDismiss: () -> Unit, onSave: (String, Long?, String, String?) -> Unit) {
    var address by remember { mutableStateOf("") }
    var selectedClientId by remember { mutableLongStateOf(0L) }
    var client by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_new_object)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(address, { address = it }, label = { Text(stringResource(R.string.label_address)) }, singleLine = true)
                if (clients.isNotEmpty()) {
                    EntityPickerField(
                        label = stringResource(R.string.label_customer),
                        items = clients,
                        selectedId = selectedClientId,
                        idOf = { it.id },
                        titleOf = { customer ->
                            customer.phone?.takeIf { it.isNotBlank() }?.let { "${customer.name} - $it" } ?: customer.name
                        },
                        onSelect = { id ->
                            selectedClientId = id
                            clients.firstOrNull { it.id == id }?.let { customer ->
                                client = customer.name
                                phone = customer.phone.orEmpty()
                            }
                        }
                    )
                }
                OutlinedTextField(
                    value = client,
                    onValueChange = { client = it },
                    label = { Text(stringResource(R.string.label_customer)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                PhoneField(
                    value = phone,
                    onValueChange = { phone = it },
                    onContactPicked = { contactName, contactPhone ->
                        selectedClientId = 0L
                        if (contactName.isNotBlank()) client = contactName
                        phone = contactPhone
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(address, selectedClientId.takeIf { it != 0L }, client, phone) },
                enabled = address.isNotBlank() && client.isNotBlank() && phone.isValidPhoneOrBlank()
            ) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateDayDialog(vm: AppViewModel, objectId: Long, onDismiss: () -> Unit, onCreated: (Long) -> Unit) {
    val workers by vm.activeWorkers.collectAsState()
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var date by remember { mutableLongStateOf(todayMillis()) }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_new_work_day)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DateButton(stringResource(R.string.label_date), date) { date = it }
                MultiEntityPickerField(
                    label = stringResource(R.string.tab_workers),
                    items = workers,
                    selectedIds = selected,
                    idOf = { it.id },
                    titleOf = { it.name },
                    onSelectionChange = { selected = it }
                )
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.label_notes)) })
            }
        },
        confirmButton = { Button(onClick = { vm.createDay(objectId, date, selected, notes, onCreated) }, enabled = selected.isNotEmpty()) { Text(stringResource(R.string.action_create)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun AddEntryDialog(worker: Worker, types: List<WorkType>, onDismiss: () -> Unit, onSave: (Long, Long, String?) -> Unit) {
    var typeId by remember { mutableLongStateOf(types.firstOrNull()?.id ?: 0L) }
    var amount by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_work_entry)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(worker.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                DropdownPickerField(
                    label = stringResource(R.string.label_work_type),
                    items = types,
                    selectedId = typeId,
                    idOf = { it.id },
                    titleOf = { it.name },
                    onSelect = { typeId = it }
                )
                OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.label_amount)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.label_notes)) })
            }
        },
        confirmButton = {
            Button(onClick = { onSave(typeId, amount.toLongOrNull() ?: 0L, notes) }, enabled = typeId != 0L && (amount.toLongOrNull() ?: 0L) > 0) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun WorkerDialog(worker: Worker?, onDismiss: () -> Unit, onSave: (String, String, Boolean) -> Unit) {
    var name by remember { mutableStateOf(worker?.name.orEmpty()) }
    var phone by remember { mutableStateOf(worker?.phone.orEmpty()) }
    var active by remember { mutableStateOf(worker?.isActive ?: true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (worker == null) R.string.dialog_new_worker else R.string.report_tab_worker)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.label_name)) }, singleLine = true)
                PhoneField(
                    value = phone,
                    onValueChange = { phone = it },
                    onContactPicked = { contactName, contactPhone ->
                        if (contactName.isNotBlank()) name = contactName
                        phone = contactPhone
                    }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.status_active), modifier = Modifier.weight(1f))
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(name, phone, active) }, enabled = name.isNotBlank() && phone.isValidPhoneOrBlank()) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun WorkTypeDialog(type: WorkType?, onDismiss: () -> Unit, onSave: (String, Boolean) -> Unit) {
    var name by remember { mutableStateOf(type?.name.orEmpty()) }
    var active by remember { mutableStateOf(type?.isActive ?: true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (type == null) R.string.dialog_new_work_type else R.string.label_work_type)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.label_name)) }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.status_active), modifier = Modifier.weight(1f))
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(name, active) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun ConfirmDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { Button(onClick = onConfirm) { Text(stringResource(R.string.action_yes)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateButton(label: String, value: Long, onChange: (Long) -> Unit) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { show = true }, modifier = Modifier.fillMaxWidth()) {
        Text("$label: ${value.formatDate()}")
    }
    if (show) {
        val state = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = value)
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                Button(onClick = {
                    state.selectedDateMillis?.let(onChange)
                    show = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text(stringResource(R.string.action_cancel)) } }
        ) {
            DatePicker(state = state)
        }
    }
}
