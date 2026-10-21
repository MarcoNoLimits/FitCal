package com.fitcal.shared.data

import com.fitcal.shared.model.UserProfile
import com.fitcal.shared.model.WeightEntry
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun getUserProfile(): UserProfile
    suspend fun saveUserProfile(profile: UserProfile)
    suspend fun getWaterIntake(date: String): Int
    suspend fun setWaterIntake(date: String, amountMl: Int)
    fun observeWaterIntake(date: String): Flow<Int>

    /** Weight history, one entry per day, oldest first. */
    suspend fun getWeightEntries(): List<WeightEntry>

    /** Records a weigh-in; an existing entry for the same day is replaced (the latest wins). */
    suspend fun recordWeight(entry: WeightEntry)

    fun observeWeightEntries(): Flow<List<WeightEntry>>
}
