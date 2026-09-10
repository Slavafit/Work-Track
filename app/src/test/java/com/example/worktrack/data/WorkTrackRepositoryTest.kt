package com.example.worktrack.data

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkTrackRepositoryTest {
    private lateinit var db: WorkTrackDatabase
    private lateinit var dao: WorkTrackDao
    private lateinit var repo: WorkTrackRepository
    private var objectId = 0L
    private var dayId = 0L
    private var workerId = 0L
    private var typeId = 0L
    private var materialId = 0L

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), WorkTrackDatabase::class.java).allowMainThreadQueries().build()
        dao = db.dao()
        repo = WorkTrackRepository(db)
        objectId = repo.createObject("Address", null, "Client", "123456789")
        workerId = repo.addWorker("Worker", null)
        typeId = repo.addWorkType("Service")
        materialId = repo.addMaterial("Material")
        dayId = repo.createDay(objectId, 1000, setOf(workerId), null)
    }

    @After fun teardown() { db.close() }

    @Test fun `object contact edit is isolated unless shared update is explicit`() = runBlocking {
        val clientId = repo.objectById(objectId)!!.clientId
        val other = repo.createObject("Other", clientId, "ignored", null)
        repo.editObjectDetails(objectId, " New address ", "New name", "+34 612 345 678", false)
        val edited = repo.objectById(objectId)!!
        assertEquals("New address", edited.address)
        assertNotEquals(clientId, edited.clientId)
        assertEquals("Client", repo.clientById(repo.objectById(other)!!.clientId)!!.name)
        assertEquals("New name", repo.clientById(edited.clientId)!!.name)
        val third = repo.createObject("Third", edited.clientId, "ignored", null)
        repo.editObjectDetails(objectId, "New address", "Shared correction", null, true)
        assertEquals("Shared correction", repo.clientById(repo.objectById(third)!!.clientId)!!.name)
        assertNull(repo.clientById(edited.clientId)!!.phone)
    }

    @Test fun `address only correction preserves shared customer and closed work records`() = runBlocking {
        val original = repo.objectById(objectId)!!
        val other = repo.createObject("Other", original.clientId, "ignored", null)
        repo.addEntry(dayId, workerId, typeId, 1250, null)
        repo.savePayment(null, objectId, 1000, 500, null)
        repo.completeObject(objectId)
        val completedAt = repo.objectById(objectId)!!.completedAt
        repo.editObjectDetails(objectId, "Corrected", "Client", "123456789", false)
        val edited = repo.objectById(objectId)!!
        assertEquals(original.clientId, edited.clientId)
        assertTrue(edited.isCompleted)
        assertEquals(completedAt, edited.completedAt)
        assertEquals("Other", repo.objectById(other)!!.address)
        assertEquals(1250L, repo.objectFinance(objectId).first().workAmount)
        assertEquals(500L, repo.objectFinance(objectId).first().paidAmount)
    }

    @Test fun `invalid object edits preserve address and contact`() = runBlocking {
        val original = repo.objectById(objectId)!!
        for ((address, name, phone) in listOf(Triple("", "New", "123456789"), Triple("Changed", " ", "123456789"), Triple("Changed", "New", "123abc456789"))) {
            try { repo.editObjectDetails(objectId, address, name, phone, true); fail() } catch (_: IllegalArgumentException) { }
        }
        assertEquals(original, repo.objectById(objectId))
        assertEquals("Client", repo.clientById(original.clientId)!!.name)
    }

    @Test fun `completed object rejects every work day mutation without losing data`() = runBlocking {
        val entry = repo.addEntry(dayId, workerId, typeId, 100, null)
        val material = repo.addMaterialEntry(dayId, workerId, materialId, 50, null)
        val photo = repo.addDayPhoto(dayId, "content://test/photo")
        repo.completeObject(objectId)
        val actions: List<suspend () -> Unit> = listOf(
            { repo.createDay(objectId, 2000, setOf(workerId), null) },
            { repo.addEntry(dayId, workerId, typeId, 1, null) },
            { repo.updateEntry(entry, dayId, workerId, typeId, 1, null) },
            { repo.deleteEntry(entry) },
            { repo.addMaterialEntry(dayId, workerId, materialId, 1, null) },
            { repo.updateMaterialEntry(material, dayId, workerId, materialId, 1, null) },
            { repo.deleteMaterialEntry(material) },
            { repo.addDayPhoto(dayId, "content://test/new") },
            { repo.deleteDayPhoto(photo) }
        )
        for (action in actions) {
            try { action(); fail("Completed object accepted a mutation") } catch (_: ClosedObjectException) { }
        }
        assertEquals(100L, dao.entryById(entry)!!.amount)
        assertEquals(50L, dao.materialEntryById(material)!!.amount)
        assertNotNull(dao.photoById(photo))
        assertEquals(1, dao.workDays(objectId).first().size)
    }

    @Test fun `active object allows updates and deletion`() = runBlocking {
        val entry = repo.addEntry(dayId, workerId, typeId, 0, null)
        repo.updateEntry(entry, dayId, workerId, typeId, 200, "note")
        assertEquals(200L, dao.entryById(entry)!!.amount)
        repo.deleteEntry(entry)
        assertNull(dao.entryById(entry))
    }

    @Test fun `existing client details are not overwritten by object creation`() = runBlocking {
        val clientId = repo.objectById(objectId)!!.clientId
        val nextObject = repo.createObject("Other address", clientId, "Changed name", "999999999")
        assertEquals(clientId, repo.objectById(nextObject)!!.clientId)
        assertEquals("Client", repo.clientById(clientId)!!.name)
        assertEquals("123456789", repo.clientById(clientId)!!.phone)
    }

    @Test fun `hidden service is preserved when an existing proposal is saved again`() = runBlocking {
        val id = repo.saveProposal(null, objectId,
            listOf(ProposalItem(proposalId = 0, workTypeId = typeId, amount = 100)),
            listOf(ProposalMaterialItem(proposalId = 0, materialId = materialId, amount = 50)))
        repo.updateWorkType(WorkType(typeId, "Service", false))
        val loaded = repo.proposalSnapshot(id)
        repo.saveProposal(id, objectId, loaded.items, loaded.materialItems)
        val saved = repo.proposalSnapshot(id)
        assertEquals(typeId, saved.items.single().workTypeId)
        assertEquals(100L, saved.items.single().amount)
        assertEquals(50L, saved.materialItems.single().amount)
    }

    @Test fun `failed replacement rolls back previous proposal lines`() = runBlocking {
        val id = repo.saveProposal(null, objectId, listOf(ProposalItem(proposalId = 0, workTypeId = typeId, amount = 100)), emptyList())
        try {
            repo.saveProposal(id, objectId, listOf(ProposalItem(proposalId = 0, workTypeId = 9999, amount = 200)), emptyList())
            fail("Missing service should fail")
        } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(100L, repo.proposalSnapshot(id).items.single().amount)
    }

    @Test fun `completed proposal cannot be modified moved or deleted`() = runBlocking {
        val id = repo.saveProposal(null, objectId, listOf(ProposalItem(proposalId = 0, workTypeId = typeId, amount = 100)), emptyList())
        val other = repo.createObject("Other", null, "Other client", null)
        repo.completeObject(objectId)
        try {
            repo.saveProposal(id, other, listOf(ProposalItem(proposalId = 0, workTypeId = typeId, amount = 200)), emptyList())
            fail("Closed proposal moved")
        } catch (_: ClosedObjectException) { }
        try { repo.deleteProposal(id); fail("Closed proposal deleted") } catch (_: ClosedObjectException) { }
        assertEquals(objectId, repo.proposalSnapshot(id).proposal.objectId)
    }

    @Test fun `worker must belong to the day`() = runBlocking {
        val outsider = repo.addWorker("Other", null)
        try { repo.addEntry(dayId, outsider, typeId, 10, null); fail("Unassigned worker accepted") } catch (_: IllegalArgumentException) { }
        assertTrue(dao.entries(dayId).first().isEmpty())
    }

    @Test fun `overflow is rejected and replacing an amount does not double count it`() = runBlocking {
        val entry = repo.addEntry(dayId, workerId, typeId, Long.MAX_VALUE, null)
        try {
            repo.addMaterialEntry(dayId, workerId, materialId, 1, null)
            fail("Overflow was accepted")
        } catch (_: IllegalArgumentException) { }
        assertTrue(dao.materialEntries(dayId).first().isEmpty())
        repo.updateEntry(entry, dayId, workerId, typeId, 100, null)
        repo.addMaterialEntry(dayId, workerId, materialId, 50, null)
        assertEquals(150L, dao.objectSummaries().first().single().totalAmount)
    }
}
