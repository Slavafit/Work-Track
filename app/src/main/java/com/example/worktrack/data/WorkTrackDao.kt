package com.example.worktrack.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkTrackDao {
    @Query("""
        SELECT o.id, o.address, c.name AS clientName, o.isCompleted, o.completedAt,
               COALESCE(SUM(e.amount), 0) AS totalAmount,
               COUNT(DISTINCT d.id) AS dayCount
        FROM WorkObject o
        JOIN Client c ON c.id = o.clientId
        LEFT JOIN WorkDay d ON d.objectId = o.id
        LEFT JOIN WorkEntry e ON e.workDayId = d.id
        GROUP BY o.id
        ORDER BY o.isCompleted ASC, o.id DESC
    """)
    fun objectSummaries(): Flow<List<ObjectSummary>>

    @Query("""
        SELECT d.id, d.objectId, d.date, d.notes, COALESCE(SUM(e.amount), 0) AS totalAmount,
               COUNT(DISTINCT wdw.workerId) AS workerCount, COUNT(DISTINCT e.id) AS entryCount
        FROM WorkDay d
        LEFT JOIN WorkEntry e ON e.workDayId = d.id
        LEFT JOIN WorkDayWorker wdw ON wdw.workDayId = d.id
        WHERE d.objectId = :objectId
        GROUP BY d.id
        ORDER BY d.date DESC
    """)
    fun workDays(objectId: Long): Flow<List<WorkDaySummary>>

    @Query("SELECT * FROM Worker ORDER BY isActive DESC, name")
    fun workers(): Flow<List<Worker>>

    @Query("SELECT * FROM WorkType ORDER BY isActive DESC, name")
    fun workTypes(): Flow<List<WorkType>>

    @Query("SELECT * FROM Client ORDER BY name")
    fun clients(): Flow<List<Client>>

    @Query("SELECT * FROM Worker WHERE isActive = 1 ORDER BY name")
    fun activeWorkers(): Flow<List<Worker>>

    @Query("SELECT * FROM WorkType WHERE isActive = 1 ORDER BY name")
    fun activeWorkTypes(): Flow<List<WorkType>>

    @Query("SELECT * FROM WorkObject WHERE id = :id")
    suspend fun objectById(id: Long): WorkObject?

    @Query("SELECT * FROM Client WHERE id = :id")
    suspend fun clientById(id: Long): Client?

    @Query("SELECT workerId FROM WorkDayWorker WHERE workDayId = :dayId")
    fun dayWorkerIds(dayId: Long): Flow<List<Long>>

    @Query("""
        SELECT e.id, e.workDayId, e.workerId, w.name AS workerName, e.workTypeId, t.name AS workTypeName, e.amount, e.notes
        FROM WorkEntry e
        JOIN Worker w ON w.id = e.workerId
        JOIN WorkType t ON t.id = e.workTypeId
        WHERE e.workDayId = :dayId
        ORDER BY e.id DESC
    """)
    fun entries(dayId: Long): Flow<List<EntryDetail>>

    @Insert
    suspend fun insertClient(client: Client): Long

    @Insert
    suspend fun insertObject(obj: WorkObject): Long

    @Insert
    suspend fun insertWorker(worker: Worker): Long

    @Insert
    suspend fun insertWorkType(type: WorkType): Long

    @Insert
    suspend fun insertWorkDay(day: WorkDay): Long

    @Insert
    suspend fun insertDayWorker(link: WorkDayWorker): Long

    @Insert
    suspend fun insertEntry(entry: WorkEntry): Long

    @Update
    suspend fun updateClient(client: Client)

    @Update
    suspend fun updateWorker(worker: Worker)

    @Update
    suspend fun updateWorkType(type: WorkType)

    @Update
    suspend fun updateObject(obj: WorkObject)

    @Delete
    suspend fun deleteEntry(entry: WorkEntry)

    @Query("DELETE FROM WorkEntry WHERE id = :id")
    suspend fun deleteEntryById(id: Long)

    @Query("DELETE FROM WorkDayWorker WHERE workDayId = :dayId")
    suspend fun clearDayWorkers(dayId: Long)

    @Transaction
    suspend fun createObject(address: String, selectedClientId: Long?, clientName: String, phone: String?): Long {
        val cleanName = clientName.trim()
        val cleanPhone = phone?.trim()?.ifBlank { null }
        val clientId = selectedClientId?.takeIf { it != 0L }?.also { id ->
            updateClient(Client(id = id, name = cleanName, phone = cleanPhone))
        } ?: insertClient(Client(name = cleanName, phone = cleanPhone))
        return insertObject(WorkObject(clientId = clientId, address = address.trim()))
    }

    @Transaction
    suspend fun createDay(objectId: Long, date: Long, workerIds: Set<Long>, notes: String?): Long {
        val dayId = insertWorkDay(WorkDay(objectId = objectId, date = date, notes = notes?.ifBlank { null }))
        workerIds.forEach { insertDayWorker(WorkDayWorker(workDayId = dayId, workerId = it)) }
        return dayId
    }

    @Transaction
    suspend fun setDayWorkers(dayId: Long, workerIds: Set<Long>) {
        clearDayWorkers(dayId)
        workerIds.forEach { insertDayWorker(WorkDayWorker(workDayId = dayId, workerId = it)) }
    }

    @Query("UPDATE WorkObject SET isCompleted = 1, completedAt = :completedAt WHERE id = :objectId")
    suspend fun completeObject(objectId: Long, completedAt: Long)

    @Query("""
        SELECT o.address AS objectAddress, w.name AS workerName, t.name AS workTypeName, e.amount
        FROM WorkEntry e
        JOIN WorkDay d ON d.id = e.workDayId
        JOIN WorkObject o ON o.id = d.objectId
        JOIN Worker w ON w.id = e.workerId
        JOIN WorkType t ON t.id = e.workTypeId
        WHERE d.date BETWEEN :start AND :end
        ORDER BY o.address, w.name, t.name
    """)
    suspend fun reportByDate(start: Long, end: Long): List<DateReportRow>

    @Query("""
        SELECT d.date, o.address AS objectAddress, t.name AS workTypeName, e.amount
        FROM WorkEntry e
        JOIN WorkDay d ON d.id = e.workDayId
        JOIN WorkObject o ON o.id = d.objectId
        JOIN WorkType t ON t.id = e.workTypeId
        WHERE e.workerId = :workerId AND d.date BETWEEN :start AND :end
        ORDER BY d.date DESC, o.address
    """)
    suspend fun reportByWorker(workerId: Long, start: Long, end: Long): List<WorkerReportRow>

    @Query("""
        SELECT d.date, w.name AS workerName, t.name AS workTypeName, e.amount
        FROM WorkEntry e
        JOIN WorkDay d ON d.id = e.workDayId
        JOIN Worker w ON w.id = e.workerId
        JOIN WorkType t ON t.id = e.workTypeId
        WHERE d.objectId = :objectId
        ORDER BY d.date DESC, w.name
    """)
    suspend fun reportByObject(objectId: Long): List<ObjectReportRow>
}
