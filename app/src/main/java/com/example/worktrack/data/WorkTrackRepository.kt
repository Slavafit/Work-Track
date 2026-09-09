package com.example.worktrack.data

import androidx.room.withTransaction
import com.example.worktrack.checkedAmountTotal
import com.example.worktrack.InvalidAmountException

class ClosedObjectException : IllegalStateException("Object is completed")

class WorkTrackRepository(private val db: WorkTrackDatabase) : ProposalStore {
    private val dao = db.dao()

    private suspend fun requireEditableObject(id: Long) {
        if (requireNotNull(dao.objectById(id)).isCompleted) throw ClosedObjectException()
    }

    private suspend fun <T> editDay(dayId: Long, action: suspend () -> T): T = db.withTransaction {
        requireEditableObject(requireNotNull(dao.dayById(dayId)).objectId)
        action()
    }

    private suspend fun validateEntry(dayId: Long, workerId: Long, amount: Long, excludedEntryId: Long = 0, excludedMaterialId: Long = 0) {
        require(amount >= 0)
        require(dao.hasDayWorker(dayId, workerId))
        val total = dao.workTotalExcluding(excludedEntryId, excludedMaterialId)
        if (checkedAmountTotal(listOf(total, amount)) == null) throw InvalidAmountException()
    }

    fun dayCompleted(dayId: Long) = dao.dayCompleted(dayId)

    val objects = dao.objectSummaries()
    val clients = dao.clients()
    val workers = dao.workers()
    val workTypes = dao.workTypes()
    val materials = dao.materials()
    val activeWorkers = dao.activeWorkers()
    val activeWorkTypes = dao.activeWorkTypes()
    val proposals = dao.proposals()

    fun workDays(objectId: Long) = dao.workDays(objectId)
    fun dayWorkerIds(dayId: Long) = dao.dayWorkerIds(dayId)
    fun entries(dayId: Long) = dao.entries(dayId)
    fun materialEntries(dayId: Long) = dao.materialEntries(dayId)
    fun dayPhotos(dayId: Long) = dao.dayPhotos(dayId)
    fun proposalItems(proposalId: Long) = dao.proposalItems(proposalId)
    fun proposalMaterialItems(proposalId: Long) = dao.proposalMaterialItems(proposalId)

    suspend fun createObject(address: String, selectedClientId: Long?, clientName: String, phone: String?) =
        dao.createObject(address, selectedClientId, clientName, phone)
    suspend fun addWorker(name: String, phone: String?) = dao.insertWorker(Worker(name = name.trim(), phone = phone?.trim()?.ifBlank { null }))
    suspend fun updateWorker(worker: Worker) = dao.updateWorker(worker)
    suspend fun addWorkType(name: String) = dao.insertWorkType(WorkType(name = name.trim()))
    suspend fun updateWorkType(type: WorkType) = dao.updateWorkType(type)
    suspend fun addMaterial(name: String) = dao.insertMaterial(Material(name = name.trim()))
    suspend fun updateMaterial(material: Material) = dao.updateMaterial(material)
    suspend fun createDay(objectId: Long, date: Long, workerIds: Set<Long>, notes: String?) = db.withTransaction {
        requireEditableObject(objectId)
        require(workerIds.isNotEmpty())
        dao.createDay(objectId, date, workerIds, notes)
    }
    suspend fun addEntry(dayId: Long, workerId: Long, typeId: Long, amount: Long, notes: String?) = editDay(dayId) {
        validateEntry(dayId, workerId, amount)
        dao.insertEntry(WorkEntry(workDayId = dayId, workerId = workerId, workTypeId = typeId, amount = amount, notes = notes?.ifBlank { null }))
    }
    suspend fun updateEntry(id: Long, dayId: Long, workerId: Long, typeId: Long, amount: Long, notes: String?) = editDay(dayId) {
        require(requireNotNull(dao.entryById(id)).workDayId == dayId)
        validateEntry(dayId, workerId, amount, excludedEntryId = id)
        dao.updateEntry(WorkEntry(id = id, workDayId = dayId, workerId = workerId, workTypeId = typeId, amount = amount, notes = notes?.ifBlank { null }))
    }
    suspend fun deleteEntry(id: Long) = db.withTransaction {
        val entry = requireNotNull(dao.entryById(id))
        editDay(entry.workDayId) { dao.deleteEntryById(id) }
    }
    suspend fun addMaterialEntry(dayId: Long, workerId: Long, materialId: Long, amount: Long, notes: String?) = editDay(dayId) {
        validateEntry(dayId, workerId, amount)
        dao.insertMaterialEntry(WorkMaterialEntry(workDayId = dayId, workerId = workerId, materialId = materialId, amount = amount, notes = notes?.ifBlank { null }))
    }
    suspend fun updateMaterialEntry(id: Long, dayId: Long, workerId: Long, materialId: Long, amount: Long, notes: String?) = editDay(dayId) {
        require(requireNotNull(dao.materialEntryById(id)).workDayId == dayId)
        validateEntry(dayId, workerId, amount, excludedMaterialId = id)
        dao.updateMaterialEntry(WorkMaterialEntry(id = id, workDayId = dayId, workerId = workerId, materialId = materialId, amount = amount, notes = notes?.ifBlank { null }))
    }
    suspend fun deleteMaterialEntry(id: Long) = db.withTransaction {
        val entry = requireNotNull(dao.materialEntryById(id))
        editDay(entry.workDayId) { dao.deleteMaterialEntryById(id) }
    }
    suspend fun addDayPhoto(dayId: Long, uri: String) = editDay(dayId) {
        dao.insertDayPhoto(WorkDayPhoto(workDayId = dayId, uri = uri, createdAt = System.currentTimeMillis()))
    }
    suspend fun deleteDayPhoto(id: Long) = db.withTransaction {
        val photo = requireNotNull(dao.photoById(id))
        editDay(photo.workDayId) { dao.deleteDayPhotoById(id) }
    }
    suspend fun completeObject(objectId: Long) = dao.completeObject(objectId, System.currentTimeMillis())
    override suspend fun proposalSnapshot(id: Long) = dao.proposalSnapshot(id)
    override suspend fun saveProposal(id: Long?, objectId: Long, items: List<ProposalItem>, materialItems: List<ProposalMaterialItem>): Long = db.withTransaction {
        requireEditableObject(objectId)
        id?.let { requireEditableObject(requireNotNull(dao.proposalById(it)).objectId) }
        require(items.isNotEmpty() || materialItems.isNotEmpty())
        if (checkedAmountTotal(items.map { it.amount } + materialItems.map { it.amount }) == null) throw InvalidAmountException()
        dao.saveProposal(id, objectId, items.map { it.copy(id = 0) }, materialItems.map { it.copy(id = 0) })
    }
    override suspend fun deleteProposal(id: Long) = db.withTransaction {
        requireEditableObject(requireNotNull(dao.proposalById(id)).objectId)
        dao.deleteProposalById(id)
    }
    suspend fun objectById(id: Long) = dao.objectById(id)
    suspend fun clientById(id: Long) = dao.clientById(id)
    suspend fun reportByDate(start: Long, end: Long) = dao.reportByDate(start, end)
    suspend fun reportByWorker(workerId: Long, start: Long, end: Long) = dao.reportByWorker(workerId, start, end)
    suspend fun reportByObject(objectId: Long) = dao.reportByObject(objectId)
    suspend fun photosByObject(objectId: Long) = dao.photosByObject(objectId)

}
