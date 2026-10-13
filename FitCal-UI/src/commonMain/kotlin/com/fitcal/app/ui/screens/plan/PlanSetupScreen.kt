package com.fitcal.app.ui.screens.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitcal.app.learn.GuideIds
import com.fitcal.app.plan.BodyStats
import com.fitcal.app.plan.PlanGoal
import com.fitcal.app.plan.PlanPace
import com.fitcal.app.plan.SuggestionNote
import com.fitcal.app.plan.TargetsMode
import com.fitcal.app.plan.addDays
import com.fitcal.app.plan.formatKg
import com.fitcal.app.plan.formatPlanDate
import com.fitcal.app.plan.isPaceAllowed
import com.fitcal.app.plan.planTargets
import com.fitcal.app.plan.resolvedTargetsMode
import com.fitcal.app.plan.startPlan
import com.fitcal.app.plan.targetWeightRange
import com.fitcal.app.plan.weeksToTarget
import com.fitcal.app.ui.components.ActivityLevel
import com.fitcal.app.ui.components.FitCalTextField
import com.fitcal.app.ui.theme.*
import com.fitcal.shared.model.UserProfile

/**
 * Optional personal plan: goal → body → activity → target weight and pace → targets.
 * Opened only when the user asks for it (dashboard card or Settings); never at first launch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanSetupScreen(
    profile: UserProfile,
    today: String,
    onSave: (UserProfile) -> Unit,
    onBack: () -> Unit,
    onOpenGuide: (String) -> Unit = {},
) {
    val editing = profile.resolvedTargetsMode() == TargetsMode.PLAN && profile.plan != null
    // A fresh profile still holds the 70 kg / 175 cm placeholders; prefill only what the user set.
    val hasRealBody = editing || profile.weight != 70f || profile.height != 175f

    var goal by remember { mutableStateOf(if (editing) PlanGoal.fromKey(profile.goalType) else PlanGoal.LOSE) }
    var heightStr by remember { mutableStateOf(if (hasRealBody) formatKg(profile.height) else "") }
    var weightStr by remember { mutableStateOf(if (hasRealBody) formatKg(profile.weight) else "") }
    var ageStr by remember { mutableStateOf(if (hasRealBody) profile.age.toString() else "") }
    var gender by remember { mutableStateOf(profile.gender) }
    var activity by remember { mutableStateOf(ActivityLevel.fromKey(profile.activityLevel)) }
    var pace by remember { mutableStateOf(PlanPace.fromKey(profile.plan?.pace)) }
    var targetStr by remember { mutableStateOf(profile.plan?.targetWeightKg?.let { formatKg(it) } ?: "") }

    val body = BodyStats.from(
        weightStr.replace(',', '.').toFloatOrNull(),
        heightStr.replace(',', '.').toFloatOrNull(),
        ageStr.toIntOrNull(),
        gender,
        activity.key,
    )
    val range = body?.let { targetWeightRange(it, goal) }
    val target = targetStr.replace(',', '.').toFloatOrNull()
    val targetValid = goal == PlanGoal.MAINTAIN || (range != null && target != null && target in range)

    // Keep the chosen pace safe as the inputs change.
    LaunchedEffect(body, goal) {
        if (body != null && !isPaceAllowed(body, goal, pace)) {
            PlanPace.entries.lastOrNull { isPaceAllowed(body, goal, it) }?.let { pace = it }
        }
    }
    val targets = body?.let { planTargets(it, goal, pace) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(44.dp)
                    .shadow(1.dp, CircleShape)
                    .background(Color.White, CircleShape)
                    .border(1.dp, BorderColor, CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextColor, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = if (editing) "Edit Your Plan" else "Personal Plan",
                style = BrandTypography.ScreenTitle,
                color = TextColor
            )
        }
        Text(
            text = "Optional. FitCal works the same without a plan, and you can change or end it any time.",
            style = BrandTypography.BodySmall,
            color = MutedTextColor
        )

        PlanCard(title = "1 · YOUR GOAL") {
            ChipRow(
                options = PlanGoal.entries,
                selected = goal,
                label = { it.label },
                onSelect = { goal = it },
            )
        }

        PlanCard(title = "2 · ABOUT YOU") {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FitCalTextField(value = heightStr, onValueChange = { heightStr = it }, label = "Height", unit = "cm", modifier = Modifier.weight(1f))
                FitCalTextField(value = weightStr, onValueChange = { weightStr = it }, label = "Weight", unit = "kg", modifier = Modifier.weight(1f))
            }
            FitCalTextField(value = ageStr, onValueChange = { ageStr = it }, label = "Age", unit = "yrs", modifier = Modifier.fillMaxWidth())
            ChipRow(
                options = listOf("Male", "Female"),
                selected = gender,
                label = { it },
                onSelect = { gender = it },
            )
            Text(
                text = "Used only to estimate how much energy your body uses.",
                style = BrandTypography.BodySmall,
                color = MutedTextColor
            )
        }

        PlanCard(title = "3 · HOW ACTIVE ARE YOU?") {
            ActivityLevel.entries.chunked(2).forEach { row ->
                ChipRow(options = row, selected = activity, label = { it.label }, onSelect = { activity = it })
            }
            Text(text = activity.description, style = BrandTypography.BodySmall, color = MutedTextColor)
            Text(
                text = "Not sure? Pick the lower one; you can change it later.",
                style = BrandTypography.BodySmall,
                color = MutedTextColor
            )
        }

        if (goal != PlanGoal.MAINTAIN) {
            PlanCard(title = "4 · TARGET AND PACE") {
                if (body == null) {
                    Text(
                        text = "Enter your height, weight and age (18+) above first.",
                        style = BrandTypography.BodySmall,
                        color = TextColor
                    )
                } else if (range == null) {
                    Text(
                        text = "Your weight is already below the healthy range, so FitCal won't plan weight loss. Try Maintain or Gain muscle instead.",
                        style = BrandTypography.BodySmall,
                        color = DangerTextStrong
                    )
                } else {
                    FitCalTextField(
                        value = targetStr,
                        onValueChange = { targetStr = it },
                        label = "Target weight",
                        unit = "kg",
                        isError = targetStr.isNotBlank() && !targetValid,
                        supportingText = "Between ${formatKg(range.start)} and ${formatKg(range.endInclusive)} kg",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "How fast?",
                            style = BrandTypography.BodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MutedTextColor,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onOpenGuide(GuideIds.TRACKING_WEIGHT) }) {
                            Text("What's a healthy pace?", color = PrimaryAccent, style = BrandTypography.BodySmall)
                        }
                    }
                    PlanPace.entries.forEach { option ->
                        val allowed = isPaceAllowed(body, goal, option)
                        val optionTargets = planTargets(body, goal, option)
                        val kgPerWeek = option.kgPerWeek(goal)
                        val eta = if (targetValid && target != null) {
                            weeksToTarget(body.weightKg, target, kgPerWeek)?.let { addDays(today, it * 7) }
                        } else null
                        PaceRow(
                            title = "${option.label} · ${formatKg(kgPerWeek)} kg/week",
                            detail = if (allowed) {
                                "${optionTargets.calories} kcal/day" + (eta?.let { " · around ${formatPlanDate(it)}" } ?: "")
                            } else {
                                "Too fast to be safe for you"
                            },
                            selected = pace == option,
                            enabled = allowed,
                            recommended = option == PlanPace.STEADY,
                            onSelect = { pace = option },
                        )
                    }
                }
            }
        }

        // Result
        if (targets != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(InfoPanel, RoundedCornerShape(RadiusM))
                    .border(1.dp, BorderColor, RoundedCornerShape(RadiusM))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = "YOUR DAILY TARGETS", style = BrandTypography.CardTitle, color = PrimaryAccent)
                Text(
                    text = "${targets.calories} kcal",
                    style = BrandTypography.SectionTitle,
                    fontWeight = FontWeight.Bold,
                    color = TextColor
                )
                Text(
                    text = "Protein ${targets.proteinG} g · Carbs ${targets.carbsG} g · Fat ${targets.fatG} g",
                    style = BrandTypography.BodySmall,
                    fontWeight = FontWeight.Bold,
                    color = TextColor
                )
                Text(
                    text = "Your maintenance is about ${targets.maintenance} kcal/day.",
                    style = BrandTypography.BodySmall,
                    color = MutedTextColor
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onOpenGuide(GuideIds.ENERGY_BALANCE) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text("What's maintenance?", color = PrimaryAccent, style = BrandTypography.BodySmall, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(12.dp))
                    TextButton(onClick = { onOpenGuide(GuideIds.PROTEIN) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text("Why this much protein?", color = PrimaryAccent, style = BrandTypography.BodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
                when (targets.note) {
                    SuggestionNote.UNDERWEIGHT -> Text(
                        text = "Your BMI is below the healthy range, so these targets maintain your weight.",
                        style = BrandTypography.BodySmall,
                        color = DangerTextStrong
                    )
                    SuggestionNote.NO_SAFE_DEFICIT -> Text(
                        text = "Eating less would go under the safe minimum, so these targets maintain your weight. More activity is the safer way to lose weight here.",
                        style = BrandTypography.BodySmall,
                        color = DangerTextStrong
                    )
                    null -> Unit
                }
                Text(
                    text = "General estimate, not medical advice. If you're pregnant, have a medical condition or a history of disordered eating, check with a doctor first.",
                    style = BrandTypography.BodySmall,
                    color = MutedTextColor
                )
            }
        }

        Button(
            onClick = {
                if (body != null && targetValid) {
                    onSave(profile.startPlan(body, goal, pace, if (goal == PlanGoal.MAINTAIN) null else target, today))
                }
            },
            enabled = body != null && targetValid,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
            shape = RoundedCornerShape(RadiusM),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text(
                text = if (editing) "Save Plan" else "Start My Plan",
                style = BrandTypography.CardTitle,
                color = Color.White
            )
        }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Not now", color = MutedTextColor)
        }
    }
}

@Composable
private fun PlanCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(RadiusL),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(RadiusL))
            .border(1.dp, BorderColor, RoundedCornerShape(RadiusL))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, style = BrandTypography.CardTitle, color = MutedTextColor)
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(option) },
                label = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = label(option),
                            style = BrandTypography.BodySmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryAccent,
                    selectedLabelColor = Color.White,
                    containerColor = SurfaceTint,
                    labelColor = TextColor
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = BorderColor,
                    selectedBorderColor = PrimaryAccent,
                    borderWidth = 1.dp,
                    selectedBorderWidth = 1.5.dp,
                    enabled = true,
                    selected = isSelected
                ),
                shape = RoundedCornerShape(RadiusS),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
            )
        }
    }
}

@Composable
private fun PaceRow(
    title: String,
    detail: String,
    selected: Boolean,
    enabled: Boolean,
    recommended: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .background(if (selected) PrimaryAccent.copy(alpha = 0.08f) else SurfaceTint, RoundedCornerShape(RadiusS))
            .border(1.dp, if (selected) PrimaryAccent else BorderColor, RoundedCornerShape(RadiusS))
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(selectedColor = PrimaryAccent)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, style = BrandTypography.BodySmall, fontWeight = FontWeight.Bold, color = TextColor)
                if (recommended) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Recommended", style = BrandTypography.BodySmall, color = PrimaryAccent)
                }
            }
            Text(text = detail, style = BrandTypography.BodySmall, color = MutedTextColor)
        }
    }
}
