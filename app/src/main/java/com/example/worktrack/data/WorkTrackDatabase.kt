package com.example.worktrack.data

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Client::class,
        WorkObject::class,
        Worker::class,
        WorkType::class,
        WorkDay::class,
        WorkDayWorker::class,
        WorkEntry::class,
        WorkDayPhoto::class,
        Proposal::class,
        ProposalItem::class
    ],
    version = 3,
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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `Proposal` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `objectId` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`objectId`) REFERENCES `WorkObject`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_Proposal_objectId` ON `Proposal` (`objectId`)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `ProposalItem` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `proposalId` INTEGER NOT NULL,
                        `workTypeId` INTEGER NOT NULL,
                        `amount` INTEGER NOT NULL,
                        FOREIGN KEY(`proposalId`) REFERENCES `Proposal`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`workTypeId`) REFERENCES `WorkType`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ProposalItem_proposalId` ON `ProposalItem` (`proposalId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ProposalItem_workTypeId` ON `ProposalItem` (`workTypeId`)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `WorkDayPhoto` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `workDayId` INTEGER NOT NULL,
                        `uri` TEXT NOT NULL,
                        `note` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`workDayId`) REFERENCES `WorkDay`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_WorkDayPhoto_workDayId` ON `WorkDayPhoto` (`workDayId`)")
            }
        }
    }
}
