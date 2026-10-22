package com.fitcal.app

import com.fitcal.app.nutrition.dayTotals
import kotlin.math.roundToInt
import com.fitcal.app.plan.hasDailyTargets
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.fitcal.app.ads.AdManager
import com.fitcal.app.privacy.PrivacyConsent
import com.fitcal.app.ui.screens.privacy.PrivacyConsentScreen
import com.fitcal.app.ads.ScanQuotaManager
import com.fitcal.app.data.PreferenceKeyValueStorage
import com.fitcal.app.telemetry.Analytics
import com.fitcal.app.telemetry.CohortRetentionTracker
import com.fitcal.app.ui.navigation.AuthDestination
import com.fitcal.app.ui.navigation.BarcodeResultDestination
import com.fitcal.shared.api.OpenFoodFactsClient
import com.fitcal.app.ui.navigation.CameraDestination
import com.fitcal.app.ui.navigation.DashboardDestination
import com.fitcal.app.ui.navigation.DescribeMealDestination
import com.fitcal.app.ui.screens.describe.DescribeMealScreen
import com.fitcal.app.ui.navigation.EditMealDestination
import com.fitcal.app.ui.navigation.GoalQuizDestination
import com.fitcal.app.plan.GoalQuizPolicy
import com.fitcal.app.plan.acceptCalorieSuggestion
import com.fitcal.app.plan.calorieSuggestion
import com.fitcal.app.plan.dismissCalorieSuggestion
import com.fitcal.app.ui.screens.progress.WeeklySummaryPrefs
import com.fitcal.app.ui.screens.progress.lastCompletedWeek
import com.fitcal.app.ui.screens.progress.weeklySummaryFor
import com.fitcal.app.ui.screens.plan.GoalQuizOffer
import com.fitcal.app.ui.screens.plan.GoalQuizScreen
import com.fitcal.app.ui.navigation.PlanDestination
import com.fitcal.app.ui.screens.plan.PlanSetupScreen
import com.fitcal.app.plan.BodyStats
import com.fitcal.app.plan.PlanGoal
import com.fitcal.app.plan.PlanPace
import com.fitcal.app.plan.addDays
import com.fitcal.app.plan.checkInWithEntry
import com.fitcal.app.plan.weightEntryForChange
import com.fitcal.app.ui.navigation.ProgressDestination
import com.fitcal.app.ui.screens.progress.ProgressScreen
import com.fitcal.app.plan.endPlan
import com.fitcal.app.plan.hasReachedTarget
import com.fitcal.app.plan.resolvedTargetsMode
import com.fitcal.app.plan.startPlan
import com.fitcal.app.ui.navigation.MonetizationDestination
import com.fitcal.app.ui.navigation.ResultDestination
import com.fitcal.app.ui.navigation.SettingsDestination
import com.fitcal.app.ui.navigation.FoodLibraryDestination
import com.fitcal.app.ui.navigation.GuideDestination
import com.fitcal.app.ui.screens.dashboard.components.copyAsNewLog
import com.fitcal.app.ui.screens.dashboard.components.ringScaleWithoutTarget
import com.fitcal.app.ui.screens.library.GuideScreen
import com.fitcal.app.learn.Guides
import com.fitcal.app.ui.navigation.PrivacyPolicyDestination
import com.fitcal.app.ui.navigation.TermsDestination
import com.fitcal.app.ui.screens.privacy.PrivacyPolicyScreen
import com.fitcal.app.ui.screens.privacy.AgeGateScreen
import com.fitcal.app.ui.screens.library.FoodLibraryScreen
import com.fitcal.app.privacy.AgeGate
import com.fitcal.app.ui.components.newMealId
import com.fitcal.app.auth.SignInPromptPolicy
import com.fitcal.app.auth.SignInCardPolicy
import com.fitcal.app.ui.screens.auth.AuthActions
import com.fitcal.app.ui.screens.auth.AuthScreen
import com.fitcal.app.ui.screens.auth.SignInCard
import com.fitcal.app.ui.screens.auth.SocialSignIn
import com.fitcal.shared.auth.AuthMessages
import com.fitcal.shared.auth.EmailSignUpStart
import com.fitcal.shared.data.AccountDataManager
import io.github.jan.supabase.compose.auth.ComposeAuth
import io.github.jan.supabase.compose.auth.IdTokenCallback
import io.github.jan.supabase.compose.auth.appleNativeLogin
import io.github.jan.supabase.compose.auth.composable.NativeSignInResult
import io.github.jan.supabase.compose.auth.composable.rememberSignInWithApple
import io.github.jan.supabase.compose.auth.composable.rememberSignInWithGoogle
import io.github.jan.supabase.compose.auth.composeAuth
import io.github.jan.supabase.compose.auth.googleNativeLogin
import com.fitcal.app.ui.screens.camera.CameraScreen
import com.fitcal.app.ui.screens.dashboard.DashboardScreen
import com.fitcal.app.ui.screens.monetization.MonetizationScreen
import com.fitcal.app.ui.screens.review.ResultScreen
import com.fitcal.app.ui.screens.review.components.LEGACY_PORTION_GRAMS
import com.fitcal.app.ui.screens.review.components.toEditableResponse
import com.fitcal.app.ui.screens.settings.SettingsScreen
import com.fitcal.app.ui.theme.BgColor
import com.fitcal.app.ui.theme.FitCalTheme
import com.fitcal.shared.api.FeedbackRequest
import com.fitcal.shared.api.FeedbackSendResult
import com.fitcal.shared.api.GatewayNutritionClient
import com.fitcal.shared.auth.SupabaseAuthService
import com.fitcal.shared.auth.SupabaseClientFactory
import com.fitcal.shared.data.LocalMealRepository
import com.fitcal.shared.data.LocalUserRepository
import com.fitcal.shared.data.MealRepository
import com.fitcal.shared.data.UserRepository
import com.fitcal.shared.model.LoggedMeal
import com.fitcal.shared.model.NutritionResponse
import com.fitcal.shared.model.UserProfile
import com.fitcal.app.telemetry.DiagnosticLevel
import com.fitcal.app.telemetry.DiagnosticsCrashHook
import com.fitcal.shared.telemetry.TelemetryUploader
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private const val KEY_PLAN_PROMPT_DISMISSED = "plan_prompt_dismissed"
private const val KEY_PLAN_CHECKIN_SNOOZED_UNTIL = "plan_checkin_snoozed_until"
private const val HISTORY_DAYS = 365

@Composable
fun App() {
    val coroutineScope = rememberCoroutineScope()
    val storage = remember {
        PreferenceKeyValueStorage().also {
            com.fitcal.shared.subscription.SubscriptionManager.initialize(it)
        }
    }
    val supabaseClient = remember {
        SupabaseClientFactory.getOrCreate(
            url = supabaseUrl,
            anonKey = supabaseAnonKey,
            secureStore = createSecureStringStore(),
        ) {
            install(ComposeAuth) {
                if (isGoogleSignInAvailable) googleNativeLogin(serverClientId = googleWebClientId)
                if (isAppleSignInAvailable) appleNativeLogin()
            }
        }
    }
    val authService = remember {
        SupabaseAuthService(supabaseClient).also { service ->
            ScanQuotaManager.isPermanentAccountProvider = { service.isPermanentUser() }
        }
    }
    val quotaManager = remember {
        com.fitcal.shared.quota.SupabaseQuotaManager(supabaseClient).apply {
            // Rewarded-ad bonus grants must be server-authoritative: the client used
            // to call grant_bonus_scan directly, which let any user mint unlimited
            // scans. The gateway derives the user from the verified JWT instead.
            gatewayClient = GatewayNutritionClient(
                gatewayUrl = gatewayUrl,
                jwtProvider = { authService.currentSession()?.accessToken },
                reAuthenticator = { authService.ensureSignedIn() },
                dailyAllowanceProvider = { ScanQuotaManager.getDailyFreeLimit() },
            )
        }
    }
    // GatewayNutritionClient — JWT injected from auth service at call time.
    // Phase 10: reAuthenticator re-establishes the session on 401/missing JWT
    // so a stale/expired token triggers re-auth + one retry instead of the
    // "Authentication required" dead-end.
    val apiClient = remember {
        GatewayNutritionClient(
            gatewayUrl = gatewayUrl,
            jwtProvider = { authService.currentSession()?.accessToken },
            reAuthenticator = { authService.ensureSignedIn() },
            dailyAllowanceProvider = { ScanQuotaManager.getDailyFreeLimit() }
        ).also { client ->
            // Events queue on the device from here on; nothing is recorded before consent.
            Analytics.init(sender = client::sendEvents)
        }
    }
    val syncEngine = remember {
        com.fitcal.shared.sync.SyncEngine(storage, supabaseClient)
    }
    val localMealRepository = remember { LocalMealRepository(storage) }
    // Barcode lookups: Open Food Facts, cached on the device by barcode. No AI, no quota.
    val openFoodFactsClient = remember { OpenFoodFactsClient(storage = storage) }
    val localUserRepository = remember { LocalUserRepository(storage) }
    val mealRepository: MealRepository = remember {
        com.fitcal.shared.data.SupabaseMealRepository(
            localMealRepository,
            syncEngine,
            supabaseClient
        )
    }
    val userRepository: UserRepository = remember {
        com.fitcal.shared.data.SupabaseUserRepository(
            localUserRepository,
            syncEngine,
            supabaseClient
        )
    }
    val accountData = remember {
        AccountDataManager(localMealRepository, localUserRepository, syncEngine, ::newMealId)
    }

    var userProfile by remember {
        mutableStateOf(
            UserProfile(
                weight = 70f,
                height = 175f,
                calGoal = 2000,
                proteinGoal = 150,
                carbsGoal = 200,
                fatGoal = 65
            )
        )
    }

    var selectedDateKey by remember { mutableStateOf(getCurrentDateString()) }

    // Optional personal plan: the dashboard invitation can be dismissed for good, and the
    // two-weekly check-in can be snoozed for a few days. Both are per-device conveniences.
    var planPromptDismissed by remember { mutableStateOf(loadPreference(KEY_PLAN_PROMPT_DISMISSED, "") == "true") }
    var checkInSnoozedUntil by remember {
        mutableStateOf(loadPreference(KEY_PLAN_CHECKIN_SNOOZED_UNTIL, "").ifBlank { null })
    }

    val mealsFlow = remember(selectedDateKey, mealRepository) {
        mealRepository.observeMealsForDate(selectedDateKey)
    }
    val loggedMeals by mealsFlow.collectAsState(initial = emptyList())
    // Daily totals across all days, so the ring has a scale when there's no calorie target.
    val allMeals by remember(mealRepository) { mealRepository.observeAllMeals() }.collectAsState(initial = emptyList())
    val ringScaleKcal = remember(allMeals) {
        ringScaleWithoutTarget(
            allMeals.groupBy { it.date }.mapValues { (_, meals) -> meals.sumOf { it.calories } },
            getCurrentDateString(),
        )
    }

    val waterFlow = remember(selectedDateKey, userRepository) {
        userRepository.observeWaterIntake(selectedDateKey)
    }
    val waterLoggedToday by waterFlow.collectAsState(initial = 0)
    val weightEntries by remember(userRepository) { userRepository.observeWeightEntries() }
        .collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        userProfile = userRepository.getUserProfile()
    }

    // Keep track of the last captured image bytes to render on ResultScreen
    var lastCapturedImageBytes by remember { mutableStateOf<ByteArray?>(null) }

    // ── Privacy consent gate ──────────────────────────────────────────────────
    // Nothing below this line may run before consent: no Supabase session, no ad
    // preload, no camera, no gateway call. initPrivacyConsentStore() is invoked by
    // the platform entry point (MainActivity / iOS main) before setContent.
    var privacyAccepted by remember { mutableStateOf(PrivacyConsent.isPrivacyAccepted()) }
    var showLegalDocument by remember { mutableStateOf(false) }
    var ageAcknowledged by remember { mutableStateOf(AgeGate.hasAcknowledged()) }

    // Set when the user confirms the eating-disorder caution at the age gate.
    // Suppresses calorie-goal emphasis for younger adult profiles.
    var userProfileCautionAccepted by remember { mutableStateOf(false) }

    // Legal docs must be readable BEFORE consent — a user cannot accept terms
    // they are unable to read. This early return keeps it a full-screen overlay
    // rather than composing both screens at once.
    if (showLegalDocument) {
        FitCalTheme {
            PrivacyPolicyScreen(onClose = { showLegalDocument = false })
        }
        return
    }

    if (!privacyAccepted) {
        FitCalTheme {
            PrivacyConsentScreen(
                onDecisionComplete = {
                    privacyAccepted = true
                    Analytics.onPrivacyStateChanged()
                    Analytics.track(
                        "consent_completed",
                        "ads_personalized" to PrivacyConsent.isAdsPersonalizationEnabled(),
                    )
                },
                onOpenPrivacyPolicy = { showLegalDocument = true },
                onOpenTerms = { showLegalDocument = true },
            )
        }
        return
    }

    // ── Age gate (Play Families / AdMob adult-content policy) ───────────────
    // Runs AFTER consent but BEFORE any app surface. FitCal is declared 18+ only;
    // this makes the declaration enforceable rather than a store-listing promise.
    if (!ageAcknowledged) {
        FitCalTheme {
            AgeGateScreen(
                onComplete = {
                    AgeGate.setAcknowledged(true)
                    ageAcknowledged = true
                    Analytics.track("age_gate_completed")
                    userProfileCautionAccepted = it
                },
            )
        }
        return
    }

    FitCalTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgColor)
        ) {
            val navController = rememberNavController()
            val adManager = remember { getPlatformAdManager() }

            // Ad visibility = premium status AND an accepted privacy decision.
            // Personalized ad *requests* are additionally gated on the CMP/ATT
            // signal in MainActivity — showing a non-personalized ad is always
            // allowed once the privacy notice has been accepted.
            val showAdsForUser = !com.fitcal.shared.subscription.SubscriptionManager.isPremiumUser() &&
                PrivacyConsent.canRequestAds()

            // Phase 10 — auth readiness gate:
            // null = boot in-flight | true = auth confirmed | false = offline (all retries failed)
            var authReady by remember { mutableStateOf<Boolean?>(null) }

            // Account email, shown only for permanent accounts (null while using FitCal as a guest)
            fun accountEmailOrNull(): String? =
                if (authService.isPermanentUser()) authService.getCurrentUserEmail() ?: "your account" else null
            var userEmail by remember { mutableStateOf(accountEmailOrNull()) }
            var signingOut by remember { mutableStateOf(false) }
            var signOutMessage by remember { mutableStateOf<String?>(null) }
            var deletingAccount by remember { mutableStateOf(false) }
            var deleteAccountMessage by remember { mutableStateOf<String?>(null) }
            var totalMealsLogged by remember { mutableStateOf(0) }
            LaunchedEffect(Unit) {
                val stored = localMealRepository.getAllMeals()
                totalMealsLogged = stored.size
                // Photos of meals deleted since the last launch (after their undo window).
                pruneMealPhotos(stored.mapTo(mutableSetOf()) { it.id })
            }
            var showSignInCard by remember { mutableStateOf(false) }
            var showQuizOffer by remember { mutableStateOf(false) }
            // True once startup has loaded the account's synced profile (or found it offline),
            // so first-open offers never judge a returning user by the placeholder profile.
            var bootSyncDone by remember { mutableStateOf(false) }
            var weeklySummaryCard by remember {
                mutableStateOf<Pair<com.fitcal.app.ui.screens.progress.SummaryWeek, com.fitcal.app.ui.screens.progress.WeekSummary>?>(null)
            }

            var scansRemainingToday by remember(selectedDateKey) {
                mutableStateOf(ScanQuotaManager.getRemainingScans(selectedDateKey))
            }

            // What the other meals of [date] add up to, for the review screen's over-target note.
            fun eatenOn(date: String, excludeMealId: String? = null): com.fitcal.app.nutrition.DayTotals =
                allMeals.filter { it.date == date && it.id != excludeMealId }.dayTotals()

            // Brings back the account's last year of meals and water, so a reinstall or a new
            // phone shows the history and not only the day on screen.
            suspend fun pullAccountHistory() {
                val from = com.fitcal.app.plan.addDays(getCurrentDateString(), -HISTORY_DAYS) ?: selectedDateKey
                (mealRepository as? com.fitcal.shared.data.SupabaseMealRepository)?.pullHistory(from)
                (userRepository as? com.fitcal.shared.data.SupabaseUserRepository)?.pullWaterHistory(from)
                (userRepository as? com.fitcal.shared.data.SupabaseUserRepository)?.pullWeightHistory(from)
            }

            // Saves a profile and, when its weight changed, records the day's weigh-in.
            suspend fun saveProfileRecordingWeight(updated: UserProfile) {
                val entry = weightEntryForChange(userProfile, updated, getCurrentDateString())
                userRepository.saveUserProfile(updated)
                userProfile = updated
                if (entry != null) userRepository.recordWeight(entry)
            }

            // Runs after any sign-in. When the user ID changed (a guest signed into an existing
            // account), the guest's meals are moved into that account before pulling its data.
            suspend fun switchAccount(signIn: suspend () -> Unit) {
                val previousUserId = authService.getUserId()
                val wasGuest = !authService.isPermanentUser()
                signIn()
                val switchedUser = authService.getUserId() != previousUserId
                if (wasGuest && switchedUser) {
                    accountData.mergeGuestMealsIntoCurrentAccount()
                }
                when {
                    // Same user, now permanent: the guest was upgraded in place.
                    wasGuest && !switchedUser && authService.isPermanentUser() ->
                        Analytics.track("account_created", "meals_logged" to totalMealsLogged)
                    switchedUser -> Analytics.track("signed_in", "was_guest" to wasGuest)
                }
                userEmail = accountEmailOrNull()
                signOutMessage = null
                setCrashReportingUser(authService.getUserId())
                try {
                    pullAccountHistory()
                    (userRepository as? com.fitcal.shared.data.SupabaseUserRepository)?.pullRemote(selectedDateKey)
                    userProfile = userRepository.getUserProfile()
                    syncEngine.flushOutbox()
                    ScanQuotaManager.syncQuotaFromServer(selectedDateKey)
                } catch (_: Throwable) {
                    // Sync retries on the next launch; the sign-in itself succeeded.
                }
                scansRemainingToday = ScanQuotaManager.getRemainingScans(selectedDateKey)
            }

            // ── Primary boot: retry ensureSignedIn up to 3 times before declaring OfflineMode ─
            LaunchedEffect(Unit) {
                // ── Step 1: Ensure a valid Supabase anonymous JWT exists BEFORE any server call ──
                // Phase 10: bounded backoff retry — 3 attempts (1s / 2s / 4s delay between tries).
                // If all exhausted → authReady = false (OfflineMode); scan button is disabled.
                val backoffs = listOf(1_000L, 2_000L, 4_000L)
                var userId: String? = null
                try {
                    userId = authService.ensureSignedIn(
                        maxAttempts = 3,
                        backoffsMs = backoffs,
                        onAttemptFailed = { attempt, e ->
                            DiagnosticsCrashHook.log(
                                level = DiagnosticLevel.WARN,
                                tag = "Auth",
                                message = "ensureSignedIn attempt $attempt/${backoffs.size} failed: ${e.message}",
                                throwable = e
                            )
                            TelemetryUploader.trackDiagnostic(
                                level = "WARN",
                                tag = "auth",
                                message = "boot_retry_$attempt: ${e.message ?: "unknown"}",
                                details = null
                            )
                        }
                    )
                    userEmail = accountEmailOrNull()
                    setCrashReportingUser(userId)
                    ScanQuotaManager.remoteQuotaManager = quotaManager
                    ScanQuotaManager.syncQuotaFromServer(selectedDateKey)
                    scansRemainingToday = ScanQuotaManager.getRemainingScans(selectedDateKey)
                    authReady = true
                } catch (e: Exception) {
                    authReady = false
                    DiagnosticsCrashHook.log(
                        level = DiagnosticLevel.ERROR,
                        tag = "Auth",
                        message = "Boot auth failed after ${backoffs.size} attempts — OfflineMode active",
                        throwable = e
                    )
                    TelemetryUploader.trackDiagnostic(
                        level = "ERROR",
                        tag = "auth",
                        message = "boot_offline: 3 attempts exhausted",
                        details = null
                    )
                }


                // Record active retention session for cohort tracking
                CohortRetentionTracker.recordActiveDailySession(selectedDateKey)
                Analytics.track(
                    "app_open",
                    "online" to (userId != null),
                    "is_guest" to (userEmail == null),
                    "premium" to com.fitcal.shared.subscription.SubscriptionManager.isPremiumUser(),
                    "days_since_install" to CohortRetentionTracker.getDaysSinceInstall(),
                    "free_scan_limit" to ScanQuotaManager.getDailyFreeLimit(),
                    "scans_remaining" to scansRemainingToday,
                    "meals_logged_total" to totalMealsLogged,
                )

                // ── Step 2: Boot-side effects (only after JWT confirmed) ─────────────────
                if (userId != null) {
                    // Background sync
                    pullAccountHistory()
                    (userRepository as? com.fitcal.shared.data.SupabaseUserRepository)?.pullRemote(selectedDateKey)
                    // Show the synced profile (targets, plan) right away, not after a restart.
                    userProfile = userRepository.getUserProfile()
                    syncEngine.flushOutbox()
                    Analytics.flush()
                }
                bootSyncDone = true

                // Ad preload + Meal Scan Reminder Notifications
                adManager.preloadAds()
                com.fitcal.app.notifications.MealReminderManager.syncNotifications()
                syncWeeklySummaryNotification(WeeklySummaryPrefs.notificationsEnabled())
            }

            // ── Recovery observer: when auth goes offline, periodically retry sign-in ─────────
            // Re-runs whenever authReady transitions to false. Uses exponential backoff so we
            // don't hammer the network while offline. Once auth is restored, gates re-open.
            LaunchedEffect(authReady) {
                if (authReady == false) {
                    val recoveryBackoffs = listOf(5_000L, 15_000L, 30_000L, 60_000L)
                    for (recoveryDelay in recoveryBackoffs) {
                        delay(recoveryDelay)
                        try {
                            authService.ensureSignedIn()
                            // Trigger server sync now that we're back online before unlocking scan gate
                            ScanQuotaManager.remoteQuotaManager = quotaManager
                            ScanQuotaManager.syncQuotaFromServer(selectedDateKey)
                            scansRemainingToday = ScanQuotaManager.getRemainingScans(selectedDateKey)
                            authReady = true
                            DiagnosticsCrashHook.log(
                                level = DiagnosticLevel.INFO,
                                tag = "Auth",
                                message = "Auth recovered after offline period"
                            )
                            TelemetryUploader.trackDiagnostic(
                                level = "INFO",
                                tag = "auth",
                                message = "boot_recovered",
                                details = null
                            )
                            break
                        } catch (_: Exception) {
                            // Still offline — continue waiting
                        }
                    }
                }
            }

            // ── Analytics: sessions, periodic upload, screen views ─────────────────
            LifecycleEventEffect(Lifecycle.Event.ON_START) { Analytics.onForeground() }
            LifecycleEventEffect(Lifecycle.Event.ON_STOP) { Analytics.onBackground() }
            LaunchedEffect(authReady) {
                while (authReady == true) {
                    delay(60_000)
                    Analytics.flush()
                }
            }
            LaunchedEffect(navController) {
                navController.currentBackStackEntryFlow.collect { entry ->
                    // Type-safe routes look like "com.fitcal.app.ui.navigation.CameraDestination/{arg}".
                    val screen = entry.destination.route.orEmpty()
                        .substringBefore('/').substringBefore('?')
                        .substringAfterLast('.').removeSuffix("Destination").lowercase()
                    if (screen.isNotEmpty()) Analytics.screen(screen)
                }
            }

            // ── Scan gate shared by every AI scan (photo and typed description) ──────
            // Both screens take their interstitial decision and quota bookkeeping from here,
            // so a typed meal gets exactly the photo scan's quota and ad behaviour (AGENTS.md 5.2).
            fun playScanAdNow(): Boolean =
                ScanQuotaManager.shouldPlayScanAd(selectedDateKey, adsAllowed = showAdsForUser)

            fun recordScanConsumed() {
                // Record local consumption immediately for responsive UI; the Gateway already
                // consumed 1 server-side quota after VLM inference succeeded, so sync from
                // server instead of calling consumeScanServer (which would double-increment).
                ScanQuotaManager.consumeScan(selectedDateKey)
                scansRemainingToday = ScanQuotaManager.getRemainingScans(selectedDateKey)
                coroutineScope.launch {
                    ScanQuotaManager.syncQuotaFromServer(selectedDateKey)
                    scansRemainingToday = ScanQuotaManager.getRemainingScans(selectedDateKey)
                }
            }

            // Client-side mock mode is retired. The gateway controls mock responses via FITCAL_ENV=dev.
            // Keep isMockMode = false so CameraScreen/ResultScreen compile without changes.
            val isMockMode = false

            // Widget's Scan button: open the camera once the app can navigate (after consent).
            val pendingLaunch by PendingLaunchAction.action.collectAsState()
            LaunchedEffect(pendingLaunch) {
                if (pendingLaunch == LaunchAction.SCAN && PendingLaunchAction.take() == LaunchAction.SCAN) {
                    Analytics.track("widget_scan_opened")
                    navController.navigate(CameraDestination) { launchSingleTop = true }
                }
            }

            // Keep the home-screen widget in step with today's meals and targets.
            val todayKey = getCurrentDateString()
            LaunchedEffect(allMeals, userProfile, todayKey) {
                val today = allMeals.filter { it.date == todayKey }.dayTotals()
                updateHomeWidget(
                    WidgetSummary(
                        date = todayKey,
                        calories = today.calories,
                        calorieGoal = if (userProfile.hasDailyTargets()) userProfile.calGoal else 0,
                        proteinG = today.protein.roundToInt(),
                        carbsG = today.carbs.roundToInt(),
                        fatG = today.fat.roundToInt(),
                    )
                )
            }

            NavHost(
                navController = navController,
                startDestination = DashboardDestination,
                modifier = Modifier.fillMaxSize()
            ) {
                composable<DashboardDestination> {
                    // One-time "Create a free account" card for guests (AGENTS.md 5.2a). Only on the
                    // dashboard, never over the camera, and only once the boot sign-in has told us
                    // whether this is a guest.
                    // One-time goal quiz offer (owner decision 2026-10-22). Comes first; the
                    // sign-in card waits until it has been answered, so they never stack.
                    LaunchedEffect(userProfile, bootSyncDone) {
                        if (bootSyncDone && !showQuizOffer && GoalQuizPolicy.shouldOffer(userProfile)) {
                            GoalQuizPolicy.recordOffered()
                            showQuizOffer = true
                            Analytics.track("goal_quiz_offered")
                        }
                    }
                    if (showQuizOffer) {
                        GoalQuizOffer(
                            onStart = {
                                showQuizOffer = false
                                Analytics.track("goal_quiz_started")
                                navController.navigate(GoalQuizDestination)
                            },
                            onSkip = {
                                showQuizOffer = false
                                Analytics.track("goal_quiz_skipped", "step" to "offer")
                            },
                        )
                    }
                    LaunchedEffect(authReady, userEmail, showQuizOffer, bootSyncDone) {
                        // Wait for the quiz offer to be answered (or not be due at all).
                        val quizPending = !bootSyncDone || GoalQuizPolicy.shouldOffer(userProfile)
                        if (!showSignInCard && !showQuizOffer && !quizPending &&
                            SignInCardPolicy.shouldShow(isPermanentUser = userEmail != null, signInSettled = authReady == true)
                        ) {
                            SignInCardPolicy.recordShown()
                            showSignInCard = true
                            Analytics.track("signin_card_shown")
                        }
                    }
                    if (showSignInCard) {
                        SignInCard(
                            onCreateAccount = {
                                showSignInCard = false
                                Analytics.track("signin_card_accept")
                                navController.navigate(AuthDestination)
                            },
                            onNotNow = {
                                showSignInCard = false
                                Analytics.track("signin_card_dismiss")
                            },
                        )
                    }
                    // Adjusting target: recomputed as weigh-ins, meals or the plan change.
                    val calorieSuggestion = remember(userProfile, weightEntries, allMeals) {
                        userProfile.calorieSuggestion(weightEntries, allMeals, getCurrentDateString())
                    }
                    LaunchedEffect(calorieSuggestion) {
                        calorieSuggestion?.let { Analytics.track("plan_adjustment_shown", "delta_kcal" to it.deltaKcal) }
                    }
                    // Last week's summary, once per week, after the meal history has loaded.
                    LaunchedEffect(allMeals, bootSyncDone, userProfile) {
                        if (!bootSyncDone || weeklySummaryCard != null) return@LaunchedEffect
                        val week = lastCompletedWeek(getCurrentDateString()) ?: return@LaunchedEffect
                        if (!WeeklySummaryPrefs.isDue(week)) return@LaunchedEffect
                        val summary = weeklySummaryFor(allMeals, week, if (userProfile.hasDailyTargets()) userProfile.calGoal else null)
                            ?: return@LaunchedEffect
                        weeklySummaryCard = week to summary
                        Analytics.track("weekly_summary_shown", "logged_days" to summary.loggedDays)
                    }
                    DashboardScreen(
                        meals = loggedMeals,
                        profile = userProfile,
                        selectedDate = selectedDateKey,
                        waterLogged = waterLoggedToday,
                        scansRemaining = scansRemainingToday,
                        onDateSelected = { newDate ->
                            selectedDateKey = newDate
                            Analytics.track("date_selected", "is_today" to (newDate == getCurrentDateString()))
                        },
                        onWaterChanged = { newWater ->
                            Analytics.track("water_changed", "increased" to (newWater > waterLoggedToday))
                            coroutineScope.launch {
                                userRepository.setWaterIntake(selectedDateKey, newWater)
                            }
                        },
                        onScanClicked = {
                            navController.navigate(CameraDestination)
                        },
                        onDescribeMealClicked = {
                            Analytics.track("describe_opened", "source" to "dashboard")
                            navController.navigate(DescribeMealDestination)
                        },
                        onSettingsClicked = {
                            navController.navigate(SettingsDestination)
                        },
                        onOpenFoodLibrary = {
                            navController.navigate(FoodLibraryDestination)
                        },
                        onMonetizationClicked = {
                            navController.navigate(MonetizationDestination)
                        },
                        onDeleteMeal = { mealToDelete ->
                            Analytics.track("meal_deleted")
                            coroutineScope.launch {
                                mealRepository.deleteMeal(mealToDelete.id)
                            }
                        },
                        onMealClicked = { meal ->
                            Analytics.track("meal_opened", "has_items" to meal.items.isNotEmpty())
                            navController.navigate(EditMealDestination(meal.id))
                        },
                        onRestoreMeal = { mealToRestore ->
                            Analytics.track("meal_delete_undone")
                            coroutineScope.launch {
                                mealRepository.saveMeal(mealToRestore)
                            }
                        },
                        allMeals = allMeals,
                        // Log again: a copy on today's date, no scan used.
                        onLogAgain = { meal ->
                            Analytics.track("meal_logged_again", "favorite" to meal.favorite, "has_items" to meal.items.isNotEmpty())
                            val copy = meal.copyAsNewLog(newMealId(), getCurrentDateString(), getCurrentTimeString())
                            totalMealsLogged += 1
                            coroutineScope.launch { mealRepository.saveMeal(copy) }
                        },
                        onToggleFavorite = { meal ->
                            Analytics.track("meal_favorite_toggled", "favorite" to !meal.favorite)
                            coroutineScope.launch { mealRepository.updateMeal(meal.copy(favorite = !meal.favorite)) }
                        },
                        today = getCurrentDateString(),
                        showPlanPrompt = !planPromptDismissed,
                        onSetUpPlan = {
                            Analytics.track("plan_setup_opened", "source" to "dashboard")
                            navController.navigate(PlanDestination)
                        },
                        onDismissPlanPrompt = {
                            Analytics.track("plan_prompt_dismissed")
                            planPromptDismissed = true
                            savePreference(KEY_PLAN_PROMPT_DISMISSED, "true")
                        },
                        checkInSnoozedUntil = checkInSnoozedUntil,
                        onCheckIn = { newWeight ->
                            val today = getCurrentDateString()
                            val (updated, weightEntry) = userProfile.checkInWithEntry(newWeight, today)
                            Analytics.track(
                                "plan_checked_in",
                                "weight_change_kg" to (newWeight - userProfile.weight),
                                "calories_change" to (updated.calGoal - userProfile.calGoal),
                                "target_reached" to hasReachedTarget(
                                    PlanGoal.fromKey(updated.goalType), newWeight, updated.plan?.targetWeightKg
                                ),
                            )
                            coroutineScope.launch {
                                userRepository.saveUserProfile(updated)
                                userProfile = updated
                                if (weightEntry != null) userRepository.recordWeight(weightEntry)
                            }
                        },
                        onSnoozeCheckIn = {
                            Analytics.track("plan_check_in_snoozed")
                            val until = addDays(getCurrentDateString(), 3)
                            checkInSnoozedUntil = until
                            savePreference(KEY_PLAN_CHECKIN_SNOOZED_UNTIL, until.orEmpty())
                        },
                        onSwitchToMaintain = {
                            val body = BodyStats.from(userProfile)
                            if (body != null) {
                                val updated = userProfile.startPlan(
                                    body, PlanGoal.MAINTAIN, PlanPace.STEADY, null, getCurrentDateString()
                                )
                                Analytics.track("plan_target_reached_maintain")
                                coroutineScope.launch {
                                    userRepository.saveUserProfile(updated)
                                    userProfile = updated
                                }
                            }
                        },
                        onEndPlan = {
                            val updated = userProfile.endPlan()
                            Analytics.track("plan_ended", "source" to "dashboard", "restored_own_numbers" to (updated.targetsMode == "custom"))
                            coroutineScope.launch {
                                userRepository.saveUserProfile(updated)
                                userProfile = updated
                            }
                        },
                        onOpenGuide = { id -> navController.navigate(GuideDestination(id, "dashboard")) },
                        ringScaleKcal = ringScaleKcal,
                        onOpenProgress = {
                            Analytics.track("progress_opened", "weight_entries" to weightEntries.size)
                            navController.navigate(ProgressDestination)
                        },
                        calorieSuggestion = calorieSuggestion,
                        onAcceptSuggestion = {
                            val s = calorieSuggestion ?: return@DashboardScreen
                            val updated = userProfile.acceptCalorieSuggestion(s, getCurrentDateString())
                            Analytics.track(
                                "plan_adjustment_accepted",
                                "delta_kcal" to s.deltaKcal,
                                "actual_kg_week" to s.actualKgPerWeek,
                                "planned_kg_week" to s.plannedKgPerWeek,
                            )
                            coroutineScope.launch {
                                userRepository.saveUserProfile(updated)
                                userProfile = updated
                            }
                        },
                        onDismissSuggestion = {
                            Analytics.track("plan_adjustment_dismissed", "delta_kcal" to (calorieSuggestion?.deltaKcal ?: 0))
                            val updated = userProfile.dismissCalorieSuggestion(getCurrentDateString())
                            coroutineScope.launch {
                                userRepository.saveUserProfile(updated)
                                userProfile = updated
                            }
                        },
                        weeklySummary = weeklySummaryCard,
                        onDismissWeeklySummary = {
                            weeklySummaryCard?.first?.let { WeeklySummaryPrefs.markSeen(it) }
                            Analytics.track("weekly_summary_dismissed")
                            weeklySummaryCard = null
                        },
                    )
                }

                composable<ProgressDestination> {
                    ProgressScreen(
                        profile = userProfile,
                        weightEntries = weightEntries,
                        meals = allMeals,
                        today = getCurrentDateString(),
                        onBack = { navController.popBackStack() },
                    )
                }

                composable<CameraDestination> {
                    // Re-read the ad decision whenever an interstitial shows (guest 90 s cooldown, AGENTS.md 5.2a).
                    val lastInterstitialShownAt by ScanQuotaManager.interstitialShownAt.collectAsState()
                    val playScanAd = remember(lastInterstitialShownAt, selectedDateKey, scansRemainingToday, showAdsForUser, userEmail) {
                        playScanAdNow()
                    }
                    CameraScreen(
                        apiClient = apiClient,
                        isMockMode = isMockMode,
                        plateSizeInches = userProfile.defaultPlateSize,
                        adManager = adManager,
                        playAdDuringScan = playScanAd,
                        onScanConsumed = { recordScanConsumed() },
                        onPhotoCaptured = { bytes ->
                            lastCapturedImageBytes = bytes
                        },
                        onResultObtained = { responseJson ->
                            navController.navigate(ResultDestination(responseJson)) {
                                popUpTo(DashboardDestination) { saveState = false }
                            }
                        },
                        onNavigateBack = {
                            navController.popBackStack()
                        },
                        scanReadiness = ensureReadyForScan(authReady, scansRemainingToday),
                        scansRemaining = scansRemainingToday,
                        onBonusScansEarned = {
                            ScanQuotaManager.addBonusScansSuspend(selectedDateKey, 2)
                            scansRemainingToday = ScanQuotaManager.getRemainingScans(selectedDateKey)
                        },
                        onCreateAccount = if (userEmail == null) {
                            { navController.navigate(AuthDestination) }
                        } else {
                            null
                        },
                        barcodeLookup = { code -> openFoodFactsClient.lookup(code) },
                        onOpenBarcodeReview = { responseJson ->
                            // No photo for a barcode product; never consumes a scan.
                            lastCapturedImageBytes = null
                            navController.navigate(BarcodeResultDestination(responseJson)) {
                                popUpTo(DashboardDestination) { saveState = false }
                            }
                        },
                        onDescribe = {
                            Analytics.track("describe_opened", "source" to "camera")
                            // Swaps the camera for the describe screen, so Back returns to the dashboard.
                            navController.navigate(DescribeMealDestination) {
                                popUpTo(CameraDestination) { inclusive = true }
                            }
                        },
                    )
                }

                composable<BarcodeResultDestination> { backStackEntry ->
                    val destination = backStackEntry.toRoute<BarcodeResultDestination>()
                    val nutritionData = remember(destination.responseJson) {
                        Json.decodeFromString<NutritionResponse>(destination.responseJson)
                    }
                    ResultScreen(
                        apiClient = apiClient,
                        data = nutritionData,
                        profile = userProfile,
                        isMock = isMockMode,
                        capturedImageBytes = null,
                        eatenBeforeThisMeal = eatenOn(selectedDateKey),
                        ingredientsNote = if (nutritionData.items.isEmpty()) {
                            "Add what you ate from the food library."
                        } else {
                            "From the package label via Open Food Facts. Change the weight to match what you ate."
                        },
                        onMealLogged = { mealName, cal, p, c, f, items ->
                            val newMeal = LoggedMeal(
                                id = newMealId(),
                                name = mealName,
                                calories = cal,
                                protein = p,
                                carbs = c,
                                fat = f,
                                timestamp = getCurrentTimeString(),
                                date = selectedDateKey,
                                items = items
                            )
                            totalMealsLogged += 1
                            if (totalMealsLogged == 1) Analytics.track("first_meal_logged")
                            Analytics.track("barcode_meal_logged", "items" to items.size)
                            coroutineScope.launch {
                                mealRepository.saveMeal(newMeal)
                            }
                        },
                        onLogAgain = {
                            navController.navigate(CameraDestination) {
                                popUpTo(DashboardDestination)
                            }
                        },
                        shouldSuggestSignIn = {
                            SignInPromptPolicy.shouldPromptAfterMealLogged(
                                isPermanentUser = userEmail != null,
                                totalMealsLogged = totalMealsLogged,
                            )
                        },
                        // Label data, not an AI estimate: keep the rating local, don't send scan-accuracy feedback.
                        onRateEstimate = { positive, _ ->
                            Analytics.track("barcode_rated", "positive" to positive)
                        },
                        onSignInSuggestionShown = {
                            SignInPromptPolicy.recordShown()
                            Analytics.track("sign_in_prompt_shown", "meals_logged" to totalMealsLogged)
                        },
                        onSignInSuggestionDismissed = {
                            SignInPromptPolicy.recordDismissed()
                            Analytics.track("sign_in_prompt_dismissed")
                        },
                        onCreateAccount = {
                            Analytics.track("sign_in_prompt_accepted")
                            navController.navigate(AuthDestination) {
                                popUpTo(DashboardDestination)
                            }
                        },
                    )
                }

                composable<DescribeMealDestination> {
                    // Same guest cooldown-aware decision as the camera (AGENTS.md 5.2a).
                    val lastInterstitialShownAt by ScanQuotaManager.interstitialShownAt.collectAsState()
                    val playScanAd = remember(lastInterstitialShownAt, selectedDateKey, scansRemainingToday, showAdsForUser, userEmail) {
                        playScanAdNow()
                    }
                    DescribeMealScreen(
                        apiClient = apiClient,
                        adManager = adManager,
                        playAdDuringScan = playScanAd,
                        onScanConsumed = { recordScanConsumed() },
                        onResultObtained = { responseJson ->
                            // No photo for a typed meal; don't show the previous scan's.
                            lastCapturedImageBytes = null
                            navController.navigate(ResultDestination(responseJson)) {
                                popUpTo(DashboardDestination) { saveState = false }
                            }
                        },
                        onNavigateBack = { navController.popBackStack() },
                        scanReadiness = ensureReadyForScan(authReady, scansRemainingToday),
                        scansRemaining = scansRemainingToday,
                        onBonusScansEarned = {
                            ScanQuotaManager.addBonusScansSuspend(selectedDateKey, 2)
                            scansRemainingToday = ScanQuotaManager.getRemainingScans(selectedDateKey)
                        },
                        onCreateAccount = if (userEmail == null) {
                            { navController.navigate(AuthDestination) }
                        } else {
                            null
                        },
                    )
                }

                composable<ResultDestination> { backStackEntry ->
                    val destination = backStackEntry.toRoute<ResultDestination>()
                    val nutritionData = remember(destination.responseJson) {
                        Json.decodeFromString<NutritionResponse>(destination.responseJson)
                    }
                    ResultScreen(
                        apiClient = apiClient,
                        data = nutritionData,
                        profile = userProfile,
                        isMock = isMockMode,
                        capturedImageBytes = lastCapturedImageBytes,
                        eatenBeforeThisMeal = eatenOn(selectedDateKey),
                        onMealLogged = { mealName, cal, p, c, f, items ->
                            val mealId = newMealId() // F0.1: collision-proof UUID v4
                            // Kept on this phone only, at upload size (owner decision 2026-10-22).
                            val photoPath = lastCapturedImageBytes?.let { saveMealPhoto(mealId, compressImage(it)) }
                            val newMeal = LoggedMeal(
                                id = mealId,
                                name = mealName,
                                calories = cal,
                                protein = p,
                                carbs = c,
                                fat = f,
                                timestamp = getCurrentTimeString(),
                                date = selectedDateKey,
                                items = items,
                                photoPath = photoPath,
                            )
                            totalMealsLogged += 1
                            if (totalMealsLogged == 1) Analytics.track("first_meal_logged")
                            coroutineScope.launch {
                                mealRepository.saveMeal(newMeal)
                            }
                        },
                        onLogAgain = {
                            lastCapturedImageBytes = null
                            navController.navigate(CameraDestination) {
                                popUpTo(DashboardDestination)
                            }
                        },
                        shouldSuggestSignIn = {
                            SignInPromptPolicy.shouldPromptAfterMealLogged(
                                isPermanentUser = userEmail != null,
                                totalMealsLogged = totalMealsLogged,
                            )
                        },
                        onRateEstimate = { positive, reasons ->
                            Analytics.track(
                                "scan_rated",
                                "positive" to positive,
                                "reasons" to reasons,
                                "items_ai" to nutritionData.items.size,
                            )
                            coroutineScope.launch {
                                apiClient.submitFeedback(
                                    FeedbackRequest(
                                        kind = "scan_accuracy",
                                        rating = if (positive) 5 else 1,
                                        context = Analytics.toJson(
                                            mapOf("reasons" to reasons, "items_ai" to nutritionData.items.size)
                                        ),
                                        app_version = appVersionName,
                                        platform = platformName,
                                    )
                                )
                            }
                        },
                        onSignInSuggestionShown = {
                            SignInPromptPolicy.recordShown()
                            Analytics.track("sign_in_prompt_shown", "meals_logged" to totalMealsLogged)
                        },
                        onSignInSuggestionDismissed = {
                            SignInPromptPolicy.recordDismissed()
                            Analytics.track("sign_in_prompt_dismissed")
                        },
                        onCreateAccount = {
                            Analytics.track("sign_in_prompt_accepted")
                            lastCapturedImageBytes = null
                            navController.navigate(AuthDestination) {
                                popUpTo(DashboardDestination)
                            }
                        },
                    )
                }

                composable<GoalQuizDestination> {
                    GoalQuizScreen(
                        profile = userProfile,
                        today = getCurrentDateString(),
                        onFinish = { updated ->
                            Analytics.track(
                                "goal_quiz_completed",
                                "goal_type" to updated.goalType,
                                "pace" to (updated.plan?.pace ?: ""),
                                "activity_level" to updated.activityLevel,
                            )
                            coroutineScope.launch { saveProfileRecordingWeight(updated) }
                            navController.popBackStack()
                        },
                        onSkip = {
                            Analytics.track("goal_quiz_skipped", "step" to "quiz")
                            navController.popBackStack()
                        },
                        onOpenGuide = { id -> navController.navigate(GuideDestination(id, "goal_quiz")) },
                    )
                }

                composable<PlanDestination> {
                    PlanSetupScreen(
                        profile = userProfile,
                        today = getCurrentDateString(),
                        onSave = { updated ->
                            val wasPlan = userProfile.plan != null
                            Analytics.track(
                                if (wasPlan) "plan_edited" else "plan_started",
                                "goal_type" to updated.goalType,
                                "pace" to (updated.plan?.pace ?: ""),
                                "activity_level" to updated.activityLevel,
                                "has_target_weight" to (updated.plan?.targetWeightKg != null),
                            )
                            coroutineScope.launch { saveProfileRecordingWeight(updated) }
                            navController.popBackStack()
                        },
                        onBack = { navController.popBackStack() },
                        onOpenGuide = { id -> navController.navigate(GuideDestination(id, "plan_setup")) },
                    )
                }

                composable<GuideDestination> { backStackEntry ->
                    val destination = backStackEntry.toRoute<GuideDestination>()
                    val guide = remember(destination.guideId) { Guides.byId(destination.guideId) }
                    if (guide == null) {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                        return@composable
                    }
                    LaunchedEffect(guide.id) {
                        Analytics.track("guide_opened", "guide" to guide.id, "source" to destination.source)
                    }
                    GuideScreen(
                        guide = guide,
                        profile = userProfile,
                        onBack = { navController.popBackStack() },
                        onOpenGuide = { id -> navController.navigate(GuideDestination(id, "related")) },
                    )
                }

                composable<EditMealDestination> { backStackEntry ->
                    val destination = backStackEntry.toRoute<EditMealDestination>()
                    // Looked up once on open; the meal keeps its id, date and time when saved.
                    val meal = remember(destination.mealId) {
                        loggedMeals.firstOrNull { it.id == destination.mealId }
                    }
                    if (meal == null) {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                        return@composable
                    }
                    val editData = remember(meal) { meal.toEditableResponse() }
                    ResultScreen(
                        apiClient = apiClient,
                        data = editData,
                        profile = userProfile,
                        isMock = isMockMode,
                        capturedImageBytes = null,
                        savedPhoto = mealPhotoModel(meal.photoPath),
                        eatenBeforeThisMeal = eatenOn(meal.date, excludeMealId = meal.id),
                        isEditingLoggedMeal = true,
                        ingredientsNote = if (meal.items.isEmpty()) {
                            "This meal was logged before ingredients were saved. $LEGACY_PORTION_GRAMS g = the amount you logged; change it to scale the whole meal."
                        } else {
                            null
                        },
                        onMealLogged = { _, cal, p, c, f, items ->
                            val updated = meal.copy(calories = cal, protein = p, carbs = c, fat = f, items = items)
                            Analytics.track(
                                "meal_edited",
                                "calories_delta" to (cal - meal.calories),
                                "items_before" to meal.items.size,
                                "items_after" to items.size,
                            )
                            coroutineScope.launch {
                                mealRepository.updateMeal(updated)
                            }
                        },
                        onLogAgain = { navController.popBackStack() },
                    )
                }

                composable<MonetizationDestination> {
                    MonetizationScreen(
                        adManager = adManager,
                        scansRemainingToday = scansRemainingToday,
                        currentDateKey = selectedDateKey,
                        onScansUpdated = {
                            scansRemainingToday = ScanQuotaManager.getRemainingScans(selectedDateKey)
                        },
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }

                composable<SettingsDestination> {
                    SettingsScreen(
                        onExportCsv = { range ->
                            val today = getCurrentDateString()
                            val meals = com.fitcal.app.export.mealsInRange(allMeals, today, range)
                            val weights = com.fitcal.app.export.weightsInRange(weightEntries, today, range)
                            if (meals.isEmpty() && weights.isEmpty()) {
                                false
                            } else {
                                shareTextFile(
                                    fileName = com.fitcal.app.export.exportFileName(today, range),
                                    mimeType = "text/csv",
                                    content = com.fitcal.app.export.buildMealsCsv(meals, weights),
                                    chooserTitle = "Share your FitCal meals",
                                )
                            }
                        },
                        profile = userProfile,
                        userEmail = userEmail,
                        onNavigateToAuth = {
                            navController.navigate(AuthDestination)
                        },
                        signOutMessage = signOutMessage,
                        signingOut = signingOut,
                        onSignOut = {
                            coroutineScope.launch {
                                signingOut = true
                                signOutMessage = null
                                if (accountData.syncAndCountPending() > 0) {
                                    signOutMessage = "Some changes haven't synced yet. Connect to the internet and try again so nothing is lost."
                                } else {
                                    Analytics.track("signed_out")
                                    Analytics.flush()
                                    accountData.clearLocalData()
                                    pruneMealPhotos(emptySet())
                                    authService.signOut()
                                    userEmail = accountEmailOrNull()
                                    userProfile = userRepository.getUserProfile()
                                    totalMealsLogged = 0
                                    ScanQuotaManager.syncQuotaFromServer(selectedDateKey)
                                    scansRemainingToday = ScanQuotaManager.getRemainingScans(selectedDateKey)
                                    authReady = true
                                }
                                signingOut = false
                            }
                        },
                        onSave = { updated ->
                            Analytics.track(
                                "profile_saved",
                                "goals_changed" to (updated.calGoal != userProfile.calGoal ||
                                    updated.proteinGoal != userProfile.proteinGoal ||
                                    updated.carbsGoal != userProfile.carbsGoal ||
                                    updated.fatGoal != userProfile.fatGoal),
                                "targets_mode" to updated.resolvedTargetsMode().key,
                                "plate_size_changed" to (updated.defaultPlateSize != userProfile.defaultPlateSize),
                            )
                            coroutineScope.launch { saveProfileRecordingWeight(updated) }
                            navController.popBackStack()
                        },
                        onBack = {
                            navController.popBackStack()
                        },
                        onOpenPlan = {
                            Analytics.track("plan_setup_opened", "source" to "settings")
                            navController.navigate(PlanDestination)
                        },
                        onEndPlan = {
                            val updated = userProfile.endPlan()
                            Analytics.track("plan_ended", "source" to "settings", "restored_own_numbers" to (updated.targetsMode == "custom"))
                            coroutineScope.launch {
                                userRepository.saveUserProfile(updated)
                                userProfile = updated
                            }
                        },
                        deletingAccount = deletingAccount,
                        deleteAccountMessage = deleteAccountMessage,
                        onDeleteAllData = {
                            // Play requires a real account-deletion path, and GDPR
                            // Art. 17 requires the request actually be honoured.
                            coroutineScope.launch {
                                deletingAccount = true
                                deleteAccountMessage = null
                                val confirmed = apiClient.deleteAccount()
                                deletingAccount = false
                                // On success the server erases this user's events too, so only
                                // failures are recorded here (the gateway counts deletions).
                                if (!confirmed) {
                                    Analytics.track("account_delete_failed")
                                    deleteAccountMessage = "We couldn't delete your account. Check your connection and try again. Nothing was removed from this device."
                                }
                                if (confirmed) {
                                    accountData.clearLocalData()
                                    pruneMealPhotos(emptySet())
                                    PrivacyConsent.clearAll()
                                    Analytics.onPrivacyStateChanged()
                                    AgeGate.reset()
                                    privacyAccepted = false
                                    ageAcknowledged = false
                                    authService.signOut()
                                    userEmail = null
                                }
                            }
                        },
                        onOpenPrivacyPolicy = {
                            navController.navigate(PrivacyPolicyDestination)
                        },
                        onOpenTerms = {
                            navController.navigate(TermsDestination)
                        },
                        onSendFeedback = { kind, message, email ->
                            val result = apiClient.submitFeedback(
                                FeedbackRequest(
                                    kind = kind,
                                    message = message,
                                    contact_email = email,
                                    context = Analytics.toJson(
                                        mapOf("is_guest" to (userEmail == null), "meals_logged" to totalMealsLogged)
                                    ),
                                    app_version = appVersionName,
                                    platform = platformName,
                                )
                            )
                            Analytics.track("feedback_sent", "kind" to kind, "result" to result, "with_email" to (email != null))
                            result
                        },
                    )
                }

                composable<PrivacyPolicyDestination> {
                    PrivacyPolicyScreen(
                        onClose = { navController.popBackStack() },
                        showTermsFirst = false,
                    )
                }

                composable<TermsDestination> {
                    PrivacyPolicyScreen(
                        onClose = { navController.popBackStack() },
                        showTermsFirst = true,
                    )
                }

                composable<FoodLibraryDestination> {
                    FoodLibraryScreen(
                        onBack = { navController.popBackStack() },
                        onOpenGuide = { id -> navController.navigate(GuideDestination(id, "library")) },
                    )
                }

                composable<AuthDestination> {
                    var socialInProgress by remember { mutableStateOf(false) }
                    var socialError by remember { mutableStateOf<String?>(null) }

                    fun finish() {
                        authReady = true
                        navController.popBackStack()
                    }

                    val onIdToken = remember {
                        object : IdTokenCallback {
                            override suspend fun invoke(composeAuth: ComposeAuth, result: IdTokenCallback.Result) {
                                switchAccount {
                                    authService.signInWithIdToken(result.provider, result.idToken, result.nonce)
                                }
                            }
                        }
                    }
                    // Auth funnel: which methods people try, and why they fail.
                    var socialMethod by remember { mutableStateOf("") }
                    suspend fun <T> trackedEmailAuth(flow: String, block: suspend () -> T): T =
                        try {
                            block()
                        } catch (e: Exception) {
                            Analytics.track(
                                "auth_failed",
                                "method" to "email",
                                "flow" to flow,
                                "error" to (e::class.simpleName ?: "Exception"),
                            )
                            throw e
                        }

                    val onNativeResult: (NativeSignInResult) -> Unit = { result ->
                        socialInProgress = false
                        when (result) {
                            is NativeSignInResult.Success -> finish()
                            is NativeSignInResult.ClosedByUser ->
                                Analytics.track("auth_cancelled", "method" to socialMethod)
                            is NativeSignInResult.NetworkError -> {
                                Analytics.track("auth_failed", "method" to socialMethod, "error" to "network")
                                socialError = AuthMessages.NETWORK
                            }
                            is NativeSignInResult.Error -> {
                                Analytics.track(
                                    "auth_failed",
                                    "method" to socialMethod,
                                    "error" to (result.exception?.let { it::class.simpleName } ?: "unknown"),
                                )
                                socialError = result.exception?.let { AuthMessages.forError(it) } ?: AuthMessages.GENERIC
                            }
                        }
                    }
                    val googleSignIn = supabaseClient.composeAuth.rememberSignInWithGoogle(
                        onResult = onNativeResult,
                        onIdToken = onIdToken,
                        fallback = {
                            socialInProgress = false
                            socialError = "Google sign-in isn't available on this device. Use email instead."
                        },
                    )
                    val appleSignIn = supabaseClient.composeAuth.rememberSignInWithApple(
                        onResult = onNativeResult,
                        onIdToken = onIdToken,
                        fallback = {
                            socialInProgress = false
                            socialError = AuthMessages.METHOD_UNAVAILABLE
                        },
                    )

                    AuthScreen(
                        actions = AuthActions(
                            signIn = { email, password ->
                                trackedEmailAuth("sign_in") {
                                    switchAccount { authService.signInWithEmail(email, password) }
                                }
                                finish()
                            },
                            startSignUp = { email, password ->
                                val start = trackedEmailAuth("sign_up_start") {
                                    authService.startEmailSignUp(email, password)
                                }
                                Analytics.track(
                                    "sign_up_started",
                                    "outcome" to (start::class.simpleName ?: "unknown"),
                                )
                                if (start == EmailSignUpStart.Completed) {
                                    switchAccount {}
                                    finish()
                                }
                                start
                            },
                            completeSignUp = { email, code, password ->
                                trackedEmailAuth("sign_up_verify") {
                                    switchAccount { authService.completeEmailSignUp(email, code, password) }
                                }
                                finish()
                            },
                            requestPasswordReset = { email ->
                                Analytics.track("password_reset_requested")
                                authService.requestPasswordReset(email)
                            },
                            completePasswordReset = { email, code, newPassword ->
                                trackedEmailAuth("password_reset") {
                                    switchAccount { authService.completePasswordReset(email, code, newPassword) }
                                }
                                finish()
                            },
                        ),
                        social = SocialSignIn(
                            showGoogle = isGoogleSignInAvailable,
                            showApple = isAppleSignInAvailable,
                            onGoogle = {
                                socialMethod = "google"
                                Analytics.track("auth_started", "method" to "google")
                                socialError = null
                                socialInProgress = true
                                googleSignIn.startFlow()
                            },
                            onApple = {
                                socialMethod = "apple"
                                Analytics.track("auth_started", "method" to "apple")
                                socialError = null
                                socialInProgress = true
                                appleSignIn.startFlow()
                            },
                            inProgress = socialInProgress,
                            error = socialError,
                        ),
                        onClose = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
