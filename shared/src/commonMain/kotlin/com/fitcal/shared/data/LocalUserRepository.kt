package com.fitcal.shared.data

import com.fitcal.shared.model.UserProfile
import com.fitcal.shared.model.WeightEntry
import com.fitcal.shared.model.WeightHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class LocalUserRepository(
    private val storage: KeyValueStorage,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = false }
) : UserRepository {

    private val mutex = Mutex()
    private val defaultProfile = UserProfile(
        weight = 70.0f,
        height = 175.0f,
        calGoal = 2000,
        proteinGoal = 150,
        carbsGoal = 200,
        fatGoal = 65,
        age = 25,
        gender = "Male",
        goalType = "Maintain",
        defaultPlateSize = 9.0f
    )

    private val _profileFlow = MutableStateFlow<UserProfile?>(null)
    private val _waterIntakeMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    // Null until first read from storage.
    private val _weightEntries = MutableStateFlow<List<WeightEntry>?>(null)
    private val weightListSerializer = ListSerializer(WeightEntry.serializer())

    override suspend fun getUserProfile(): UserProfile {
        return mutex.withLock {
            _profileFlow.value ?: loadProfileFromStorageLocked()
        }
    }

    private suspend fun loadProfileFromStorageLocked(): UserProfile {
        val loaded = withContext(Dispatchers.Default) {
            try {
                val raw = storage.getString(KEY_USER_PROFILE, "")
                if (raw.isNotBlank()) {
                    json.decodeFromString<UserProfile>(raw)
                } else {
                    defaultProfile
                }
            } catch (e: Exception) {
                defaultProfile
            }
        }
        _profileFlow.value = loaded
        return loaded
    }

    override suspend fun saveUserProfile(profile: UserProfile) {
        mutex.withLock {
            _profileFlow.value = profile
            withContext(Dispatchers.Default) {
                try {
                    val raw = json.encodeToString(UserProfile.serializer(), profile)
                    storage.putString(KEY_USER_PROFILE, raw)
                } catch (_: Exception) {
                }
            }
        }
    }

    override suspend fun getWaterIntake(date: String): Int {
        return mutex.withLock {
            val cached = _waterIntakeMap.value[date]
            if (cached != null) {
                cached
            } else {
                val stored = withContext(Dispatchers.Default) {
                    storage.getString(waterKey(date), "0").toIntOrNull() ?: 0
                }
                _waterIntakeMap.value = _waterIntakeMap.value + (date to stored)
                stored
            }
        }
    }

    override suspend fun setWaterIntake(date: String, amountMl: Int) {
        mutex.withLock {
            _waterIntakeMap.value = _waterIntakeMap.value + (date to amountMl)
            withContext(Dispatchers.Default) {
                storage.putString(waterKey(date), amountMl.toString())
                val dates = waterDatesLocked()
                if (date !in dates) storage.putString(KEY_WATER_DATES, (dates + date).joinToString(","))
            }
        }
    }

    override fun observeWaterIntake(date: String): Flow<Int> {
        return _waterIntakeMap.map { map -> map[date] ?: (storage.getString(waterKey(date), "0").toIntOrNull() ?: 0) }
    }

    override suspend fun getWeightEntries(): List<WeightEntry> =
        mutex.withLock { _weightEntries.value ?: loadWeightsLocked() }

    override suspend fun recordWeight(entry: WeightEntry) {
        mutex.withLock {
            val current = _weightEntries.value ?: loadWeightsLocked()
            saveWeightsLocked(WeightHistory.upsert(current, entry))
        }
    }

    override fun observeWeightEntries(): Flow<List<WeightEntry>> =
        _weightEntries
            .onStart { if (_weightEntries.value == null) getWeightEntries() }
            .filterNotNull()

    /** Adds server entries for days this device doesn't have; local days keep their value. */
    suspend fun mergeWeightEntries(remote: List<WeightEntry>) {
        if (remote.isEmpty()) return
        mutex.withLock {
            val current = _weightEntries.value ?: loadWeightsLocked()
            saveWeightsLocked(WeightHistory.fillMissingDays(current, remote))
        }
    }

    private suspend fun loadWeightsLocked(): List<WeightEntry> {
        val loaded = withContext(Dispatchers.Default) {
            try {
                val raw = storage.getString(KEY_WEIGHT_ENTRIES, "")
                if (raw.isBlank()) emptyList()
                else WeightHistory.dedupePerDay(json.decodeFromString(weightListSerializer, raw))
            } catch (_: Exception) {
                emptyList()
            }
        }
        _weightEntries.value = loaded
        return loaded
    }

    private suspend fun saveWeightsLocked(entries: List<WeightEntry>) {
        _weightEntries.value = entries
        withContext(Dispatchers.Default) {
            try {
                storage.putString(KEY_WEIGHT_ENTRIES, json.encodeToString(weightListSerializer, entries))
            } catch (_: Exception) {
            }
        }
    }

    /** Removes the profile, every water entry and the weight history from this device. */
    suspend fun clearAll() {
        mutex.withLock {
            val dates = waterDatesLocked() + _waterIntakeMap.value.keys
            dates.forEach { storage.remove(waterKey(it)) }
            storage.remove(KEY_WATER_DATES)
            storage.remove(KEY_USER_PROFILE)
            storage.remove(KEY_WEIGHT_ENTRIES)
            _waterIntakeMap.value = emptyMap()
            _profileFlow.value = null
            _weightEntries.value = emptyList()
        }
    }

    private fun waterDatesLocked(): List<String> =
        storage.getString(KEY_WATER_DATES, "").split(",").filter { it.isNotBlank() }

    private fun waterKey(date: String) = "water_intake_$date"

    companion object {
        const val KEY_USER_PROFILE = "user_profile"
        const val KEY_WATER_DATES = "water_intake_dates"
        const val KEY_WEIGHT_ENTRIES = "weight_entries"
    }
}
