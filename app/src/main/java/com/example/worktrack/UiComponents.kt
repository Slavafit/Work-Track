package com.example.worktrack

import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.example.worktrack.data.LanguageMode

@Composable
fun <T> EntityChips(items: List<T>, selectedId: Long, idOf: (T) -> Long, titleOf: (T) -> String, onSelect: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            val id = idOf(item)
            FilterChip(
                selected = selectedId == id,
                onClick = { onSelect(id) },
                label = { Text(titleOf(item), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = if (selectedId == id) ({ Icon(Icons.Outlined.Check, null) }) else null
            )
        }
    }
}

@Composable
fun <T> EntityPickerField(
    label: String,
    items: List<T>,
    selectedId: Long,
    idOf: (T) -> Long,
    titleOf: (T) -> String,
    onSelect: (Long) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val selectedTitle = items.firstOrNull { idOf(it) == selectedId }?.let(titleOf)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = selectedTitle ?: stringResource(R.string.action_select),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (showPicker) {
        val filtered = remember(items, query) {
            val normalizedQuery = query.trim()
            if (normalizedQuery.isEmpty()) items
            else items.filter { titleOf(it).contains(normalizedQuery, ignoreCase = true) }
        }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(R.string.label_search)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        if (filtered.isEmpty()) {
                            item { EmptyText(stringResource(R.string.empty_search_results)) }
                        } else {
                            items(filtered, key = { idOf(it) }) { item ->
                                TextButton(
                                    onClick = {
                                        onSelect(idOf(item))
                                        showPicker = false
                                        query = ""
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(titleOf(item), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
fun EmptyText(text: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PhoneField(
    value: String,
    onValueChange: (String) -> Unit,
    onContactPicked: (name: String, phone: String) -> Unit = { _, phone -> onValueChange(phone) }
) {
    val context = LocalContext.current
    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        context.contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val phoneIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val contactName = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
                val contactPhone = if (phoneIndex >= 0) cursor.getString(phoneIndex).phoneDigits() else ""
                if (contactPhone.isNotEmpty()) onContactPicked(contactName, contactPhone)
            }
        }
    }
    val isValid = value.isEmpty() || value.length in 9..12

    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.phoneDigits()) },
        label = { Text(stringResource(R.string.label_phone)) },
        singleLine = true,
        isError = !isValid,
        supportingText = if (!isValid) ({ Text(stringResource(R.string.phone_error)) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        trailingIcon = {
            IconButton(
                onClick = {
                    contactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                }
            ) {
                Icon(Icons.Outlined.People, stringResource(R.string.action_pick_contact))
            }
        }
    )
}

fun String.phoneDigits(): String = filter(Char::isDigit).take(12)

fun String.isValidPhoneOrBlank(): Boolean = isBlank() || length in 9..12

@StringRes
fun LanguageMode.titleRes(): Int = when (this) {
    LanguageMode.System -> R.string.language_system
    LanguageMode.RU -> R.string.language_ru
    LanguageMode.EN -> R.string.language_en
    LanguageMode.ES -> R.string.language_es
}

fun Context.shareText(text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(intent, getString(R.string.action_share)))
}
