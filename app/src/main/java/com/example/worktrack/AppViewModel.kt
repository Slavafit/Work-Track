package com.example.worktrack

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.LocaleList
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.worktrack.BuildConfig
import com.example.worktrack.data.AppSettings
import com.example.worktrack.data.LanguageMode
import com.example.worktrack.data.ObjectSummary
import com.example.worktrack.data.ProposalItem
import com.example.worktrack.data.ProposalItemDetail
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
import java.io.File
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
    val proposals = repo.proposals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings: StateFlow<AppSettings> = settingsStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun workDays(objectId: Long) = repo.workDays(objectId)
    fun dayWorkerIds(dayId: Long) = repo.dayWorkerIds(dayId)
    fun entries(dayId: Long) = repo.entries(dayId)
    fun dayPhotos(dayId: Long) = repo.dayPhotos(dayId)
    fun proposalItems(proposalId: Long) = repo.proposalItems(proposalId)

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

    fun updateEntry(id: Long, dayId: Long, workerId: Long, typeId: Long, amount: Long, notes: String?) = viewModelScope.launch {
        if (id != 0L && amount > 0) repo.updateEntry(id, dayId, workerId, typeId, amount, notes)
    }

    fun deleteEntry(id: Long) = viewModelScope.launch { repo.deleteEntry(id) }
    fun addDayPhotos(dayId: Long, uris: List<String>) = viewModelScope.launch {
        uris.distinct().forEach { uri -> repo.addDayPhoto(dayId, uri) }
    }
    fun deleteDayPhoto(id: Long) = viewModelScope.launch { repo.deleteDayPhoto(id) }
    fun completeObject(objectId: Long) = viewModelScope.launch { repo.completeObject(objectId) }
    fun saveProposal(proposalId: Long?, objectId: Long, items: List<ProposalItem>, onSaved: (Long) -> Unit) = viewModelScope.launch {
        if (objectId != 0L && items.isNotEmpty()) onSaved(repo.saveProposal(proposalId, objectId, items))
    }
    fun loadProposalItems(proposalId: Long, onLoaded: (List<ProposalItemDetail>) -> Unit) = viewModelScope.launch {
        onLoaded(repo.proposalItems(proposalId).first())
    }
    fun deleteProposal(id: Long) = viewModelScope.launch { repo.deleteProposal(id) }
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

    fun shareObjectReport(objectId: Long, sharePdf: (String) -> Unit, shareText: (String) -> Unit) = viewModelScope.launch {
        runCatching {
            val report = buildObjectReportShare(objectId)
            createObjectReportPdf(report.text, report.photoUris)
        }.onSuccess(sharePdf).onFailure { shareText(reportError(it)) }
    }

    private suspend fun buildObjectReportShare(objectId: Long): ObjectReportShare {
            val objectInfo = repo.objectById(objectId)
            val client = objectInfo?.let { repo.clientById(it.clientId) }
            val objectDays = repo.workDays(objectId).first()
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
            appendLine(text(R.string.report_total_format, total.reportMoney()))
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
                        val workerTotal = workerRows.sumOf { it.amount }
                        val workerHeader = if (workerRows.size == 1) {
                            "${text(R.string.report_tab_worker)}: $workerName"
                        } else {
                            "${text(R.string.report_tab_worker)}: $workerName - ${text(R.string.report_total_format, workerTotal.reportMoney())}"
                        }
                        appendLine("  $workerHeader")
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

    private fun createObjectReportPdf(text: String, photoUris: List<String>): String {
        val app = getApplication<Application>()
        val outputDir = File(app.cacheDir, "reports").apply { mkdirs() }
        val output = File(outputDir, "object-report.pdf")
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val normalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 12f
        }
        val titlePaint = Paint(normalPaint).apply {
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = margin

        fun finishPage() {
            document.finishPage(page)
        }

        fun newPage() {
            finishPage()
            pageNumber += 1
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = margin
        }

        fun drawWrappedLine(line: String, paint: Paint) {
            val maxWidth = pageWidth - margin * 2
            val words = line.split(" ")
            var current = ""
            val source = if (words.isEmpty()) listOf(line) else words
            source.forEach { word ->
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    current = candidate
                } else {
                    if (y > pageHeight - margin) newPage()
                    canvas.drawText(current, margin, y, paint)
                    y += paint.textSize + 6f
                    current = word
                }
            }
            if (current.isNotEmpty()) {
                if (y > pageHeight - margin) newPage()
                canvas.drawText(current, margin, y, paint)
                y += paint.textSize + 6f
            } else {
                y += paint.textSize + 6f
            }
        }

        text.lines().forEachIndexed { index, line ->
            val paint = if (index == 0) titlePaint else normalPaint
            if (line.isBlank()) {
                y += 10f
            } else {
                drawWrappedLine(line, paint)
            }
        }

        photoUris.forEach { uri ->
            val bitmap = app.contentResolver.openInputStream(Uri.parse(uri))?.use(BitmapFactory::decodeStream)
            if (bitmap != null) {
                newPage()
                canvas.drawBitmap(bitmap, null, fitRect(bitmap, pageWidth - margin * 2, pageHeight - margin * 2, margin, margin), null)
                bitmap.recycle()
            }
        }

        finishPage()
        output.outputStream().use(document::writeTo)
        document.close()
        return FileProvider.getUriForFile(app, "${BuildConfig.APPLICATION_ID}.fileprovider", output).toString()
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

private fun fitRect(bitmap: Bitmap, maxWidth: Float, maxHeight: Float, left: Float, top: Float): RectF {
    val scale = minOf(maxWidth / bitmap.width, maxHeight / bitmap.height)
    val width = bitmap.width * scale
    val height = bitmap.height * scale
    return RectF(left, top, left + width, top + height)
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
