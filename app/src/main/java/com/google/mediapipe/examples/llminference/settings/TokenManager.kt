package com.google.mediapipe.examples.llminference.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Secure storage for Hugging Face authentication token.
 * Uses EncryptedSharedPreferences (AES-256-GCM via Android Keystore).
 */
class TokenManager(context: Context) {

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val sharedPreferences = EncryptedSharedPreferences.create(
        "secure_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_HF_TOKEN = "huggingface_token"
        private const val KEY_USE_TOKEN = "use_direct_token"
        private const val KEY_HF_USERNAME = "huggingface_username"
        private const val KEY_HF_DISPLAY_NAME = "huggingface_display_name"
        private const val KEY_TOKEN_VERIFIED = "huggingface_token_verified"
    }

    // ── Token ─────────────────────────────────────────────────────────

    /** Save Hugging Face token */
    fun saveToken(token: String) {
        sharedPreferences.edit()
            .putString(KEY_HF_TOKEN, token)
            .putBoolean(KEY_USE_TOKEN, true)
            .apply()
    }

    /** Get saved Hugging Face token */
    fun getToken(): String? {
        return if (shouldUseToken()) {
            sharedPreferences.getString(KEY_HF_TOKEN, null)
        } else {
            null
        }
    }

    /** Check if direct token authentication should be used */
    fun shouldUseToken(): Boolean {
        return sharedPreferences.getBoolean(KEY_USE_TOKEN, false)
    }

    /** Check if token is saved */
    fun hasToken(): Boolean {
        return !getToken().isNullOrBlank()
    }

    // ── Verified user info ────────────────────────────────────────────

    /** Save the HF username + display name after successful verification */
    fun saveVerifiedUser(username: String, displayName: String?) {
        sharedPreferences.edit()
            .putString(KEY_HF_USERNAME, username)
            .putString(KEY_HF_DISPLAY_NAME, displayName ?: "")
            .putBoolean(KEY_TOKEN_VERIFIED, true)
            .apply()
    }

    fun getUsername(): String? = sharedPreferences.getString(KEY_HF_USERNAME, null)

    fun getDisplayName(): String? =
        sharedPreferences.getString(KEY_HF_DISPLAY_NAME, null)?.ifBlank { null }

    fun isTokenVerified(): Boolean =
        sharedPreferences.getBoolean(KEY_TOKEN_VERIFIED, false)

    // ── Clear ─────────────────────────────────────────────────────────

    /** Clear everything: token, user info, verified flag */
    fun clearToken() {
        sharedPreferences.edit()
            .remove(KEY_HF_TOKEN)
            .remove(KEY_HF_USERNAME)
            .remove(KEY_HF_DISPLAY_NAME)
            .putBoolean(KEY_USE_TOKEN, false)
            .putBoolean(KEY_TOKEN_VERIFIED, false)
            .apply()
    }
}
