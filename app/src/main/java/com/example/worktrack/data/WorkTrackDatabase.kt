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
        CustomerPayment::class,
        WorkObject::class,
        Worker::class,
        WorkType::class,
        Material::class,
        WorkDay::class,
        WorkDayWorker::class,
        WorkEntry::class,
        WorkMaterialEntry::class,
        WorkDayPhoto::class,
        Proposal::class,
        ProposalItem::class,
        ProposalMaterialItem::class
    ],
    version = 9,
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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9).build().also { instance = it }
            }

        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE WorkEntry ADD COLUMN isAmountPending INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE WorkMaterialEntry ADD COLUMN isAmountPending INTEGER NOT NULL DEFAULT 0")
            }
        }

        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `CustomerPayment` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `objectId` INTEGER NOT NULL, `date` INTEGER NOT NULL,
                    `amount` INTEGER NOT NULL, `notes` TEXT,
                    FOREIGN KEY(`objectId`) REFERENCES `WorkObject`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_CustomerPayment_objectId` ON `CustomerPayment` (`objectId`)")
            }
        }

        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val tables = listOf("WorkEntry", "WorkMaterialEntry", "ProposalItem", "ProposalMaterialItem")
                val limit = Long.MAX_VALUE / 100
                tables.forEach { table ->
                    db.query("SELECT 1 FROM `$table` WHERE typeof(amount) != 'integer' OR amount < 0 OR amount > $limit LIMIT 1").use {
                        check(!it.moveToFirst()) { "Amount cannot be converted to euro cents" }
                    }
                }
                db.query("SELECT SUM(amount) FROM (SELECT amount FROM WorkEntry UNION ALL SELECT amount FROM WorkMaterialEntry)").use {
                    if (it.moveToFirst()) check(it.getLong(0) <= limit) { "Work total exceeds euro cent range" }
                }
                db.query("SELECT SUM(amount) FROM (SELECT proposalId, amount FROM ProposalItem UNION ALL SELECT proposalId, amount FROM ProposalMaterialItem) GROUP BY proposalId").use {
                    while (it.moveToNext()) check(it.getLong(0) <= limit) { "Proposal total exceeds euro cent range" }
                }
                tables.forEach { db.execSQL("UPDATE `$it` SET amount = amount * 100") }
            }
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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `Material` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `isActive` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `ProposalMaterialItem` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `proposalId` INTEGER NOT NULL,
                        `materialId` INTEGER NOT NULL,
                        `amount` INTEGER NOT NULL,
                        FOREIGN KEY(`proposalId`) REFERENCES `Proposal`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`materialId`) REFERENCES `Material`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ProposalMaterialItem_proposalId` ON `ProposalMaterialItem` (`proposalId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ProposalMaterialItem_materialId` ON `ProposalMaterialItem` (`materialId`)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `WorkMaterialEntry` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `workDayId` INTEGER NOT NULL,
                        `workerId` INTEGER NOT NULL,
                        `materialId` INTEGER NOT NULL,
                        `amount` INTEGER NOT NULL,
                        `notes` TEXT,
                        FOREIGN KEY(`workDayId`) REFERENCES `WorkDay`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`workerId`) REFERENCES `Worker`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`materialId`) REFERENCES `Material`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_WorkMaterialEntry_workDayId` ON `WorkMaterialEntry` (`workDayId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_WorkMaterialEntry_workerId` ON `WorkMaterialEntry` (`workerId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_WorkMaterialEntry_materialId` ON `WorkMaterialEntry` (`materialId`)")
            }
        }
    }
}
