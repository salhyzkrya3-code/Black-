package com.agon.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "editor_prefs")

@Serializable
data class SavedOverlay(
    val id: String,
    val uriString: String,
    val isGif: Boolean,
    val offsetX: Float,
    val offsetY: Float,
    val scale: Float,
    val zIndex: Int
)

@Serializable
data class SavedPreset(
    val id: String,
    val name: String,
    val adjustments: com.agon.app.data.Adjustments,
    val toning: com.agon.app.data.TonalAdjustments,
    val curves: com.agon.app.data.CurveSliders,
    val cinematic: com.agon.app.data.CinematicEffects
)

@Serializable
data class SavedLut(
    val id: String,
    val name: String,
    val uriString: String
)

class PreferencesManager(private val context: Context) {
    private val OVERLAYS_KEY = stringPreferencesKey("saved_overlays")
    private val PRESETS_KEY = stringPreferencesKey("saved_presets")
    private val LUTS_KEY = stringPreferencesKey("saved_luts")
    
    private val json = Json { ignoreUnknownKeys = true }

    val overlaysFlow: Flow<List<SavedOverlay>> = context.dataStore.data.map { prefs ->
        val jsonString = prefs[OVERLAYS_KEY] ?: "[]"
        try { json.decodeFromString(jsonString) } catch (e: Exception) { emptyList() }
    }

    suspend fun saveOverlays(overlays: List<SavedOverlay>) {
        context.dataStore.edit { prefs ->
            prefs[OVERLAYS_KEY] = json.encodeToString(overlays)
        }
    }
    
    val presetsFlow: Flow<List<SavedPreset>> = context.dataStore.data.map { prefs ->
        val jsonString = prefs[PRESETS_KEY] ?: "[]"
        try { json.decodeFromString(jsonString) } catch (e: Exception) { emptyList() }
    }
    
    suspend fun savePresets(presets: List<SavedPreset>) {
        context.dataStore.edit { prefs ->
            prefs[PRESETS_KEY] = json.encodeToString(presets)
        }
    }
    
    val lutsFlow: Flow<List<SavedLut>> = context.dataStore.data.map { prefs ->
        val jsonString = prefs[LUTS_KEY] ?: "[]"
        try { json.decodeFromString(jsonString) } catch (e: Exception) { emptyList() }
    }
    
    suspend fun saveLuts(luts: List<SavedLut>) {
        context.dataStore.edit { prefs ->
            prefs[LUTS_KEY] = json.encodeToString(luts)
        }
    }
}
