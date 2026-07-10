package com.example.worktrack

import android.app.Application
import android.content.res.Configuration
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repo = WorkTrackRepository(WorkTrackDatabase.get(app).dao())
    private val settingsStore = SettingsStore(app)

    val objects = repo.objects.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val workers = repo.workers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val workTypes = repo.workTypes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val activeWorkers = repo.activeWorkers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val activeWorkTypes = repo.activeWorkTypes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings: StateFlow<AppSettings> = settingsStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private fun getLocale(): Locale {
        return when (settings.value.language) {
            LanguageMode.RU -> Locale("ru")
            LanguageMode.EN -> Locale("en")
            LanguageMode.ES -> Locale("es")
            else -> Locale.getDefault()
        }
    }

    private fun getString(resId: Int, vararg args: Any): String {
        val config = Configuration(app.resources.configuration)
        config.setLocale(getLocale())
        return app.createConfigurationContext(config).getString(resId, *args)
    }

    fun workDays(objectId: Long) = repo.workDays(objectId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun dayWorkerIds(dayId: Long) = repo.dayWorkerIds(dayId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun entries(dayId: Long) = repo.entries(dayId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createObject(address: String, clientName: String, phone: String?) = viewModelScope.launch {
        if (address.isNotBlank() && clientName.isNotBlank()) repo.createObject(address, clientName, phone)
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

    fun shareObjectReport(objectId: Long, share: (String) -> Unit) = viewModelScope.launch {
        val objectInfo = repo.objectById(objectId)
        val client = objectInfo?.let { repo.clientById(it.clientId) }
        val rows = repo.reportByObject(objectId)
        val total = rows.sumOf { it.amount }
        val locale = getLocale()
        share(buildString {
            appendLine(getString(R.string.report_object_header))
            appendLine(getString(R.string.report_address, objectInfo?.address.orEmpty()))
            appendLine(getString(R.string.report_client, client?.name.orEmpty()))
            appendLine(getString(R.string.label_total, total.money(locale)))
            appendLine()
            rows.groupBy { it.date }.forEach { (date, items) ->
                appendLine(date.formatDate(locale))
                items.forEach { appendLine(" - ${it.workerName}: ${it.workTypeName}, ${it.amount.money(locale)}") }
            }
        })
    }

    fun shareDateReport(date: Long, share: (String) -> Unit) = viewModelScope.launch {
        val rows = repo.reportByDate(date.startOfDay(), date.endOfDay())
        val locale = getLocale()
        share(buildString {
            appendLine(getString(R.string.report_date_header, date.formatDate(locale)))
            appendLine(getString(R.string.label_total, rows.sumOf { it.amount }.money(locale)))
            appendLine()
            rows.groupBy { it.objectAddress }.forEach { (objectAddress, items) ->
                appendLine(objectAddress)
                items.forEach { appendLine(" - ${it.workerName}: ${it.workTypeName}, ${it.amount.money(locale)}") }
            }
        })
    }

    fun shareWorkerReport(workerId: Long, from: Long, to: Long, share: (String) -> Unit) = viewModelScope.launch {
        val worker = workers.value.firstOrNull { it.id == workerId }
        val rows = repo.reportByWorker(workerId, from.startOfDay(), to.endOfDay())
        val locale = getLocale()
        share(buildString {
            appendLine(getString(R.string.report_worker_header, worker?.name.orEmpty()))
            appendLine(getString(R.string.report_period, from.formatDate(locale), to.formatDate(locale)))
            appendLine(getString(R.string.label_total, rows.sumOf { it.amount }.money(locale)))
            appendLine()
            rows.groupBy { it.date }.forEach { (date, items) ->
                appendLine(date.formatDate(locale))
                items.forEach { appendLine(" - ${it.objectAddress}: ${it.workTypeName}, ${it.amount.money(locale)}") }
            }
        })
    }

    private fun Long.money(locale: Locale): String = getString(R.string.money_format, this.formatNumber(locale))
}

fun Long.formatDate(locale: Locale = Locale.getDefault()): String = 
    SimpleDateFormat("dd.MM.yyyy", locale).format(Date(this))

fun Long.formatNumber(locale: Locale = Locale.getDefault()): String = 
    "%,d".format(locale, this).replace(',', ' ').replace('.', ' ')

fun todayMillis(): Long = System.currentTimeMillis().startOfDay()

fun Long.startOfDay(): Long {
    val calendar = Calendar.getInstance().apply { timeInMillis = this@startOfDay }
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

fun Long.endOfDay(): Long {
    val calendar = Calendar.getInstance().apply { timeInMillis = this@endOfDay }
    calendar.set(Calendar.HOUR_OF_DAY, 23)
    calendar.set(Calendar.MINUTE, 59)
    calendar.set(Calendar.SECOND, 59)
    calendar.set(Calendar.MILLISECOND, 999)
    return calendar.timeInMillis
}
