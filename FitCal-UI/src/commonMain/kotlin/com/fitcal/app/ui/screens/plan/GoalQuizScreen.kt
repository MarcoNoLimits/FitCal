package com.fitcal.app.ui.screens.plan

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitcal.app.learn.GuideIds
import com.fitcal.app.plan.BodyStats
import com.fitcal.app.plan.PlanGoal
import com.fitcal.app.plan.PlanPace
import com.fitcal.app.plan.QuizStep
import com.fitcal.app.plan.SuggestionNote
import com.fitcal.app.plan.formatKg
import com.fitcal.app.plan.isPaceAllowed
import com.fitcal.app.plan.planTargets
import com.fitcal.app.plan.quizSteps
import com.fitcal.app.plan.startPlan
import com.fitcal.app.plan.targetWeightRange
import com.fitcal.app.ui.components.ActivityLevel
import com.fitcal.app.ui.components.FitCalTextField
import com.fitcal.app.ui.theme.*
import com.fitcal.shared.model.UserProfile

/**
 * First-open offer for the goal quiz. "Skip" is as easy as "Let's do it"; tapping outside skips.
 */
@Composable
fun GoalQuizOffer(onStart: () -> Unit, onSkip: () -> Unit) {
    AlertDialog(
        onDismissRequest = onSkip,
        containerColor = CardBackground,
        shape = RoundedCornerShape(RadiusL),
        title = { Text("Want daily targets made for you?", style = BrandTypography.SectionTitle, color = TextColor) },
        text = {
            Text(
                "Answer 5 quick questions (about a minute) and FitCal suggests calories and macros for your goal. " +
                    "Prefer to just track? Skip it: FitCal works the same, and you can do it later from Settings.",
                style = BrandTypography.Body,
                color = TextColor,
            )
        },
        confirmButton = {
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                shape = RoundedCornerShape(RadiusM),
            ) { Text("Let's do it", color = Color.White) }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onSkip,
                shape = RoundedCornerShape(RadiusM),
                border = BorderStroke(1.dp, BorderColor),
            ) { Text("Skip", color = TextColor) }
        },
    )
}

/**
 * The goal quiz: one question per screen, then the suggested targets. Same safety rules as
 * [PlanSetupScreen] (healthy target range, safe paces, minimum intake). Skip leaves at any step
 * without saving anything.
 */
@Composable
fun GoalQuizScreen(
    profile: UserProfile,
    today: String,
    onFinish: (UserProfile) -> Unit,
    onSkip: () -> Unit,
    onOpenGuide: (String) -> Unit = {},
) {
    var goal by remember { mutableStateOf(PlanGoal.LOSE) }
    var gender by remember { mutableStateOf(profile.gender) }
    var ageStr by remember { mutableStateOf("") }
    var heightStr by remember { mutableStateOf("") }
    var weightStr by remember { mutableStateOf("") }
    var activity by remember { mutableStateOf(ActivityLevel.SEDENTARY) }
    var pace by remember { mutableStateOf(PlanPace.STEADY) }
    var targetStr by remember { mutableStateOf("") }
    var stepIndex by remember { mutableStateOf(0) }

    val steps = quizSteps(goal)
    val step = steps[stepIndex.coerceIn(0, steps.lastIndex)]

    val age = ageStr.toIntOrNull()
    val height = heightStr.replace(',', '.').toFloatOrNull()
    val weight = weightStr.replace(',', '.').toFloatOrNull()
    val body = BodyStats.from(weight, height, age, gender, activity.key)
    val range = body?.let { targetWeightRange(it, goal) }
    val target = targetStr.replace(',', '.').toFloatOrNull()
    val targetValid = goal == PlanGoal.MAINTAIN || (range != null && target != null && target in range)

    LaunchedEffect(body, goal) {
        if (body != null && !isPaceAllowed(body, goal, pace)) {
            PlanPace.entries.lastOrNull { isPaceAllowed(body, goal, it) }?.let { pace = it }
        }
    }

    val canContinue = when (step) {
        QuizStep.GOAL, QuizStep.ACTIVITY -> true
        QuizStep.ABOUT_YOU -> age != null && age in 18..100
        QuizStep.BODY -> body != null
        QuizStep.TARGET -> body != null && targetValid
        QuizStep.RESULT -> body != null && targetValid
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Progress and Skip, on every step
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { (stepIndex + 1f) / steps.size },
                color = PrimaryAccent,
                trackColor = SurfaceTint,
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .semantics { contentDescription = "Question ${stepIndex + 1} of ${steps.size}" },
            )
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = onSkip) { Text("Skip", color = MutedTextColor) }
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.weight(1f),
            label = "quiz-step",
        ) { current ->
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (current) {
                    QuizStep.GOAL -> {
                        Question("What's your goal?")
                        PlanGoal.entries.forEach { option ->
                            ChoiceRow(option.label, selected = goal == option) { goal = option }
                        }
                    }
                    QuizStep.ABOUT_YOU -> {
                        Question("A bit about you")
                        ChipRow(options = listOf("Male", "Female"), selected = gender, label = { it }, onSelect = { gender = it })
                        FitCalTextField(
                            value = ageStr,
                            onValueChange = { ageStr = it.filter(Char::isDigit).take(3) },
                            label = "Age",
                            unit = "yrs",
                            isError = ageStr.isNotBlank() && (age == null || age !in 18..100),
                            supportingText = "FitCal is for adults (18+).",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Hint("Used only to estimate how much energy your body uses.")
                    }
                    QuizStep.BODY -> {
                        Question("Your height and weight")
                        FitCalTextField(value = heightStr, onValueChange = { heightStr = it }, label = "Height", unit = "cm", modifier = Modifier.fillMaxWidth())
                        FitCalTextField(value = weightStr, onValueChange = { weightStr = it }, label = "Weight", unit = "kg", modifier = Modifier.fillMaxWidth())
                        if (heightStr.isNotBlank() && weightStr.isNotBlank() && body == null) {
                            Hint("Check the numbers: height 120–230 cm, weight 30–300 kg.")
                        }
                    }
                    QuizStep.ACTIVITY -> {
                        Question("How active are you?")
                        ActivityLevel.entries.forEach { option ->
                            ChoiceRow(option.label, detail = option.description, selected = activity == option) { activity = option }
                        }
                        Hint("Not sure? Pick the lower one; you can change it later.")
                    }
                    QuizStep.TARGET -> {
                        Question(if (goal == PlanGoal.GAIN) "Where do you want to get to?" else "What's your target weight?")
                        when {
                            body == null -> Hint("Go back and enter your height, weight and age first.")
                            range == null -> Text(
                                "Your weight is already below the healthy range, so FitCal won't plan weight loss. Go back and pick Maintain or Gain muscle.",
                                style = BrandTypography.BodySmall,
                                color = DangerTextStrong,
                            )
                            else -> {
                                FitCalTextField(
                                    value = targetStr,
                                    onValueChange = { targetStr = it },
                                    label = "Target weight",
                                    unit = "kg",
                                    isError = targetStr.isNotBlank() && !targetValid,
                                    supportingText = "Between ${formatKg(range.start)} and ${formatKg(range.endInclusive)} kg",
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text("How fast?", style = BrandTypography.BodySmall, fontWeight = FontWeight.Bold, color = MutedTextColor)
                                PlanPace.entries.forEach { option ->
                                    val allowed = isPaceAllowed(body, goal, option)
                                    PaceRow(
                                        title = "${option.label} · ${formatKg(option.kgPerWeek(goal))} kg/week",
                                        detail = if (allowed) "${planTargets(body, goal, option).calories} kcal/day" else "Too fast to be safe for you",
                                        selected = pace == option,
                                        enabled = allowed,
                                        recommended = option == PlanPace.STEADY,
                                        onSelect = { pace = option },
                                    )
                                }
                                TextButton(onClick = { onOpenGuide(GuideIds.TRACKING_WEIGHT) }) {
                                    Text("What's a healthy pace?", color = PrimaryAccent, style = BrandTypography.BodySmall)
                                }
                            }
                        }
                    }
                    QuizStep.RESULT -> {
                        Question("Your daily targets")
                        val targets = body?.let { planTargets(it, goal, pace) }
                        if (targets != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(InfoPanel, RoundedCornerShape(RadiusM))
                                    .border(1.dp, BorderColor, RoundedCornerShape(RadiusM))
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text("${targets.calories} kcal a day", style = BrandTypography.SectionTitle, fontWeight = FontWeight.Bold, color = TextColor)
                                Text(
                                    "Protein ${targets.proteinG} g · Carbs ${targets.carbsG} g · Fat ${targets.fatG} g",
                                    style = BrandTypography.BodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextColor,
                                )
                                Text("Your maintenance is about ${targets.maintenance} kcal a day.", style = BrandTypography.BodySmall, color = MutedTextColor)
                                when (targets.note) {
                                    SuggestionNote.UNDERWEIGHT -> Text(
                                        "Your BMI is below the healthy range, so these targets maintain your weight.",
                                        style = BrandTypography.BodySmall,
                                        color = DangerTextStrong,
                                    )
                                    SuggestionNote.NO_SAFE_DEFICIT -> Text(
                                        "Eating less would go under the safe minimum, so these targets maintain your weight.",
                                        style = BrandTypography.BodySmall,
                                        color = DangerTextStrong,
                                    )
                                    null -> Unit
                                }
                            }
                        }
                        Hint("General estimate, not medical advice. If you're pregnant, have a medical condition or a history of disordered eating, check with a doctor first. You can change or end the plan any time.")
                    }
                }
            }
        }

        // Back / Next
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (stepIndex > 0) {
                OutlinedButton(
                    onClick = { stepIndex -= 1 },
                    shape = RoundedCornerShape(RadiusM),
                    border = BorderStroke(1.dp, BorderColor),
                    modifier = Modifier.weight(1f).height(54.dp),
                ) { Text("Back", color = TextColor) }
            }
            Button(
                onClick = {
                    if (step == QuizStep.RESULT) {
                        if (body != null && targetValid) {
                            onFinish(profile.startPlan(body, goal, pace, if (goal == PlanGoal.MAINTAIN) null else target, today))
                        }
                    } else {
                        stepIndex = (stepIndex + 1).coerceAtMost(steps.lastIndex)
                    }
                },
                enabled = canContinue,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                shape = RoundedCornerShape(RadiusM),
                modifier = Modifier.weight(2f).height(54.dp),
            ) {
                Text(if (step == QuizStep.RESULT) "Use these targets" else "Next", style = BrandTypography.CardTitle, color = Color.White)
            }
        }
    }
}

@Composable
private fun Question(text: String) {
    Text(text, style = BrandTypography.ScreenTitle, color = TextColor)
}

@Composable
private fun Hint(text: String) {
    Text(text, style = BrandTypography.BodySmall, color = MutedTextColor)
}

@Composable
private fun ChoiceRow(title: String, detail: String? = null, selected: Boolean, onSelect: () -> Unit) {
    PaceRow(title = title, detail = detail ?: "", selected = selected, enabled = true, recommended = false, onSelect = onSelect)
}
