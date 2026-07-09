package com.example.worktrack.license

import android.content.Context
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

val Context.licenseDataStore: DataStore<Preferences> by preferencesDataStore(name = "license")

object LicenseManager {

    private const val BASE_URL  = "https://license-server.slavafit.workers.dev"
    private const val APP_ID    = "worktrack"
    private const val CACHE_TTL = 24 * 60 * 60 * 1000L // 24 часа

    private val KEY_TOKEN      = stringPreferencesKey("license_token")
    private val KEY_EMAIL      = stringPreferencesKey("license_email")
    private val KEY_STATUS     = stringPreferencesKey("license_status") // trial | active | revoked | trial_expired
    private val KEY_EXPIRES_AT = longPreferencesKey("license_expires_at") // unix seconds, 0 = бессрочно
    private val KEY_CHECKED_AT = longPreferencesKey("license_checked_at")

    // ── Публичный API ────────────────────────────────────────────────────────

    /** Первый запуск — регистрация. Сразу получаем триал на 3 дня. */
    suspend fun activate(context: Context, email: String): ActivateResult {
        val deviceId = getDeviceId(context)
        return try {
            val body = JSONObject().apply {
                put("email", email)
                put("device_id", deviceId)
                put("app_id", APP_ID)
            }
            val response = post("$BASE_URL/activate", body)
            val ok     = response.optBoolean("ok", false)
            val status = response.optString("status", "")
            val token  = response.optString("token", "")
            val expiresAt = response.optLong("expires_at", 0L)

            if (ok && token.isNotEmpty()) {
                saveState(context, email, token, status, expiresAt)
                if (status == "trial") ActivateResult.Trial(expiresAt)
                else ActivateResult.Active
            } else {
                val reason = response.optString("reason", "")
                if (reason == "trial_expired") ActivateResult.TrialExpired
                else ActivateResult.Error(response.optString("error", "Unknown error"))
            }
        } catch (e: Exception) {
            ActivateResult.Error(e.message ?: "Network error")
        }
    }

    /** Проверка при каждом запуске (кэш 24ч) */
    suspend fun verify(context: Context): VerifyResult {
        val prefs     = context.licenseDataStore.data.first()
        val token     = prefs[KEY_TOKEN]      ?: ""
        val status    = prefs[KEY_STATUS]     ?: ""
        val email     = prefs[KEY_EMAIL]      ?: ""
        val expiresAt = prefs[KEY_EXPIRES_AT] ?: 0L
        val checkedAt = prefs[KEY_CHECKED_AT] ?: 0L

        // Нет данных — нужна активация
        if (email.isEmpty() || token.isEmpty()) return VerifyResult.NeedActivation

        // Кэш свежий — проверяем локально
        if (System.currentTimeMillis() - checkedAt < CACHE_TTL) {
            return localCheck(status, expiresAt)
        }

        // Идём в сеть
        return try {
            val deviceId = getDeviceId(context)
            val body = JSONObject().apply {
                put("token", token)
                put("device_id", deviceId)
                put("app_id", APP_ID)
            }
            val response = post("$BASE_URL/verify", body)
            val valid  = response.optBoolean("valid", false)
            val newStatus = response.optString("status", status)
            val newExpires = response.optLong("expires_at", expiresAt)

            if (valid) {
                saveState(context, email, token, newStatus, newExpires)
                if (newStatus == "trial") VerifyResult.Trial(newExpires)
                else VerifyResult.Active
            } else {
                val reason = response.optString("reason", "invalid")
                saveState(context, email, token, reason, expiresAt)
                VerifyResult.Invalid(reason)
            }
        } catch (e: Exception) {
            // Нет интернета — доверяем кэшу
            localCheck(status, expiresAt)
        }
    }

    /** Сброс (переустановка / смена аккаунта) */
    suspend fun reset(context: Context) {
        context.licenseDataStore.edit { it.clear() }
    }

    fun getDeviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

    // ── Приватные утилиты ────────────────────────────────────────────────────

    private fun localCheck(status: String, expiresAt: Long): VerifyResult {
        val nowSec = System.currentTimeMillis() / 1000
        return when {
            status == "trial" && expiresAt > 0 && expiresAt < nowSec -> VerifyResult.Invalid("trial_expired")
            status == "trial"   -> VerifyResult.Trial(expiresAt)
            status == "active"  -> VerifyResult.Active
            else                -> VerifyResult.Invalid(status)
        }
    }

    private suspend fun saveState(
        context: Context, email: String, token: String, status: String, expiresAt: Long
    ) {
        context.licenseDataStore.edit { prefs ->
            prefs[KEY_EMAIL]      = email
            prefs[KEY_TOKEN]      = token
            prefs[KEY_STATUS]     = status
            prefs[KEY_EXPIRES_AT] = expiresAt
            prefs[KEY_CHECKED_AT] = System.currentTimeMillis()
        }
    }

    private fun post(url: String, body: JSONObject): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val response = conn.inputStream.bufferedReader().readText()
        return JSONObject(response)
    }
}

// ── Sealed классы ────────────────────────────────────────────────────────────

sealed class ActivateResult {
    object Active                        : ActivateResult()
    data class Trial(val expiresAt: Long): ActivateResult()
    object TrialExpired                  : ActivateResult()
    data class Error(val message: String): ActivateResult()
}

sealed class VerifyResult {
    object Active                        : VerifyResult()
    data class Trial(val expiresAt: Long): VerifyResult()
    object NeedActivation                : VerifyResult()
    data class Invalid(val reason: String): VerifyResult()
}
