package com.example.worktrack.license

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LicenseGate(
    content: @Composable () -> Unit,
    viewModel: LicenseViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    when (val s = state) {
        is LicenseState.Loading       -> LoadingScreen()
        is LicenseState.Active        -> content()
        is LicenseState.Trial         -> content() // триал — работает как обычно
        is LicenseState.NeedActivation-> ActivationScreen(viewModel)
        is LicenseState.Invalid       -> InvalidScreen(s.reason, viewModel)
        is LicenseState.Error         -> ErrorScreen(s.message, viewModel)
    }
}

// ── Загрузка ──────────────────────────────────────────────────────────────────

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

// ── Первый запуск: ввод email ─────────────────────────────────────────────────

@Composable
private fun ActivationScreen(viewModel: LicenseViewModel) {
    var email by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("WorkTrack", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Введите email для начала работы.\nПолучите 3 дня бесплатного доступа.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; emailError = false },
            label = { Text("Email") },
            isError = emailError,
            supportingText = if (emailError) {{ Text("Введите корректный email") }} else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (email.contains("@") && email.contains(".")) viewModel.activate(email.trim())
                else emailError = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Начать бесплатный триал")
        }
    }
}

// ── Триал истёк / лицензия недействительна ───────────────────────────────────

@Composable
private fun InvalidScreen(reason: String, viewModel: LicenseViewModel) {
    var email by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf(false) }
    var showForm by remember { mutableStateOf(false) }

    val isTrialExpired = reason == "trial_expired"

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(if (isTrialExpired) "⏰" else "⚠️", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(16.dp))
        Text(
            if (isTrialExpired) "Пробный период завершён" else "Нет доступа",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when (reason) {
                "trial_expired"   -> "Бесплатный период на 3 дня истёк. Запросите полную лицензию у разработчика."
                "revoked"         -> "Лицензия отозвана. Обратитесь к разработчику."
                "expired"         -> "Срок лицензии истёк. Обратитесь к разработчику."
                "device_mismatch" -> "Лицензия привязана к другому устройству."
                else              -> "Обратитесь к разработчику."
            },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))

        if (isTrialExpired) {
            if (!showForm) {
                Button(onClick = { showForm = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Запросить лицензию")
                }
            } else {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; emailError = false },
                    label = { Text("Email") },
                    isError = emailError,
                    supportingText = if (emailError) {{ Text("Введите корректный email") }} else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (email.contains("@") && email.contains(".")) viewModel.activate(email.trim())
                        else emailError = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Отправить запрос")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showForm = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("Отмена")
                }
            }
        } else {
            OutlinedButton(onClick = { viewModel.checkLicense() }, modifier = Modifier.fillMaxWidth()) {
                Text("Проверить снова")
            }
        }
    }
}

// ── Ошибка сети ───────────────────────────────────────────────────────────────

@Composable
private fun ErrorScreen(message: String, viewModel: LicenseViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("⚠️", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(16.dp))
        Text("Ошибка подключения", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = { viewModel.checkLicense() }, modifier = Modifier.fillMaxWidth()) {
            Text("Повторить")
        }
    }
}
