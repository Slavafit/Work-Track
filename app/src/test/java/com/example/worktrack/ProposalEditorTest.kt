package com.example.worktrack

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.activity.ComponentActivity
import android.os.Bundle
import com.example.worktrack.data.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProposalEditorTest {
    private fun persistentStore(): ProposalDraftStore {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        context.getSharedPreferences("proposal_draft", 0).edit().clear().commit()
        return ProposalDraftStore(context)
    }

    @Test fun `fresh editor without activity state restores incomplete input from local storage`() = runTest {
        val disk = persistentStore()
        val editor = ProposalEditor(SavedStateHandle(), Store(), this, disk)
        editor.selectObject(7)
        editor.addService(10)
        editor.updateService(editor.state.value.lines.single().copy(amount = "12,50"))
        editor.addMaterial(20)
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        // Flush pending Android preference writes before checking the on-disk recovery record.
        assertTrue(context.getSharedPreferences("proposal_draft", 0).edit().commit())
        val file = java.io.File(context.applicationInfo.dataDir, "shared_prefs/proposal_draft.xml")
        assertTrue(file.readText().contains("12,50"))
        val restored = ProposalEditor(SavedStateHandle(), Store(), this, ProposalDraftStore(context))
        assertEquals(editor.state.value, restored.state.value)
        assertEquals("", restored.state.value.materialLines.single().amount)
        assertFalse(restored.state.value.valid)
    }

    @Test fun `discard tombstone overrides stale activity state`() = runTest {
        val disk = persistentStore()
        val handle = SavedStateHandle()
        val editor = ProposalEditor(handle, Store(), this, disk)
        editor.selectObject(7)
        editor.addService(10)
        val oldState = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) })
        editor.newDraft()
        val restored = ProposalEditor(oldState, Store(), this, disk)
        assertEquals(ProposalDraft(), restored.state.value)
    }

    @Test fun `failed save recovers edits and successful save retains saved proposal id`() = runTest {
        val disk = persistentStore()
        val store = Store().apply { failSave = true }
        val editor = ProposalEditor(SavedStateHandle(), store, this, disk)
        editor.open(2)
        advanceUntilIdle()
        editor.updateService(editor.state.value.lines.single().copy(amount = "700"))
        editor.save()
        advanceUntilIdle()
        val restored = ProposalEditor(SavedStateHandle(), store, this, disk)
        assertTrue(restored.state.value.dirty)
        assertFalse(restored.state.value.busy)
        assertEquals("700", restored.state.value.lines.single().amount)
        store.failSave = false
        restored.save()
        advanceUntilIdle()
        val saved = ProposalEditor(SavedStateHandle(), store, this, disk)
        assertEquals(2L, saved.state.value.proposalId)
        assertFalse(saved.state.value.dirty)
        assertEquals(70000L, store.saved.single().amount)
    }

    @Test fun `deletion clears persistent selected proposal`() = runTest {
        val disk = persistentStore()
        val editor = ProposalEditor(SavedStateHandle(), Store(), this, disk)
        editor.open(2)
        advanceUntilIdle()
        editor.delete(2)
        advanceUntilIdle()
        assertEquals(ProposalDraft(), ProposalEditor(SavedStateHandle(), Store(), this, disk).state.value)
    }

    @Test fun `corrupt recovery record reports error without crashing`() = runTest {
        val disk = persistentStore()
        org.robolectric.RuntimeEnvironment.getApplication().getSharedPreferences("proposal_draft", 0)
            .edit().putString("snapshot", "broken").commit()
        val restored = ProposalEditor(SavedStateHandle(), Store(), this, disk)
        assertEquals(R.string.draft_restore_failed, restored.state.value.error)
        restored.newDraft()
        assertEquals(ProposalDraft(), disk.read())
    }

    @Test fun `default activity factory restores the editor after activity state recreation`() {
        val first = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val vm = ViewModelProvider(first.get())[AppViewModel::class.java]
        vm.setObjectSearch("Address")
        vm.setObjectStatus("completed")
        vm.setObjectsWithBalance(true)
        vm.proposalEditor.selectObject(42)
        vm.proposalEditor.addService(7)
        vm.proposalEditor.updateService(vm.proposalEditor.state.value.lines.single().copy(amount = "12,50"))
        val saved = Bundle()
        first.saveInstanceState(saved).pause().stop().destroy()
        val second = Robolectric.buildActivity(ComponentActivity::class.java).create(saved).start().resume()
        val restored = ViewModelProvider(second.get())[AppViewModel::class.java]
        assertEquals("Address", restored.objectSearch.value)
        assertEquals("completed", restored.objectStatus.value)
        assertTrue(restored.objectsWithBalance.value)
        restored.resetObjectSearch()
        assertEquals("", restored.objectSearch.value)
        assertEquals("all", restored.objectStatus.value)
        assertFalse(restored.objectsWithBalance.value)
        assertEquals(42L, restored.proposalEditor.state.value.objectId)
        assertEquals("12,50", restored.proposalEditor.state.value.lines.single().amount)
        second.pause().stop().destroy()
    }

    private class Store : ProposalStore {
        var saved: List<ProposalItem> = emptyList()
        var savedMaterials: List<ProposalMaterialItem> = emptyList()
        var saves = 0
        var failSave = false
        var failDelete = false
        override suspend fun proposalSnapshot(id: Long): ProposalSnapshot {
            // Even an uncooperative earlier request cannot overwrite the latest choice.
            withContext(NonCancellable) { delay(if (id == 1L) 100 else 1) }
            return ProposalSnapshot(Proposal(id, id, 0, 0),
                listOf(ProposalItem(1, id, 10, id * 100)), listOf(ProposalMaterialItem(1, id, 20, id * 50)))
        }
        override suspend fun saveProposal(id: Long?, objectId: Long, items: List<ProposalItem>, materialItems: List<ProposalMaterialItem>): Long {
            saves++
            delay(1)
            if (failSave) error("disk failure")
            saved = items
            savedMaterials = materialItems
            return id ?: 5L
        }
        override suspend fun deleteProposal(id: Long) { if (failDelete) error("disk failure") }
    }

    @Test fun `saved state restores unsaved services and materials without reloading database`() = runTest {
        val handle = SavedStateHandle()
        val editor = ProposalEditor(handle, Store(), this)
        editor.selectObject(7)
        editor.addService(10)
        editor.updateService(editor.state.value.lines.single().copy(amount = "123"))
        editor.addMaterial(20)
        editor.updateMaterial(editor.state.value.materialLines.single().copy(amount = "45"))
        val restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) })
        val restored = ProposalEditor(restoredHandle, Store(), this)
        assertEquals(editor.state.value, restored.state.value)
        assertEquals(16800L, restored.state.value.total)
        assertTrue(restored.state.value.dirty)
    }

    @Test fun `latest selection wins for both services and materials`() = runTest {
        val editor = ProposalEditor(SavedStateHandle(), Store(), this)
        editor.open(1)
        runCurrent()
        editor.open(2)
        advanceUntilIdle()
        assertEquals(2L, editor.state.value.proposalId)
        assertEquals("2.00", editor.state.value.lines.single().amount)
        assertEquals("1.00", editor.state.value.materialLines.single().amount)
        assertFalse(editor.state.value.loading)
    }

    @Test fun `new draft is not overwritten by a previous load`() = runTest {
        val editor = ProposalEditor(SavedStateHandle(), Store(), this)
        editor.open(1)
        runCurrent()
        editor.newDraft()
        editor.selectObject(3)
        advanceUntilIdle()
        assertEquals(3L, editor.state.value.objectId)
        assertNull(editor.state.value.proposalId)
        assertTrue(editor.state.value.lines.isEmpty())
    }

    @Test fun `all existing service references survive save without an active directory filter`() = runTest {
        val store = Store()
        val editor = ProposalEditor(SavedStateHandle(), store, this)
        editor.open(2)
        advanceUntilIdle()
        editor.save()
        editor.save()
        advanceUntilIdle()
        assertEquals(1, store.saves)
        assertEquals(10L, store.saved.single().workTypeId)
        assertEquals(20L, store.savedMaterials.single().materialId)
        assertEquals(200L, store.saved.single().amount)
        assertEquals(100L, store.savedMaterials.single().amount)
        assertFalse(editor.state.value.dirty)
    }

    @Test fun `one incomplete line prevents saving the whole proposal`() = runTest {
        val store = Store()
        val editor = ProposalEditor(SavedStateHandle(), store, this)
        editor.open(2)
        advanceUntilIdle()
        editor.addService(11)
        editor.save()
        advanceUntilIdle()
        assertEquals(0, store.saves)
        assertEquals(2, editor.state.value.lines.size)
        assertEquals(R.string.proposal_invalid, editor.state.value.error)
    }

    @Test fun `save failure retains input and can be retried`() = runTest {
        val store = Store().apply { failSave = true }
        val editor = ProposalEditor(SavedStateHandle(), store, this)
        editor.open(2)
        advanceUntilIdle()
        editor.updateService(editor.state.value.lines.single().copy(amount = "500"))
        editor.save()
        advanceUntilIdle()
        assertEquals("500", editor.state.value.lines.single().amount)
        assertTrue(editor.state.value.dirty)
        assertFalse(editor.state.value.saving)
        assertNotNull(editor.state.value.error)
        store.failSave = false
        editor.save()
        advanceUntilIdle()
        assertEquals(50000L, store.saved.single().amount)
        assertFalse(editor.state.value.dirty)
    }

    @Test fun `failed delete preserves selected proposal`() = runTest {
        val store = Store().apply { failDelete = true }
        val editor = ProposalEditor(SavedStateHandle(), store, this)
        editor.open(2)
        advanceUntilIdle()
        editor.delete(2)
        advanceUntilIdle()
        assertEquals(2L, editor.state.value.proposalId)
        assertEquals(1, editor.state.value.lines.size)
        assertNotNull(editor.state.value.error)
    }
}
