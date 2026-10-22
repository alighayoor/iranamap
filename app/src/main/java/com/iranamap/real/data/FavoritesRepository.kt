package com.iranamap.real.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.iranamap.real.core.GeoPoint
import com.iranamap.real.core.HistoryEntry
import com.iranamap.real.core.SavedPlace
import com.iranamap.real.core.SavedPlaceIds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.savedDataStore by preferencesDataStore(name = "saved_places")

/**
 * ذخیره‌سازی محلی مکان‌های ذخیره‌شده و تاریخچه‌ی مقصدها.
 * همه‌چیز روی دستگاه می‌ماند و هیچ چیزی به سرور فرستاده نمی‌شود.
 */
class FavoritesRepository private constructor(private val context: Context) {

    private val gson = Gson()

    private val placesKey = stringPreferencesKey("places_json")
    private val historyKey = stringPreferencesKey("history_json")

    private val placesType = object : TypeToken<List<StoredPlace>>() {}.type
    private val historyType = object : TypeToken<List<StoredHistory>>() {}.type

    val places: Flow<List<SavedPlace>> = context.savedDataStore.data.map { prefs ->
        decodePlaces(prefs[placesKey])
    }

    val history: Flow<List<HistoryEntry>> = context.savedDataStore.data.map { prefs ->
        decodeHistory(prefs[historyKey])
    }

    suspend fun currentPlaces(): List<SavedPlace> = places.first()

    suspend fun addPlace(label: String, address: String, point: GeoPoint, icon: String = "⭐"): SavedPlace {
        val place = SavedPlace(
            id = SavedPlaceIds.from(point) + "-" + System.currentTimeMillis().toString().takeLast(4),
            label = label.trim(),
            address = address.trim(),
            point = point,
            icon = icon,
            createdAtMillis = System.currentTimeMillis()
        )
        context.savedDataStore.edit { prefs ->
            val existing = decodePlaces(prefs[placesKey])
            val updated = (existing + place).takeLast(60)
            prefs[placesKey] = gson.toJson(updated.map { it.toStored() }, placesType)
        }
        return place
    }

    suspend fun removePlace(id: String) {
        context.savedDataStore.edit { prefs ->
            val existing = decodePlaces(prefs[placesKey]).filterNot { it.id == id }
            prefs[placesKey] = gson.toJson(existing.map { it.toStored() }, placesType)
        }
    }

    suspend fun renamePlace(id: String, newLabel: String) {
        context.savedDataStore.edit { prefs ->
            val existing = decodePlaces(prefs[placesKey]).map {
                if (it.id == id) it.copy(label = newLabel.trim()) else it
            }
            prefs[placesKey] = gson.toJson(existing.map { it.toStored() }, placesType)
        }
    }

    /** افزودن یک مقصد به تاریخچه (تکراری‌ها به روز می‌شوند و حداکثر ۵۰ مورد نگه داشته می‌شود). */
    suspend fun recordVisit(label: String, address: String, point: GeoPoint) {
        val entry = HistoryEntry(
            id = SavedPlaceIds.from(point),
            label = label,
            address = address,
            point = point,
            visitedAtMillis = System.currentTimeMillis()
        )
        context.savedDataStore.edit { prefs ->
            val existing = decodeHistory(prefs[historyKey]).filterNot { it.id == entry.id }
            val updated = (listOf(entry) + existing).take(50)
            prefs[historyKey] = gson.toJson(updated.map { it.toStored() }, historyType)
        }
    }

    suspend fun clearHistory() {
        context.savedDataStore.edit { prefs -> prefs.remove(historyKey) }
    }

    // ------------------------------------------------------------------ کدگذاری
    private fun decodePlaces(json: String?): List<SavedPlace> = runCatching {
        val stored: List<StoredPlace>? = json?.let { gson.fromJson<List<StoredPlace>>(it, placesType) }
        stored.orEmpty().map { it.toModel() }
    }.getOrDefault(emptyList())

    private fun decodeHistory(json: String?): List<HistoryEntry> = runCatching {
        val stored: List<StoredHistory>? = json?.let { gson.fromJson<List<StoredHistory>>(it, historyType) }
        stored.orEmpty().map { it.toModel() }
    }.getOrDefault(emptyList())

    private data class StoredPlace(
        val id: String = "",
        val label: String = "",
        val address: String = "",
        val lat: Double = 0.0,
        val lng: Double = 0.0,
        val icon: String = "⭐",
        val createdAt: Long = 0L
    ) {
        fun toModel() = SavedPlace(id, label, address, GeoPoint(lat, lng), icon, createdAt)
    }

    private data class StoredHistory(
        val id: String = "",
        val label: String = "",
        val address: String = "",
        val lat: Double = 0.0,
        val lng: Double = 0.0,
        val visitedAt: Long = 0L
    ) {
        fun toModel() = HistoryEntry(id, label, address, GeoPoint(lat, lng), visitedAt)
    }

    private fun SavedPlace.toStored() = StoredPlace(
        id = id, label = label, address = address,
        lat = point.lat, lng = point.lng, icon = icon, createdAt = createdAtMillis
    )

    private fun HistoryEntry.toStored() = StoredHistory(
        id = id, label = label, address = address,
        lat = point.lat, lng = point.lng, visitedAt = visitedAtMillis
    )

    companion object {
        @Volatile
        private var instance: FavoritesRepository? = null

        fun get(context: Context): FavoritesRepository =
            instance ?: synchronized(this) {
                instance ?: FavoritesRepository(context.applicationContext).also { instance = it }
            }
    }
}
