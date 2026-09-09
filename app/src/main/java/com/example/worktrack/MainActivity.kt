package com.example.worktrack

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.worktrack.data.Material
import com.example.worktrack.data.MaterialEntryDetail
import com.example.worktrack.data.ObjectSummary
import com.example.worktrack.data.ThemeMode
import com.example.worktrack.data.WorkType
import com.example.worktrack.data.WorkDayPhoto
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

private fun Context.canOpenUri(uri: String): Boolean =
    runCatching { contentResolver.openInputStream(Uri.parse(uri))?.use { true } == true }.getOrDefault(false)

private enum class MainTab(@StringRes val titleRes: Int, @StringRes val navLabelRes: Int, val icon: ImageVector) {
    Objects(R.string.tab_objects, R.string.nav_objects, Icons.Outlined.Work),
    Proposal(R.string.tab_proposal, R.string.nav_proposal, Icons.Outlined.Assessment),
    Reports(R.string.tab_reports, R.string.nav_reports, Icons.Outlined.Assessment),
    About(R.string.tab_about, R.string.nav_about, Icons.Outlined.Info)
}

private enum class SettingsSection(@StringRes val titleRes: Int) {
    Workers(R.string.tab_workers),
    Types(R.string.tab_types),
    Materials(R.string.tab_materials)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkTrackApp(vm: AppViewModel) {
    var tab by rememberSaveable { mutableStateOf(MainTab.Objects) }
    var settingsSection by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    var objectId by rememberSaveable { mutableLongStateOf(0L) }
    var dayId by rememberSaveable { mutableLongStateOf(0L) }
    val operationError by vm.operationError.collectAsState()
    operationError?.let { message ->
        AlertDialog(onDismissRequest = vm::clearError,
            text = { Text(stringResource(message)) },
            confirmButton = { TextButton(onClick = vm::clearError) { Text(stringResource(android.R.string.ok)) } })
    }
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
            settingsSection == SettingsSection.Materials -> MaterialsScreen(vm, padding, onBack = { settingsSection = null })
            tab == MainTab.Objects -> ObjectsScreen(vm, padding, onOpen = { objectId = it })
            tab == MainTab.Proposal -> ProposalScreen(vm, padding)
            tab == MainTab.Reports -> ReportsScreen(vm, padding)
            tab == MainTab.About -> AboutScreen(
                vm = vm,
                padding = padding,
                onOpenWorkers = { settingsSection = SettingsSection.Workers },
                onOpenTypes = { settingsSection = SettingsSection.Types },
                onOpenMaterials = { settingsSection = SettingsSection.Materials }
            )
        }
    }
}

@Composable
private fun ObjectsScreen(vm: AppViewModel, padding: PaddingValues, onOpen: (Long) -> Unit) {
    val objects by vm.objects.collectAsState()
    val clients by vm.clients.collectAsState()
    var showCreate by rememberSaveable { mutableStateOf(false) }
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
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).appButtonEffect(RoundedCornerShape(16.dp))
        )
    }
    if (showCreate) CreateObjectDialog(clients, onDismiss = { showCreate = false }, onSave = { address, clientId, client, phone ->
        vm.createObject(address, clientId, client, phone) { showCreate = false }
    })
}

@Composable
private fun ObjectCard(item: ObjectSummary, onOpen: (Long) -> Unit) {
    Card(
        onClick = { onOpen(item.id) },
        modifier = Modifier.fillMaxWidth().appCardEffect(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        Icons.Outlined.Work,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        item.address,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        stringResource(R.string.customer_format, item.clientName),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (item.isCompleted) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            stringResource(R.string.status_completed),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.total_format, item.totalAmount.money()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    stringResource(R.string.object_days_format, item.dayCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
    var showCreateDay by rememberSaveable { mutableStateOf(false) }
    var confirmComplete by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                Spacer(Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth().appCardEffect(RoundedCornerShape(8.dp)), shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(obj?.address.orEmpty(), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.customer_format, obj?.clientName.orEmpty()))
                        Text(stringResource(R.string.total_format, obj?.totalAmount?.money().orEmpty()), fontWeight = FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { showCreateDay = true },
                                enabled = obj?.isCompleted == false,
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.action_add_day), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            OutlinedButton(
                                onClick = { vm.shareObjectReport(objectId) { text, photos -> context.shareReport(text, photos) } },
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
                Card(onClick = { onOpenDay(day.id) }, modifier = Modifier.fillMaxWidth().appCardEffect(RoundedCornerShape(8.dp)), shape = RoundedCornerShape(8.dp)) {
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
    val completedFlow = remember(dayId) { vm.dayCompleted(dayId) }
    val completed by completedFlow.collectAsState(initial = null)
    val saving by vm.isSaving.collectAsState()
    val editable = completed == false && !saving
    val entriesFlow = remember(dayId) { vm.entries(dayId) }
    val materialEntriesFlow = remember(dayId) { vm.materialEntries(dayId) }
    val workerIdsFlow = remember(dayId) { vm.dayWorkerIds(dayId) }
    val photosFlow = remember(dayId) { vm.dayPhotos(dayId) }
    val entries by entriesFlow.collectAsState(initial = emptyList())
    val materialEntries by materialEntriesFlow.collectAsState(initial = emptyList())
    val workerIds by workerIdsFlow.collectAsState(initial = emptyList())
    val photos by photosFlow.collectAsState(initial = emptyList())
    val workers by vm.workers.collectAsState()
    val activeTypes by vm.activeWorkTypes.collectAsState()
    val activeMaterials by vm.materials.collectAsState()
    val allTypes by vm.workTypes.collectAsState()
    val context = LocalContext.current
    val dayWorkers = workers.filter { it.id in workerIds }
    val entriesByWorker = entries.groupBy { it.workerId }
    val materialsByWorker = materialEntries.groupBy { it.workerId }
    var entryWorkerId by rememberSaveable(dayId) { mutableLongStateOf(0L) }
    var materialWorkerId by rememberSaveable(dayId) { mutableLongStateOf(0L) }
    var editingEntryId by rememberSaveable(dayId) { mutableLongStateOf(0L) }
    var editingMaterialEntryId by rememberSaveable(dayId) { mutableLongStateOf(0L) }
    val entryWorker = workers.firstOrNull { it.id == entryWorkerId }
    val materialWorker = workers.firstOrNull { it.id == materialWorkerId }
    val editingEntry = entries.firstOrNull { it.id == editingEntryId }
    val editingMaterialEntry = materialEntries.firstOrNull { it.id == editingMaterialEntryId }
    var deleteId by rememberSaveable { mutableLongStateOf(0L) }
    var deleteMaterialId by rememberSaveable { mutableLongStateOf(0L) }
    var deletePhotoId by rememberSaveable { mutableLongStateOf(0L) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        vm.addDayPhotos(dayId, uris.map(Uri::toString))
    }
    Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                Spacer(Modifier.height(12.dp))
                if (completed == true) Text(stringResource(R.string.object_read_only))
                Text(
                    stringResource(R.string.day_total_format, (entries.sumOf { it.amount } + materialEntries.sumOf { it.amount }).money()),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            item {
                DayPhotosCard(
                    photos = photos,
                    editable = editable,
                    onAdd = { photoPicker.launch(arrayOf("image/*")) },
                    onDelete = { deletePhotoId = it }
                )
            }
            if (dayWorkers.isEmpty()) item { EmptyText(stringResource(R.string.empty_workers)) }
            items(dayWorkers, key = { it.id }) { worker ->
                WorkerServicesCard(
                    worker = worker,
                    editable = editable,
                    entries = entriesByWorker[worker.id].orEmpty(),
                    materialEntries = materialsByWorker[worker.id].orEmpty(),
                    onAddService = { entryWorkerId = worker.id },
                    onAddMaterial = { materialWorkerId = worker.id },
                    onEdit = { editingEntryId = it.id },
                    onEditMaterial = { editingMaterialEntryId = it.id },
                    onDelete = { deleteId = it },
                    onDeleteMaterial = { deleteMaterialId = it }
                )
            }
        }
    }
    entryWorker?.let { worker ->
        AddEntryDialog(
        worker = worker,
        types = activeTypes,
        onDismiss = { entryWorkerId = 0L },
        onAddType = { name, onCreated -> vm.addWorkType(name, onCreated) },
        saving = saving,
        onSave = { typeId, amount, notes ->
            vm.addEntry(dayId, worker.id, typeId, amount, notes) { entryWorkerId = 0L }
        }
        )
    }
    materialWorker?.let { worker ->
        AddMaterialEntryDialog(
            worker = worker,
            materials = activeMaterials.filter { it.isActive },
            onDismiss = { materialWorkerId = 0L },
            onAddMaterial = { name, onCreated -> vm.addMaterial(name, onCreated) },
            saving = saving,
            onSave = { materialId, amount, notes ->
                vm.addMaterialEntry(dayId, worker.id, materialId, amount, notes) { materialWorkerId = 0L }
            }
        )
    }
    editingEntry?.let { entry ->
        AddEntryDialog(
            worker = workers.firstOrNull { it.id == entry.workerId } ?: Worker(id = entry.workerId, name = entry.workerName),
            types = allTypes,
            entry = entry,
            onDismiss = { editingEntryId = 0L },
            onAddType = { name, onCreated -> vm.addWorkType(name, onCreated) },
        saving = saving,
            onSave = { typeId, amount, notes ->
                vm.updateEntry(entry.id, entry.workDayId, entry.workerId, typeId, amount, notes) { editingEntryId = 0L }
            }
        )
    }
    editingMaterialEntry?.let { entry ->
        AddMaterialEntryDialog(
            worker = workers.firstOrNull { it.id == entry.workerId } ?: Worker(id = entry.workerId, name = entry.workerName),
            materials = activeMaterials,
            entry = entry,
            onDismiss = { editingMaterialEntryId = 0L },
            onAddMaterial = { name, onCreated -> vm.addMaterial(name, onCreated) },
            saving = saving,
            onSave = { materialId, amount, notes ->
                vm.updateMaterialEntry(entry.id, entry.workDayId, entry.workerId, materialId, amount, notes) { editingMaterialEntryId = 0L }
            }
        )
    }
    if (deleteId != 0L) ConfirmDialog(stringResource(R.string.confirm_delete_entry_title), stringResource(R.string.confirm_delete_entry_message), onDismiss = { deleteId = 0L }) {
        vm.deleteEntry(deleteId)
        deleteId = 0L
    }
    if (deleteMaterialId != 0L) ConfirmDialog(stringResource(R.string.confirm_delete_entry_title), stringResource(R.string.confirm_delete_entry_message), onDismiss = { deleteMaterialId = 0L }) {
        vm.deleteMaterialEntry(deleteMaterialId)
        deleteMaterialId = 0L
    }
    if (deletePhotoId != 0L) ConfirmDialog(stringResource(R.string.confirm_delete_photo_title), stringResource(R.string.confirm_delete_photo_message), onDismiss = { deletePhotoId = 0L }) {
        vm.deleteDayPhoto(deletePhotoId)
        deletePhotoId = 0L
    }
}

@Composable
private fun WorkerServicesCard(
    worker: Worker,
    editable: Boolean,
    entries: List<EntryDetail>,
    materialEntries: List<MaterialEntryDetail>,
    onAddService: () -> Unit,
    onAddMaterial: () -> Unit,
    onEdit: (EntryDetail) -> Unit,
    onEditMaterial: (MaterialEntryDetail) -> Unit,
    onDelete: (Long) -> Unit,
    onDeleteMaterial: (Long) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().appCardEffect(RoundedCornerShape(8.dp)), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(worker.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.total_format, (entries.sumOf { it.amount } + materialEntries.sumOf { it.amount }).money()),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAddService, enabled = editable, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_add_service), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(onClick = onAddMaterial, enabled = editable, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_add_material), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (entries.isEmpty() && materialEntries.isEmpty()) {
                Text(stringResource(R.string.empty_entries), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (entries.isNotEmpty()) {
                Text(stringResource(R.string.section_proposal_services), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                entries.forEach { entry ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.workTypeName, fontWeight = FontWeight.SemiBold)
                            entry.notes?.takeIf { it.isNotBlank() }?.let {
                                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(entry.amount.money(), fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { onEdit(entry) }, enabled = editable) {
                            Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit))
                        }
                        IconButton(onClick = { onDelete(entry.id) }, enabled = editable) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete))
                        }
                    }
                }
            }
            if (materialEntries.isNotEmpty()) {
                Text(stringResource(R.string.section_proposal_materials), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                materialEntries.forEach { entry ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.materialName, fontWeight = FontWeight.SemiBold)
                            entry.notes?.takeIf { it.isNotBlank() }?.let {
                                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(entry.amount.money(), fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { onEditMaterial(entry) }, enabled = editable) {
                            Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit))
                        }
                        IconButton(onClick = { onDeleteMaterial(entry.id) }, enabled = editable) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayPhotosCard(
    photos: List<WorkDayPhoto>,
    editable: Boolean,
    onAdd: () -> Unit,
    onDelete: (Long) -> Unit
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth().appCardEffect(RoundedCornerShape(8.dp)), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.section_photos), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.photos_count_format, photos.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = onAdd, enabled = editable) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_add_photo), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (photos.isEmpty()) {
                Text(stringResource(R.string.empty_photos), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                photos.forEach { photo ->
                    val available = context.canOpenUri(photo.uri)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                if (available) stringResource(R.string.photo_status_available) else stringResource(R.string.photo_status_missing),
                                fontWeight = FontWeight.SemiBold,
                                color = if (available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Text(photo.uri, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { onDelete(photo.id) }, enabled = editable) {
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
    var showAdd by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { OutlinedButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } }
            if (workers.isEmpty()) item { EmptyText(stringResource(R.string.empty_workers)) }
            items(workers, key = { it.id }) { worker ->
                Card(onClick = { editing = worker }, modifier = Modifier.fillMaxWidth().appCardEffect(RoundedCornerShape(8.dp)), shape = RoundedCornerShape(8.dp)) {
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
    var showAdd by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { OutlinedButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } }
            if (types.isEmpty()) item { EmptyText(stringResource(R.string.empty_work_types)) }
            items(types, key = { it.id }) { type ->
                Card(onClick = { editing = type }, modifier = Modifier.fillMaxWidth().appCardEffect(RoundedCornerShape(8.dp)), shape = RoundedCornerShape(8.dp)) {
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
private fun MaterialsScreen(vm: AppViewModel, padding: PaddingValues, onBack: () -> Unit) {
    val materials by vm.materials.collectAsState()
    var editing by remember { mutableStateOf<Material?>(null) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { OutlinedButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } }
            if (materials.isEmpty()) item { EmptyText(stringResource(R.string.empty_materials)) }
            items(materials, key = { it.id }) { material ->
                Card(onClick = { editing = material }, modifier = Modifier.fillMaxWidth().appCardEffect(RoundedCornerShape(8.dp)), shape = RoundedCornerShape(8.dp)) {
                    ListItem(
                        headlineContent = { Text(material.name) },
                        trailingContent = { Text(if (material.isActive) stringResource(R.string.status_active) else stringResource(R.string.status_hidden)) }
                    )
                }
            }
        }
        FloatingActionButton(onClick = { showAdd = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Outlined.Add, stringResource(R.string.action_add))
        }
    }
    if (showAdd) MaterialDialog(null, onDismiss = { showAdd = false }, onSave = { name, _ ->
        vm.addMaterial(name)
        showAdd = false
    })
    editing?.let { material ->
        MaterialDialog(material, onDismiss = { editing = null }, onSave = { name, active ->
            vm.saveMaterial(material.copy(name = name, isActive = active))
            editing = null
        })
    }
}

@Composable
private fun ReportsScreen(vm: AppViewModel, padding: PaddingValues) {
    val workers by vm.workers.collectAsState()
    val objects by vm.objects.collectAsState()
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var date by rememberSaveable { mutableLongStateOf(todayMillis()) }
    var from by rememberSaveable { mutableLongStateOf(todayMillis()) }
    var to by rememberSaveable { mutableLongStateOf(todayMillis()) }
    var workerId by rememberSaveable { mutableLongStateOf(0L) }
    var objectId by rememberSaveable { mutableLongStateOf(0L) }

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
                    Button(onClick = { vm.shareObjectReport(objectId) { text, photos -> context.shareReport(text, photos) } }, enabled = objectId != 0L, modifier = Modifier.fillMaxWidth()) {
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
    var address by rememberSaveable { mutableStateOf("") }
    var selectedClientId by rememberSaveable { mutableLongStateOf(0L) }
    var client by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
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
                    onValueChange = { client = it; selectedClientId = 0L },
                    label = { Text(stringResource(R.string.label_customer)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                PhoneField(
                    value = phone,
                    onValueChange = { phone = it; selectedClientId = 0L },
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
    var selectedArray by rememberSaveable { mutableStateOf(longArrayOf()) }
    val selected = selectedArray.toSet()
    val saving by vm.isSaving.collectAsState()
    var date by rememberSaveable { mutableLongStateOf(todayMillis()) }
    var notes by rememberSaveable { mutableStateOf("") }
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
                    onSelectionChange = { selectedArray = it.toLongArray() }
                )
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.label_notes)) })
            }
        },
        confirmButton = { Button(onClick = { vm.createDay(objectId, date, selected, notes, onCreated) }, enabled = selected.isNotEmpty() && !saving) { Text(stringResource(R.string.action_create)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun AddEntryDialog(
    worker: Worker,
    types: List<WorkType>,
    entry: EntryDetail? = null,
    onDismiss: () -> Unit,
    onAddType: (String, (Long) -> Unit) -> Unit,
    saving: Boolean = false,
    onSave: (Long, Long, String?) -> Unit
) {
    var showNewType by rememberSaveable { mutableStateOf(false) }
    var typeId by rememberSaveable(entry?.id) { mutableLongStateOf(entry?.workTypeId ?: types.firstOrNull()?.id ?: 0L) }
    var amount by rememberSaveable(entry?.id) { mutableStateOf(entry?.amount?.toString().orEmpty()) }
    var notes by rememberSaveable(entry?.id) { mutableStateOf(entry?.notes.orEmpty()) }
    LaunchedEffect(types) { if (typeId == 0L) typeId = types.firstOrNull()?.id ?: 0L }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
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
                    ,onAddNew = { showNewType = true }
                )
                OutlinedTextField(amount, { amount = it }, label = { Text(stringResource(R.string.label_amount)) },
                    isError = amount.isNotEmpty() && parseAmount(amount) == null,
                    supportingText = { if (amount.isNotEmpty() && parseAmount(amount) == null) Text(stringResource(R.string.amount_invalid)) },
                    enabled = !saving, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.label_notes)) }, enabled = !saving)
            }
        },
        confirmButton = {
            Button(onClick = { onSave(typeId, requireNotNull(parseAmount(amount)), notes) }, enabled = typeId != 0L && parseAmount(amount) != null && !saving) {
                Text(stringResource(if (entry == null) R.string.action_add else R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.action_cancel)) } }
    )
    if (showNewType) QuickAddTypeDialog(
        onDismiss = { showNewType = false },
        onSave = { name ->
            onAddType(name) { typeId = it }
            showNewType = false
        }
    )
}

@Composable
private fun AddMaterialEntryDialog(
    worker: Worker,
    materials: List<Material>,
    entry: MaterialEntryDetail? = null,
    onDismiss: () -> Unit,
    onAddMaterial: (String, (Long) -> Unit) -> Unit,
    saving: Boolean = false,
    onSave: (Long, Long, String?) -> Unit
) {
    var materialId by rememberSaveable(entry?.id) {
        mutableLongStateOf(entry?.materialId ?: materials.firstOrNull()?.id ?: 0L)
    }
    var amount by rememberSaveable(entry?.id) { mutableStateOf(entry?.amount?.toString().orEmpty()) }
    var notes by rememberSaveable(entry?.id) { mutableStateOf(entry?.notes.orEmpty()) }
    var showNewMaterial by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(materials) { if (materialId == 0L) materialId = materials.firstOrNull()?.id ?: 0L }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.dialog_material_entry)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(worker.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                DropdownPickerField(
                    label = stringResource(R.string.label_material),
                    items = materials,
                    selectedId = materialId,
                    idOf = { it.id },
                    titleOf = { it.name },
                    onSelect = { materialId = it },
                    onAddNew = { showNewMaterial = true }
                )
                OutlinedTextField(
                    amount,
                    { amount = it },
                    label = { Text(stringResource(R.string.label_amount)) },
                    isError = amount.isNotEmpty() && parseAmount(amount) == null,
                    supportingText = { if (amount.isNotEmpty() && parseAmount(amount) == null) Text(stringResource(R.string.amount_invalid)) },
                    enabled = !saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.label_notes)) }, enabled = !saving)
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(materialId, requireNotNull(parseAmount(amount)), notes) },
                enabled = materialId != 0L && parseAmount(amount) != null && !saving
            ) {
                Text(stringResource(if (entry == null) R.string.action_add else R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.action_cancel)) } }
    )
    if (showNewMaterial) QuickAddMaterialDialog(
        onDismiss = { showNewMaterial = false },
        onSave = { name ->
            onAddMaterial(name) { materialId = it }
            showNewMaterial = false
        }
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
private fun MaterialDialog(material: Material?, onDismiss: () -> Unit, onSave: (String, Boolean) -> Unit) {
    var name by remember { mutableStateOf(material?.name.orEmpty()) }
    var active by remember { mutableStateOf(material?.isActive ?: true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (material == null) R.string.dialog_new_material else R.string.tab_materials)) },
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
internal fun QuickAddTypeDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_new_work_type)) },
        text = { OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.label_name)) }, singleLine = true) },
        confirmButton = { Button(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
internal fun QuickAddMaterialDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_new_material)) },
        text = { OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.label_name)) }, singleLine = true) },
        confirmButton = { Button(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
internal fun ConfirmDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
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
    var show by rememberSaveable { mutableStateOf(false) }
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
