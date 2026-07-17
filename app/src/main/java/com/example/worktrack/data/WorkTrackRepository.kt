package com.example.worktrack.data

class WorkTrackRepository(private val dao: WorkTrackDao) {
    val objects = dao.objectSummaries()
    val clients = dao.clients()
    val workers = dao.workers()
    val workTypes = dao.workTypes()
    val activeWorkers = dao.activeWorkers()
    val activeWorkTypes = dao.activeWorkTypes()

    fun workDays(objectId: Long) = dao.workDays(objectId)
    fun dayWorkerIds(dayId: Long) = dao.dayWorkerIds(dayId)
    fun entries(dayId: Long) = dao.entries(dayId)

    suspend fun createObject(address: String, clientId: Long) = dao.createObject(address, clientId)
    suspend fun addClient(name: String, phone: String?) =
        dao.insertClient(Client(name = name.trim(), phone = phone?.trim()?.ifBlank { null }))
    suspend fun addWorker(name: String, phone: String?) = dao.insertWorker(Worker(name = name.trim(), phone = phone?.trim()?.ifBlank { null }))
    suspend fun updateWorker(worker: Worker) = dao.updateWorker(worker)
    suspend fun addWorkType(name: String) = dao.insertWorkType(WorkType(name = name.trim()))
    suspend fun updateWorkType(type: WorkType) = dao.updateWorkType(type)
    suspend fun createDay(objectId: Long, date: Long, workerIds: Set<Long>, notes: String?) = dao.createDay(objectId, date, workerIds, notes)
    suspend fun addEntry(dayId: Long, workerId: Long, typeId: Long, amount: Long, notes: String?) =
        dao.insertEntry(WorkEntry(workDayId = dayId, workerId = workerId, workTypeId = typeId, amount = amount, notes = notes?.ifBlank { null }))
    suspend fun deleteEntry(id: Long) = dao.deleteEntryById(id)
    suspend fun completeObject(objectId: Long) = dao.completeObject(objectId, System.currentTimeMillis())
    suspend fun objectById(id: Long) = dao.objectById(id)
    suspend fun clientById(id: Long) = dao.clientById(id)
    suspend fun reportByDate(start: Long, end: Long) = dao.reportByDate(start, end)
    suspend fun reportByWorker(workerId: Long, start: Long, end: Long) = dao.reportByWorker(workerId, start, end)
    suspend fun reportByObject(objectId: Long) = dao.reportByObject(objectId)

}
