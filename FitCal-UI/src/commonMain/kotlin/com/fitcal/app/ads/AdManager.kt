package com.fitcal.app.ads

import com.fitcal.app.getCurrentEpochMillis
import com.fitcal.app.loadPreference
import com.fitcal.app.savePreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Google AdMob PRODUCTION ad unit IDs for FitCal.
 * Registered in the AdMob console under App "FitCal"
 * (App ID ca-app-pub-6111686385218385~4832925123).
 */
object AdConfig {
    // Android Official Google Test Ad Units (enforced in debug builds to avoid NO_FILL & Invalid Traffic)
    const val ANDROID_TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"
    const val ANDROID_TEST_BANNER = "ca-app-pub-3940256099942544/6300978111"
    const val ANDROID_TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    const val ANDROID_TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917"

    // Android Production Ad Units (FitCal — App ID ca-app-pub-6111686385218385~4832925123)
    const val ANDROID_APP_ID = "ca-app-pub-6111686385218385~4832925123"
    const val ANDROID_PROD_BANNER = "ca-app-pub-6111686385218385/2729505961"
    const val ANDROID_PROD_INTERSTITIAL = "ca-app-pub-6111686385218385/4814322830"
    const val ANDROID_PROD_REWARDED = "ca-app-pub-6111686385218385/6678068844"

    val ANDROID_BANNER: String
        get() = if (com.fitcal.app.isDebugBuild) ANDROID_TEST_BANNER else ANDROID_PROD_BANNER

    val ANDROID_INTERSTITIAL: String
        get() = if (com.fitcal.app.isDebugBuild) ANDROID_TEST_INTERSTITIAL else ANDROID_PROD_INTERSTITIAL

    val ANDROID_REWARDED: String
        get() = if (com.fitcal.app.isDebugBuild) ANDROID_TEST_REWARDED else ANDROID_PROD_REWARDED

    // iOS Test Ad Units (still using Google's official test IDs — replace when iOS ships)
    const val IOS_TEST_APP_ID = "ca-app-pub-3940256099942544~1458002511"
    const val IOS_TEST_BANNER = "ca-app-pub-3940256099942544/2934735716"
    const val IOS_TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/4411468910"
    const val IOS_TEST_REWARDED = "ca-app-pub-3940256099942544/1712485313"

    // Brand Safety Categories (Task 6: Strict Category Filtering)
    val BLOCKED_AD_CATEGORIES = listOf(
        "Gambling",
        "Dating",
        "Politics",
        "Religion",
        "Cosmetic Surgery",
        "Sexual Health",
        "Unverified Apps",
        "Low-quality Clickbait",
        // Diet pills and rapid-weight-loss offers contradict FitCal's guides and safety limits.
        // Mirror of the AdMob "Weight loss" sensitive-category block (set in the AdMob dashboard).
        "Weight Loss"
    )
    val ALLOWED_AD_CATEGORIES = listOf(
        "Fitness",
        "Food & Beverage",
        "Sports",
        "Technology",
        "Productivity",
        "Mobile Games"
    )

    // Monetization Quota Defaults
    const val WEEK_ONE_DAILY_FREE_SCANS = 5
    const val DEFAULT_DAILY_FREE_SCANS = 3
    const val REWARDED_SCAN_BONUS = 2
    const val ACCOUNT_DAILY_BONUS_SCANS = 1
    const val SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1000L

    // Guest ad cadence (AGENTS.md 5.2a): minimum gap between two interstitials for guests.
    const val GUEST_INTERSTITIAL_COOLDOWN_MILLIS = 90_000L
}

/**
 * Scan interstitial cadence (AGENTS.md 5.2a, owner decision 2026-10-22). Pure, so it is unit-tested.
 *
 * - Every user who can be shown ads, guest or signed in, from the first scan of the day.
 * - At most one interstitial per [AdConfig.GUEST_INTERSTITIAL_COOLDOWN_MILLIS] since the last one
 *   actually shown ([lastInterstitialMs], 0 = never), so two ads never run back to back
 *   (AdMob disallows back-to-back and every-action interstitials).
 * - A clock that went backwards counts as inside the cooldown.
 */
fun shouldShowScanInterstitial(
    lastInterstitialMs: Long,
    nowMs: Long,
): Boolean {
    if (lastInterstitialMs <= 0L) return true
    val elapsed = nowMs - lastInterstitialMs
    return elapsed >= AdConfig.GUEST_INTERSTITIAL_COOLDOWN_MILLIS
}

/**
 * Platform-independent abstraction for full-screen and rewarded ads.
 */
interface AdManager {
    /**
     * Shows an ad during food scan processing.
     * Invokes [onFinished] when the ad is closed or fails to display.
     */
    fun showScanProcessingAd(onFinished: () -> Unit)

    /**
     * Shows a rewarded video ad to unlock extra scans.
     * Invokes [onRewarded] if the user successfully watched the ad to completion,
     * and [onDismissed] when the ad is closed.
     */
    fun showRewardedScanUnlockAd(onRewarded: () -> Unit, onDismissed: () -> Unit)

    /** Returns true if an interstitial ad is preloaded and ready to show. */
    fun isInterstitialReady(): Boolean

    /** Returns true if a rewarded ad is preloaded and ready to show. */
    fun isRewardedReady(): Boolean

    /** Preloads ads in the background. */
    fun preloadAds()
}

/**
 * Manages daily scan quotas to protect against unbounded AI VLM token costs.
 * Permanent accounts get [AdConfig.WEEK_ONE_DAILY_FREE_SCANS] (5) scans/day during Week 1 (first 7 days)
 * and [AdConfig.DEFAULT_DAILY_FREE_SCANS] (3) from Day 8 onward, each plus [AdConfig.ACCOUNT_DAILY_BONUS_SCANS].
 * Guests get [AdConfig.DEFAULT_DAILY_FREE_SCANS] (3) from day one (owner decision 2026-10-21).
 * Scans beyond the daily quota trigger a forced interstitial ad before processing without blocking dialogs.
 */
object ScanQuotaManager {

    var preferenceReader: (key: String, defaultValue: String) -> String = { key, default ->
        loadPreference(key, default)
    }

    var preferenceWriter: (key: String, value: String) -> Unit = { key, value ->
        savePreference(key, value)
    }

    var currentTimeMillisProvider: () -> Long = { getCurrentEpochMillis() }

    var remoteQuotaManager: com.fitcal.shared.quota.SupabaseQuotaManager? = null

    /** True when the user has a permanent account (earns [AdConfig.ACCOUNT_DAILY_BONUS_SCANS]). */
    var isPermanentAccountProvider: () -> Boolean = { false }

    fun resetToDefaults() {
        preferenceReader = { key, default -> loadPreference(key, default) }
        preferenceWriter = { key, value -> savePreference(key, value) }
        currentTimeMillisProvider = { getCurrentEpochMillis() }
        remoteQuotaManager = null
        isPermanentAccountProvider = { false }
    }

    fun getFirstInstallTimestamp(): Long {
        val saved = preferenceReader("first_install_timestamp", "")
        if (saved.isNotEmpty()) {
            return saved.toLongOrNull() ?: currentTimeMillisProvider()
        }
        val now = currentTimeMillisProvider()
        preferenceWriter("first_install_timestamp", now.toString())
        return now
    }

    fun isWeekOneUser(): Boolean {
        val installed = getFirstInstallTimestamp()
        val now = currentTimeMillisProvider()
        return (now - installed) < AdConfig.SEVEN_DAYS_MILLIS
    }

    /**
     * Free scans per day (display only; the gateway computes the same figure and is authoritative).
     * Guests: 3. Permanent accounts: 5 + 1 in week one, 3 + 1 after.
     */
    fun getDailyFreeLimit(): Int {
        if (!isPermanentAccountProvider()) return AdConfig.DEFAULT_DAILY_FREE_SCANS
        val base = if (isWeekOneUser()) {
            AdConfig.WEEK_ONE_DAILY_FREE_SCANS
        } else {
            AdConfig.DEFAULT_DAILY_FREE_SCANS
        }
        return base + AdConfig.ACCOUNT_DAILY_BONUS_SCANS
    }

    private const val KEY_LAST_INTERSTITIAL_SHOWN = "last_interstitial_shown_ms"

    /** Time the last interstitial actually appeared on screen, or 0 if none has. */
    fun getLastInterstitialShownMillis(): Long =
        preferenceReader(KEY_LAST_INTERSTITIAL_SHOWN, "0").toLongOrNull() ?: 0L

    /**
     * Call from the ad SDK's "shown" callback (AdMob `onAdShowedFullScreenContent`), never when an
     * ad is merely requested, so a scan with no ad loaded does not start the guest cooldown.
     */
    fun recordInterstitialShown() {
        val now = currentTimeMillisProvider()
        preferenceWriter(KEY_LAST_INTERSTITIAL_SHOWN, now.toString())
        _interstitialShownAt.value = now
    }

    private val _interstitialShownAt = MutableStateFlow(0L)

    /**
     * Emits each time an interstitial is shown, so the camera screen re-evaluates [shouldPlayScanAd]
     * and a retake right after an ad does not play a second one back to back.
     */
    val interstitialShownAt: StateFlow<Long> = _interstitialShownAt.asStateFlow()

    fun getUsedScans(dateKey: String): Int {
        return preferenceReader("quota_used_$dateKey", "0").toIntOrNull() ?: 0
    }

    fun getBonusScans(dateKey: String): Int {
        return preferenceReader("quota_bonus_$dateKey", "0").toIntOrNull() ?: 0
    }

    fun getRemainingScans(dateKey: String): Int {
        if (com.fitcal.shared.subscription.SubscriptionManager.isPremiumUser()) {
            return 999
        }
        val used = getUsedScans(dateKey)
        val bonus = getBonusScans(dateKey)
        val totalAllowed = getDailyFreeLimit() + bonus
        return (totalAllowed - used).coerceAtLeast(0)
    }

    fun hasQuota(dateKey: String): Boolean {
        if (com.fitcal.shared.subscription.SubscriptionManager.isPremiumUser()) {
            return true
        }
        return getRemainingScans(dateKey) > 0
    }

    /**
     * Determines if a forced interstitial scan processing ad must be shown.
     * When free/bonus quota is exhausted (hasQuota == false), the scan is NOT blocked,
     * but an interstitial ad MUST be shown before processing.
     * FitCal Premium subscribers receive unlimited ad-free scans.
     */
    fun shouldForceInterstitialAd(dateKey: String): Boolean {
        if (com.fitcal.shared.subscription.SubscriptionManager.isPremiumUser()) {
            return false
        }
        return !hasQuota(dateKey)
    }

    /**
     * Whether a scan plays the processing interstitial, only for users who can be shown ads at all:
     * - past the free quota, always (AGENTS.md 5.2);
     * - within the free scans, for guests and accounts alike, from the first scan of the day,
     *   at most one interstitial per 90 s ([shouldShowScanInterstitial], AGENTS.md 5.2a).
     */
    fun shouldPlayScanAd(dateKey: String, adsAllowed: Boolean): Boolean {
        if (!adsAllowed) return false
        if (com.fitcal.shared.subscription.SubscriptionManager.isPremiumUser()) return false
        if (shouldForceInterstitialAd(dateKey)) return true
        return shouldShowScanInterstitial(
            lastInterstitialMs = getLastInterstitialShownMillis(),
            nowMs = currentTimeMillisProvider(),
        )
    }

    fun consumeScan(dateKey: String) {
        val currentUsed = getUsedScans(dateKey)
        preferenceWriter("quota_used_$dateKey", (currentUsed + 1).toString())
    }

    /**
     * Consumes one scan:
     * 1. Calls server RPC (fail-closed).
     * 2. Returns:
     *    - `true`  → within allowance; local counter incremented
     *    - `false` → hard server denial; local counter still incremented (scan counted even if denied)
     *    - `null`  → network error; local counter incremented; caller shows offline banner
     *
     * UI should NOT allow VLM spend on `false`. On `null`, use cached values + offline banner.
     */
    suspend fun consumeScanServer(dateKey: String): Boolean? {
        val remote = remoteQuotaManager
        if (remote != null) {
            val result = remote.consumeScan(getDailyFreeLimit())
            consumeScan(dateKey)  // Always increment local counter
            return result         // null=offline, false=denied, true=ok
        }
        // No remote configured (tests / offline-only mode): use local cache
        val hadQuota = hasQuota(dateKey)
        consumeScan(dateKey)
        return hadQuota
    }

    /**
     * Syncs quota from server. On success, reconciles local cache to server snapshot.
     * On null (offline): keeps cached values and sets [isOffline] = true.
     * Returns remaining scans, or cached value if offline.
     */
    suspend fun syncQuotaFromServer(dateKey: String): Int {
        val remote = remoteQuotaManager ?: return getRemainingScans(dateKey)
        val snapshot = remote.fetchQuota(getDailyFreeLimit())
        return if (snapshot != null) {
            // Reconcile: server is authoritative; overwrite local cache
            preferenceWriter("quota_used_$dateKey", snapshot.used.toString())
            preferenceWriter("quota_bonus_$dateKey", snapshot.bonus.toString())
            preferenceWriter("quota_offline", "false")
            snapshot.remaining
        } else {
            // Network failure: mark as offline, use cached values
            preferenceWriter("quota_offline", "true")
            getRemainingScans(dateKey)
        }
    }

    /** Returns true if the last syncQuotaFromServer call failed (offline state). */
    fun isOffline(): Boolean {
        return preferenceReader("quota_offline", "false").toBoolean()
    }

    fun addBonusScans(dateKey: String, amount: Int = AdConfig.REWARDED_SCAN_BONUS) {
        val currentBonus = getBonusScans(dateKey)
        // Optimistic local write
        preferenceWriter("quota_bonus_$dateKey", (currentBonus + amount).toString())
        val remote = remoteQuotaManager
        if (remote != null) {
            CoroutineScope(Dispatchers.Default).launch {
                val serverBonus = try {
                    remote.grantBonusScan(amount)
                } catch (e: Exception) {
                    println("Failed to sync bonus scan to server: ${e.message}")
                    null
                }
                // Reconcile: if server returned a confirmed value, overwrite local
                if (serverBonus != null) {
                    preferenceWriter("quota_bonus_$dateKey", serverBonus.toString())
                }
            }
        }
    }

    /**
     * Suspending variant of [addBonusScans] that awaits server confirmation via [SupabaseQuotaManager.grantBonusScan]
     * so an immediate follow-up scan sees the updated server-side bonus quota.
     */
    suspend fun addBonusScansSuspend(dateKey: String, amount: Int = AdConfig.REWARDED_SCAN_BONUS) {
        val currentBonus = getBonusScans(dateKey)
        preferenceWriter("quota_bonus_$dateKey", (currentBonus + amount).toString())
        val remote = remoteQuotaManager
        if (remote != null) {
            val serverBonus = try {
                remote.grantBonusScan(amount)
            } catch (e: Exception) {
                println("Failed to sync bonus scan to server: ${e.message}")
                null
            }
            if (serverBonus != null) {
                preferenceWriter("quota_bonus_$dateKey", serverBonus.toString())
            }
        }
    }
}

