package com.example.worktrack.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MoneyMigrationTest {
    // Schema 7 changes units only: its table definitions are identical to schema 6.
    private fun seedLegacy(name: String, work: Long = 100, version: Int = 6) = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(name)
        val db = Room.databaseBuilder(context, WorkTrackDatabase::class.java, name).build()
        try {
            val repo = WorkTrackRepository(db)
            val obj = repo.createObject("Address", null, "Client", null)
            val worker = repo.addWorker("Worker", null)
            val type = repo.addWorkType("Work")
            val material = repo.addMaterial("Material")
            val day = repo.createDay(obj, 1000, setOf(worker), "Note")
            repo.addEntry(day, worker, type, work, null)
            repo.addMaterialEntry(day, worker, material, 50, null)
            repo.saveProposal(null, obj, listOf(ProposalItem(proposalId = 0, workTypeId = type, amount = 200)),
                listOf(ProposalMaterialItem(proposalId = 0, materialId = material, amount = 75)))
        } finally { db.close() }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("DROP TABLE room_master_table")
            it.execSQL("DROP TABLE CustomerPayment")
            it.version = version
        }
    }

    @Test fun `schema seven upgrades without changing cents and accepts payments`() = runBlocking {
        val name = "payments-migration.db"
        seedLegacy(name, version = 7)
        val db = Room.databaseBuilder(RuntimeEnvironment.getApplication(), WorkTrackDatabase::class.java, name)
            .addMigrations(WorkTrackDatabase.MIGRATION_7_8).build()
        try {
            val repo = WorkTrackRepository(db)
            val obj = db.dao().objectSummaries().first().single()
            assertEquals(150L, obj.totalAmount)
            assertTrue(repo.customerPayments(obj.id).first().isEmpty())
            repo.savePayment(null, obj.id, 1000, 25, null)
            assertEquals(125L, repo.objectFinance(obj.id).first().balance)
            assertEquals(8, db.openHelper.writableDatabase.version)
        } finally { db.close() }
    }

    @Test fun `Room upgrades all four amount tables exactly once and preserves relationships`() = runBlocking {
        val name = "money-migration.db"
        seedLegacy(name)
        val context = RuntimeEnvironment.getApplication()
        repeat(2) {
            val db = Room.databaseBuilder(context, WorkTrackDatabase::class.java, name)
                .addMigrations(WorkTrackDatabase.MIGRATION_6_7, WorkTrackDatabase.MIGRATION_7_8).build()
            try {
                assertEquals(15000L, db.dao().objectSummaries().first().single().totalAmount)
                assertEquals(27500L, db.dao().proposals().first().single().totalAmount)
                db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
                assertEquals(8, db.openHelper.writableDatabase.version)
            } finally { db.close() }
        }
    }

    @Test fun `out of range total aborts migration without partial conversion`() = runBlocking {
        val name = "money-overflow.db"
        seedLegacy(name, Long.MAX_VALUE / 100)
        val context = RuntimeEnvironment.getApplication()
        val db = Room.databaseBuilder(context, WorkTrackDatabase::class.java, name)
            .addMigrations(WorkTrackDatabase.MIGRATION_6_7, WorkTrackDatabase.MIGRATION_7_8).build()
        try {
            try { db.openHelper.writableDatabase; fail("Overflow accepted") } catch (_: IllegalStateException) { }
        } finally { db.close() }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use {
            assertEquals(6, it.version)
            it.rawQuery("SELECT amount FROM WorkEntry", null).use { row ->
                assertTrue(row.moveToFirst())
                assertEquals(Long.MAX_VALUE / 100, row.getLong(0))
            }
            it.rawQuery("SELECT amount FROM WorkMaterialEntry", null).use { row ->
                assertTrue(row.moveToFirst()); assertEquals(50L, row.getLong(0))
            }
        }
    }
}
