package com.example.worktrack

import com.example.worktrack.data.ObjectSummary
import java.text.Normalizer
import java.util.Locale

private fun searchText(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")

fun filterObjects(objects: List<ObjectSummary>, query: String, status: String, withBalance: Boolean): List<ObjectSummary> {
    val text = searchText(query.trim())
    val phoneQuery = text.filter { it in '0'..'9' }.takeIf {
        it.isNotEmpty() && text.all { c -> c in '0'..'9' || c.isWhitespace() || c in "+()- ." }
    }
    val terms = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
    return objects.filter { obj ->
        val statusMatches = when (status) { "active" -> !obj.isCompleted; "completed" -> obj.isCompleted; else -> true }
        val searchable = searchText("${obj.address} ${obj.clientName} ${obj.clientPhone.orEmpty()}")
        val textMatches = terms.all { searchable.contains(it) }
        val phoneMatches = phoneQuery != null && obj.clientPhone.orEmpty().filter { it in '0'..'9' }.contains(phoneQuery)
        statusMatches && (!withBalance || obj.totalAmount > obj.paidAmount) && (textMatches || phoneMatches)
    }
}
