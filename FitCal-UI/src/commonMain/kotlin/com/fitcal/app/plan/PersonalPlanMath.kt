package com.fitcal.app.plan

import com.fitcal.app.ui.components.ActivityLevel
import com.fitcal.app.ui.components.mifflinStJeorBmr
import com.fitcal.shared.model.MacroGoals
import com.fitcal.shared.model.PersonalPlan
import com.fitcal.shared.model.UserProfile
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/*
 * The optional personal plan: daily calorie and macro targets from the user's body, activity,
 * goal and chosen pace. General estimates, not medical advice. Safety rules:
 *  - A deficit is at most 25% of maintenance and at most 1% of body weight per week, and never
 *    takes calories below 1,200 kcal (women) or 1,500 kcal (men). A pace that would break a rule
 *    can't be picked; if no deficit is safe at all, the plan holds maintenance.
 *  - No deficit for an underweight BMI (< 18.5); a "lose" target can't be below BMI 18.5.
 *  - Protein per kg (2.0 losing, 1.6 maintaining, 1.8 gaining), based on at most the weight at
 *    BMI 25. Fat 25% of calories, at least 0.6 g/kg. Carbs: the remaining calories.
 */

/** Stored in UserProfile.goalType. */
enum class PlanGoal(val key: String, val label: String) {
    LOSE("Lose Weight", "Lose weight"),
    MAINTAIN("Maintain", "Maintain"),
    GAIN("Gain Muscle", "Gain muscle");

    companion object {
        fun fromKey(key: String?): PlanGoal = entries.firstOrNull { it.key == key } ?: MAINTAIN
    }
}

/** Stored in PersonalPlan.pace. Gaining uses slower rates to keep the gain mostly lean. */
enum class PlanPace(val key: String, val label: String, val loseKgPerWeek: Float, val gainKgPerWeek: Float) {
    GENTLE("Gentle", "Gentle", 0.25f, 0.1f),
    STEADY("Steady", "Steady", 0.5f, 0.25f),
    FASTER("Faster", "Faster", 0.75f, 0.5f);

    fun kgPerWeek(goal: PlanGoal): Float = when (goal) {
        PlanGoal.LOSE -> loseKgPerWeek
        PlanGoal.GAIN -> gainKgPerWeek
        PlanGoal.MAINTAIN -> 0f
    }

    companion object {
        fun fromKey(key: String?): PlanPace = entries.firstOrNull { it.key == key } ?: STEADY
    }
}

/** Stored in UserProfile.targetsMode. */
enum class TargetsMode(val key: String) {
    NONE("none"), PLAN("plan"), CUSTOM("custom");

    companion object {
        fun fromKey(key: String?): TargetsMode? = entries.firstOrNull { it.key == key }
    }
}

/** Goals every profile had before plans existed; unchanged ones mean "no targets set". */
val FACTORY_GOALS = MacroGoals(calories = 2000, proteinG = 150, carbsG = 200, fatG = 65)

private const val KCAL_PER_KG = 7700f
private const val UNDERWEIGHT_BMI = 18.5f
const val CHECK_IN_INTERVAL_DAYS = 14

data class BodyStats(
    val weightKg: Float,
    val heightCm: Float,
    val age: Int,
    val gender: String,
    val activity: ActivityLevel,
) {
    private val heightM get() = heightCm / 100f
    val bmi: Float get() = weightKg / (heightM * heightM)
    fun weightAtBmi(bmi: Float): Float = bmi * heightM * heightM
    val maintenance: Int
        get() = (mifflinStJeorBmr(weightKg, heightCm, age, gender) * activity.factor).roundToInt()
    val calorieFloor: Int get() = if (gender == "Male") 1500 else 1200

    /** Largest daily deficit the safety rules allow (0 when none is safe). */
    val maxSafeDeficit: Int
        get() = minOf(
            (maintenance * 0.25f).roundToInt(),
            (weightKg * 0.01f * KCAL_PER_KG / 7f).roundToInt(),
            maintenance - calorieFloor,
        ).coerceAtLeast(0)

    companion object {
        /** Null when anything is missing or out of a realistic adult range. */
        fun from(weightKg: Float?, heightCm: Float?, age: Int?, gender: String, activityKey: String?): BodyStats? {
            if (weightKg == null || heightCm == null || age == null) return null
            if (weightKg !in 30f..300f || heightCm !in 120f..230f || age !in 18..100) return null
            return BodyStats(weightKg, heightCm, age, gender, ActivityLevel.fromKey(activityKey))
        }

        fun from(profile: UserProfile): BodyStats? =
            from(profile.weight, profile.height, profile.age, profile.gender, profile.activityLevel)
    }
}

enum class SuggestionNote {
    /** BMI below 18.5: no deficit is proposed. */
    UNDERWEIGHT,
    /** The calorie floor leaves no room for a deficit, so the plan holds maintenance. */
    NO_SAFE_DEFICIT,
}

data class TargetSuggestion(
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val maintenance: Int,
    /** Expected weight change per week at these calories (0 when maintaining). */
    val kgPerWeek: Float,
    val note: SuggestionNote? = null,
) {
    fun toGoals() = MacroGoals(calories, proteinG, carbsG, fatG)
}

/** Whether [pace] is safe for this body and goal; unsafe paces are shown but can't be picked. */
fun isPaceAllowed(body: BodyStats, goal: PlanGoal, pace: PlanPace): Boolean = when (goal) {
    PlanGoal.LOSE -> body.bmi >= UNDERWEIGHT_BMI &&
        (pace.loseKgPerWeek * KCAL_PER_KG / 7f).roundToInt() <= body.maxSafeDeficit
    else -> true
}

/** The safest pace's fallback when the chosen one isn't allowed (e.g. after a weight check-in). */
fun safestAllowedPace(body: BodyStats, goal: PlanGoal, preferred: PlanPace): PlanPace? =
    PlanPace.entries.filter { it.ordinal <= preferred.ordinal && isPaceAllowed(body, goal, it) }.maxByOrNull { it.ordinal }

fun planTargets(body: BodyStats, goal: PlanGoal, pace: PlanPace): TargetSuggestion {
    val maintenance = body.maintenance
    var note: SuggestionNote? = null
    var dailyChange = 0
    when (goal) {
        PlanGoal.LOSE -> when {
            body.bmi < UNDERWEIGHT_BMI -> note = SuggestionNote.UNDERWEIGHT
            else -> {
                val usable = safestAllowedPace(body, goal, pace)
                if (usable == null) note = SuggestionNote.NO_SAFE_DEFICIT
                else dailyChange = -(usable.loseKgPerWeek * KCAL_PER_KG / 7f).roundToInt()
            }
        }
        PlanGoal.GAIN -> dailyChange = (pace.gainKgPerWeek * KCAL_PER_KG / 7f).roundToInt()
        PlanGoal.MAINTAIN -> Unit
    }
    val calories = ((maintenance + dailyChange) / 10f).roundToInt() * 10

    val referenceWeight = minOf(body.weightKg, body.weightAtBmi(25f))
    val proteinPerKg = when {
        goal == PlanGoal.LOSE && dailyChange < 0 -> 2.0f
        goal == PlanGoal.GAIN -> 1.8f
        else -> 1.6f
    }
    val protein = (referenceWeight * proteinPerKg).roundToInt()
    val fat = maxOf(calories * 0.25f / 9f, referenceWeight * 0.6f).roundToInt()
    val carbs = ((calories - protein * 4 - fat * 9) / 4f).roundToInt().coerceAtLeast(0)

    return TargetSuggestion(
        calories = calories,
        proteinG = protein,
        carbsG = carbs,
        fatG = fat,
        maintenance = maintenance,
        kgPerWeek = abs(dailyChange) * 7f / KCAL_PER_KG,
        note = note,
    )
}

/** Sensible target weights for a goal, or null when the goal has none (or none is possible). */
fun targetWeightRange(body: BodyStats, goal: PlanGoal): ClosedFloatingPointRange<Float>? = when (goal) {
    PlanGoal.LOSE -> {
        val low = body.weightAtBmi(UNDERWEIGHT_BMI)
        val high = body.weightKg - 0.5f
        if (low < high) low..high else null
    }
    PlanGoal.GAIN -> (body.weightKg + 0.5f)..body.weightAtBmi(30f)
    PlanGoal.MAINTAIN -> null
}

fun weeksToTarget(currentKg: Float, targetKg: Float, kgPerWeek: Float): Int? =
    if (kgPerWeek <= 0f) null else ceil(abs(currentKg - targetKg) / kgPerWeek).toInt()

fun hasReachedTarget(goal: PlanGoal, currentKg: Float, targetKg: Float?): Boolean = when {
    targetKg == null -> false
    goal == PlanGoal.LOSE -> currentKg <= targetKg
    goal == PlanGoal.GAIN -> currentKg >= targetKg
    else -> false
}

// ── Profile-level operations ────────────────────────────────────────────────────

fun UserProfile.goals() = MacroGoals(calGoal, proteinGoal, carbsGoal, fatGoal)

private fun UserProfile.withGoals(g: MacroGoals) =
    copy(calGoal = g.calories, proteinGoal = g.proteinG, carbsGoal = g.carbsG, fatGoal = g.fatG)

/** Where the daily targets come from. Profiles from before plans: changed goals mean "custom". */
fun UserProfile.resolvedTargetsMode(): TargetsMode {
    val stored = TargetsMode.fromKey(targetsMode)
    return when {
        stored == TargetsMode.PLAN && plan == null -> TargetsMode.NONE
        stored != null -> stored
        goals() == FACTORY_GOALS -> TargetsMode.NONE
        else -> TargetsMode.CUSTOM
    }
}

fun UserProfile.hasDailyTargets(): Boolean = resolvedTargetsMode() != TargetsMode.NONE

/** The plan's current targets, from the profile's body stats; null without an active plan. */
fun UserProfile.currentPlanTargets(): TargetSuggestion? {
    val p = plan ?: return null
    if (resolvedTargetsMode() != TargetsMode.PLAN) return null
    val body = BodyStats.from(this) ?: return null
    return planTargets(body, PlanGoal.fromKey(goalType), PlanPace.fromKey(p.pace))
}

/**
 * Starts (or replaces) the plan with these body stats, goal and pace, effective [today].
 * The user's own numbers from before the first plan are kept so ending the plan restores them.
 */
fun UserProfile.startPlan(body: BodyStats, goal: PlanGoal, pace: PlanPace, targetWeightKg: Float?, today: String): UserProfile {
    val previousCustom = when (resolvedTargetsMode()) {
        TargetsMode.CUSTOM -> goals()
        TargetsMode.PLAN -> plan?.customGoalsBeforePlan
        TargetsMode.NONE -> null
    }
    val targets = planTargets(body, goal, pace)
    return copy(
        weight = body.weightKg,
        height = body.heightCm,
        age = body.age,
        gender = body.gender,
        activityLevel = body.activity.key,
        goalType = goal.key,
        targetsMode = TargetsMode.PLAN.key,
        plan = PersonalPlan(
            pace = pace.key,
            targetWeightKg = if (goal == PlanGoal.MAINTAIN) null else targetWeightKg,
            startDate = today,
            startWeightKg = body.weightKg,
            lastCheckInDate = today,
            customGoalsBeforePlan = previousCustom,
        ),
    ).withGoals(targets.toGoals())
}

/** Ends the plan: back to the user's own numbers if they had some, otherwise to no targets. */
fun UserProfile.endPlan(): UserProfile {
    val restore = plan?.customGoalsBeforePlan
    return if (restore != null) {
        copy(targetsMode = TargetsMode.CUSTOM.key, plan = null).withGoals(restore)
    } else {
        copy(targetsMode = TargetsMode.NONE.key, plan = null)
    }
}

/** Recomputes plan targets after body stats change (e.g. weight saved in Settings). */
fun UserProfile.withPlanTargetsRecomputed(): UserProfile {
    val targets = currentPlanTargets() ?: return this
    return withGoals(targets.toGoals())
}

/** An optional weight check-in: updates the weight and recomputes the targets. */
fun UserProfile.checkIn(weightKg: Float, today: String): UserProfile {
    val p = plan ?: return this
    return copy(weight = weightKg, plan = p.copy(lastCheckInDate = today)).withPlanTargetsRecomputed()
}

/** Estimated date the target weight is reached at the plan's pace, from the last check-in. */
fun UserProfile.projectedTargetDate(today: String): String? {
    val p = plan ?: return null
    val target = p.targetWeightKg ?: return null
    val goal = PlanGoal.fromKey(goalType)
    if (hasReachedTarget(goal, weight, target)) return null
    val targets = currentPlanTargets() ?: return null
    val weeks = weeksToTarget(weight, target, targets.kgPerWeek) ?: return null
    return addDays(p.lastCheckInDate ?: p.startDate, weeks * 7)
        ?.let { if ((daysBetween(today, it) ?: 0) < 0) today else it }
}

/** A check-in is offered every [CHECK_IN_INTERVAL_DAYS] days, unless snoozed. */
fun UserProfile.isCheckInDue(today: String, snoozedUntil: String?): Boolean {
    val p = plan ?: return false
    if (resolvedTargetsMode() != TargetsMode.PLAN) return false
    if (snoozedUntil != null && (daysBetween(today, snoozedUntil) ?: 0) > 0) return false
    val since = daysBetween(p.lastCheckInDate ?: p.startDate, today) ?: return false
    return since >= CHECK_IN_INTERVAL_DAYS
}

// ── Dates (YYYY-MM-DD, no time zone) ────────────────────────────────────────────

/** Days since 1970-01-01 (proleptic Gregorian), or null if [date] isn't YYYY-MM-DD. */
fun epochDay(date: String): Long? {
    if (date.length != 10 || date[4] != '-' || date[7] != '-') return null
    val y0 = date.substring(0, 4).toIntOrNull() ?: return null
    val m = date.substring(5, 7).toIntOrNull() ?: return null
    val d = date.substring(8, 10).toIntOrNull() ?: return null
    if (m !in 1..12 || d !in 1..31) return null
    val y = if (m <= 2) y0 - 1 else y0
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097L + doe - 719468L
}

fun dateFromEpochDay(day: Long): String {
    val z = day + 719468L
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val y = yoe + era * 400 + if (m <= 2) 1 else 0
    return "${y.toString().padStart(4, '0')}-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
}

fun addDays(date: String, days: Int): String? = epochDay(date)?.let { dateFromEpochDay(it + days) }

fun daysBetween(from: String, to: String): Long? {
    val a = epochDay(from) ?: return null
    val b = epochDay(to) ?: return null
    return b - a
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "2027-02-14" → "14 Feb 2027". */
fun formatPlanDate(date: String): String {
    if (epochDay(date) == null) return date
    val m = date.substring(5, 7).toInt()
    return "${date.substring(8, 10).trimStart('0')} ${MONTHS[m - 1]} ${date.substring(0, 4)}"
}

/** One decimal place, without a trailing ".0": 72.0 → "72", 72.25 → "72.3". */
fun formatKg(kg: Float): String {
    val tenths = (kg * 10f).roundToInt()
    return if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${abs(tenths % 10)}"
}
