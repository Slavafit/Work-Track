package com.example.worktrack.license

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LicenseViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow<LicenseState>(LicenseState.Loading)
    val state: StateFlow<LicenseState> = _state
    private val _email = MutableStateFlow<String?>(null)
    val email: StateFlow<String?> = _email
    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking
    private var verifyJob: Job? = null
    private var verifyGeneration = 0L

    init {
        verify(showLoading = true)
    }

    fun checkLicense() = verify(showLoading = false)

    fun refreshOnResume() {
        if (_state.value !is LicenseState.Loading) verify(showLoading = false)
    }

    private fun verify(showLoading: Boolean) {
        val request = ++verifyGeneration
        verifyJob?.cancel()
        verifyJob = viewModelScope.launch {
            _checking.value = true
            if (showLoading) _state.value = LicenseState.Loading
            try {
                _state.value = when (val result = LicenseManager.verify(getApplication())) {
                    is VerifyResult.Active -> LicenseState.Active(result.expiresAt)
                    is VerifyResult.Trial -> LicenseState.Trial(result.expiresAt)
                    is VerifyResult.NeedActivation -> LicenseState.NeedActivation
                    is VerifyResult.Invalid -> LicenseState.Invalid(result.reason, result.expiresAt)
                    is VerifyResult.Error -> LicenseState.Error(result.message)
                }
                _email.value = LicenseManager.savedEmail(getApplication())
            } finally {
                if (request == verifyGeneration) _checking.value = false
            }
        }
    }

    fun activate(email: String) {
        viewModelScope.launch {
            _state.value = LicenseState.Loading
            _state.value = when (val result = LicenseManager.activate(getApplication(), email)) {
                is ActivateResult.Active -> LicenseState.Active(result.expiresAt)
                is ActivateResult.Trial -> LicenseState.Trial(result.expiresAt)
                is ActivateResult.Pending -> LicenseState.Pending(result.message)
                is ActivateResult.TrialExpired -> LicenseState.Invalid("trial_expired")
                is ActivateResult.Error -> {
                    val reason = result.message.trim()
                    if (reason == "expired" || reason == "trial_expired") LicenseState.Invalid(reason) else LicenseState.Error(result.message)
                }
            }
            _email.value = LicenseManager.savedEmail(getApplication()) ?: email
        }
    }
}

sealed class LicenseState {
    data object Loading : LicenseState()
    data class Active(val expiresAt: Long) : LicenseState()
    data class Trial(val expiresAt: Long) : LicenseState()
    data class Pending(val message: String) : LicenseState()
    data object NeedActivation : LicenseState()
    data class Invalid(val reason: String, val expiresAt: Long = 0L) : LicenseState()
    data class Error(val message: String) : LicenseState()
}

enum class LicenseAccessMode { FULL, EXPIRED_READ_ONLY, NETWORK_READ_ONLY, INVALID_READ_ONLY, BLOCKED }

fun LicenseState.accessMode(): LicenseAccessMode = when (this) {
    is LicenseState.Active, is LicenseState.Trial -> LicenseAccessMode.FULL
    is LicenseState.Invalid -> when (reason) {
        "expired", "trial_expired" -> LicenseAccessMode.EXPIRED_READ_ONLY
        else -> LicenseAccessMode.INVALID_READ_ONLY
    }
    is LicenseState.Error -> LicenseAccessMode.NETWORK_READ_ONLY
    is LicenseState.Loading, is LicenseState.Pending, is LicenseState.NeedActivation -> LicenseAccessMode.BLOCKED
}
