package com.zip2git.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.zip2git.data.remote.NetworkModule

/**
 * Stores the Personal Access Token in [EncryptedSharedPreferences], falling back
 * to a private, unencrypted preference file if the keystore is unavailable (for
 * example on some emulators or after a backup restore). The fallback mirrors the
 * original app's `zip2git_secure_prefs_fallback` behaviour.
 */
class TokenStore(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy { createPrefs() }

    init {
        // Feed the current token to the network layer on startup.
        NetworkModule.token = token
    }

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }
        set(value) {
            val clean = value?.trim()?.takeIf { it.isNotBlank() }
            prefs.edit().apply {
                if (clean == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, clean)
            }.apply()
            NetworkModule.token = clean
        }

    var lastUserLogin: String?
        get() = prefs.getString(KEY_LOGIN, null)
        set(value) { prefs.edit().putString(KEY_LOGIN, value).apply() }

    fun hasToken(): Boolean = !token.isNullOrBlank()

    private fun createPrefs(): SharedPreferences =
        runCatching {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                SECURE_PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            ) as SharedPreferences
        }.getOrElse {
            appContext.getSharedPreferences(FALLBACK_PREFS, Context.MODE_PRIVATE)
        }

    private companion object {
        const val SECURE_PREFS = "zip2git_secure_prefs"
        const val FALLBACK_PREFS = "zip2git_secure_prefs_fallback"
        const val KEY_TOKEN = "git_pat"
        const val KEY_LOGIN = "git_login"
    }
}
