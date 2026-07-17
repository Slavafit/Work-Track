package com.example.worktrack.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity
data class Client(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String? = null
)

@Entity(
    foreignKeys = [ForeignKey(Client::class, ["id"], ["clientId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("clientId")]
)
data class WorkObject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long,
    val address: String,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null
)

@Entity
data class Worker(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String? = null,
    val isActive: Boolean = true
)

@Entity
data class WorkType(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isActive: Boolean = true
)

@Entity(
    foreignKeys = [ForeignKey(WorkObject::class, ["id"], ["objectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("objectId")]
)
data class WorkDay(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val objectId: Long,
    val date: Long,
    val notes: String? = null
)

@Entity(
    foreignKeys = [
        ForeignKey(WorkDay::class, ["id"], ["workDayId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Worker::class, ["id"], ["workerId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("workDayId"), Index("workerId")]
)
data class WorkDayWorker(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workDayId: Long,
    val workerId: Long
)

@Entity(
    foreignKeys = [
        ForeignKey(WorkDay::class, ["id"], ["workDayId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Worker::class, ["id"], ["workerId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(WorkType::class, ["id"], ["workTypeId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("workDayId"), Index("workerId"), Index("workTypeId")]
)
data class WorkEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workDayId: Long,
    val workerId: Long,
    val workTypeId: Long,
    val amount: Long,
    val notes: String? = null
)

data class ObjectSummary(
    val id: Long,
    val address: String,
    val clientName: String,
    val isCompleted: Boolean,
    val completedAt: Long?,
    val totalAmount: Long,
    val dayCount: Int
)

data class WorkDaySummary(
    val id: Long,
    val objectId: Long,
    val date: Long,
    val notes: String?,
    val totalAmount: Long,
    val workerCount: Int,
    val entryCount: Int
)

data class EntryDetail(
    val id: Long,
    val workDayId: Long,
    val workerId: Long,
    val workerName: String,
    val workTypeId: Long,
    val workTypeName: String,
    val amount: Long,
    val notes: String?
)

data class DateReportRow(
    val objectAddress: String,
    val workerName: String,
    val workTypeName: String,
    val amount: Long
)

data class WorkerReportRow(
    val date: Long,
    val objectAddress: String,
    val workTypeName: String,
    val amount: Long
)

data class ObjectReportRow(
    val workDayId: Long,
    val date: Long,
    val workerId: Long,
    val workerName: String,
    val workTypeName: String,
    val amount: Long,
    val notes: String?
)
