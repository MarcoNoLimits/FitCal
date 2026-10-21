package com.fitcal.shared.data

import com.fitcal.shared.auth.SupabaseClientFactory
import com.fitcal.shared.model.UserProfile
import com.fitcal.shared.model.WeightEntry
import com.fitcal.shared.sync.OutboxItem
import com.fitcal.shared.sync.SupabaseProfileRow
import com.fitcal.shared.sync.SupabaseWaterRow
import com.fitcal.shared.sync.SupabaseWeightRow
import com.fitcal.shared.sync.SyncEngine
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow

class SupabaseUserRepository(
    private val localRepo: LocalUserRepository,
    private val syncEngine: SyncEngine,
    private val client: SupabaseClient = SupabaseClientFactory.getOrCreate()
) : UserRepository {

    override suspend fun getUserProfile(): UserProfile {
        return localRepo.getUserProfile()
    }

    override suspend fun saveUserProfile(profile: UserProfile) {
        localRepo.saveUserProfile(profile)
        syncEngine.enqueue(OutboxItem.UpsertProfile(profile))
    }

    override suspend fun getWaterIntake(date: String): Int {
        return localRepo.getWaterIntake(date)
    }

    override suspend fun setWaterIntake(date: String, amountMl: Int) {
        localRepo.setWaterIntake(date, amountMl)
        syncEngine.enqueue(OutboxItem.UpsertWater(date, amountMl))
    }

    override fun observeWaterIntake(date: String): Flow<Int> {
        return localRepo.observeWaterIntake(date)
    }

    override suspend fun getWeightEntries(): List<WeightEntry> = localRepo.getWeightEntries()

    override suspend fun recordWeight(entry: WeightEntry) {
        localRepo.recordWeight(entry)
        syncEngine.enqueue(OutboxItem.UpsertWeight(entry.date, entry.weightKg))
    }

    override fun observeWeightEntries(): Flow<List<WeightEntry>> = localRepo.observeWeightEntries()

    /** Weigh-ins from [fromDateKey] on, so a reinstall gets the weight history back. */
    suspend fun pullWeightHistory(fromDateKey: String) {
        try {
            val rows = client.from("weight_entries")
                .select {
                    filter { gte("day", fromDateKey) }
                    limit(1000)
                }
                .decodeList<SupabaseWeightRow>()
            localRepo.mergeWeightEntries(rows.map { WeightEntry(it.day, it.weight_kg) })
        } catch (e: Exception) {
            println("SupabaseUserRepository pullWeightHistory failed: ${e.message}")
        }
    }

    suspend fun pullRemote(dateKey: String) {
        try {
            val profiles = client.from("profiles")
                .select()
                .decodeList<SupabaseProfileRow>()

            profiles.firstOrNull()?.let { row ->
                val current = localRepo.getUserProfile()
                val updated = current.copy(
                    weight = row.weight ?: current.weight,
                    height = row.height ?: current.height,
                    calGoal = row.cal_goal ?: current.calGoal,
                    proteinGoal = row.protein_goal ?: current.proteinGoal,
                    carbsGoal = row.carbs_goal ?: current.carbsGoal,
                    fatGoal = row.fat_goal ?: current.fatGoal,
                    age = row.age ?: current.age,
                    gender = row.gender ?: current.gender,
                    goalType = row.goal_type ?: current.goalType,
                    defaultPlateSize = row.default_plate_size ?: current.defaultPlateSize,
                    activityLevel = row.activity_level ?: current.activityLevel,
                    targetsMode = row.targets_mode ?: current.targetsMode
                ).let { merged ->
                    // A plan only counts while the mode is "plan"; an ended plan may still be in the row.
                    merged.copy(plan = if (merged.targetsMode == "plan") row.plan ?: current.plan else null)
                }
                localRepo.saveUserProfile(updated)
            }

            pullWater(dateKey, onlyDay = true)
        } catch (e: Exception) {
            println("SupabaseUserRepository pullRemote failed: ${e.message}")
        }
    }

    /** Water for every day from [fromDateKey] on, so a reinstall gets past days back. */
    suspend fun pullWaterHistory(fromDateKey: String) {
        try {
            pullWater(fromDateKey, onlyDay = false)
        } catch (e: Exception) {
            println("SupabaseUserRepository pullWaterHistory failed: ${e.message}")
        }
    }

    private suspend fun pullWater(dateKey: String, onlyDay: Boolean) {
        val waters = client.from("water_intake")
            .select {
                filter {
                    if (onlyDay) eq("day", dateKey) else gte("day", dateKey)
                }
                limit(1000)
            }
            .decodeList<SupabaseWaterRow>()
        for (row in waters) {
            // A day already tracked on this device keeps its value; only missing days fill in.
            if (!onlyDay && localRepo.getWaterIntake(row.day) > 0) continue
            localRepo.setWaterIntake(row.day, row.amount_ml)
        }
    }
}
