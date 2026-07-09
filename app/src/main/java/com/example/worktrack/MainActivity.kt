package com.example.worktrack

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.material3.ModalBottomSheet
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: AppViewModel = viewModel()
            val settings by vm.settings.collectAsState()
            val dark = when (settings.themeMode) {
                ThemeMode.System -> androidx.compose.foundation.isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
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

private enum class MainTab(val title: String, val icon: ImageVector) {
    Objects("ÐžÐ±ÑŠÐµÐºÑ‚Ñ‹", Icons.Outlined.Work),
    Workers("Ð Ð°Ð±Ð¾Ñ‚Ð½Ð¸ÐºÐ¸", Icons.Outlined.People),
    Types("Ð’Ð¸Ð´Ñ‹ Ñ€Ð°Ð±Ð¾Ñ‚", Icons.Outlined.Construction),
    Reports("ÐžÑ‚Ñ‡Ñ‘Ñ‚Ñ‹", Icons.Outlined.Assessment),
    About("Ðž Ð¿Ñ€Ð¸Ð»Ð¾Ð¶ÐµÐ½Ð¸Ð¸", Icons.Outlined.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkTrackApp(vm: AppViewModel) {
    var tab by remember { mutableStateOf(MainTab.Objects) }
    var objectId by remember { mutableLongStateOf(0L) }
    var dayId by remember { mutableLongStateOf(0L) }
    val title = when {
        dayId != 0L -> "Ð Ð°Ð±Ð¾Ñ‡Ð¸Ð¹ Ð´ÐµÐ½ÑŒ"
        objectId != 0L -> "ÐžÐ±ÑŠÐµÐºÑ‚"
        else -> tab.title
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }) },
        bottomBar = {
            if (objectId == 0L && dayId == 0L) NavigationBar {
                MainTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) }
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
            if (active.isEmpty() && completed.isEmpty()) item { EmptyText("Ð”Ð¾Ð±Ð°Ð²ÑŒÑ‚Ðµ Ð¿ÐµÑ€Ð²Ñ‹Ð¹ Ð¾Ð±ÑŠÐµÐºÑ‚") }
            items(active, key = { it.id }) { ObjectCard(it, onOpen) }
            if (completed.isNotEmpty()) {
                item { SectionTitle("Ð—Ð°Ð²ÐµÑ€ÑˆÑ‘Ð½Ð½Ñ‹Ðµ") }
                items(completed, key = { it.id }) { ObjectCard(it, onOpen) }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { showCreate = true },
            icon = { Icon(Icons.Outlined.Add, null) },
            text = { Text("ÐžÐ±ÑŠÐµÐºÑ‚") },
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
                if (item.isCompleted) Text("Ð—Ð°Ð²ÐµÑ€ÑˆÑ‘Ð½", color = MaterialTheme.colorScheme.primary)
            }
            Text("Ð—Ð°ÐºÐ°Ð·Ñ‡Ð¸Ðº: ${item.clientName}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${item.totalAmount.money()} Â· Ð´Ð½ÐµÐ¹: ${item.dayCount}", fontWeight = FontWeight.SemiBold)
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
                OutlinedButton(onClick = onBack) { Text("ÐÐ°Ð·Ð°Ð´") }
                Spacer(Modifier.height(12.dp))
                Card(shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(obj?.address.orEmpty(), style = MaterialTheme.typography.titleLarge)
                        Text("Ð—Ð°ÐºÐ°Ð·Ñ‡Ð¸Ðº: ${obj?.clientName.orEmpty()}")
                        Text("Ð˜Ñ‚Ð¾Ð³Ð¾: ${obj?.totalAmount?.money().orEmpty()}", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showCreateDay = true }, enabled = obj?.isCompleted != true) { Text("Ð”Ð¾Ð±Ð°Ð²Ð¸Ñ‚ÑŒ Ð´ÐµÐ½ÑŒ") }
                            OutlinedButton(onClick = { vm.shareObjectReport(objectId) { context.shareText(it) } }) {
                                Icon(Icons.Outlined.Share, null)
                                Spacer(Modifier.width(6.dp))
                                Text("ÐžÑ‚Ñ‡Ñ‘Ñ‚")
                            }
                        }
                        if (obj?.isCompleted != true) {
                            OutlinedButton(onClick = { confirmComplete = true }) { Text("Ð—Ð°Ð²ÐµÑ€ÑˆÐ¸Ñ‚ÑŒ Ð¾Ð±ÑŠÐµÐºÑ‚") }
                        }
                    }
                }
            }
            if (days.isEmpty()) item { EmptyText("Ð Ð°Ð±Ð¾Ñ‡Ð¸Ðµ Ð´Ð½Ð¸ Ð¿Ð¾ÐºÐ° Ð½Ðµ Ð´Ð¾Ð±Ð°Ð²Ð»ÐµÐ½Ñ‹") }
            items(days, key = { it.id }) { day ->
                Card(onClick = { onOpenDay(day.id) }, shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(day.date.formatDate(), style = MaterialTheme.typography.titleMedium)
                        Text("Ð Ð°Ð±Ð¾Ñ‚Ð½Ð¸ÐºÐ¾Ð²: ${day.workerCount} Â· Ð·Ð°Ð¿Ð¸ÑÐµÐ¹: ${day.entryCount}")
                        Text("Ð˜Ñ‚Ð¾Ð³Ð¾: ${day.totalAmount.money()}", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
    if (showCreateDay) CreateDayDialog(vm, objectId, onDismiss = { showCreateDay = false }, onCreated = { id ->
        showCreateDay = false
        onOpenDay(id)
    })
    if (confirmComplete) ConfirmDialog("Ð—Ð°Ð²ÐµÑ€ÑˆÐ¸Ñ‚ÑŒ Ð¾Ð±ÑŠÐµÐºÑ‚?", "ÐŸÐ¾ÑÐ»Ðµ Ð·Ð°Ð²ÐµÑ€ÑˆÐµÐ½Ð¸Ñ Ð¾Ð±ÑŠÐµÐºÑ‚ ÑÑ‡Ð¸Ñ‚Ð°ÐµÑ‚ÑÑ read-only.", onDismiss = { confirmComplete = false }) {
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
                OutlinedButton(onClick = onBack) { Text("ÐÐ°Ð·Ð°Ð´") }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ð˜Ñ‚Ð¾Ð³ Ð´Ð½Ñ: ${entries.sumOf { it.amount }.money()}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Button(onClick = { showEntry = true }, enabled = workerIds.isNotEmpty() && types.isNotEmpty()) { Text("Ð”Ð¾Ð±Ð°Ð²Ð¸Ñ‚ÑŒ") }
                }
            }
            if (entries.isEmpty()) item { EmptyText("Ð”Ð¾Ð±Ð°Ð²ÑŒÑ‚Ðµ Ð²Ñ‹Ð¿Ð¾Ð»Ð½ÐµÐ½Ð½Ñ‹Ðµ Ñ€Ð°Ð±Ð¾Ñ‚Ñ‹") }
            items(entries, key = { it.id }) { entry ->
                Card(shape = RoundedCornerShape(8.dp)) {
                    ListItem(
                        headlineContent = { Text("${entry.workerName} Â· ${entry.workTypeName}") },
                        supportingContent = { Text(entry.notes.orEmpty()) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(entry.amount.money(), fontWeight = FontWeight.SemiBold)
                                IconButton(onClick = { deleteId = entry.id }) { Icon(Icons.Outlined.Delete, "Ð£Ð´Ð°Ð»Ð¸Ñ‚ÑŒ") }
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
    if (deleteId != 0L) ConfirmDialog("Ð£Ð´Ð°Ð»Ð¸Ñ‚ÑŒ Ð·Ð°Ð¿Ð¸ÑÑŒ?", "Ð”ÐµÐ¹ÑÑ‚Ð²Ð¸Ðµ Ð½ÐµÐ»ÑŒÐ·Ñ Ð¾Ñ‚Ð¼ÐµÐ½Ð¸Ñ‚ÑŒ.", onDismiss = { deleteId = 0L }) {
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
            if (workers.isEmpty()) item { EmptyText("Ð”Ð¾Ð±Ð°Ð²ÑŒÑ‚Ðµ Ñ€Ð°Ð±Ð¾Ñ‚Ð½Ð¸ÐºÐ¾Ð²") }
            items(workers, key = { it.id }) { worker ->
                Card(onClick = { editing = worker }, shape = RoundedCornerShape(8.dp)) {
                    ListItem(
                        headlineContent = { Text(worker.name) },
                        supportingContent = { Text(worker.phone.orEmpty()) },
                        trailingContent = { Text(if (worker.isActive) "ÐÐºÑ‚Ð¸Ð²ÐµÐ½" else "Ð¡ÐºÑ€Ñ‹Ñ‚") }
                    )
                }
            }
        }
        FloatingActionButton(onClick = { showAdd = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Outlined.Add, "Ð”Ð¾Ð±Ð°Ð²Ð¸Ñ‚ÑŒ")
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
            if (types.isEmpty()) item { EmptyText("Ð”Ð¾Ð±Ð°Ð²ÑŒÑ‚Ðµ Ð²Ð¸Ð´Ñ‹ Ñ€Ð°Ð±Ð¾Ñ‚") }
            items(types, key = { it.id }) { type ->
                Card(onClick = { editing = type }, shape = RoundedCornerShape(8.dp)) {
                    ListItem(
                        headlineContent = { Text(type.name) },
                        trailingContent = { Text(if (type.isActive) "ÐÐºÑ‚Ð¸Ð²ÐµÐ½" else "Ð¡ÐºÑ€Ñ‹Ñ‚") }
                    )
                }
            }
        }
        FloatingActionButton(onClick = { showAdd = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Outlined.Add, "Ð”Ð¾Ð±Ð°Ð²Ð¸Ñ‚ÑŒ")
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
            listOf("Ð”Ð°Ñ‚Ð°", "Ð Ð°Ð±Ð¾Ñ‚Ð½Ð¸Ðº", "ÐžÐ±ÑŠÐµÐºÑ‚").forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (tab) {
                0 -> {
                    DateButton("Ð”Ð°Ñ‚Ð°", date) { date = it }
                    Button(onClick = { vm.shareDateReport(date) { context.shareText(it) } }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Share, null)
                        Spacer(Modifier.width(8.dp))
                        Text("ÐŸÐ¾Ð´ÐµÐ»Ð¸Ñ‚ÑŒÑÑ Ð¾Ñ‚Ñ‡Ñ‘Ñ‚Ð¾Ð¼")
                    }
                }
                1 -> {
                    EntityChips(workers, workerId, { it.id }, { it.name }) { workerId = it }
                    DateButton("Ð¡", from) { from = it }
                    DateButton("ÐŸÐ¾", to) { to = it }
                    Button(onClick = { vm.shareWorkerReport(workerId, from, to) { context.shareText(it) } }, enabled = workerId != 0L, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Share, null)
                        Spacer(Modifier.width(8.dp))
                        Text("ÐŸÐ¾Ð´ÐµÐ»Ð¸Ñ‚ÑŒÑÑ Ð¾Ñ‚Ñ‡Ñ‘Ñ‚Ð¾Ð¼")
                    }
                }
                2 -> {
                    EntityChips(objects, objectId, { it.id }, { it.address }) { objectId = it }
                    Button(onClick = { vm.shareObjectReport(objectId) { context.shareText(it) } }, enabled = objectId != 0L, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Share, null)
                        Spacer(Modifier.width(8.dp))
                        Text("ÐŸÐ¾Ð´ÐµÐ»Ð¸Ñ‚ÑŒÑÑ Ð¾Ñ‚Ñ‡Ñ‘Ñ‚Ð¾Ð¼")
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
            SectionTitle("Ð¢ÐµÐ¼Ð°")
            SingleChoiceSegmentedButtonRow {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = settings.themeMode == mode,
                        onClick = { vm.setTheme(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                        label = { Text(when (mode) {
                            ThemeMode.System -> "Ð¡Ð¸ÑÑ‚ÐµÐ¼Ð½Ð°Ñ"
                            ThemeMode.Light -> "Ð¡Ð²ÐµÑ‚Ð»Ð°Ñ"
                            ThemeMode.Dark -> "Ð¢Ñ‘Ð¼Ð½Ð°Ñ"
                        }) }
                    )
                }
            }
        }
        item {
            SectionTitle("Ð¯Ð·Ñ‹Ðº")
            SingleChoiceSegmentedButtonRow {
                LanguageMode.entries.forEachIndexed { index, lang ->
                    SegmentedButton(
                        selected = settings.language == lang,
                        onClick = { vm.setLanguage(lang) },
                        shape = SegmentedButtonDefaults.itemShape(index, LanguageMode.entries.size),
                        label = { Text(lang.name) }
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
        title = { Text("ÐÐ¾Ð²Ñ‹Ð¹ Ð¾Ð±ÑŠÐµÐºÑ‚") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(address, { address = it }, label = { Text("ÐÐ´Ñ€ÐµÑ") }, singleLine = true)
                OutlinedTextField(client, { client = it }, label = { Text("Ð—Ð°ÐºÐ°Ð·Ñ‡Ð¸Ðº") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Ð¢ÐµÐ»ÐµÑ„Ð¾Ð½") }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { onSave(address, client, phone) }, enabled = address.isNotBlank() && client.isNotBlank()) { Text("Ð¡Ð¾Ð·Ð´Ð°Ñ‚ÑŒ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ÐžÑ‚Ð¼ÐµÐ½Ð°") } }
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
        title = { Text("ÐÐ¾Ð²Ñ‹Ð¹ Ñ€Ð°Ð±Ð¾Ñ‡Ð¸Ð¹ Ð´ÐµÐ½ÑŒ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DateButton("Ð”Ð°Ñ‚Ð°", date) { date = it }
                Text("Ð Ð°Ð±Ð¾Ñ‚Ð½Ð¸ÐºÐ¸", fontWeight = FontWeight.SemiBold)
                workers.forEach { worker ->
                    FilterChip(
                        selected = worker.id in selected,
                        onClick = { selected = if (worker.id in selected) selected - worker.id else selected + worker.id },
                        label = { Text(worker.name) },
                        leadingIcon = if (worker.id in selected) ({ Icon(Icons.Outlined.Check, null) }) else null
                    )
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Ð—Ð°Ð¼ÐµÑ‚ÐºÐ¸") })
            }
        },
        confirmButton = { Button(onClick = { vm.createDay(objectId, date, selected, notes, onCreated) }, enabled = selected.isNotEmpty()) { Text("Ð¡Ð¾Ð·Ð´Ð°Ñ‚ÑŒ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ÐžÑ‚Ð¼ÐµÐ½Ð°") } }
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
        title = { Text("Ð—Ð°Ð¿Ð¸ÑÑŒ Ñ€Ð°Ð±Ð¾Ñ‚Ñ‹") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ð Ð°Ð±Ð¾Ñ‚Ð½Ð¸Ðº", fontWeight = FontWeight.SemiBold)
                EntityChips(workers, workerId, { it.id }, { it.name }) { workerId = it }
                Text("Ð’Ð¸Ð´ Ñ€Ð°Ð±Ð¾Ñ‚Ñ‹", fontWeight = FontWeight.SemiBold)
                EntityChips(types, typeId, { it.id }, { it.name }) { typeId = it }
                OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("Ð¡ÑƒÐ¼Ð¼Ð°") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(notes, { notes = it }, label = { Text("Ð—Ð°Ð¼ÐµÑ‚ÐºÐ¸") })
            }
        },
        confirmButton = {
            Button(onClick = { onSave(workerId, typeId, amount.toLongOrNull() ?: 0L, notes) }, enabled = workerId != 0L && typeId != 0L && (amount.toLongOrNull() ?: 0L) > 0) {
                Text("Ð”Ð¾Ð±Ð°Ð²Ð¸Ñ‚ÑŒ")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ÐžÑ‚Ð¼ÐµÐ½Ð°") } }
    )
}

@Composable
private fun WorkerDialog(worker: Worker?, onDismiss: () -> Unit, onSave: (String, String, Boolean) -> Unit) {
    var name by remember { mutableStateOf(worker?.name.orEmpty()) }
    var phone by remember { mutableStateOf(worker?.phone.orEmpty()) }
    var active by remember { mutableStateOf(worker?.isActive ?: true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (worker == null) "ÐÐ¾Ð²Ñ‹Ð¹ Ñ€Ð°Ð±Ð¾Ñ‚Ð½Ð¸Ðº" else "Ð Ð°Ð±Ð¾Ñ‚Ð½Ð¸Ðº") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Ð˜Ð¼Ñ") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Ð¢ÐµÐ»ÐµÑ„Ð¾Ð½") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ÐÐºÑ‚Ð¸Ð²ÐµÐ½", modifier = Modifier.weight(1f))
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(name, phone, active) }, enabled = name.isNotBlank()) { Text("Ð¡Ð¾Ñ…Ñ€Ð°Ð½Ð¸Ñ‚ÑŒ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ÐžÑ‚Ð¼ÐµÐ½Ð°") } }
    )
}

@Composable
private fun WorkTypeDialog(type: WorkType?, onDismiss: () -> Unit, onSave: (String, Boolean) -> Unit) {
    var name by remember { mutableStateOf(type?.name.orEmpty()) }
    var active by remember { mutableStateOf(type?.isActive ?: true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (type == null) "ÐÐ¾Ð²Ñ‹Ð¹ Ð²Ð¸Ð´ Ñ€Ð°Ð±Ð¾Ñ‚" else "Ð’Ð¸Ð´ Ñ€Ð°Ð±Ð¾Ñ‚") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("ÐÐ°Ð·Ð²Ð°Ð½Ð¸Ðµ") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ÐÐºÑ‚Ð¸Ð²ÐµÐ½", modifier = Modifier.weight(1f))
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(name, active) }, enabled = name.isNotBlank()) { Text("Ð¡Ð¾Ñ…Ñ€Ð°Ð½Ð¸Ñ‚ÑŒ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ÐžÑ‚Ð¼ÐµÐ½Ð°") } }
    )
}

@Composable
private fun ConfirmDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { Button(onClick = onConfirm) { Text("Ð”Ð°") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ÐžÑ‚Ð¼ÐµÐ½Ð°") } }
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
                }) { Text("ÐžÐš") }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("ÐžÑ‚Ð¼ÐµÐ½Ð°") } }
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

private fun android.content.Context.shareText(text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(intent, "ÐŸÐ¾Ð´ÐµÐ»Ð¸Ñ‚ÑŒÑÑ"))
}
