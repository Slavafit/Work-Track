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
        SELECT
          COALESCE((SELECT SUM(e.amount) FROM WorkEntry e JOIN WorkDay d ON d.id=e.workDayId WHERE d.objectId=:objectId),0) AS workAmount,
          COALESCE((SELECT SUM(e.amount) FROM WorkMaterialEntry e JOIN WorkDay d ON d.id=e.workDayId WHERE d.objectId=:objectId),0) AS materialAmount,
          COALESCE((SELECT SUM(amount) FROM CustomerPayment WHERE objectId=:objectId),0) AS paidAmount
    """)
    fun objectFinance(objectId: Long): Flow<ObjectFinance>

    @Query("SELECT * FROM CustomerPayment WHERE objectId=:objectId ORDER BY date DESC, id DESC")
    fun customerPayments(objectId: Long): Flow<List<CustomerPayment>>

    @Query("SELECT COALESCE(SUM(amount),0) FROM CustomerPayment WHERE objectId=:objectId AND id!=:excludedId")
    suspend fun paymentTotal(objectId: Long, excludedId: Long): Long

    @Query("SELECT * FROM CustomerPayment WHERE id=:id")
    suspend fun paymentById(id: Long): CustomerPayment?

    @Insert
    suspend fun insertPayment(payment: CustomerPayment): Long

    @Update
    suspend fun updatePayment(payment: CustomerPayment)

    @Query("DELETE FROM CustomerPayment WHERE id=:id")
    suspend fun deletePayment(id: Long)

    @Query("""
        SELECT o.id, o.address, c.name AS clientName, o.isCompleted, o.completedAt,
               COALESCE(totals.totalAmount, 0) AS totalAmount,
               COALESCE(days.dayCount, 0) AS dayCount
        FROM WorkObject o
        JOIN Client c ON c.id = o.clientId
        LEFT JOIN (
            SELECT d.objectId, SUM(x.amount) AS totalAmount
            FROM WorkDay d
            JOIN (
                SELECT workDayId, amount FROM WorkEntry
                UNION ALL
                SELECT workDayId, amount FROM WorkMaterialEntry
            ) x ON x.workDayId = d.id
            GROUP BY d.objectId
        ) totals ON totals.objectId = o.id
        LEFT JOIN (
            SELECT objectId, COUNT(id) AS dayCount
            FROM WorkDay
            GROUP BY objectId
        ) days ON days.objectId = o.id
        ORDER BY o.isCompleted ASC, o.id DESC
    """)
    fun objectSummaries(): Flow<List<ObjectSummary>>

    @Query("""
        SELECT d.id, d.objectId, d.date, d.notes,
               COALESCE(entries.totalAmount, 0) AS totalAmount,
               COALESCE(workers.workerCount, 0) AS workerCount,
               COALESCE(entries.entryCount, 0) AS entryCount
        FROM WorkDay d
        LEFT JOIN (
            SELECT workDayId, SUM(amount) AS totalAmount, COUNT(*) AS entryCount
            FROM (
                SELECT workDayId, amount FROM WorkEntry
                UNION ALL
                SELECT workDayId, amount FROM WorkMaterialEntry
            )
            GROUP BY workDayId
        ) entries ON entries.workDayId = d.id
        LEFT JOIN (
            SELECT workDayId, COUNT(workerId) AS workerCount
            FROM WorkDayWorker
            GROUP BY workDayId
        ) workers ON workers.workDayId = d.id
        WHERE d.objectId = :objectId
        ORDER BY d.date DESC
    """)
    fun workDays(objectId: Long): Flow<List<WorkDaySummary>>

    @Query("SELECT * FROM Worker ORDER BY isActive DESC, name")
    fun workers(): Flow<List<Worker>>

    @Query("SELECT * FROM WorkType ORDER BY isActive DESC, name")
    fun workTypes(): Flow<List<WorkType>>

    @Query("SELECT * FROM Material ORDER BY isActive DESC, name")
    fun materials(): Flow<List<Material>>

    @Query("SELECT * FROM Client ORDER BY name")
    fun clients(): Flow<List<Client>>

    @Query("SELECT * FROM Worker WHERE isActive = 1 ORDER BY name")
    fun activeWorkers(): Flow<List<Worker>>

    @Query("SELECT * FROM WorkType WHERE isActive = 1 ORDER BY name")
    fun activeWorkTypes(): Flow<List<WorkType>>

    @Query("""
        SELECT p.id, p.objectId, o.address, c.name AS clientName, p.updatedAt,
               COALESCE(services.totalAmount, 0) + COALESCE(materials.totalAmount, 0) AS totalAmount,
               COALESCE(services.itemCount, 0) + COALESCE(materials.itemCount, 0) AS itemCount
        FROM Proposal p
        JOIN WorkObject o ON o.id = p.objectId
        JOIN Client c ON c.id = o.clientId
        LEFT JOIN (
            SELECT proposalId, SUM(amount) AS totalAmount, COUNT(id) AS itemCount
            FROM ProposalItem GROUP BY proposalId
        ) services ON services.proposalId = p.id
        LEFT JOIN (
            SELECT proposalId, SUM(amount) AS totalAmount, COUNT(id) AS itemCount
            FROM ProposalMaterialItem GROUP BY proposalId
        ) materials ON materials.proposalId = p.id
        ORDER BY p.updatedAt DESC
    """)
    fun proposals(): Flow<List<ProposalSummary>>

    @Query("""
        SELECT i.id, i.proposalId, i.workTypeId, t.name AS workTypeName, i.amount
        FROM ProposalItem i
        JOIN WorkType t ON t.id = i.workTypeId
        WHERE i.proposalId = :proposalId
        ORDER BY i.id
    """)
    fun proposalItems(proposalId: Long): Flow<List<ProposalItemDetail>>

    @Query("""
        SELECT i.id, i.proposalId, i.materialId, m.name AS materialName, i.amount
        FROM ProposalMaterialItem i
        JOIN Material m ON m.id = i.materialId
        WHERE i.proposalId = :proposalId
        ORDER BY i.id
    """)
    fun proposalMaterialItems(proposalId: Long): Flow<List<ProposalMaterialItemDetail>>

    @Query("SELECT * FROM WorkObject WHERE id = :id")
    suspend fun objectById(id: Long): WorkObject?

    @Query("SELECT * FROM Client WHERE id = :id")
    suspend fun clientById(id: Long): Client?

    @Query("SELECT * FROM WorkDay WHERE id = :id")
    suspend fun dayById(id: Long): WorkDay?

    @Query("SELECT * FROM WorkEntry WHERE id = :id")
    suspend fun entryById(id: Long): WorkEntry?

    @Query("SELECT * FROM WorkMaterialEntry WHERE id = :id")
    suspend fun materialEntryById(id: Long): WorkMaterialEntry?

    @Query("SELECT * FROM WorkDayPhoto WHERE id = :id")
    suspend fun photoById(id: Long): WorkDayPhoto?

    @Query("SELECT EXISTS(SELECT 1 FROM WorkDayWorker WHERE workDayId = :dayId AND workerId = :workerId)")
    suspend fun hasDayWorker(dayId: Long, workerId: Long): Boolean

    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM (
            SELECT amount FROM WorkEntry WHERE id != :excludedEntryId
            UNION ALL
            SELECT amount FROM WorkMaterialEntry WHERE id != :excludedMaterialId
        )
    """)
    suspend fun workTotalExcluding(excludedEntryId: Long, excludedMaterialId: Long): Long

    @Query("SELECT o.isCompleted FROM WorkObject o JOIN WorkDay d ON d.objectId = o.id WHERE d.id = :dayId")
    fun dayCompleted(dayId: Long): Flow<Boolean?>

    @Query("SELECT * FROM Proposal WHERE id = :id")
    suspend fun proposalById(id: Long): Proposal?

    @Query("SELECT * FROM ProposalItem WHERE proposalId = :id ORDER BY id")
    suspend fun proposalItemsOnce(id: Long): List<ProposalItem>

    @Query("SELECT * FROM ProposalMaterialItem WHERE proposalId = :id ORDER BY id")
    suspend fun proposalMaterialItemsOnce(id: Long): List<ProposalMaterialItem>

    @Transaction
    suspend fun proposalSnapshot(id: Long): ProposalSnapshot {
        return ProposalSnapshot(requireNotNull(proposalById(id)), proposalItemsOnce(id), proposalMaterialItemsOnce(id))
    }

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

    @Query("""
        SELECT e.id, e.workDayId, e.workerId, w.name AS workerName, e.materialId, m.name AS materialName, e.amount, e.notes
        FROM WorkMaterialEntry e
        JOIN Worker w ON w.id = e.workerId
        JOIN Material m ON m.id = e.materialId
        WHERE e.workDayId = :dayId
        ORDER BY e.id DESC
    """)
    fun materialEntries(dayId: Long): Flow<List<MaterialEntryDetail>>

    @Query("SELECT * FROM WorkDayPhoto WHERE workDayId = :dayId ORDER BY createdAt DESC")
    fun dayPhotos(dayId: Long): Flow<List<WorkDayPhoto>>

    @Query("""
        SELECT p.id, p.workDayId, d.date, p.uri, p.note
        FROM WorkDayPhoto p
        JOIN WorkDay d ON d.id = p.workDayId
        WHERE d.objectId = :objectId
        ORDER BY d.date DESC, p.createdAt DESC
    """)
    suspend fun photosByObject(objectId: Long): List<WorkDayPhotoDetail>

    @Insert
    suspend fun insertClient(client: Client): Long

    @Insert
    suspend fun insertObject(obj: WorkObject): Long

    @Insert
    suspend fun insertWorker(worker: Worker): Long

    @Insert
    suspend fun insertWorkType(type: WorkType): Long

    @Insert
    suspend fun insertMaterial(material: Material): Long

    @Insert
    suspend fun insertWorkDay(day: WorkDay): Long

    @Insert
    suspend fun insertDayWorker(link: WorkDayWorker): Long

    @Insert
    suspend fun insertEntry(entry: WorkEntry): Long

    @Insert
    suspend fun insertMaterialEntry(entry: WorkMaterialEntry): Long

    @Insert
    suspend fun insertDayPhoto(photo: WorkDayPhoto): Long

    @Insert
    suspend fun insertProposal(proposal: Proposal): Long

    @Insert
    suspend fun insertProposalItem(item: ProposalItem): Long

    @Insert
    suspend fun insertProposalMaterialItem(item: ProposalMaterialItem): Long

    @Update
    suspend fun updateClient(client: Client)

    @Update
    suspend fun updateWorker(worker: Worker)

    @Update
    suspend fun updateWorkType(type: WorkType)

    @Update
    suspend fun updateMaterial(material: Material)

    @Update
    suspend fun updateObject(obj: WorkObject)

    @Update
    suspend fun updateEntry(entry: WorkEntry)

    @Update
    suspend fun updateMaterialEntry(entry: WorkMaterialEntry)

    @Delete
    suspend fun deleteEntry(entry: WorkEntry)

    @Query("DELETE FROM WorkEntry WHERE id = :id")
    suspend fun deleteEntryById(id: Long)

    @Query("DELETE FROM WorkMaterialEntry WHERE id = :id")
    suspend fun deleteMaterialEntryById(id: Long)

    @Query("DELETE FROM WorkDayPhoto WHERE id = :id")
    suspend fun deleteDayPhotoById(id: Long)

    @Query("DELETE FROM ProposalItem WHERE proposalId = :proposalId")
    suspend fun deleteProposalItems(proposalId: Long)

    @Query("DELETE FROM ProposalMaterialItem WHERE proposalId = :proposalId")
    suspend fun deleteProposalMaterialItems(proposalId: Long)

    @Query("DELETE FROM Proposal WHERE id = :id")
    suspend fun deleteProposalById(id: Long)

    @Query("DELETE FROM WorkDayWorker WHERE workDayId = :dayId")
    suspend fun clearDayWorkers(dayId: Long)

    @Transaction
    suspend fun createObject(address: String, selectedClientId: Long?, clientName: String, phone: String?): Long {
        val cleanName = clientName.trim()
        val cleanPhone = phone?.trim()?.ifBlank { null }
        val clientId = selectedClientId?.takeIf { it != 0L }?.also { id ->
            requireNotNull(clientById(id))
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

    @Transaction
    suspend fun saveProposal(
        proposalId: Long?,
        objectId: Long,
        items: List<ProposalItem>,
        materialItems: List<ProposalMaterialItem>
    ): Long {
        val now = System.currentTimeMillis()
        val id = proposalId?.takeIf { it != 0L } ?: insertProposal(Proposal(objectId = objectId, createdAt = now, updatedAt = now))
        if (proposalId != null && proposalId != 0L) {
            updateProposalTimestamp(id, objectId, now)
            deleteProposalItems(id)
            deleteProposalMaterialItems(id)
        }
        items.forEach { item -> insertProposalItem(item.copy(proposalId = id)) }
        materialItems.forEach { item -> insertProposalMaterialItem(item.copy(proposalId = id)) }
        return id
    }

    @Query("UPDATE Proposal SET objectId = :objectId, updatedAt = :updatedAt WHERE id = :proposalId")
    suspend fun updateProposalTimestamp(proposalId: Long, objectId: Long, updatedAt: Long)

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
        UNION ALL
        SELECT o.address AS objectAddress, w.name AS workerName, m.name AS workTypeName, e.amount
        FROM WorkMaterialEntry e
        JOIN WorkDay d ON d.id = e.workDayId
        JOIN WorkObject o ON o.id = d.objectId
        JOIN Worker w ON w.id = e.workerId
        JOIN Material m ON m.id = e.materialId
        WHERE d.date BETWEEN :start AND :end
        ORDER BY objectAddress, workerName, workTypeName
    """)
    suspend fun reportByDate(start: Long, end: Long): List<DateReportRow>

    @Query("""
        SELECT d.date, o.address AS objectAddress, t.name AS workTypeName, e.amount
        FROM WorkEntry e
        JOIN WorkDay d ON d.id = e.workDayId
        JOIN WorkObject o ON o.id = d.objectId
        JOIN WorkType t ON t.id = e.workTypeId
        WHERE e.workerId = :workerId AND d.date BETWEEN :start AND :end
        UNION ALL
        SELECT d.date, o.address AS objectAddress, m.name AS workTypeName, e.amount
        FROM WorkMaterialEntry e
        JOIN WorkDay d ON d.id = e.workDayId
        JOIN WorkObject o ON o.id = d.objectId
        JOIN Material m ON m.id = e.materialId
        WHERE e.workerId = :workerId AND d.date BETWEEN :start AND :end
        ORDER BY date DESC, objectAddress
    """)
    suspend fun reportByWorker(workerId: Long, start: Long, end: Long): List<WorkerReportRow>

    @Query("""
        SELECT d.id AS workDayId, d.date, w.id AS workerId, w.name AS workerName,
               t.name AS workTypeName, e.amount, e.notes
        FROM WorkEntry e
        JOIN WorkDay d ON d.id = e.workDayId
        JOIN Worker w ON w.id = e.workerId
        JOIN WorkType t ON t.id = e.workTypeId
        WHERE d.objectId = :objectId
        UNION ALL
        SELECT d.id AS workDayId, d.date, w.id AS workerId, w.name AS workerName,
               m.name AS workTypeName, e.amount, e.notes
        FROM WorkMaterialEntry e
        JOIN WorkDay d ON d.id = e.workDayId
        JOIN Worker w ON w.id = e.workerId
        JOIN Material m ON m.id = e.materialId
        WHERE d.objectId = :objectId
        ORDER BY date DESC, workerName, workDayId
    """)
    suspend fun reportByObject(objectId: Long): List<ObjectReportRow>
}
