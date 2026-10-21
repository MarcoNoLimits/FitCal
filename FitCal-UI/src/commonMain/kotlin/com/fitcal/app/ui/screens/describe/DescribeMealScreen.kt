package com.fitcal.app.ui.screens.describe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fitcal.app.ScanReadiness
import com.fitcal.app.ads.AdManager
import com.fitcal.app.getCurrentEpochMillis
import com.fitcal.app.telemetry.Analytics
import com.fitcal.app.telemetry.DiagnosticsCrashHook
import com.fitcal.app.ui.theme.*
import com.fitcal.shared.api.AnalyzeResult
import com.fitcal.shared.api.MealTextLimits
import com.fitcal.shared.api.NutritionClient
import com.fitcal.shared.api.normalizeMealDescription
import com.fitcal.shared.model.NutritionResponse
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * "Describe your meal": the user types what they ate and FitCal estimates it like a
 * photo. It is one AI scan and goes through the same gate as the camera: the same
 * readiness and quota wall, the same interstitial decision ([playAdDuringScan], from
 * App.kt's shared scan gate) and the same [onScanConsumed]. No banner ads here.
 */
@Composable
fun DescribeMealScreen(
    apiClient: NutritionClient,
    adManager: AdManager,
    playAdDuringScan: Boolean,
    onScanConsumed: () -> Unit,
    onResultObtained: (String) -> Unit,
    onNavigateBack: () -> Unit,
    scanReadiness: ScanReadiness = ScanReadiness.Ready,
    scansRemaining: Int = 1,
    onBonusScansEarned: (suspend () -> Unit)? = null,
    onCreateAccount: (() -> Unit)? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<DescribeOutcome.Fail?>(null) }
    var activeScan by remember { mutableStateOf<AdGatedScan?>(null) }

    fun show(outcome: DescribeOutcome?) {
        when (outcome) {
            is DescribeOutcome.Deliver -> {
                onScanConsumed()
                onResultObtained(outcome.responseJson)
            }
            is DescribeOutcome.Fail -> {
                error = outcome
                isAnalyzing = false
            }
            null -> Unit
        }
    }

    // Same funnel events as the photo scan, marked input = text. Never the text itself.
    suspend fun analyze(description: String, attempt: String, withAd: Boolean): DescribeOutcome {
        val startedAt = getCurrentEpochMillis()
        Analytics.track("scan_started", "attempt" to attempt, "with_ad" to withAd, "input" to "text", "chars" to description.length)
        val result = apiClient.analyzeMealTextWithResult(description)
        val outcome = describeOutcomeOf(result) { success ->
            Json.encodeToString(NutritionResponse.serializer(), success.response)
        }
        val errorClass = describeErrorClass(result)
        if (errorClass == null) {
            val items = (result as? AnalyzeResult.Success)?.response?.items.orEmpty()
            Analytics.track(
                "scan_succeeded",
                "attempt" to attempt,
                "input" to "text",
                "latency_ms" to getCurrentEpochMillis() - startedAt,
                "items" to items.size,
                "low_confidence_items" to items.count { it.confidence.equals("low", ignoreCase = true) },
                "no_food" to items.isEmpty(),
            )
        } else {
            Analytics.track(
                "scan_failed",
                "attempt" to attempt,
                "input" to "text",
                "latency_ms" to getCurrentEpochMillis() - startedAt,
                "error" to errorClass,
            )
            if (errorClass != "quota") {
                DiagnosticsCrashHook.logVlmError("DescribeMealScreen", errorClass, null)
            }
        }
        return outcome
    }

    // attempt = "first": the gate's interstitial decision applies, like a new photo.
    // attempt = "resend": no ad, like the camera's "Resend Photo" (that scan already had its turn).
    fun submit(attempt: String) {
        val description = normalizeMealDescription(text) ?: return
        val withAd = attempt == "first" && playAdDuringScan
        error = null
        isAnalyzing = true
        val scan = AdGatedScan(adPlaying = withAd)
        activeScan = scan
        coroutineScope.launch {
            val outcome = try {
                analyze(description, attempt, withAd)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                DiagnosticsCrashHook.logVlmError("DescribeMealScreen", e.message ?: "describe failed", e)
                DescribeOutcome.Fail("We couldn't estimate this meal. Check your connection and try again.")
            }
            show(scan.onAnswer(outcome))
        }
        if (withAd) {
            adManager.showScanProcessingAd { show(scan.onAdClosed()) }
        }
    }

    fun cancel() {
        Analytics.track("scan_cancelled", "input" to "text")
        activeScan?.cancel()
        isAnalyzing = false
        onNavigateBack()
    }

    val watchRewardedAd: (Boolean) -> Unit = { retryAfter ->
        var rewarded = false
        val source = if (retryAfter) "scan_error" else "quota_wall"
        Analytics.track("rewarded_ad_requested", "source" to source)
        adManager.showRewardedScanUnlockAd(
            onRewarded = {
                rewarded = true
                Analytics.track("rewarded_ad_earned", "source" to source)
                coroutineScope.launch {
                    onBonusScansEarned?.invoke()
                    if (retryAfter && canSubmitDescription(text)) submit("resend")
                }
            },
            onDismissed = {
                if (!rewarded) Analytics.track("rewarded_ad_closed_early", "source" to source)
            },
        )
    }

    val gate = describeGate(scanReadiness, scansRemaining, isAnalyzing, hasError = error != null)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        when (gate) {
            DescribeGate.CONNECTING -> CenteredStatus {
                CircularProgressIndicator(color = PrimaryAccent)
                Text("Connecting…", style = BrandTypography.SectionTitle, color = TextColor)
                Text("Securing session before scanning", style = BrandTypography.BodySmall, color = MutedTextColor)
            }
            DescribeGate.OFFLINE -> CenteredStatus {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = FatColor, modifier = Modifier.size(48.dp))
                Text("You're offline", style = BrandTypography.SectionTitle, color = TextColor)
                Text(
                    "FitCal needs a connection to estimate a meal. Try again when you're back online.",
                    style = BrandTypography.BodySmall,
                    color = MutedTextColor,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onNavigateBack,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                    shape = RoundedCornerShape(RadiusM),
                ) { Text("Back to Dashboard", color = Color.White) }
            }
            DescribeGate.QUOTA_WALL -> CenteredStatus {
                QuotaCard(
                    body = "You've used all your free scans for today. Watch a short reward video to unlock +2 bonus scans right now!",
                    watchLabel = "Watch Video (+2 Scans)",
                    onWatch = { watchRewardedAd(false) },
                    onCreateAccount = onCreateAccount,
                    onBack = onNavigateBack,
                )
            }
            DescribeGate.FORM -> DescribeForm(
                text = text,
                onTextChange = { text = capDescriptionInput(it) },
                enabled = !isAnalyzing,
                scansRemaining = scansRemaining,
                onSubmit = { submit("first") },
                onClose = onNavigateBack,
            )
        }

        AnimatedVisibility(
            visible = isAnalyzing,
            enter = if (REDUCED_MOTION_ENABLED) fadeIn() else fadeIn() + scaleIn(initialScale = 0.98f, animationSpec = spring(stiffness = 300f)),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        shape = RoundedCornerShape(RadiusL),
                        modifier = Modifier.fillMaxWidth(0.85f).border(1.dp, BorderColor, RoundedCornerShape(RadiusL)),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(28.dp),
                        ) {
                            CircularProgressIndicator(color = PrimaryAccent, strokeWidth = 4.dp, modifier = Modifier.size(48.dp))
                            Text("Estimating your meal…", style = BrandTypography.SectionTitle, color = TextColor)
                            Text(
                                "FitCal is turning your description into ingredients, portions and macros.",
                                style = BrandTypography.BodySmall,
                                color = MutedTextColor,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { cancel() },
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                    ) { Text("Cancel", color = Color.White, style = BrandTypography.BodySmall) }
                }
            }
        }

        error?.let { failure ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (failure.isQuota) {
                    QuotaCard(
                        body = "You've used all your free scans for today. Watch a short reward video to unlock +2 bonus scans and estimate this meal right now!",
                        watchLabel = "Watch Video (+2 Scans & Estimate)",
                        onWatch = { watchRewardedAd(true) },
                        onCreateAccount = onCreateAccount,
                        onBack = {
                            error = null
                            onNavigateBack()
                        },
                    )
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        shape = RoundedCornerShape(RadiusL),
                        modifier = Modifier.fillMaxWidth().border(1.dp, DangerColor.copy(alpha = 0.4f), RoundedCornerShape(RadiusL)),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = DangerColor, modifier = Modifier.size(40.dp))
                            Text("Couldn't estimate this meal", style = BrandTypography.SectionTitle, color = TextColor, textAlign = TextAlign.Center)
                            Text(failure.message, style = BrandTypography.BodySmall, color = MutedTextColor, textAlign = TextAlign.Center)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { error = null },
                                    border = BorderStroke(1.dp, BorderColor),
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                ) { Text("Edit text", color = TextColor, style = BrandTypography.BodySmall) }
                                Button(
                                    onClick = { submit("resend") },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                ) { Text("Try again", color = Color.White, style = BrandTypography.BodySmall) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DescribeForm(
    text: String,
    onTextChange: (String) -> Unit,
    enabled: Boolean,
    scansRemaining: Int,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
) {
    val canSubmit = enabled && canSubmitDescription(text)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text("TYPE A MEAL", style = BrandTypography.Eyebrow, color = MutedTextColor)
                Text("Describe your meal", style = BrandTypography.ScreenTitle, color = TextColor)
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(44.dp)
                    .shadow(1.dp, CircleShape)
                    .background(Color.White, CircleShape)
                    .border(1.dp, BorderColor, CircleShape),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextColor, modifier = Modifier.size(20.dp))
            }
        }

        Text(
            "Write what you ate, with amounts if you know them. FitCal estimates it like a photo scan, and you can correct everything on the next screen.",
            style = BrandTypography.Body,
            color = MutedTextColor,
        )

        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            enabled = enabled,
            label = { Text("What did you eat?", style = BrandTypography.BodySmall) },
            placeholder = { Text(DESCRIBE_PLACEHOLDER, color = MutedTextColor.copy(alpha = 0.8f)) },
            supportingText = {
                Text(
                    "${text.length}/${MealTextLimits.MAX_DESCRIPTION_CHARS}",
                    style = BrandTypography.Micro,
                    color = MutedTextColor,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                )
            },
            minLines = 4,
            maxLines = 8,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Default,
            ),
            shape = RoundedCornerShape(RadiusM),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryAccent,
                unfocusedBorderColor = InputBorder,
                cursorColor = PrimaryAccent,
                focusedTextColor = TextColor,
                unfocusedTextColor = TextColor,
                focusedContainerColor = CardBackground,
                unfocusedContainerColor = CardBackground,
                disabledContainerColor = CardBackground,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = onSubmit,
            enabled = canSubmit,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
            shape = RoundedCornerShape(RadiusM),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
        ) {
            Text("Estimate meal", style = BrandTypography.SectionTitle, color = Color.White)
        }

        // Premium users report 999 remaining; say nothing about quota then.
        if (scansRemaining in 1..99) {
            Text(
                text = if (scansRemaining == 1) "Uses 1 scan. This is your last free scan today."
                else "Uses 1 scan. $scansRemaining free scans left today.",
                style = BrandTypography.BodySmall,
                color = MutedTextColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CenteredStatus(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

/** The quota wall, worded like the camera's: rewarded ads stay opt-in (AGENTS.md 5.2). */
@Composable
private fun QuotaCard(
    body: String,
    watchLabel: String,
    onWatch: () -> Unit,
    onCreateAccount: (() -> Unit)?,
    onBack: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(RadiusL),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(RadiusL))
            .border(1.dp, BorderColor, RoundedCornerShape(RadiusL)),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text("Daily Scan Limit Reached", style = BrandTypography.SectionTitle, color = TextColor, textAlign = TextAlign.Center)
            Text(body, style = BrandTypography.BodySmall, color = MutedTextColor, textAlign = TextAlign.Center)
            Button(
                onClick = onWatch,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                shape = RoundedCornerShape(RadiusM),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(watchLabel, color = Color.White, fontWeight = FontWeight.Bold) }
            if (onCreateAccount != null) {
                OutlinedButton(
                    onClick = onCreateAccount,
                    shape = RoundedCornerShape(RadiusM),
                    border = BorderStroke(1.dp, PrimaryAccent),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(
                        "Create Free Account (+1 Scan Every Day)",
                        color = PrimaryAccent,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(RadiusM),
                border = BorderStroke(1.dp, BorderColor),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Back to Dashboard", color = TextColor) }
        }
    }
}
