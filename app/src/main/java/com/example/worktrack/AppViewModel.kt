package com.example.worktrack

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.worktrack.data.AppSettings
import com.example.worktrack.data.LanguageMode
import com.example.worktrack.data.ObjectSummary
import com.example.worktrack.data.SettingsStore
import com.example.worktrack.data.ThemeMode
import com.example.worktrack.data.WorkTrackDatabase
import com.example.worktrack.data.WorkTrackRepository
import com.example.worktrack.data.WorkType
import com.example.worktrack.data.Worker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = WorkTrackRepository(WorkTrackDatabase.get(app).dao())
    private val settingsStore = SettingsStore(app)

    val objects = repo.objects.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val clients = repo.clients.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val workers = repo.workers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val workTypes = repo.workTypes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val activeWorkers = repo.activeWorkers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val activeWorkTypes = repo.activeWorkTypes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings: StateFlow<AppSettings> = settingsStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun workDays(objectId: Long) = repo.workDays(objectId)
    fun dayWorkerIds(dayId: Long) = repo.dayWorkerIds(dayId)
    fun entries(dayId: Long) = repo.entries(dayId)

    fun createObject(address: String, selectedClientId: Long?, clientName: String, phone: String?) = viewModelScope.launch {
        if (address.isNotBlank() && clientName.isNotBlank()) repo.createObject(address, selectedClientId, clientName, phone)
    }

    fun addWorker(name: String, phone: String?) = viewModelScope.launch {
        if (name.isNotBlank()) repo.addWorker(name, phone)
    }

    fun saveWorker(worker: Worker) = viewModelScope.launch {
        if (worker.name.isNotBlank()) repo.updateWorker(worker)
    }

    fun addWorkType(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) repo.addWorkType(name)
    }

    fun saveWorkType(type: WorkType) = viewModelScope.launch {
        if (type.name.isNotBlank()) repo.updateWorkType(type)
    }

    fun createDay(objectId: Long, date: Long, workerIds: Set<Long>, notes: String?, onCreated: (Long) -> Unit) = viewModelScope.launch {
        if (workerIds.isNotEmpty()) onCreated(repo.createDay(objectId, date, workerIds, notes))
    }

    fun addEntry(dayId: Long, workerId: Long, typeId: Long, amount: Long, notes: String?) = viewModelScope.launch {
        if (amount > 0) repo.addEntry(dayId, workerId, typeId, amount, notes)
    }

    fun deleteEntry(id: Long) = viewModelScope.launch { repo.deleteEntry(id) }
    fun completeObject(objectId: Long) = viewModelScope.launch { repo.completeObject(objectId) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsStore.setTheme(mode) }
    fun setLanguage(language: LanguageMode) = viewModelScope.launch { settingsStore.setLanguage(language) }

    private fun text(id: Int, vararg args: Any): String =
        getApplication<Application>().localized(settings.value.language).getString(id, *args)

    fun shareObjectReport(objectId: Long, share: (String) -> Unit) = viewModelScope.launch {
        val objectInfo = repo.objectById(objectId)
        val client = objectInfo?.let { repo.clientById(it.clientId) }
        val rows = repo.reportByObject(objectId)
        val total = rows.sumOf { it.amount }
        share(buildString {
            appendLine(text(R.string.report_object_title))
            appendLine(text(R.string.report_address_format, objectInfo?.address.orEmpty()))
            appendLine(text(R.string.report_customer_format, client?.name.orEmpty()))
            appendLine(text(R.string.report_total_format, total.money()))
            appendLine()
            rows.groupBy { it.date }.forEach { (date, items) ->
                appendLine(date.formatDate())
                items.forEach { appendLine(" - ${it.workerName}: ${it.workTypeName}, ${it.amount.money()}") }
            }
        })
    }

    fun shareDateReport(date: Long, share: (String) -> Unit) = viewModelScope.launch {
        val rows = repo.reportByDate(date.startOfDay(), date.endOfDay())
        share(buildString {
            appendLine(text(R.string.report_date_title_format, date.formatDate()))
            appendLine(text(R.string.report_total_format, rows.sumOf { it.amount }.money()))
            appendLine()
            rows.groupBy { it.objectAddress }.forEach { (objectAddress, items) ->
                appendLine(objectAddress)
                items.forEach { appendLine(" - ${it.workerName}: ${it.workTypeName}, ${it.amount.money()}") }
            }
        })
    }

    fun shareWorkerReport(workerId: Long, from: Long, to: Long, share: (String) -> Unit) = viewModelScope.launch {
        val worker = workers.value.firstOrNull { it.id == workerId }
        val rows = repo.reportByWorker(workerId, from.startOfDay(), to.endOfDay())
        share(buildString {
            appendLine(text(R.string.report_worker_title_format, worker?.name.orEmpty()))
            appendLine(text(R.string.report_period_format, from.formatDate(), to.formatDate()))
            appendLine(text(R.string.report_total_format, rows.sumOf { it.amount }.money()))
            appendLine()
            rows.groupBy { it.date }.forEach { (date, items) ->
                appendLine(date.formatDate())
                items.forEach { appendLine(" - ${it.objectAddress}: ${it.workTypeName}, ${it.amount.money()}") }
            }
        })
    }
}

private fun Context.localized(language: LanguageMode): Context {
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
