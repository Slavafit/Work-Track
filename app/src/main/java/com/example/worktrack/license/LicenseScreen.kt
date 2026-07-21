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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.ceil
import kotlin.math.max

@Composable
fun LicenseGate(
    viewModel: LicenseViewModel = viewModel(),
    content: @Composable () -> Unit
) {
    when (val state = viewModel.state.collectAsState().value) {
        is LicenseState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is LicenseState.Active -> content()
        is LicenseState.Trial -> content()
        is LicenseState.Pending -> PendingScreen(state.message, viewModel)
        is LicenseState.NeedActivation -> ActivationScreen(viewModel)
        is LicenseState.Invalid -> InvalidScreen(state.reason, state.expiresAt, viewModel)
        is LicenseState.Error -> ErrorScreen(state.message, viewModel)
    }
}

@Composable
private fun PendingScreen(message: String, viewModel: LicenseViewModel) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(com.example.worktrack.R.string.license_pending_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = viewModel::checkLicense, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(com.example.worktrack.R.string.action_check_again))
        }
    }
}

@Composable
private fun ActivationScreen(viewModel: LicenseViewModel) {
    var email by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    LicenseForm(
        title = stringResource(com.example.worktrack.R.string.app_name),
        message = stringResource(com.example.worktrack.R.string.license_activation_message),
        email = email,
        error = error,
        onEmailChange = { email = it; error = false },
        button = stringResource(com.example.worktrack.R.string.license_start_trial),
        onSubmit = {
            if (email.isValidEmail()) viewModel.activate(email.trim()) else error = true
        }
    )
}

@Composable
private fun InvalidScreen(reason: String, expiresAt: Long, viewModel: LicenseViewModel) {
    val title = stringResource(
        when (reason) {
            "trial_expired" -> com.example.worktrack.R.string.license_trial_expired_title
            "expired" -> com.example.worktrack.R.string.license_expired_title
            else -> com.example.worktrack.R.string.license_no_access_title
        }
    )
    val message = when (reason) {
        "trial_expired" -> stringResource(com.example.worktrack.R.string.license_trial_expired_message)
        "revoked" -> stringResource(com.example.worktrack.R.string.license_revoked_message)
        "expired" -> stringResource(com.example.worktrack.R.string.license_expired_message)
        "device_mismatch" -> stringResource(com.example.worktrack.R.string.license_device_mismatch_message)
        else -> stringResource(com.example.worktrack.R.string.license_invalid_message)
    }
    val daysText = if (reason == "trial_expired" || reason == "expired") {
        stringResource(com.example.worktrack.R.string.license_trial_days_format, daysLeft(expiresAt))
    } else {
        null
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
        daysText?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = viewModel::checkLicense, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(com.example.worktrack.R.string.action_check_again))
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
        Text(stringResource(com.example.worktrack.R.string.connection_error_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(com.example.worktrack.R.string.connection_error_message), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = viewModel::checkLicense, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(com.example.worktrack.R.string.action_retry))
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
            label = { Text(stringResource(com.example.worktrack.R.string.label_email)) },
            isError = error,
            supportingText = if (error) ({ Text(stringResource(com.example.worktrack.R.string.email_error)) }) else null,
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

private fun daysLeft(expiresAtSeconds: Long): Int {
    val millisLeft = expiresAtSeconds * 1000L - System.currentTimeMillis()
    return max(0, ceil(millisLeft / 86_400_000.0).toInt())
}
