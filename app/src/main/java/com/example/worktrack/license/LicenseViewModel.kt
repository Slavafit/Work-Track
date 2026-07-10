package com.example.worktrack.license

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LicenseViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow<LicenseState>(LicenseState.Loading)
    val state: StateFlow<LicenseState> = _state
    private val _email = MutableStateFlow<String?>(null)
    val email: StateFlow<String?> = _email

    init {
        checkLicense()
    }

    fun checkLicense() {
        viewModelScope.launch {
            _state.value = LicenseState.Loading
            _state.value = when (val result = LicenseManager.verify(getApplication())) {
                is VerifyResult.Active -> LicenseState.Active
                is VerifyResult.Trial -> LicenseState.Trial(result.expiresAt)
                is VerifyResult.NeedActivation -> LicenseState.NeedActivation
                is VerifyResult.Invalid -> LicenseState.Invalid(result.reason)
            }
            _email.value = LicenseManager.savedEmail(getApplication())
        }
    }

    fun activate(email: String) {
        viewModelScope.launch {
            _state.value = LicenseState.Loading
            _state.value = when (val result = LicenseManager.activate(getApplication(), email)) {
                is ActivateResult.Active -> LicenseState.Active
                is ActivateResult.Trial -> LicenseState.Trial(result.expiresAt)
                is ActivateResult.Pending -> LicenseState.Pending(result.message)
                is ActivateResult.TrialExpired -> LicenseState.Invalid("trial_expired")
                is ActivateResult.Error -> LicenseState.Error(result.message)
            }
            _email.value = LicenseManager.savedEmail(getApplication()) ?: email
        }
    }
}

sealed class LicenseState {
    data object Loading : LicenseState()
    data object Active : LicenseState()
    data class Trial(val expiresAt: Long) : LicenseState()
    data class Pending(val message: String) : LicenseState()
    data object NeedActivation : LicenseState()
    data class Invalid(val reason: String) : LicenseState()
    data class Error(val message: String) : LicenseState()
}
