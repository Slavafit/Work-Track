package com.example.worktrack.data

import androidx.room.Room
import com.example.worktrack.InvalidAmountException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CustomerPaymentsTest {
    @Test fun `payments update balance without multiplying work or materials and allow overpayment`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), WorkTrackDatabase::class.java).build()
        try {
            val repo = WorkTrackRepository(db)
            val obj = repo.createObject("A", null, "Client", null)
            val worker = repo.addWorker("Worker", null)
            val type = repo.addWorkType("Work")
            val material = repo.addMaterial("Material")
            val day = repo.createDay(obj, 1000, setOf(worker), null)
            repo.addEntry(day, worker, type, 10000, null)
            repo.addEntry(day, worker, type, 20000, null)
            repo.addMaterialEntry(day, worker, material, 5000, null)
            repo.saveProposal(null, obj, listOf(ProposalItem(proposalId=0, workTypeId=type, amount=99999)), emptyList())
            val first = repo.savePayment(null, obj, 1000, 10000, "cash")
            repo.savePayment(null, obj, 2000, 5000, null)
            assertEquals(ObjectFinance(30000, 5000, 15000), repo.objectFinance(obj).first())
            assertEquals(20000L, repo.objectFinance(obj).first().balance)
            repo.savePayment(first, obj, 1000, 40000, "edited")
            assertEquals(-10000L, repo.objectFinance(obj).first().balance)
            assertEquals(2, repo.customerPayments(obj).first().size)
            repo.deletePayment(first, obj)
            assertEquals(30000L, repo.objectFinance(obj).first().balance)
        } finally { db.close() }
    }

    @Test fun `closed objects can receive settlement but invalid and cross object edits fail`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), WorkTrackDatabase::class.java).build()
        try {
            val repo = WorkTrackRepository(db)
            val obj = repo.createObject("A", null, "Client", null)
            val other = repo.createObject("B", null, "Client", null)
            db.dao().completeObject(obj, 1000)
            val payment = repo.savePayment(null, obj, 1000, 12345, null)
            assertEquals(-12345L, repo.objectFinance(obj).first().balance)
            for (amount in listOf(0L, -1L)) {
                try { repo.savePayment(null, obj, 1000, amount, null); fail() } catch (_: IllegalArgumentException) { }
            }
            try { repo.savePayment(null, obj, 0, 10, null); fail() } catch (_: IllegalArgumentException) { }
            try { repo.savePayment(payment, other, 1000, 10, null); fail() } catch (_: IllegalArgumentException) { }
            try { repo.deletePayment(payment, other); fail() } catch (_: IllegalArgumentException) { }
            assertEquals(12345L, repo.objectFinance(obj).first().paidAmount)
            assertEquals(0L, repo.objectFinance(other).first().paidAmount)
        } finally { db.close() }
    }

    @Test fun `overflow is rejected and replacing existing amount does not count it twice`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), WorkTrackDatabase::class.java).build()
        try {
            val repo = WorkTrackRepository(db)
            val obj = repo.createObject("A", null, "Client", null)
            val id = repo.savePayment(null, obj, 1000, Long.MAX_VALUE, null)
            repo.savePayment(id, obj, 1000, Long.MAX_VALUE, "same")
            try { repo.savePayment(null, obj, 1000, 1, null); fail() } catch (_: InvalidAmountException) { }
            assertEquals(Long.MAX_VALUE, repo.objectFinance(obj).first().paidAmount)
            assertEquals(1, repo.customerPayments(obj).first().size)
        } finally { db.close() }
    }
}
