package com.fitcal.app.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.fitcal.app.mealPhotoModel
import com.fitcal.app.nutrition.SUGAR_GUIDELINE_G
import com.fitcal.app.nutrition.isOver
import com.fitcal.app.ui.screens.dashboard.components.SugarFiberCard
import com.fitcal.app.nutrition.FIBER_GUIDE_G
import com.fitcal.app.nutrition.SHOW_SUGAR_AND_FIBRE
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitcal.app.ads.AdBanner
import com.fitcal.app.getCurrentDateString
import com.fitcal.app.ui.components.PressableBox
import com.fitcal.app.ui.components.maintenanceCalories
import com.fitcal.app.ui.components.getAiCoachFeedback
import com.fitcal.app.ui.screens.dashboard.components.CalendarStrip
import com.fitcal.app.ui.screens.dashboard.components.CircularCalorieProgressRing
import com.fitcal.app.ui.screens.dashboard.components.DashboardMacroCard
import com.fitcal.app.ui.screens.dashboard.components.PlanCheckInCard
import com.fitcal.app.ui.screens.dashboard.components.CalorieSuggestionCard
import com.fitcal.app.ui.screens.dashboard.components.WeeklySummaryCard
import com.fitcal.app.ui.screens.dashboard.components.PlanPromptCard
import com.fitcal.app.ui.screens.dashboard.components.PlanTargetReachedCard
import com.fitcal.app.ui.screens.dashboard.components.QuickLogRow
import com.fitcal.app.ui.screens.dashboard.components.StreakChip
import com.fitcal.app.ui.screens.dashboard.components.mealStreak
import com.fitcal.app.ui.screens.dashboard.components.quickLogMeals
import com.fitcal.app.ui.screens.dashboard.components.streakChipLabel
import com.fitcal.app.ui.screens.dashboard.components.WaterTrackerCard
import com.fitcal.app.plan.PlanGoal
import com.fitcal.app.learn.GuideIds
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import com.fitcal.app.plan.TargetsMode
import com.fitcal.app.plan.hasReachedTarget
import com.fitcal.app.plan.isCheckInDue
import com.fitcal.app.plan.projectedTargetDate
import com.fitcal.app.plan.resolvedTargetsMode
import com.fitcal.app.ui.theme.*
import com.fitcal.shared.model.LoggedMeal
import com.fitcal.shared.model.UserProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.fitcal.app.getCurrentHourOfDay
import com.fitcal.app.ui.screens.dashboard.components.DockedScanButtonSize
import com.fitcal.app.ui.screens.dashboard.components.FloatingScanButton
import com.fitcal.app.ui.screens.dashboard.components.ScanDockMargin
import com.fitcal.app.ui.screens.dashboard.components.ScanHeroCard
import com.fitcal.app.ui.screens.dashboard.components.ScanShutterSize
import com.fitcal.app.ui.screens.dashboard.components.placeScanButton
import com.fitcal.app.ui.screens.dashboard.components.scanPromptForHour
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    meals: List<LoggedMeal>,
    profile: UserProfile,
    selectedDate: String,
    waterLogged: Int,
    scansRemaining: Int,
    onDateSelected: (String) -> Unit,
    onWaterChanged: (Int) -> Unit,
    onScanClicked: () -> Unit,
    onSettingsClicked: () -> Unit,
    // "Describe your meal": type it instead of taking a photo (still one AI scan).
    onDescribeMealClicked: (() -> Unit)? = null,
    onMonetizationClicked: () -> Unit,
    onOpenFoodLibrary: () -> Unit = {},
    onDeleteMeal: (LoggedMeal) -> Unit,
    onMealClicked: (LoggedMeal) -> Unit,
    onRestoreMeal: (LoggedMeal) -> Unit,
    // Optional personal plan
    today: String = getCurrentDateString(),
    showPlanPrompt: Boolean = false,
    onSetUpPlan: () -> Unit = {},
    onDismissPlanPrompt: () -> Unit = {},
    checkInSnoozedUntil: String? = null,
    onCheckIn: (Float) -> Unit = {},
    onSnoozeCheckIn: () -> Unit = {},
    onSwitchToMaintain: () -> Unit = {},
    onEndPlan: () -> Unit = {},
    // Opens a guide from Library → Guides by id (see com.fitcal.app.learn.Guides).
    onOpenGuide: (String) -> Unit = {},
    // Without a calorie target, a full ring = this many kcal (the recent daily high).
    ringScaleKcal: Int = 0,
    // Every logged meal, for the streak and the favourites/recent row.
    allMeals: List<LoggedMeal> = meals,
    // Copies a meal, items included, to today at the current time. Uses no scan.
    onLogAgain: (LoggedMeal) -> Unit = {},
    // Stars or unstars a meal.
    onToggleFavorite: (LoggedMeal) -> Unit = {},
    // Opens the Progress screen (weight history, last 7 days). Linked from the calorie card.
    onOpenProgress: () -> Unit = {},
    // Adjusting target: a suggestion for plan users, shown until accepted or "Not now".
    calorieSuggestion: com.fitcal.app.plan.CalorieSuggestion? = null,
    onAcceptSuggestion: () -> Unit = {},
    onDismissSuggestion: () -> Unit = {},
    // Last week's summary, on the first open of a new week.
    weeklySummary: Pair<com.fitcal.app.ui.screens.progress.SummaryWeek, com.fitcal.app.ui.screens.progress.WeekSummary>? = null,
    onDismissWeeklySummary: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // F0.2 — One-undo-slot: stores the deleted meal + its position in filteredMeals.
    // A second delete while a slot is pending replaces it (oldest delete is NOT kept,
    // per spec: "one undo slot only").
    var pendingUndoMeal by remember { mutableStateOf<Pair<LoggedMeal, Int>?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Filter meals for the selected date
    val filteredMeals = remember(meals, selectedDate) {
        meals.filter {
            it.date == selectedDate || (it.date.isBlank() && selectedDate == getCurrentDateString())
        }
    }

    val streakLabel = remember(allMeals, today) { streakChipLabel(mealStreak(allMeals.map { it.date }, today)) }
    val quickLog = remember(allMeals) { quickLogMeals(allMeals) }
    val logAgain: (LoggedMeal) -> Unit = { meal ->
        onLogAgain(meal)
        coroutineScope.launch {
            snackbarHostState.showSnackbar(
                message = if (selectedDate == today) "${meal.name} logged again" else "${meal.name} logged to today",
                duration = SnackbarDuration.Short
            )
        }
    }

    // Aggregate values
    val totalCalories = filteredMeals.sumOf { it.calories }
    val totalProtein = filteredMeals.sumOf { it.protein.toDouble() }.toFloat()
    val totalCarbs = filteredMeals.sumOf { it.carbs.toDouble() }.toFloat()
    val totalFat = filteredMeals.sumOf { it.fat.toDouble() }.toFloat()
    val totalSugar = filteredMeals.sumOf { it.sugar.toDouble() }.toFloat()
    val totalFiber = filteredMeals.sumOf { it.fiber.toDouble() }.toFloat()

    // Targets are optional: without a plan or own numbers, show what was eaten, no goals.
    val targetsMode = remember(profile) { profile.resolvedTargetsMode() }
    val hasTargets = targetsMode != TargetsMode.NONE
    val planGoal = PlanGoal.fromKey(profile.goalType)
    val planTarget = profile.plan?.targetWeightKg
    val targetReached = targetsMode == TargetsMode.PLAN && hasReachedTarget(planGoal, profile.weight, planTarget)
    val checkInDue = !targetReached && profile.isCheckInDue(today, checkInSnoozedUntil)

    val caloriePercent = if (profile.calGoal > 0) ((totalCalories.toFloat() / profile.calGoal) * 100).toInt() else 0
    val proteinPercent = if (profile.proteinGoal > 0) ((totalProtein / profile.proteinGoal) * 100).toInt() else 0
    val carbsPercent = if (profile.carbsGoal > 0) ((totalCarbs / profile.carbsGoal) * 100).toInt() else 0
    val fatPercent = if (profile.fatGoal > 0) ((totalFat / profile.fatGoal) * 100).toInt() else 0

    // Dynamic AI Coach Comments
    val aiCoachFeedback = remember(totalCalories, totalProtein, totalCarbs, totalFat, profile) {
        getAiCoachFeedback(totalProtein, totalCarbs, totalFat, totalCalories, profile)
    }

    // Maintenance calories for the user's activity level
    val maintenanceCal = remember(profile) { maintenanceCalories(profile) }
    val deficitBalance = maintenanceCal - totalCalories

    // Floating scan button: docks above the banner, lands in the scan card's shutter slot
    // when it scrolls into place (see placeScanButton).
    val scanPrompt = remember { scanPromptForHour(getCurrentHourOfDay()) }
    var overlayTopY by remember { mutableFloatStateOf(0f) }
    var overlayHeight by remember { mutableFloatStateOf(0f) }
    var shutterSlotCenterY by remember { mutableStateOf<Float?>(null) }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = DockedScanButtonSize + ScanDockMargin)
            )
        },
        bottomBar = {
            AdBanner(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgColor)
                    .navigationBarsPadding()
            )
        },
        containerColor = Color.Transparent,
        // The content applies statusBarsPadding() itself; the default insets would add the
        // status bar a second time and leave a big gap above the header.
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        val density = LocalDensity.current
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        // Bottom of the dock: above the banner, or above the navigation bar when there is no banner.
        val dockBottomInset by rememberUpdatedState(maxOf(paddingValues.calculateBottomPadding(), navBarBottom))
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val scanButtonPlacement by remember(density, statusBarTop) {
            derivedStateOf {
                with(density) {
                    val dockCenterY = overlayHeight - (dockBottomInset + ScanDockMargin).toPx() - DockedScanButtonSize.toPx() / 2f
                    val topLimitY = (statusBarTop + ScanDockMargin).toPx() + ScanShutterSize.toPx() / 2f
                    placeScanButton(
                        slotCenterY = shutterSlotCenterY?.minus(overlayTopY),
                        dockCenterY = dockCenterY,
                        topLimitY = topLimitY
                    )
                }
            }
        }
        val scanButtonY = remember { Animatable(0f) }
        var scanButtonPositioned by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            // While landed, the button follows the slot exactly. When it docks or undocks it
            // springs to its new place, and keeps chasing a moving slot until it catches up.
            val settlePx = with(density) { 2.dp.toPx() }
            var lastLanded: Boolean? = null
            var settling = false
            snapshotFlow { scanButtonPlacement }
                .filter { overlayHeight > 0f }
                .collectLatest { placement ->
                    val landedChanged = lastLanded != null && lastLanded != placement.landed
                    lastLanded = placement.landed
                    val distance = abs(scanButtonY.value - placement.centerY)
                    if (scanButtonPositioned && !REDUCED_MOTION_ENABLED && (landedChanged || settling) && distance > settlePx) {
                        settling = true
                        scanButtonY.animateTo(placement.centerY, spring(dampingRatio = 0.75f, stiffness = 600f))
                        settling = false
                    } else {
                        settling = false
                        scanButtonY.snapTo(placement.centerY)
                    }
                    scanButtonPositioned = true
                }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    overlayTopY = coords.positionInRoot().y
                    overlayHeight = coords.size.height.toFloat()
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(scrollState)
                    .padding(24.dp)
                    .padding(paddingValues),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header Row: Today + Settings Gear Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        // F2.1 ScreenTitle replaces hardcoded 28.sp
                        Text(
                            text = if (selectedDate == getCurrentDateString()) "Today" else "Log History",
                            style = BrandTypography.ScreenTitle,
                            color = TextColor
                        )
                        // F2.2 BodySmall (12sp) is fine for MutedTextColor
                        Text(
                            text = "FitCal Wellness Dashboard",
                            style = BrandTypography.BodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MutedTextColor
                        )
                    }

                    // Spaced so the circles never touch; each keeps a 44dp hit area (F0.3).
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Food Library — browsable reference content. First-class surface
                        // (not buried in Settings): AdMob rejected earlier builds partly for thin
                        // content, and this is genuine user value independent of scanning.
                        // Search icon: the library is searched; a ☰ read as "open the menu".
                        HeaderIconButton(Icons.Default.Search, "Food Library", onOpenFoodLibrary)
                        // Ads & Perks - monetization is a first-class surface, NOT buried in Settings.
                        // Star icon opens the dedicated Ads & Perks screen (quota, rewarded ads, provider).
                        HeaderIconButton(Icons.Default.Star, "Ads & Perks", onMonetizationClicked)
                        HeaderIconButton(Icons.Default.Settings, "Settings", onSettingsClicked)
                    }
                }

                // Logging streak; only shown from 2 days in a row.
                streakLabel?.let { StreakChip(it) }

                // Calendar Day Selection Strip
                CalendarStrip(
                    selectedDate = selectedDate,
                    today = today,
                    onDateSelected = onDateSelected
                )

                // Calorie Progress Circular Ring
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(RadiusXL), // F1.2: was 28.dp → RadiusXL=32
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(2.dp, RoundedCornerShape(RadiusXL))
                        .border(1.dp, BorderColor, RoundedCornerShape(RadiusXL))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        CircularCalorieProgressRing(
                            consumed = totalCalories,
                            goal = if (hasTargets) profile.calGoal else 0,
                            scaleKcal = ringScaleKcal
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // F2.1 Eyebrow replaces 10.sp hardcode
                            Text(
                                text = "CALORIE STATS",
                                style = BrandTypography.Eyebrow,
                                color = MutedTextColor
                            )

                            Column {
                                Text(
                                    text = "Consumed",
                                    style = BrandTypography.BodySmall,
                                    color = MutedTextColor
                                )
                                Text(
                                    text = "$totalCalories kcal",
                                    style = BrandTypography.KpiNumeral,
                                    color = TextColor
                                )
                            }

                            if (hasTargets) Column {
                                Text(
                                    text = "Daily Budget",
                                    style = BrandTypography.BodySmall,
                                    color = MutedTextColor
                                )
                                Text(
                                    text = "${profile.calGoal} kcal",
                                    style = BrandTypography.KpiNumeral,
                                    color = PrimaryAccent
                                )
                            } else {
                                Text(
                                    text = "No daily target set",
                                    style = BrandTypography.BodySmall,
                                    color = MutedTextColor
                                )
                            }

                            Text(
                                text = "What's a kcal?",
                                style = BrandTypography.BodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryAccent,
                                modifier = Modifier
                                    .clickable(role = Role.Button) { onOpenGuide(GuideIds.CALORIES) }
                                    .padding(vertical = 6.dp)
                            )

                            Text(
                                text = "Progress →",
                                style = BrandTypography.BodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryAccent,
                                modifier = Modifier
                                    .semantics { contentDescription = "Progress: weight and last 7 days" }
                                    .clickable(role = Role.Button, onClick = onOpenProgress)
                                    .padding(vertical = 6.dp)
                            )

                            // Maintenance needs real body stats, which only a plan guarantees.
                            if (targetsMode == TargetsMode.PLAN) Column {
                                Text(
                                    text = "BMR Deficit",
                                    style = BrandTypography.BodySmall,
                                    color = MutedTextColor
                                )
                                Text(
                                    text = if (deficitBalance >= 0) "$deficitBalance kcal deficit" else "${-deficitBalance} kcal surplus",
                                    style = BrandTypography.BodySmall,
                                    fontWeight = FontWeight.Bold,
                                    // F1.1: was Color(0xFFEF4444) → DangerColor
                                    color = if (deficitBalance >= 0) PrimaryAccent else DangerColor
                                )
                            }
                        }
                    }
                }

                // Horizontal Macro strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        DashboardMacroCard("Protein", "${totalProtein.toInt()}g", if (hasTargets) "$proteinPercent%" else "", ProteinColor, over = false) { onOpenGuide(GuideIds.PROTEIN) }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        DashboardMacroCard("Carbs", "${totalCarbs.toInt()}g", if (hasTargets) "$carbsPercent%" else "", CarbsColor, over = hasTargets && isOver(totalCarbs, profile.carbsGoal)) { onOpenGuide(GuideIds.CARBS) }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        DashboardMacroCard("Fats", "${totalFat.toInt()}g", if (hasTargets) "$fatPercent%" else "", FatColor, over = hasTargets && isOver(totalFat, profile.fatGoal)) { onOpenGuide(GuideIds.FAT) }
                    }
                }

                // Only once something is logged, so an empty day stays uncluttered.
                if (SHOW_SUGAR_AND_FIBRE && filteredMeals.isNotEmpty()) {
                    SugarFiberCard(totalSugar, SUGAR_GUIDELINE_G, totalFiber, FIBER_GUIDE_G) { onOpenGuide(GuideIds.SUGAR) }
                }

                weeklySummary?.let { (week, summary) ->
                    WeeklySummaryCard(
                        week = week,
                        summary = summary,
                        onSeeProgress = onOpenProgress,
                        onDismiss = onDismissWeeklySummary,
                    )
                }

                // Optional personal plan: invitation, check-in, suggestion, or target reached
                when {
                    targetReached && planTarget != null -> PlanTargetReachedCard(
                        targetWeightKg = planTarget,
                        onMaintain = onSwitchToMaintain,
                        onNewTarget = onSetUpPlan,
                        onEndPlan = onEndPlan,
                    )
                    checkInDue -> PlanCheckInCard(
                        currentWeightKg = profile.weight,
                        projectedDate = profile.projectedTargetDate(today),
                        onUpdate = onCheckIn,
                        onNotNow = onSnoozeCheckIn,
                        onWhyFluctuates = { onOpenGuide(GuideIds.TRACKING_WEIGHT) },
                    )
                    calorieSuggestion != null -> CalorieSuggestionCard(
                        currentCalories = profile.calGoal,
                        suggestion = calorieSuggestion,
                        onAccept = onAcceptSuggestion,
                        onNotNow = onDismissSuggestion,
                        onLearnMore = { onOpenGuide(GuideIds.TRACKING_WEIGHT) },
                    )
                    targetsMode == TargetsMode.NONE && showPlanPrompt -> PlanPromptCard(
                        onSetUp = onSetUpPlan,
                        onDismiss = onDismissPlanPrompt,
                    )
                }

                // Water Intake Logger
                WaterTrackerCard(
                    waterLogged = waterLogged,
                    onWaterChanged = onWaterChanged
                )

                // F1.1 + F2.1: AI Coach card — was Color(0xFFF8FAFC)/9.sp → InfoPanel/Eyebrow
                // Its advice compares intake with targets, so it needs targets.
                if (hasTargets) Card(
                    colors = CardDefaults.cardColors(containerColor = InfoPanel),
                    shape = RoundedCornerShape(RadiusL), // F1.2: was 20.dp → RadiusL=24
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderColor, RoundedCornerShape(RadiusL))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🧠", fontSize = 28.sp) // non-interactive decoration
                        Column {
                            // F2.1: Eyebrow (10sp) replaces 9.sp — 9sp removed per spec
                            Text(
                                text = "AI COACH ADVICE",
                                style = BrandTypography.Eyebrow,
                                color = PrimaryAccent
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = aiCoachFeedback,
                                style = BrandTypography.BodySmall,
                                fontWeight = FontWeight.Medium,
                                color = TextColor
                            )
                        }
                    }
                }

                // Snap and log card. Its shutter slot is where the floating scan button lands.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "SNAP AND LOG",
                        style = BrandTypography.Eyebrow,
                        color = MutedTextColor
                    )
                    ScanHeroCard(
                        prompt = scanPrompt,
                        scansRemaining = scansRemaining,
                        onScan = onScanClicked,
                        onShutterSlotPositioned = { shutterSlotCenterY = it }
                    )
                    if (onDescribeMealClicked != null) {
                        TextButton(
                            onClick = onDescribeMealClicked,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Text(
                                text = "No photo? Describe your meal instead",
                                style = BrandTypography.BodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryAccent
                            )
                        }
                    }
                }

                // One-tap logging above the history: favourites, or recent meals while nothing is starred.
                QuickLogRow(quickLog = quickLog, onLog = logAgain)

                // Logged Meals History List
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "LOGGED HISTORY",
                        style = BrandTypography.Eyebrow,
                        color = MutedTextColor
                    )

                    if (filteredMeals.isEmpty()) {
                        // F5.1 — Friendly empty state with CTA
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(1.dp, RoundedCornerShape(RadiusL))
                                .background(Color.White, RoundedCornerShape(RadiusL))
                                .border(1.dp, BorderColor, RoundedCornerShape(RadiusL))
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(text = "🍽️", fontSize = 48.sp) // non-interactive decoration
                                Text(
                                    text = "No meals yet",
                                    style = BrandTypography.SectionTitle,
                                    fontWeight = FontWeight.Bold,
                                    color = TextColor,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Snap a photo of your next meal and FitCal will log it for you.",
                                    style = BrandTypography.BodySmall,
                                    color = MutedTextColor,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = onScanClicked,
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                                    shape = RoundedCornerShape(RadiusM)
                                ) {
                                    Text(
                                        text = "Start Scanning",
                                        style = BrandTypography.Body,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    } else {
                        filteredMeals.forEachIndexed { filteredIndex, meal ->
                            // Tap a logged meal to reopen its ingredients and correct them.
                            PressableBox(onTap = { onMealClicked(meal) }) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .shadow(1.dp, RoundedCornerShape(RadiusM))
                                        .background(Color.White, RoundedCornerShape(RadiusM))
                                        .border(1.dp, BorderColor, RoundedCornerShape(RadiusM))
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    val emoji = remember(meal.name) {
                                        val lower = meal.name.lowercase()
                                        when {
                                            lower.contains("salad") || lower.contains("bowl") || lower.contains("quinoa") || lower.contains("avocado") -> "🥗"
                                            lower.contains("salmon") || lower.contains("fish") || lower.contains("seafood") -> "🐟"
                                            lower.contains("egg") -> "🍳"
                                            lower.contains("steak") || lower.contains("beef") || lower.contains("pork") || lower.contains("meat") -> "🥩"
                                            lower.contains("chicken") || lower.contains("turkey") || lower.contains("poultry") -> "🍗"
                                            lower.contains("fruit") || lower.contains("apple") || lower.contains("banana") -> "🍎"
                                            else -> "🍱"
                                        }
                                    }

                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(48.dp)
                                            // F1.1: was Color(0xFFF1F5F9) → SurfaceTint
                                            .background(SurfaceTint, CircleShape)
                                    ) {
                                        // The meal's own photo when this phone has it, else an emoji.
                                        val photo = remember(meal.photoPath) { mealPhotoModel(meal.photoPath) }
                                        if (photo != null) {
                                            coil3.compose.AsyncImage(
                                                model = photo,
                                                contentDescription = null,
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                            )
                                        } else {
                                            Text(text = emoji, fontSize = 24.sp) // non-interactive decoration
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (meal.favorite) "★ ${meal.name}" else meal.name,
                                            style = BrandTypography.SectionTitle,
                                            color = TextColor
                                        )
                                        Text(
                                            text = "${meal.timestamp} | P: ${meal.protein.toInt()}g C: ${meal.carbs.toInt()}g F: ${meal.fat.toInt()}g",
                                            // F2.2: Micro (11sp) at MutedTextColor — 11sp >= 12sp threshold? No — spec says nothing below 12sp
                                            // in MutedTextColor. Substitution: use BodySmall (12sp) for this secondary line.
                                            style = BrandTypography.BodySmall,
                                            color = MutedTextColor
                                        )
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "${meal.calories} kcal",
                                            style = BrandTypography.SectionTitle,
                                            color = TextColor
                                        )

                                        Row {
                                            // Log again / favourite, behind an overflow menu.
                                            var menuOpen by remember(meal.id) { mutableStateOf(false) }
                                            Box {
                                                IconButton(
                                                    onClick = { menuOpen = true },
                                                    modifier = Modifier.size(44.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.MoreVert,
                                                        contentDescription = "More actions for ${meal.name}",
                                                        tint = MutedTextColor,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                DropdownMenu(
                                                    expanded = menuOpen,
                                                    onDismissRequest = { menuOpen = false }
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text("Log again today") },
                                                        onClick = {
                                                            menuOpen = false
                                                            logAgain(meal)
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(if (meal.favorite) "Remove from favourites" else "Add to favourites") },
                                                        leadingIcon = {
                                                            Icon(
                                                                imageVector = Icons.Default.Star,
                                                                contentDescription = null,
                                                                tint = if (meal.favorite) MutedTextColor else FatColor
                                                            )
                                                        },
                                                        onClick = {
                                                            menuOpen = false
                                                            onToggleFavorite(meal)
                                                        }
                                                    )
                                                }
                                            }
    
                                            // F0.3: wrap delete icon in IconButton (≥44dp hit area)
                                            // F0.2: onDeleteMeal now receives the meal object; undo slot updated
                                            IconButton(
                                                onClick = {
                                                    val position = filteredIndex
                                                    pendingUndoMeal = Pair(meal, position)
                                                    onDeleteMeal(meal)
                                                    // Auto-dismiss snackbar after 5s; the LaunchedEffect below handles it
                                                    coroutineScope.launch {
                                                        val result = snackbarHostState.showSnackbar(
                                                            message = "Meal deleted",
                                                            actionLabel = "Undo",
                                                            duration = SnackbarDuration.Short // ~4s — closest to 5s in M3
                                                        )
                                                        if (result == SnackbarResult.ActionPerformed) {
                                                            val toRestore = pendingUndoMeal
                                                            if (toRestore != null) {
                                                                onRestoreMeal(toRestore.first)
                                                                pendingUndoMeal = null
                                                            }
                                                        } else {
                                                            pendingUndoMeal = null
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.size(44.dp) // F0.3: ≥44dp
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Delete meal ${meal.name}",
                                                    // F1.1: was Color(0xFFEF4444) → DangerColor
                                                    tint = DangerColor.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Room for the docked scan button so it never covers the last meal.
                Spacer(modifier = Modifier.height(DockedScanButtonSize))
            }

            FloatingScanButton(
                landed = scanButtonPlacement.landed,
                scansRemaining = scansRemaining,
                onClick = onScanClicked,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset {
                        IntOffset(0, (scanButtonY.value - DockedScanButtonSize.toPx() / 2f).roundToInt())
                    }
                    .graphicsLayer { alpha = if (scanButtonPositioned) 1f else 0f }
            )
        }
    }
}

/** Round header action: 44dp hit area, white surface with a hairline border. */
@Composable
private fun HeaderIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .shadow(1.dp, CircleShape)
            .background(Color.White, CircleShape)
            .border(1.dp, BorderColor, CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = TextColor,
            modifier = Modifier.size(20.dp)
        )
    }
}
