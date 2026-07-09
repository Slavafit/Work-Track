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

    init { checkLicense() }

    fun checkLicense() {
        viewModelScope.launch {
            _state.value = LicenseState.Loading
            _state.value = when (val r = LicenseManager.verify(getApplication())) {
                is VerifyResult.Active        -> LicenseState.Active
                is VerifyResult.Trial         -> LicenseState.Trial(r.expiresAt)
                is VerifyResult.NeedActivation-> LicenseState.NeedActivation
                is VerifyResult.Invalid       -> LicenseState.Invalid(r.reason)
            }
        }
    }

    fun activate(email: String) {
        viewModelScope.launch {
            _state.value = LicenseState.Loading
            _state.value = when (val r = LicenseManager.activate(getApplication(), email)) {
                is ActivateResult.Active      -> LicenseState.Active
                is ActivateResult.Trial       -> LicenseState.Trial(r.expiresAt)
                is ActivateResult.TrialExpired-> LicenseState.Invalid("trial_expired")
                is ActivateResult.Error       -> LicenseState.Error(r.message)
            }
        }
    }
}

sealed class LicenseState {
    object Loading                          : LicenseState()
    object Active                           : LicenseState()
    data class Trial(val expiresAt: Long)   : LicenseState()
    object NeedActivation                   : LicenseState()
    data class Invalid(val reason: String)  : LicenseState()
    data class Error(val message: String)   : LicenseState()
}
