package com.example.worktrack

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
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
import androidx.compose.material.icons.outlined.Check
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.worktrack.data.LanguageMode
import com.example.worktrack.data.ObjectSummary
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
            
            // Настройка темы
            val dark = when (settings.themeMode) {
                ThemeMode.System -> androidx.compose.foundation.isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }

            // Динамическая настройка локали
            val locale = when (settings.language) {
                LanguageMode.RU -> Locale("ru")
                LanguageMode.EN -> Locale("en")
                LanguageMode.ES -> Locale("es")
                else -> Locale.getDefault()
            }
            
            // Устанавливаем локаль по умолчанию для форматирования в ViewModel
            Locale.setDefault(locale)
            
            val configuration = Configuration(LocalConfiguration.current)
            configuration.setLocale(locale)
            
            CompositionLocalProvider(LocalContext provides LocalContext.current.createConfigurationContext(configuration)) {
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

private enum class MainTab(val titleRes: Int, val icon: ImageVector) {
    Objects(R.string.tab_objects, Icons.Outlined.Work),
    Workers(R.string.tab_workers, Icons.Outlined.People),
    Types(R.string.tab_types, Icons.Outlined.Construction),
    Reports(R.string.tab_reports, Icons.Outlined.Assessment),
    About(R.string.tab_about, Icons.Outlined.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkTrackApp(vm: AppViewModel) {
    var tab by remember { mutableStateOf(MainTab.Objects) }
    var objectId by remember { mutableLongStateOf(0L) }
    var dayId by remember { mutableLongStateOf(0L) }
    
    val title = when {
        dayId != 0L -> stringResource(R.string.title_work_day)
        objectId != 0L -> stringResource(R.string.title_object)
        else -> stringResource(tab.titleRes)
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }) },
        bottomBar = {
            if (objectId == 0L && dayId == 0L) NavigationBar {
                MainTab.entries.forEach { item ->
                    val label = stringResource(item.titleRes)
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        when {
            dayId != 0L -> WorkDayScreen(vm, dayId, padding, onBack = { dayId = 0L })
            objectId != 0L -> ObjectDetailsScreen(vm, objectId, padding, onBack = { objectId = 0L }, onOpenDay = { dayId = it })
            tab == MainTab.Objects -> ObjectsScreen(vm, padding, onOpen = { objectId = it })
            tab == MainTab.Workers -> WorkersScreen(vm, padding)
            tab == MainTab.Types -> WorkTypesScreen(vm, padding)
            tab == MainTab.Reports -> ReportsScreen(vm, padding)
            tab == MainTab.About -> AboutScreen(vm, padding)
        }
    }
}

@Composable
private fun ObjectsScreen(vm: AppViewModel, padding: PaddingValues, onOpen: (Long) -> Unit) {
    val objects by vm.objects.collectAsState()
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
            text = { Text(stringResource(R.string.btn_add_object)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }
    if (showCreate) CreateObjectDialog(onDismiss = { showCreate = false }, onSave = { address, client, phone ->
        vm.createObject(address, client, phone)
        showCreate = false
    })
}

@Composable
private fun ObjectCard(item: ObjectSummary, onOpen: (Long) -> Unit) {
    Card(
        onClick = { onOpen(item.id) },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.address, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 2)
                if (item.isCompleted) Text(stringResource(R.string.status_completed), color = MaterialTheme.colorScheme.primary)
            }
            Text(stringResource(R.string.label_client, item.clientName), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${item.totalAmount.money()} · ${stringResource(R.string.label_days, item.dayCount)}", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ObjectDetailsScreen(vm: AppViewModel, objectId: Long, padding: PaddingValues, onBack: () -> Unit, onOpenDay: (Long) -> Unit) {
    val objects by vm.objects.collectAsState()
    val days by vm.workDays(objectId).collectAsState()
    val context = LocalContext.current
    val obj = objects.firstOrNull { it.id == objectId }
    var showCreateDay by remember { mutableStateOf(false) }
    var confirmComplete by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedButton(onClick = onBack) { Text(stringResource(R.string.btn_back)) }
                Spacer(Modifier.height(12.dp))
                Card(shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(obj?.address.orEmpty(), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.label_client, obj?.clientName.orEmpty()))
                        Text(stringResource(R.string.label_total, obj?.totalAmount?.money().orEmpty()), fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showCreateDay = true }, enabled = obj?.isCompleted != true) { Text(stringResource(R.string.btn_add_day)) }
                            OutlinedButton(onClick = { vm.shareObjectReport(objectId) { context.shareText(it) } }) {
                                Icon(Icons.Outlined.Share, null)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.btn_report))
                            }
                        }
                        if (obj?.isCompleted != true) {
                            OutlinedButton(onClick = { confirmComplete = true }) { Text(stringResource(R.string.btn_complete_object)) }
                        }
                    }
                }
            }
            if (days.isEmpty()) item { EmptyText(stringResource(R.string.empty_days)) }
            items(days, key = { it.id }) { day ->
                Card(onClick = { onOpenDay(day.id) }, shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(day.date.formatDate(), style = MaterialTheme.typography.titleMedium)
                        Text("${stringResource(R.string.label_workers_count, day.workerCount)} · ${stringResource(R.string.label_entries_count, day.entryCount)}")
                        Text(stringResource(R.string.label_total, day.totalAmount.money()), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
    if (showCreateDay) CreateDayDialog(vm, objectId, onDismiss = { showCreateDay = false }, onCreated = { id ->
        showCreateDay = false
        onOpenDay(id)
    })
    if (confirmComplete) ConfirmDialog(stringResource(R.string.dialog_complete_title), stringResource(R.string.dialog_complete_msg), onDismiss = { confirmComplete = false }) {
        vm.completeObject(objectId)
        confirmComplete = false
    }
}

@Composable
private fun WorkDayScreen(vm: AppViewModel, dayId: Long, padding: PaddingValues, onBack: () -> Unit) {
    val entries by vm.entries(dayId).collectAsState()
    val workerIds by vm.dayWorkerIds(dayId).collectAsState()
    val workers by vm.activeWorkers.collectAsState()
    val types by vm.activeWorkTypes.collectAsState()
    var showEntry by remember { mutableStateOf(false) }
    var deleteId by remember { mutableLongStateOf(0L) }
    Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedButton(onClick = onBack) { Text(stringResource(R.string.btn_back)) }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.label_day_total, entries.sumOf { it.amount }.money()), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Button(onClick = { showEntry = true }, enabled = workerIds.isNotEmpty() && types.isNotEmpty()) { Text(stringResource(R.string.btn_add)) }
                }
            }
            if (entries.isEmpty()) item { EmptyText(stringResource(R.string.empty_work_entries)) }
            items(entries, key = { it.id }) { entry ->
                Card(shape = RoundedCornerShape(8.dp)) {
                    ListItem(
                        headlineContent = { Text("${entry.workerName} · ${entry.workTypeName}") },
                        supportingContent = { Text(entry.notes.orEmpty()) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(entry.amount.money(), fontWeight = FontWeight.SemiBold)
                                IconButton(onClick = { deleteId = entry.id }) { Icon(Icons.Outlined.Delete, stringResource(R.string.btn_delete)) }
                            }
                        }
                    )
                }
            }
        }
    }
    if (showEntry) AddEntryDialog(
        workers = workers.filter { it.id in workerIds },
        types = types,
        onDismiss = { showEntry = false },
        onSave = { workerId, typeId, amount, notes ->
            vm.addEntry(dayId, workerId, typeId, amount, notes)
            showEntry = false
        }
    )
    if (deleteId != 0L) ConfirmDialog(stringResource(R.string.dialog_delete_title), stringResource(R.string.dialog_delete_msg), onDismiss = { deleteId = 0L }) {
        vm.deleteEntry(deleteId)
        deleteId = 0L
    }
}

@Composable
private fun WorkersScreen(vm: AppViewModel, padding: PaddingValues) {
    val workers by vm.workers.collectAsState()
    var editing by remember { mutableStateOf<Worker?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Icon(Icons.Outlined.Add, stringResource(R.string.btn_add))
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
private fun WorkTypesScreen(vm: AppViewModel, padding: PaddingValues) {
    val types by vm.workTypes.collectAsState()
    var editing by remember { mutableStateOf<WorkType?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Icon(Icons.Outlined.Add, stringResource(R.string.btn_add))
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
                        Text(stringResource(R.string.btn_share_report))
                    }
                }
                1 -> {
                    EntityChips(workers, workerId, { it.id }, { it.name }) { workerId = it }
                    DateButton(stringResource(R.string.label_from), from) { from = it }
                    DateButton(stringResource(R.string.label_to), to) { to = it }
                    Button(onClick = { vm.shareWorkerReport(workerId, from, to) { context.shareText(it) } }, enabled = workerId != 0L, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Share, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_share_report))
                    }
                }
                2 -> {
                    EntityChips(objects, objectId, { it.id }, { it.address }) { objectId = it }
                    Button(onClick = { vm.shareObjectReport(objectId) { context.shareText(it) } }, enabled = objectId != 0L, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Share, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_share_report))
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutScreen(vm: AppViewModel, padding: PaddingValues) {
    val settings by vm.settings.collectAsState()
    val uriHandler = LocalUriHandler.current
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("WorkTrack", style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(id = com.example.worktrack.R.string.developer))
            TextButton(onClick = { uriHandler.openUri("https://t.me/Slavafit") }) {
                Text(stringResource(id = com.example.worktrack.R.string.developer_contacts))
            }
            Text(stringResource(id = com.example.worktrack.R.string.app_version, BuildConfig.VERSION_NAME))
        }
        item {
            SectionTitle(stringResource(R.string.section_theme))
            SingleChoiceSegmentedButtonRow {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = settings.themeMode == mode,
                        onClick = { vm.setTheme(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                        label = { Text(when (mode) {
                            ThemeMode.System -> stringResource(R.string.theme_system)
                            ThemeMode.Light -> stringResource(R.string.theme_light)
                            ThemeMode.Dark -> stringResource(R.string.theme_dark)
                        }) }
                    )
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.section_language))
            SingleChoiceSegmentedButtonRow {
                LanguageMode.entries.forEachIndexed { index, lang ->
                    SegmentedButton(
                        selected = settings.language == lang,
                        onClick = { vm.setLanguage(lang) },
                        shape = SegmentedButtonDefaults.itemShape(index, LanguageMode.entries.size),
                        label = { Text(lang.title()) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateObjectDialog(onDismiss: () -> Unit, onSave: (String, String, String?) -> Unit) {
    var address by remember { mutableStateOf("") }
    var client by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_new_object_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(address, { address = it }, label = { Text(stringResource(R.string.hint_address)) }, singleLine = true)
                OutlinedTextField(client, { client = it }, label = { Text(stringResource(R.string.hint_client)) }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text(stringResource(R.string.hint_phone)) }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { onSave(address, client, phone) }, enabled = address.isNotBlank() && client.isNotBlank()) { Text(stringResource(R.string.btn_create)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
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
        title = { Text(stringResource(R.string.dialog_new_day_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DateButton(stringResource(R.string.label_date), date) { date = it }
                Text(stringResource(R.string.label_workers_list), fontWeight = FontWeight.SemiBold)
                workers.forEach { worker ->
                    FilterChip(
                        selected = worker.id in selected,
                        onClick = { selected = if (worker.id in selected) selected - worker.id else selected + worker.id },
                        label = { Text(worker.name) },
                        leadingIcon = if (worker.id in selected) ({ Icon(Icons.Outlined.Check, null) }) else null
                    )
                }
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.hint_notes)) })
            }
        },
        confirmButton = { Button(onClick = { vm.createDay(objectId, date, selected, notes, onCreated) }, enabled = selected.isNotEmpty()) { Text(stringResource(R.string.btn_create)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@Composable
private fun AddEntryDialog(workers: List<Worker>, types: List<WorkType>, onDismiss: () -> Unit, onSave: (Long, Long, Long, String?) -> Unit) {
    var workerId by remember { mutableLongStateOf(workers.firstOrNull()?.id ?: 0L) }
    var typeId by remember { mutableLongStateOf(types.firstOrNull()?.id ?: 0L) }
    var amount by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_work_entry_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.label_worker), fontWeight = FontWeight.SemiBold)
                EntityChips(workers, workerId, { it.id }, { it.name }) { workerId = it }
                Text(stringResource(R.string.label_work_type), fontWeight = FontWeight.SemiBold)
                EntityChips(types, typeId, { it.id }, { it.name }) { typeId = it }
                OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.hint_amount)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.hint_notes)) })
            }
        },
        confirmButton = {
            Button(onClick = { onSave(workerId, typeId, amount.toLongOrNull() ?: 0L, notes) }, enabled = workerId != 0L && typeId != 0L && (amount.toLongOrNull() ?: 0L) > 0) {
                Text(stringResource(R.string.btn_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@Composable
private fun WorkerDialog(worker: Worker?, onDismiss: () -> Unit, onSave: (String, String, Boolean) -> Unit) {
    var name by remember { mutableStateOf(worker?.name.orEmpty()) }
    var phone by remember { mutableStateOf(worker?.phone.orEmpty()) }
    var active by remember { mutableStateOf(worker?.isActive ?: true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (worker == null) stringResource(R.string.dialog_new_worker_title) else stringResource(R.string.dialog_worker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.hint_name)) }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text(stringResource(R.string.hint_phone)) }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.label_active), modifier = Modifier.weight(1f))
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(name, phone, active) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.btn_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@Composable
private fun WorkTypeDialog(type: WorkType?, onDismiss: () -> Unit, onSave: (String, Boolean) -> Unit) {
    var name by remember { mutableStateOf(type?.name.orEmpty()) }
    var active by remember { mutableStateOf(type?.isActive ?: true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (type == null) stringResource(R.string.dialog_new_work_type_title) else stringResource(R.string.dialog_work_type_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.hint_name)) }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.label_active), modifier = Modifier.weight(1f))
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(name, active) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.btn_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@Composable
private fun ConfirmDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { Button(onClick = onConfirm) { Text(stringResource(R.string.btn_yes)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
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
                }) { Text(stringResource(R.string.btn_ok)) }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text(stringResource(R.string.btn_cancel)) } }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun <T> EntityChips(items: List<T>, selectedId: Long, idOf: (T) -> Long, titleOf: (T) -> String, onSelect: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            val id = idOf(item)
            FilterChip(
                selected = selectedId == id,
                onClick = { onSelect(id) },
                label = { Text(titleOf(item), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = if (selectedId == id) ({ Icon(Icons.Outlined.Check, null) }) else null
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun EmptyText(text: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LanguageMode.title(): String = when (this) {
    LanguageMode.System -> stringResource(R.string.theme_system)
    LanguageMode.RU -> "Русский"
    LanguageMode.EN -> "English"
    LanguageMode.ES -> "Español"
}

@Composable
private fun Long.money(): String = stringResource(R.string.money_format, this.formatNumber())

private fun android.content.Context.shareText(text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(intent, "Share"))
}
