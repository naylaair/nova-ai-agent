package com.nova.agent.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "nova_settings")

class SettingsStore(private val context: Context) {

    private val apiKeyPref = stringPreferencesKey("anthropic_api_key")

    val apiKeyFlow: Flow<String> = context.dataStore.data.map { it[apiKeyPref].orEmpty() }

    suspend fun setApiKey(value: String) {
        context.dataStore.edit { it[apiKeyPref] = value.trim() }
    }
}
