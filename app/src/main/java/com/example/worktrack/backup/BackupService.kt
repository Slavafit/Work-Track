package com.example.worktrack.backup

import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.room.Room
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.worktrack.checkedAmountTotal
import com.example.worktrack.data.WorkTrackDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class InvalidBackupException : IOException("Invalid or unsupported WorkTrack backup")
class BackupLimitException : IOException("Backup size limit exceeded")

data class BackupSummary(val createdAt: Long, val objects: Int, val days: Int, val proposals: Int, val photos: Int, val missingPhotos: Int, val payments: Int = 0)

class PreparedBackup internal constructor(
    internal val directory: File,
    internal val manifest: JSONObject,
    internal val photos: Map<Long, File>,
    val summary: BackupSummary
) : Closeable {
    override fun close() { directory.deleteRecursively() }
}

/** Versioned data archive, never a live SQLite-file copy. License and device settings are excluded. */
class BackupService(private val context: Context, private val db: WorkTrackDatabase, private val media: BackupMedia = AndroidBackupMedia(context)) {
    private data class Column(val name: String, val type: String, val required: Boolean)

    suspend fun export(output: OutputStream): BackupSummary = withContext(Dispatchers.IO) { archiveMutex.withLock {
        val manifest = db.withTransaction {
            val sql = db.openHelper.writableDatabase
            JSONObject().put("format", FORMAT).put("version", 4).put("databaseVersion", 9).put("moneyUnit", "EUR_CENT")
                .put("createdAt", System.currentTimeMillis()).put("tables", JSONObject().apply {
                    TABLES.forEach { name ->
                        val rows = JSONArray()
                        sql.query("SELECT * FROM `$name` ORDER BY id").use { cursor ->
                            while (cursor.moveToNext()) {
                                if (rows.length() >= MAX_ROWS) throw BackupLimitException()
                                rows.put(JSONArray().apply {
                                    for (i in 0 until cursor.columnCount) put(when (cursor.getType(i)) {
                                        Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                                        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                                        Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
                                        else -> throw InvalidBackupException()
                                    })
                                })
                            }
                            put(name, JSONObject().put("columns", JSONArray(cursor.columnNames.toList())).put("rows", rows))
                        }
                    }
                })
        }
        val temp = File.createTempFile("worktrack-export-", ".zip", context.cacheDir)
        try {
            val photoTable = manifest.getJSONObject("tables").getJSONObject("WorkDayPhoto")
            val uriIndex = columnIndex(photoTable, "uri")
            val idIndex = columnIndex(photoTable, "id")
            val photoRows = photoTable.getJSONArray("rows")
            val photoIndex = JSONArray()
            val budget = Budget()
            ZipOutputStream(temp.outputStream().buffered()).use { zip ->
                for (i in 0 until photoRows.length()) {
                    val row = photoRows.getJSONArray(i)
                    val id = row.getLong(idIndex)
                    val uri = Uri.parse(row.getString(uriIndex))
                    // Imported archives never retain access to arbitrary source-device URIs.
                    row.put(uriIndex, "")
                    val source = try {
                        media.open(uri)
                    } catch (_: FileNotFoundException) { null } catch (_: SecurityException) { null }
                    if (source == null) continue
                    source.use {
                        if (photoIndex.length() >= MAX_FILES - 1) throw BackupLimitException()
                        val extension = media.extension(uri)
                        val name = "photos/$id.$extension"
                        zip.putNextEntry(ZipEntry(name))
                        val digest = MessageDigest.getInstance("SHA-256")
                        val size = copyBounded(it, zip, MAX_PHOTO, budget, digest)
                        zip.closeEntry()
                        photoIndex.put(JSONObject().put("id", id).put("file", name).put("size", size).put("sha256", hex(digest.digest())))
                    }
                }
                manifest.put("photos", photoIndex)
                val json = manifest.toString().toByteArray(Charsets.UTF_8)
                if (json.size > MAX_MANIFEST || json.size > budget.remaining) throw BackupLimitException()
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(json)
                zip.closeEntry()
            }
            temp.inputStream().use { it.copyTo(output) }
            output.flush()
            summary(manifest)
        } finally {
            temp.delete()
        }
    } }

    /** Extract into private staging, validate every row in a separate Room database, then offer preview. */
    suspend fun prepare(input: InputStream): PreparedBackup = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "worktrack-import-${UUID.randomUUID()}")
        check(directory.mkdirs())
        try {
            var manifest: JSONObject? = null
            val files = mutableMapOf<String, File>()
            val names = mutableSetOf<String>()
            val budget = Budget()
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!names.add(entry.name) || entry.isDirectory || names.size > MAX_FILES) throw InvalidBackupException()
                    if (entry.name == "manifest.json") {
                        val bytes = ByteArrayOutputStream()
                        copyBounded(zip, bytes, MAX_MANIFEST.toLong(), budget)
                        manifest = JSONObject(bytes.toString("UTF-8"))
                    } else {
                        if (!PHOTO_NAME.matches(entry.name)) throw InvalidBackupException()
                        // Do not concatenate an archive path with an output directory.
                        val target = File(directory, "${files.size}.photo")
                        target.outputStream().use { copyBounded(zip, it, MAX_PHOTO, budget) }
                        files[entry.name] = target
                    }
                    zip.closeEntry()
                }
            }
            val data = manifest ?: throw InvalidBackupException()
            if (data.getString("format") != FORMAT) throw InvalidBackupException()
            if (data.get("version") == 1 && data.get("databaseVersion") == 6) {
                listOf("WorkEntry", "WorkMaterialEntry", "ProposalItem", "ProposalMaterialItem").forEach { name ->
                    val table = data.getJSONObject("tables").getJSONObject(name)
                    val amountIndex = columnIndex(table, "amount")
                    val rows = table.getJSONArray("rows")
                    for (i in 0 until rows.length()) {
                        val row = rows.getJSONArray(i)
                        val value = row.get(amountIndex)
                        if (value !is Int && value !is Long) throw InvalidBackupException()
                        val euros = (value as Number).toLong()
                        if (euros < 0 || euros > Long.MAX_VALUE / 100) throw InvalidBackupException()
                        row.put(amountIndex, euros * 100)
                    }
                }
                data.put("version", 2).put("databaseVersion", 7).put("moneyUnit", "EUR_CENT")
            }
            if (data.get("version") == 2 && data.get("databaseVersion") == 7 && data.optString("moneyUnit") == "EUR_CENT") {
                val tables = data.getJSONObject("tables")
                if (tables.has("CustomerPayment")) throw InvalidBackupException()
                tables.put("CustomerPayment", JSONObject().put("columns", JSONArray(listOf("id", "objectId", "date", "amount", "notes"))).put("rows", JSONArray()))
                data.put("version", 3).put("databaseVersion", 8)
            }
            if (data.get("version") == 3 && data.get("databaseVersion") == 8 && data.optString("moneyUnit") == "EUR_CENT") {
                listOf("WorkEntry", "WorkMaterialEntry").forEach { name ->
                    val table = data.getJSONObject("tables").getJSONObject(name)
                    val columns = table.getJSONArray("columns")
                    if ((0 until columns.length()).any { columns.getString(it) == "isAmountPending" }) throw InvalidBackupException()
                    columns.put("isAmountPending")
                    val rows = table.getJSONArray("rows")
                    for (i in 0 until rows.length()) rows.getJSONArray(i).put(0)
                }
                data.put("version", 4).put("databaseVersion", 9)
            } else if (data.get("version") != 4 || data.get("databaseVersion") != 9 || data.optString("moneyUnit") != "EUR_CENT") {
                throw InvalidBackupException()
            }
            val createdAt = data.get("createdAt")
            if ((createdAt !is Int && createdAt !is Long) || (createdAt as Number).toLong() <= 0) throw InvalidBackupException()
            val indexedPhotos = mutableMapOf<Long, File>()
            val photoIndex = data.getJSONArray("photos")
            for (i in 0 until photoIndex.length()) {
                val photo = photoIndex.getJSONObject(i)
                val id = photo.getLong("id")
                val name = photo.getString("file")
                if (!PHOTO_NAME.matches(name) || name.substringAfter('/').substringBefore('.').toLong() != id) throw InvalidBackupException()
                val file = files.remove(name) ?: throw InvalidBackupException()
                if (indexedPhotos.put(id, file) != null || file.length() != photo.getLong("size") || sha256(file) != photo.getString("sha256")) throw InvalidBackupException()
            }
            if (files.isNotEmpty()) throw InvalidBackupException()
            val table = data.getJSONObject("tables").getJSONObject("WorkDayPhoto")
            val rows = table.getJSONArray("rows")
            val idIndex = columnIndex(table, "id")
            val uriIndex = columnIndex(table, "uri")
            val photoIds = mutableSetOf<Long>()
            for (i in 0 until rows.length()) {
                photoIds.add(rows.getJSONArray(i).getLong(idIndex))
                rows.getJSONArray(i).put(uriIndex, "")
            }
            if (!photoIds.containsAll(indexedPhotos.keys)) throw InvalidBackupException()
            val validationDb = Room.inMemoryDatabaseBuilder(context, WorkTrackDatabase::class.java).build()
            try {
                validationDb.withTransaction { replaceTables(validationDb.openHelper.writableDatabase, data) }
            } finally { validationDb.close() }
            PreparedBackup(directory, data, indexedPhotos, summary(data))
        } catch (e: Exception) {
            directory.deleteRecursively()
            when (e) {
                is BackupLimitException -> throw e
                is kotlinx.coroutines.CancellationException -> throw e
                else -> throw InvalidBackupException().apply { initCause(e) }
            }
        }
    }

    /** New photos exist before committing; an import failure rolls back all working records. */
    suspend fun restore(prepared: PreparedBackup) = withContext(Dispatchers.IO + NonCancellable) { archiveMutex.withLock {
        check(prepared.directory.isDirectory)
        val root = File(context.filesDir, "restored_photos")
        val destination = File(root, UUID.randomUUID().toString())
        check(destination.mkdirs())
        var committed = false
        try {
            val manifest = JSONObject(prepared.manifest.toString())
            val index = manifest.getJSONArray("photos")
            val uris = mutableMapOf<Long, String>()
            for (i in 0 until index.length()) {
                val info = index.getJSONObject(i)
                val id = info.getLong("id")
                val source = prepared.photos[id] ?: throw InvalidBackupException()
                if (source.length() != info.getLong("size") || sha256(source) != info.getString("sha256")) throw InvalidBackupException()
                val extension = info.getString("file").substringAfterLast('.')
                val target = File(destination, "$id.$extension")
                source.copyTo(target)
                uris[id] = media.uriFor(target).toString()
            }
            val table = manifest.getJSONObject("tables").getJSONObject("WorkDayPhoto")
            val rows = table.getJSONArray("rows")
            val uriIndex = columnIndex(table, "uri")
            val idIndex = columnIndex(table, "id")
            for (i in 0 until rows.length()) {
                val row = rows.getJSONArray(i)
                row.put(uriIndex, uris[row.getLong(idIndex)] ?: "")
            }
            db.withTransaction { replaceTables(db.openHelper.writableDatabase, manifest) }
            committed = true
            // Only application-owned media from previous restores is removed, never gallery files.
            root.listFiles()?.filter { it != destination }?.forEach { runCatching { it.deleteRecursively() } }
        } finally {
            if (!committed) destination.deleteRecursively()
            prepared.close()
        }
    } }

    private fun replaceTables(sql: SupportSQLiteDatabase, manifest: JSONObject) {
        val tables = manifest.getJSONObject("tables")
        if (tables.keys().asSequence().toSet() != TABLES.toSet()) throw InvalidBackupException()
        TABLES.asReversed().forEach { name -> sql.execSQL("DELETE FROM `$name`") }
        TABLES.forEach { name ->
            val table = tables.getJSONObject(name)
            val schema = mutableListOf<Column>()
            sql.query("PRAGMA table_info(`$name`)").use { cursor ->
                while (cursor.moveToNext()) schema.add(Column(cursor.getString(1), cursor.getString(2), cursor.getInt(3) != 0 || cursor.getInt(5) != 0))
            }
            val columns = table.getJSONArray("columns")
            if (columns.length() != schema.size || schema.indices.any { columns.getString(it) != schema[it].name }) throw InvalidBackupException()
            val rows = table.getJSONArray("rows")
            if (rows.length() > MAX_ROWS) throw BackupLimitException()
            sql.execSQL("DELETE FROM sqlite_sequence WHERE name = ?", arrayOf(name))
            val insert = "INSERT INTO `$name` (${schema.joinToString { "`${it.name}`" }}) VALUES (${schema.joinToString { "?" }})"
            for (i in 0 until rows.length()) {
                val row = rows.getJSONArray(i)
                if (row.length() != schema.size) throw InvalidBackupException()
                val values = schema.mapIndexed { j, column ->
                    val value = row.get(j)
                    when {
                        value == JSONObject.NULL -> { if (column.required) throw InvalidBackupException(); null }
                        column.type == "INTEGER" && (value is Int || value is Long) -> {
                            val number = (value as Number).toLong()
                            if ((column.name == "id" || column.name.endsWith("Id")) && number <= 0) throw InvalidBackupException()
                            if (column.name.startsWith("is") && number !in 0L..1L) throw InvalidBackupException()
                            if (column.name == "amount" && number < 0) throw InvalidBackupException()
                            number
                        }
                        column.type == "TEXT" && value is String -> value
                        else -> throw InvalidBackupException()
                    }
                }.toTypedArray()
                sql.execSQL(insert, values)
            }
        }
        sql.query("PRAGMA foreign_key_check").use { if (it.moveToFirst()) throw InvalidBackupException() }
        sql.query("""
            SELECT 1 FROM WorkEntry e WHERE NOT EXISTS (SELECT 1 FROM WorkDayWorker w WHERE w.workDayId = e.workDayId AND w.workerId = e.workerId)
            UNION ALL
            SELECT 1 FROM WorkMaterialEntry e WHERE NOT EXISTS (SELECT 1 FROM WorkDayWorker w WHERE w.workDayId = e.workDayId AND w.workerId = e.workerId)
            UNION ALL
            SELECT 1 FROM WorkDayWorker GROUP BY workDayId, workerId HAVING COUNT(*) > 1
        """.trimIndent()).use { if (it.moveToFirst()) throw InvalidBackupException() }
        sql.query("SELECT 1 FROM CustomerPayment WHERE amount <= 0 OR date <= 0 LIMIT 1").use { if (it.moveToFirst()) throw InvalidBackupException() }
        sql.query("SELECT SUM(amount) FROM CustomerPayment GROUP BY objectId").use { while (it.moveToNext()) it.getLong(0) }
        sql.query("SELECT 1 FROM WorkEntry WHERE isAmountPending=1 AND amount!=0 UNION ALL SELECT 1 FROM WorkMaterialEntry WHERE isAmountPending=1 AND amount!=0").use { if (it.moveToFirst()) throw InvalidBackupException() }
        val amounts = mutableListOf<Long>()
        sql.query("SELECT amount FROM WorkEntry UNION ALL SELECT amount FROM WorkMaterialEntry").use { while (it.moveToNext()) amounts.add(it.getLong(0)) }
        if (checkedAmountTotal(amounts) == null) throw InvalidBackupException()
        // GROUP BY SUM also rejects overflow in any individual saved proposal.
        sql.query("SELECT proposalId, SUM(amount) FROM (SELECT proposalId, amount FROM ProposalItem UNION ALL SELECT proposalId, amount FROM ProposalMaterialItem) GROUP BY proposalId").use { while (it.moveToNext()) it.getLong(1) }
    }

    private fun summary(data: JSONObject): BackupSummary {
        val tables = data.getJSONObject("tables")
        fun count(name: String) = tables.getJSONObject(name).getJSONArray("rows").length()
        val included = data.getJSONArray("photos").length()
        return BackupSummary(data.getLong("createdAt"), count("WorkObject"), count("WorkDay"), count("Proposal"), included, count("WorkDayPhoto") - included, count("CustomerPayment"))
    }

    private fun columnIndex(table: JSONObject, name: String): Int {
        val columns = table.getJSONArray("columns")
        return (0 until columns.length()).firstOrNull { columns.getString(it) == name } ?: throw InvalidBackupException()
    }

    private class Budget(var remaining: Long = MAX_TOTAL)
    private fun copyBounded(input: InputStream, output: OutputStream, limit: Long, budget: Budget, digest: MessageDigest? = null): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            budget.remaining -= read
            if (total > limit || budget.remaining < 0) throw BackupLimitException()
            digest?.update(buffer, 0, read)
            output.write(buffer, 0, read)
        }
        return total
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val n = input.read(buffer); if (n == -1) break; digest.update(buffer, 0, n) }
        }
        return hex(digest.digest())
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    companion object {
        private val archiveMutex = Mutex()
        private const val FORMAT = "worktrack-backup"
        private const val MAX_MANIFEST = 16 * 1024 * 1024
        private const val MAX_PHOTO = 32L * 1024 * 1024
        private const val MAX_TOTAL = 512L * 1024 * 1024
        private const val MAX_FILES = 10_001
        private const val MAX_ROWS = 100_000
        private val PHOTO_NAME = Regex("photos/[1-9][0-9]{0,18}\\.(jpg|png|webp|gif|heic|heif)")
        private val TABLES = listOf("Client", "Worker", "WorkType", "Material", "WorkObject", "CustomerPayment", "WorkDay", "WorkDayWorker", "WorkEntry", "WorkMaterialEntry", "WorkDayPhoto", "Proposal", "ProposalItem", "ProposalMaterialItem")
    }
}
