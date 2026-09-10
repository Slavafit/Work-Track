package com.example.worktrack

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local editor recovery, separate from saved proposals and manual archives. */
class ProposalDraftStore(context: Context) {
    private val preferences = context.getSharedPreferences("proposal_draft", Context.MODE_PRIVATE)

    fun read(): ProposalDraft? {
        if (!preferences.contains("snapshot")) return null
        return try {
            val json = JSONObject(requireNotNull(preferences.getString("snapshot", null)))
            require(json.getInt("version") == 1)
            val services = json.getJSONArray("services")
            val materials = json.getJSONArray("materials")
            ProposalDraft(
                proposalId = json.getLong("id").takeIf { it != 0L },
                objectId = json.getLong("object"),
                dirty = json.getBoolean("dirty"),
                lines = (0 until services.length()).map { index ->
                    val row = services.getJSONArray(index)
                    ProposalLine(row.getLong(0), row.getLong(1), row.getString(2))
                },
                materialLines = (0 until materials.length()).map { index ->
                    val row = materials.getJSONArray(index)
                    ProposalMaterialLine(row.getLong(0), row.getLong(1), row.getString(2))
                }
            )
        } catch (_: Exception) {
            ProposalDraft(error = R.string.draft_restore_failed)
        }
    }

    fun write(draft: ProposalDraft) {
        val json = JSONObject().put("version", 1)
            .put("id", draft.proposalId ?: 0L).put("object", draft.objectId).put("dirty", draft.dirty)
            .put("services", JSONArray().apply {
                draft.lines.forEach { put(JSONArray(listOf(it.id, it.workTypeId, it.amount))) }
            })
            .put("materials", JSONArray().apply {
                draft.materialLines.forEach { put(JSONArray(listOf(it.id, it.materialId, it.amount))) }
            })
        // apply updates memory immediately and queues an atomic disk write off the UI thread.
        // Keep an empty snapshot after reset so older Activity state cannot revive a discarded draft.
        preferences.edit().putString("snapshot", json.toString()).apply()
    }
}
