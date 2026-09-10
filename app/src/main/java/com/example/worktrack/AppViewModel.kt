package com.example.worktrack

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.LocaleList
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.worktrack.data.AppSettings
import com.example.worktrack.data.LanguageMode
import com.example.worktrack.data.Material
import com.example.worktrack.data.SettingsStore
import com.example.worktrack.data.ThemeMode
import com.example.worktrack.data.WorkTrackDatabase
import com.example.worktrack.data.WorkTrackRepository
import com.example.worktrack.data.WorkType
import com.example.worktrack.data.Worker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.worktrack.data.ClosedObjectException
import java.util.Locale
import com.example.worktrack.backup.BackupController
import com.example.worktrack.backup.BackupService

class AppViewModel(app: Application, savedStateHandle: SavedStateHandle) : AndroidViewModel(app) {
    private val repo = WorkTrackRepository(WorkTrackDatabase.get(app))
    private val settingsStore = SettingsStore(app)

    val proposalEditor = ProposalEditor(savedStateHandle, repo, viewModelScope, ProposalDraftStore(app))
    private val mutableSaving = MutableStateFlow(false)
    val isSaving = mutableSaving.asStateFlow()
    private val mutableError = MutableStateFlow<Int?>(null)
    val operationError = mutableError.asStateFlow()
    val backup = BackupController(app, BackupService(app, WorkTrackDatabase.get(app)), viewModelScope,
        canStart = { !mutableSaving.value && !proposalEditor.state.value.busy },
        onRestored = { proposalEditor.newDraft() })
    fun clearError() { mutableError.value = null }
    fun objectFinance(objectId: Long) = repo.objectFinance(objectId)
    fun customerPayments(objectId: Long) = repo.customerPayments(objectId)
    fun savePayment(id: Long?, objectId: Long, date: Long, amount: Long, notes: String?, onSaved: () -> Unit) = write {
        repo.savePayment(id, objectId, date, amount, notes)
        onSaved()
    }
    fun deletePayment(id: Long, objectId: Long, onSaved: () -> Unit) = write {
        repo.deletePayment(id, objectId)
        onSaved()
    }
    fun dayCompleted(dayId: Long) = repo.dayCompleted(dayId)

    private fun write(action: suspend () -> Unit) {
        if (mutableSaving.value || backup.state.value.busy || backup.state.value.preview != null) return
        mutableSaving.value = true
        viewModelScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableError.value = when (e) {
                    is ClosedObjectException -> R.string.object_read_only
                    is InvalidAmountException -> R.string.amount_total_invalid
                    else -> R.string.operation_failed
                }
            } finally {
                mutableSaving.value = false
            }
        }
    }

    val objects = repo.objects.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val clients = repo.clients.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val workers = repo.workers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val workTypes = repo.workTypes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val materials = repo.materials.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val activeWorkers = repo.activeWorkers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val activeWorkTypes = repo.activeWorkTypes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val proposals = repo.proposals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings: StateFlow<AppSettings> = settingsStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun workDays(objectId: Long) = repo.workDays(objectId)
    fun dayWorkerIds(dayId: Long) = repo.dayWorkerIds(dayId)
    fun entries(dayId: Long) = repo.entries(dayId)
    fun materialEntries(dayId: Long) = repo.materialEntries(dayId)
    fun dayPhotos(dayId: Long) = repo.dayPhotos(dayId)
    fun proposalItems(proposalId: Long) = repo.proposalItems(proposalId)
    fun proposalMaterialItems(proposalId: Long) = repo.proposalMaterialItems(proposalId)

    fun createObject(address: String, selectedClientId: Long?, clientName: String, phone: String?, onSaved: () -> Unit = {}) = write {
        require(address.isNotBlank() && clientName.isNotBlank())
        repo.createObject(address, selectedClientId, clientName, phone)
        onSaved()
    }

    fun addWorker(name: String, phone: String?) = write {
        if (name.isNotBlank()) repo.addWorker(name, phone)
    }

    fun saveWorker(worker: Worker) = write {
        if (worker.name.isNotBlank()) repo.updateWorker(worker)
    }

    fun addWorkType(name: String, onCreated: (Long) -> Unit = {}) = write {
        if (name.isNotBlank()) onCreated(repo.addWorkType(name))
    }

    fun saveWorkType(type: WorkType) = write {
        if (type.name.isNotBlank()) repo.updateWorkType(type)
    }

    fun addMaterial(name: String, onCreated: (Long) -> Unit = {}) = write {
        if (name.isNotBlank()) onCreated(repo.addMaterial(name))
    }

    fun saveMaterial(material: Material) = write {
        if (material.name.isNotBlank()) repo.updateMaterial(material)
    }

    fun createDay(objectId: Long, date: Long, workerIds: Set<Long>, notes: String?, onCreated: (Long) -> Unit) = write {
        if (workerIds.isNotEmpty()) onCreated(repo.createDay(objectId, date, workerIds, notes))
    }

    fun addEntry(dayId: Long, workerId: Long, typeId: Long, amount: Long, notes: String?, onSaved: () -> Unit = {}) = write {
        require(amount >= 0)
        repo.addEntry(dayId, workerId, typeId, amount, notes)
        onSaved()
    }

    fun updateEntry(id: Long, dayId: Long, workerId: Long, typeId: Long, amount: Long, notes: String?, onSaved: () -> Unit = {}) = write {
        require(id != 0L && amount >= 0)
        repo.updateEntry(id, dayId, workerId, typeId, amount, notes)
        onSaved()
    }

    fun deleteEntry(id: Long) = write { repo.deleteEntry(id) }
    fun addMaterialEntry(dayId: Long, workerId: Long, materialId: Long, amount: Long, notes: String?, onSaved: () -> Unit = {}) = write {
        require(amount >= 0)
        repo.addMaterialEntry(dayId, workerId, materialId, amount, notes)
        onSaved()
    }
    fun updateMaterialEntry(id: Long, dayId: Long, workerId: Long, materialId: Long, amount: Long, notes: String?, onSaved: () -> Unit = {}) = write {
        require(id != 0L && amount >= 0)
        repo.updateMaterialEntry(id, dayId, workerId, materialId, amount, notes)
        onSaved()
    }
    fun deleteMaterialEntry(id: Long) = write { repo.deleteMaterialEntry(id) }
    fun addDayPhotos(dayId: Long, uris: List<String>) = write {
        uris.distinct().forEach { uri -> repo.addDayPhoto(dayId, uri) }
    }
    fun deleteDayPhoto(id: Long) = write { repo.deleteDayPhoto(id) }
    fun completeObject(objectId: Long) = write { repo.completeObject(objectId) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsStore.setTheme(mode) }
    fun setLanguage(language: LanguageMode) = viewModelScope.launch { settingsStore.setLanguage(language) }
    fun setCompanyName(name: String) = viewModelScope.launch { settingsStore.setCompanyName(name) }

    private fun text(id: Int, vararg args: Any): String {
        val app = getApplication<Application>()
        return runCatching { app.localized(settings.value.language).getString(id, *args) }
            .getOrElse { app.getString(id, *args) }
    }

    private fun reportLocale(): Locale = when (settings.value.language) {
        LanguageMode.System -> Locale.getDefault()
        LanguageMode.RU -> Locale("ru")
        LanguageMode.EN -> Locale("en")
        LanguageMode.ES -> Locale("es")
    }

    private fun Long.reportDate(): String = formatDate(reportLocale())

    private fun Long.reportMoney(): String = money(reportLocale())

    fun shareObjectReport(objectId: Long, share: (String, List<String>) -> Unit) = viewModelScope.launch {
        runCatching {
            buildObjectReportShare(objectId)
        }.onSuccess { share(it.text, it.photoUris) }.onFailure { share(reportError(it), emptyList()) }
    }

    private suspend fun buildObjectReportShare(objectId: Long): ObjectReportShare {
            val objectInfo = repo.objectById(objectId)
            val client = objectInfo?.let { repo.clientById(it.clientId) }
            val objectDays = repo.workDays(objectId).first()
            val finance = repo.objectFinance(objectId).first()
            val rows = repo.reportByObject(objectId)
            val photos = repo.photosByObject(objectId)
            val availablePhotoUris = photos.map { it.uri }.filter(::photoUriAvailable).distinct()
            val total = rows.sumOf { it.amount }
            val rowsByDay = rows.groupBy { it.workDayId }
            val availablePhotoCount = photos.count { photoUriAvailable(it.uri) }
            val missingPhotoCount = photos.size - availablePhotoCount
            val text = buildString {
            appendLine(text(R.string.report_object_title))
            settings.value.companyName.trim().takeIf { it.isNotEmpty() }?.let {
                appendLine(text(R.string.report_company_format, it))
            }
            appendLine(text(R.string.report_address_format, objectInfo?.address.orEmpty()))
            appendLine(text(R.string.report_customer_format, client?.name.orEmpty()))
            appendLine(text(R.string.object_total_days_format, total.reportMoney(), objectDays.size))
            appendLine(text(R.string.finance_work, finance.workAmount.reportMoney()))
            appendLine(text(R.string.finance_materials, finance.materialAmount.reportMoney()))
            appendLine(text(R.string.finance_paid, finance.paidAmount.reportMoney()))
            appendLine(text(if (finance.balance < 0) R.string.finance_credit else R.string.finance_due,
                (if (finance.balance < 0) -finance.balance else finance.balance).reportMoney()))
            appendLine(text(R.string.finance_basis))
            appendLine()
            if (objectDays.isEmpty()) {
                appendLine(text(R.string.empty_entries))
            }
            objectDays.forEach { day ->
                val dayRows = rowsByDay[day.id].orEmpty()
                appendLine("${day.date.reportDate()} - ${text(R.string.report_total_format, dayRows.sumOf { it.amount }.reportMoney())}")
                if (dayRows.isEmpty()) {
                    appendLine("  ${text(R.string.empty_entries)}")
                } else {
                    dayRows.groupBy { it.workerId }.values.forEach { workerRows ->
                        val workerName = workerRows.first().workerName
                        appendLine("  ${text(R.string.report_tab_worker)}: $workerName")
                        workerRows.forEach { row ->
                            val notes = row.notes?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                            appendLine("    - ${row.workTypeName}: ${row.amount.reportMoney()}$notes")
                        }
                    }
                }
                appendLine()
            }
            if (photos.isNotEmpty()) {
                appendLine(text(R.string.report_photos_title))
                appendLine(text(R.string.report_photos_attached_format, availablePhotoCount))
                if (missingPhotoCount > 0) {
                    appendLine(text(R.string.report_photos_missing_format, missingPhotoCount))
                }
            }
            }
            return ObjectReportShare(text, availablePhotoUris)
    }

    fun shareDateReport(date: Long, share: (String) -> Unit) = viewModelScope.launch {
        runCatching {
            val rows = repo.reportByDate(date.startOfDay(), date.endOfDay())
            buildString {
            appendLine(text(R.string.report_date_title_format, date.reportDate()))
            appendLine(text(R.string.report_total_format, rows.sumOf { it.amount }.reportMoney()))
            appendLine()
            rows.groupBy { it.objectAddress }.forEach { (objectAddress, items) ->
                appendLine(objectAddress)
                items.forEach { appendLine(" - ${it.workerName}: ${it.workTypeName}, ${it.amount.reportMoney()}") }
            }
            }
        }.onSuccess(share).onFailure { share(reportError(it)) }
    }

    fun shareWorkerReport(workerId: Long, from: Long, to: Long, share: (String) -> Unit) = viewModelScope.launch {
        runCatching {
            val worker = workers.value.firstOrNull { it.id == workerId }
            val rows = repo.reportByWorker(workerId, from.startOfDay(), to.endOfDay())
            buildString {
            appendLine(text(R.string.report_worker_title_format, worker?.name.orEmpty()))
            appendLine(text(R.string.report_period_format, from.reportDate(), to.reportDate()))
            appendLine(text(R.string.report_total_format, rows.sumOf { it.amount }.reportMoney()))
            appendLine()
            rows.groupBy { it.date }.forEach { (date, items) ->
                appendLine(date.reportDate())
                items.forEach { appendLine(" - ${it.objectAddress}: ${it.workTypeName}, ${it.amount.reportMoney()}") }
            }
            }
        }.onSuccess(share).onFailure { share(reportError(it)) }
    }

    private fun reportError(error: Throwable): String =
        "Report error: ${error.message ?: error::class.java.simpleName}"

    private fun photoUriAvailable(uri: String): Boolean {
        val app = getApplication<Application>()
        return runCatching {
            app.contentResolver.openInputStream(Uri.parse(uri))?.use { true } == true
        }.getOrDefault(false)
    }
}

private data class ObjectReportShare(
    val text: String,
    val photoUris: List<String>
)

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
