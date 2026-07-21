package com.example.worktrack

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.worktrack.data.LanguageMode
import com.example.worktrack.data.ThemeMode
import com.example.worktrack.license.LicenseState
import com.example.worktrack.license.LicenseViewModel
import kotlin.math.ceil
import kotlin.math.max

@Composable
fun AboutScreen(
    vm: AppViewModel,
    padding: PaddingValues,
    onOpenWorkers: () -> Unit,
    onOpenTypes: () -> Unit,
    licenseViewModel: LicenseViewModel = viewModel()
) {
    val settings by vm.settings.collectAsState()
    val licenseState by licenseViewModel.state.collectAsState()
    val licenseEmail by licenseViewModel.email.collectAsState()
    val uriHandler = LocalUriHandler.current
    var companyName by remember { mutableStateOf(settings.companyName) }
    LaunchedEffect(settings.companyName) {
        if (companyName.isBlank() && settings.companyName.isNotBlank()) {
            companyName = settings.companyName
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SettingsCard {
                Text("WorkTrack", style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(id = R.string.app_version, BuildConfig.VERSION_NAME), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(id = R.string.developer), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(
                    onClick = { uriHandler.openUri("https://t.me/Slavafit") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(id = R.string.developer_contacts))
                }
            }
        }
        item {
            SettingsCard(verticalGap = 6.dp) {
                SectionTitle(stringResource(R.string.section_license))
                Text(licenseState.title(), style = MaterialTheme.typography.bodyLarge)
                Text(licenseState.detail(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = licenseEmail?.let { stringResource(R.string.license_email_format, it) }
                        ?: stringResource(R.string.license_email_missing),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            SettingsCard {
                SectionTitle(stringResource(R.string.section_company))
                OutlinedTextField(
                    value = companyName,
                    onValueChange = {
                        companyName = it
                        vm.setCompanyName(it)
                    },
                    label = { Text(stringResource(R.string.label_company_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }
        item {
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    SectionTitle(stringResource(R.string.section_directories))
                }
                OutlinedButton(onClick = onOpenWorkers, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.People, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.tab_workers))
                }
                OutlinedButton(onClick = onOpenTypes, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Construction, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.tab_types))
                }
            }
        }
        item {
            SettingsCard {
                SectionTitle(stringResource(R.string.section_theme))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = settings.themeMode == mode,
                            onClick = { vm.setTheme(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                            modifier = Modifier.weight(1f),
                            label = {
                                Text(
                                    when (mode) {
                                        ThemeMode.System -> stringResource(R.string.theme_system)
                                        ThemeMode.Light -> stringResource(R.string.theme_light)
                                        ThemeMode.Dark -> stringResource(R.string.theme_dark)
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }
        }
        item {
            SettingsCard {
                SectionTitle(stringResource(R.string.section_language))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    LanguageMode.entries.forEachIndexed { index, lang ->
                        SegmentedButton(
                            selected = settings.language == lang,
                            onClick = { vm.setLanguage(lang) },
                            shape = SegmentedButtonDefaults.itemShape(index, LanguageMode.entries.size),
                            modifier = Modifier.weight(1f),
                            label = {
                                Text(
                                    stringResource(lang.titleRes()),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    verticalGap: androidx.compose.ui.unit.Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(verticalGap),
            content = content
        )
    }
}

@Composable
private fun LicenseState.title(): String = when (this) {
    is LicenseState.Active -> stringResource(R.string.license_status_active)
    is LicenseState.Trial -> stringResource(R.string.license_status_trial)
    is LicenseState.Pending -> stringResource(R.string.license_status_pending)
    is LicenseState.NeedActivation -> stringResource(R.string.license_status_need_activation)
    is LicenseState.Invalid -> stringResource(
        when (reason) {
            "trial_expired" -> R.string.license_trial_expired_title
            "expired" -> R.string.license_expired_title
            else -> R.string.license_status_invalid
        }
    )
    is LicenseState.Error -> stringResource(R.string.connection_error_title)
    is LicenseState.Loading -> stringResource(R.string.license_status_checking)
}

@Composable
private fun LicenseState.detail(): String = when (this) {
    is LicenseState.Active -> stringResource(R.string.license_active_detail)
    is LicenseState.Trial -> stringResource(R.string.license_trial_days_format, daysLeft(expiresAt))
    is LicenseState.Pending -> stringResource(R.string.license_pending_detail)
    is LicenseState.NeedActivation -> stringResource(R.string.license_need_activation_detail)
    is LicenseState.Invalid -> when (reason) {
        "trial_expired", "expired" -> stringResource(R.string.license_trial_days_format, daysLeft(expiresAt))
        else -> stringResource(R.string.license_invalid_detail)
    }
    is LicenseState.Error -> stringResource(R.string.connection_error_message)
    is LicenseState.Loading -> stringResource(R.string.license_checking_detail)
}

private fun daysLeft(expiresAtSeconds: Long): Int {
    val millisLeft = expiresAtSeconds * 1000L - System.currentTimeMillis()
    return max(0, ceil(millisLeft / 86_400_000.0).toInt())
}
