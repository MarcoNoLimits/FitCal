package com.fitcal.app.ui.screens.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fitcal.app.ui.theme.BorderColor
import com.fitcal.app.ui.theme.BrandTypography
import com.fitcal.app.ui.theme.CardBackground
import com.fitcal.app.ui.theme.PrimaryAccent
import com.fitcal.app.ui.theme.RadiusL
import com.fitcal.app.ui.theme.RadiusM
import com.fitcal.app.ui.theme.TextColor

const val SIGN_IN_CARD_TITLE = "Create a free account"
const val SIGN_IN_CARD_BODY =
    "More free scans in your first week, plus 1 extra scan every day. Everything you log comes with you."

/**
 * One-time first-open card for guests (AGENTS.md 5.2a). Never blocks the app: tapping outside
 * counts as "Not now". Shown and recorded by [com.fitcal.app.auth.SignInCardPolicy].
 */
@Composable
fun SignInCard(
    onCreateAccount: () -> Unit,
    onNotNow: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onNotNow,
        containerColor = CardBackground,
        shape = RoundedCornerShape(RadiusL),
        title = {
            Text(SIGN_IN_CARD_TITLE, style = BrandTypography.SectionTitle, color = TextColor)
        },
        text = {
            Text(SIGN_IN_CARD_BODY, style = BrandTypography.Body, color = TextColor)
        },
        confirmButton = {
            Button(
                onClick = onCreateAccount,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                shape = RoundedCornerShape(RadiusM),
            ) { Text("Create account", color = Color.White) }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onNotNow,
                shape = RoundedCornerShape(RadiusM),
                border = BorderStroke(1.dp, BorderColor),
            ) { Text("Not now", color = TextColor) }
        },
    )
}
