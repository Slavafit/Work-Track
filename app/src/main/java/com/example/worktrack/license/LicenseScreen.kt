package com.example.worktrack.license

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun LicenseGate(
    viewModel: LicenseViewModel = viewModel(),
    content: @Composable () -> Unit
) {
    when (val state = viewModel.state.collectAsState().value) {
        is LicenseState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is LicenseState.Active -> content()
        is LicenseState.Trial -> content()
        is LicenseState.NeedActivation -> ActivationScreen(viewModel)
        is LicenseState.Invalid -> InvalidScreen(state.reason, viewModel)
        is LicenseState.Error -> ErrorScreen(state.message, viewModel)
    }
}

@Composable
private fun ActivationScreen(viewModel: LicenseViewModel) {
    var email by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    LicenseForm(
        title = "WorkTrack",
        message = "Ð’Ð²ÐµÐ´Ð¸Ñ‚Ðµ email Ð´Ð»Ñ Ð½Ð°Ñ‡Ð°Ð»Ð° Ñ€Ð°Ð±Ð¾Ñ‚Ñ‹. ÐŸÑ€Ð¾Ð±Ð½Ñ‹Ð¹ Ð´Ð¾ÑÑ‚ÑƒÐ¿ Ð´ÐµÐ¹ÑÑ‚Ð²ÑƒÐµÑ‚ 3 Ð´Ð½Ñ.",
        email = email,
        error = error,
        onEmailChange = { email = it; error = false },
        button = "ÐÐ°Ñ‡Ð°Ñ‚ÑŒ Ð¿Ñ€Ð¾Ð±Ð½Ñ‹Ð¹ Ð¿ÐµÑ€Ð¸Ð¾Ð´",
        onSubmit = {
            if (email.isValidEmail()) viewModel.activate(email.trim()) else error = true
        }
    )
}

@Composable
private fun InvalidScreen(reason: String, viewModel: LicenseViewModel) {
    val title = if (reason == "trial_expired") "ÐŸÑ€Ð¾Ð±Ð½Ñ‹Ð¹ Ð¿ÐµÑ€Ð¸Ð¾Ð´ Ð·Ð°Ð²ÐµÑ€ÑˆÑ‘Ð½" else "ÐÐµÑ‚ Ð´Ð¾ÑÑ‚ÑƒÐ¿Ð°"
    val message = when (reason) {
        "trial_expired" -> "Ð—Ð°Ð¿Ñ€Ð¾ÑÐ¸Ñ‚Ðµ Ð¿Ð¾Ð»Ð½ÑƒÑŽ Ð»Ð¸Ñ†ÐµÐ½Ð·Ð¸ÑŽ Ñƒ Ñ€Ð°Ð·Ñ€Ð°Ð±Ð¾Ñ‚Ñ‡Ð¸ÐºÐ°."
        "revoked" -> "Ð›Ð¸Ñ†ÐµÐ½Ð·Ð¸Ñ Ð¾Ñ‚Ð¾Ð·Ð²Ð°Ð½Ð°. ÐžÐ±Ñ€Ð°Ñ‚Ð¸Ñ‚ÐµÑÑŒ Ðº Ñ€Ð°Ð·Ñ€Ð°Ð±Ð¾Ñ‚Ñ‡Ð¸ÐºÑƒ."
        "expired" -> "Ð¡Ñ€Ð¾Ðº Ð»Ð¸Ñ†ÐµÐ½Ð·Ð¸Ð¸ Ð¸ÑÑ‚Ñ‘Ðº. ÐžÐ±Ñ€Ð°Ñ‚Ð¸Ñ‚ÐµÑÑŒ Ðº Ñ€Ð°Ð·Ñ€Ð°Ð±Ð¾Ñ‚Ñ‡Ð¸ÐºÑƒ."
        "device_mismatch" -> "Ð›Ð¸Ñ†ÐµÐ½Ð·Ð¸Ñ Ð¿Ñ€Ð¸Ð²ÑÐ·Ð°Ð½Ð° Ðº Ð´Ñ€ÑƒÐ³Ð¾Ð¼Ñƒ ÑƒÑÑ‚Ñ€Ð¾Ð¹ÑÑ‚Ð²Ñƒ."
        else -> "Ð›Ð¸Ñ†ÐµÐ½Ð·Ð¸Ñ Ð½ÐµÐ´ÐµÐ¹ÑÑ‚Ð²Ð¸Ñ‚ÐµÐ»ÑŒÐ½Ð°. ÐžÐ±Ñ€Ð°Ñ‚Ð¸Ñ‚ÐµÑÑŒ Ðº Ñ€Ð°Ð·Ñ€Ð°Ð±Ð¾Ñ‚Ñ‡Ð¸ÐºÑƒ."
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = viewModel::checkLicense, modifier = Modifier.fillMaxWidth()) {
            Text("ÐŸÑ€Ð¾Ð²ÐµÑ€Ð¸Ñ‚ÑŒ ÑÐ½Ð¾Ð²Ð°")
        }
    }
}

@Composable
private fun ErrorScreen(message: String, viewModel: LicenseViewModel) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("ÐžÑˆÐ¸Ð±ÐºÐ° Ð¿Ð¾Ð´ÐºÐ»ÑŽÑ‡ÐµÐ½Ð¸Ñ", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = viewModel::checkLicense, modifier = Modifier.fillMaxWidth()) {
            Text("ÐŸÐ¾Ð²Ñ‚Ð¾Ñ€Ð¸Ñ‚ÑŒ")
        }
    }
}

@Composable
private fun LicenseForm(
    title: String,
    message: String,
    email: String,
    error: Boolean,
    onEmailChange: (String) -> Unit,
    button: String,
    onSubmit: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text("Email") },
            isError = error,
            supportingText = if (error) ({ Text("Ð’Ð²ÐµÐ´Ð¸Ñ‚Ðµ ÐºÐ¾Ñ€Ñ€ÐµÐºÑ‚Ð½Ñ‹Ð¹ email") }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth()) {
            Text(button)
        }
    }
}

private fun String.isValidEmail(): Boolean = contains("@") && contains(".") && length >= 5
