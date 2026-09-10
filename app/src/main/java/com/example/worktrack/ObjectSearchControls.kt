package com.example.worktrack

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun ObjectSearchControls(query: String, status: String, withBalance: Boolean, count: Int, vm: AppViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(query, vm::setObjectSearch, modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.object_search_label)) },
            supportingText = { Text(stringResource(R.string.object_search_hint)) },
            trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { vm.setObjectSearch("") }) { Text(stringResource(R.string.object_search_clear)) } })
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to R.string.objects_all, "active" to R.string.objects_active, "completed" to R.string.section_completed).forEach { (value, title) ->
                FilterChip(selected = status == value, onClick = { vm.setObjectStatus(value) }, label = { Text(stringResource(title)) })
            }
        }
        FilterChip(selected = withBalance, onClick = { vm.setObjectsWithBalance(!withBalance) }, label = { Text(stringResource(R.string.objects_with_balance)) })
        Text(stringResource(R.string.objects_found, count), style = MaterialTheme.typography.bodySmall)
        if (query.isNotEmpty() || status != "all" || withBalance) TextButton(onClick = vm::resetObjectSearch) {
            Text(stringResource(R.string.object_search_reset))
        }
    }
}
