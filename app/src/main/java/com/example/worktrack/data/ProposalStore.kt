package com.example.worktrack.data

data class ProposalSnapshot(
    val proposal: Proposal,
    val items: List<ProposalItem>,
    val materialItems: List<ProposalMaterialItem>
)

interface ProposalStore {
    suspend fun proposalSnapshot(id: Long): ProposalSnapshot
    suspend fun saveProposal(id: Long?, objectId: Long, items: List<ProposalItem>, materialItems: List<ProposalMaterialItem>): Long
    suspend fun deleteProposal(id: Long)
}
