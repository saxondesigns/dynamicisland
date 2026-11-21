package com.kiwi.island

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "island")

val DEFAULT_TRACKED = setOf("com.dd.doordash", "com.talabat", "com.ubercab.eats", "com.deliveroo.orderapp")

data class IslandSettings(
    val cameraAware: Boolean = false,
    val margin: Float = 8f,
    val width: Float = 126f,
    val height: Float = 36f,
    val radius: Float = 44f,
    val intensity: Float = 0.6f,
    val speed: Float = 1f,
    val light: Boolean = false,
    val disabled: Set<String> = setOf(Category.Battery.name, Category.Notification.name),
    val tracked: String = "",
    val swipeToExpand: Boolean = true
) {
    val trackedPackages: Set<String>
        get() = DEFAULT_TRACKED + tracked.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }
}

object Keys {
    val cameraAware = booleanPreferencesKey("camera_aware")
    val margin = floatPreferencesKey("margin")
    val width = floatPreferencesKey("width")
    val height = floatPreferencesKey("height")
    val radius = floatPreferencesKey("radius")
    val intensity = floatPreferencesKey("intensity")
    val speed = floatPreferencesKey("speed")
    val light = booleanPreferencesKey("light")
    val disabled = stringSetPreferencesKey("disabled")
    val tracked = stringPreferencesKey("tracked")
    val swipeToExpand = booleanPreferencesKey("swipe_to_expand")
}

class SettingsStore(context: Context) {
    private val store = context.applicationContext.dataStore
    private val d = IslandSettings()

    val flow: Flow<IslandSettings> = store.data.map { p ->
        IslandSettings(
            cameraAware = p[Keys.cameraAware] ?: d.cameraAware,
            margin = p[Keys.margin] ?: d.margin,
            width = p[Keys.width] ?: d.width,
            height = p[Keys.height] ?: d.height,
            radius = p[Keys.radius] ?: d.radius,
            intensity = p[Keys.intensity] ?: d.intensity,
            speed = p[Keys.speed] ?: d.speed,
            light = p[Keys.light] ?: d.light,
            disabled = p[Keys.disabled] ?: d.disabled,
            tracked = p[Keys.tracked] ?: d.tracked,
            swipeToExpand = p[Keys.swipeToExpand] ?: d.swipeToExpand
        )
    }

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        store.edit { it[key] = value }
    }
}
