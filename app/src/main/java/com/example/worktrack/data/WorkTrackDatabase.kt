package com.example.worktrack.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Client::class, WorkObject::class, Worker::class, WorkType::class, WorkDay::class, WorkDayWorker::class, WorkEntry::class],
    version = 1,
    exportSchema = false
)
abstract class WorkTrackDatabase : RoomDatabase() {
    abstract fun dao(): WorkTrackDao

    companion object {
        @Volatile private var instance: WorkTrackDatabase? = null

        fun get(context: Context): WorkTrackDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WorkTrackDatabase::class.java,
                    "worktrack.db"
                ).build().also { instance = it }
            }
    }
}
