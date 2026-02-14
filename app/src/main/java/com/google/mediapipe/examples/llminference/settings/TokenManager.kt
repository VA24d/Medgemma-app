package com.google.mediapipe.examples.llminference.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Secure storage for Hugging Face authentication token
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
    }

    /**
     * Save Hugging Face token
     */
    fun saveToken(token: String) {
        sharedPreferences.edit()
            .putString(KEY_HF_TOKEN, token)
            .putBoolean(KEY_USE_TOKEN, true)
            .apply()
    }

    /**
     * Get saved Hugging Face token
     */
    fun getToken(): String? {
        return if (shouldUseToken()) {
            sharedPreferences.getString(KEY_HF_TOKEN, null)
        } else {
            null
        }
    }

    /**
     * Check if direct token authentication should be used
     */
    fun shouldUseToken(): Boolean {
        return sharedPreferences.getBoolean(KEY_USE_TOKEN, false)
    }

    /**
     * Clear saved token and revert to OAuth
     */
    fun clearToken() {
        sharedPreferences.edit()
            .remove(KEY_HF_TOKEN)
            .putBoolean(KEY_USE_TOKEN, false)
            .apply()
    }

    /**
     * Check if token is saved
     */
    fun hasToken(): Boolean {
        return !getToken().isNullOrBlank()
    }
}
