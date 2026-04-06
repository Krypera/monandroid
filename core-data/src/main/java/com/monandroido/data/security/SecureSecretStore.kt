package com.monandroido.data.security

import android.annotation.SuppressLint
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureSecretStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        EncryptedSharedPreferences.create(
            appContext,
            FILE_NAME,
            MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    @SuppressLint("ApplySharedPref")
    fun putSecret(alias: String, value: String) {
        prefs.edit().putString(alias, value).commit()
    }

    fun getSecret(alias: String): String = prefs.getString(alias, "") ?: ""

    @SuppressLint("ApplySharedPref")
    fun removeSecret(alias: String) {
        prefs.edit().remove(alias).commit()
    }

    companion object {
        private const val FILE_NAME = "monandroido.secrets"
    }
}
