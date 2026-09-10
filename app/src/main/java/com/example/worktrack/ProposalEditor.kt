package com.example.worktrack

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import com.example.worktrack.data.ClosedObjectException
import com.example.worktrack.data.ProposalItem
import com.example.worktrack.data.ProposalMaterialItem
import com.example.worktrack.data.ProposalStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProposalLine(val id: Long, val workTypeId: Long, val amount: String)
data class ProposalMaterialLine(val id: Long, val materialId: Long, val amount: String)

data class ProposalDraft(
    val proposalId: Long? = null,
    val objectId: Long = 0,
    val lines: List<ProposalLine> = emptyList(),
    val materialLines: List<ProposalMaterialLine> = emptyList(),
    val dirty: Boolean = false,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: Int? = null
) {
    val busy get() = loading || saving
    val total: Long? get() {
        val values = (lines.map { it.amount } + materialLines.map { it.amount }).map { parseAmount(it) ?: return null }
        return checkedAmountTotal(values)
    }
    val valid get() = objectId != 0L && (lines.isNotEmpty() || materialLines.isNotEmpty()) &&
        lines.all { it.workTypeId != 0L } && materialLines.all { it.materialId != 0L } && total != null
}

/** Activity-owned editor: one atomic load, cancellation on switching, and saved user input. */
class ProposalEditor(
    private val savedState: SavedStateHandle,
    private val store: ProposalStore,
    private val scope: CoroutineScope,
    private val draftStore: ProposalDraftStore? = null
) {
    private val mutableState = MutableStateFlow(draftStore?.read() ?: restore(savedState[KEY]))
    val state = mutableState.asStateFlow()
    private var loadJob: Job? = null
    private var generation = 0L

    private fun publish(draft: ProposalDraft) {
        mutableState.value = draft
        savedState[KEY] = Bundle().apply {
            putLong("id", draft.proposalId ?: 0)
            putLong("object", draft.objectId)
            putBoolean("dirty", draft.dirty)
            putStringArrayList("services", ArrayList(draft.lines.flatMap { listOf(it.id.toString(), it.workTypeId.toString(), it.amount) }))
            putStringArrayList("materials", ArrayList(draft.materialLines.flatMap { listOf(it.id.toString(), it.materialId.toString(), it.amount) }))
        }
        draftStore?.write(draft)
    }

    private fun edit(change: (ProposalDraft) -> ProposalDraft) {
        if (!state.value.busy) publish(change(state.value).copy(dirty = true, error = null))
    }

    fun selectObject(id: Long) = edit { it.copy(objectId = id) }
    fun addService(id: Long) = edit { it.copy(lines = it.lines + ProposalLine(nextId(it), id, "")) }
    fun addMaterial(id: Long) = edit { it.copy(materialLines = it.materialLines + ProposalMaterialLine(nextId(it), id, "")) }
    fun updateService(line: ProposalLine) = edit { it.copy(lines = it.lines.map { old -> if (old.id == line.id) line else old }) }
    fun updateMaterial(line: ProposalMaterialLine) = edit { it.copy(materialLines = it.materialLines.map { old -> if (old.id == line.id) line else old }) }
    fun removeService(id: Long) = edit { it.copy(lines = it.lines.filterNot { line -> line.id == id }) }
    fun removeMaterial(id: Long) = edit { it.copy(materialLines = it.materialLines.filterNot { line -> line.id == id }) }

    fun newDraft() {
        if (state.value.saving) return
        generation++
        loadJob?.cancel()
        publish(ProposalDraft())
    }

    fun open(id: Long) {
        if (state.value.saving) return
        val request = ++generation
        loadJob?.cancel()
        publish(state.value.copy(loading = true, error = null))
        loadJob = scope.launch {
            try {
                val result = store.proposalSnapshot(id)
                if (request == generation) publish(ProposalDraft(
                    proposalId = result.proposal.id,
                    objectId = result.proposal.objectId,
                    lines = result.items.map { ProposalLine(it.id, it.workTypeId, it.amount.amountInput()) },
                    materialLines = result.materialItems.map { ProposalMaterialLine(it.id, it.materialId, it.amount.amountInput()) }
                ))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (request == generation) publish(state.value.copy(loading = false, error = R.string.operation_failed))
            }
        }
    }

    fun save() {
        val draft = state.value
        if (draft.busy) return
        if (!draft.valid) {
            publish(draft.copy(error = R.string.proposal_invalid))
            return
        }
        publish(draft.copy(saving = true, error = null))
        scope.launch {
            try {
                val id = store.saveProposal(draft.proposalId, draft.objectId,
                    draft.lines.map { ProposalItem(workTypeId = it.workTypeId, amount = requireNotNull(parseAmount(it.amount)), proposalId = 0) },
                    draft.materialLines.map { ProposalMaterialItem(materialId = it.materialId, amount = requireNotNull(parseAmount(it.amount)), proposalId = 0) })
                publish(draft.copy(proposalId = id, dirty = false))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                publish(draft.copy(error = errorMessage(e)))
            }
        }
    }

    fun delete(id: Long) {
        if (state.value.busy) return
        val draft = state.value
        publish(draft.copy(saving = true, error = null))
        scope.launch {
            try {
                store.deleteProposal(id)
                publish(if (draft.proposalId == id) ProposalDraft() else draft)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                publish(draft.copy(error = errorMessage(e)))
            }
        }
    }

    private fun nextId(draft: ProposalDraft) =
        minOf(0L, (draft.lines.map { it.id } + draft.materialLines.map { it.id }).minOrNull() ?: 0L) - 1

    companion object {
        private const val KEY = "proposal_draft"
        private fun errorMessage(e: Exception) = when (e) {
            is ClosedObjectException -> R.string.object_read_only
            is InvalidAmountException -> R.string.amount_total_invalid
            else -> R.string.operation_failed
        }
        private fun restore(bundle: Bundle?): ProposalDraft {
            if (bundle == null) return ProposalDraft()
            return ProposalDraft(
                proposalId = bundle.getLong("id").takeIf { it != 0L },
                objectId = bundle.getLong("object"),
                dirty = bundle.getBoolean("dirty"),
                lines = bundle.getStringArrayList("services").orEmpty().chunked(3).map { ProposalLine(it[0].toLong(), it[1].toLong(), it[2]) },
                materialLines = bundle.getStringArrayList("materials").orEmpty().chunked(3).map { ProposalMaterialLine(it[0].toLong(), it[1].toLong(), it[2]) }
            )
        }
    }
}
