package com.fitcal.app.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitcal.app.ads.BannerScaffold
import com.fitcal.app.learn.Guide
import com.fitcal.app.learn.Guides
import com.fitcal.app.ui.components.PressableBox
import com.fitcal.app.ui.theme.*
import com.fitcal.shared.model.UserProfile

/** One guide: sections, an optional note using the user's own numbers, sources, related guides. */
@Composable
fun GuideScreen(
    guide: Guide,
    profile: UserProfile,
    onBack: () -> Unit,
    onOpenGuide: (String) -> Unit,
) {
    val personal = remember(guide.id, profile) { guide.personalNote(profile) }

    // Bottom banner on reading screens, except sensitive guides (Guide.adFree).
    BannerScaffold(showBanner = !guide.adFree) { inner ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .padding(inner)
            .consumeWindowInsets(inner)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = "GUIDE · ${guide.readMinutes} MIN READ", style = BrandTypography.Eyebrow, color = MutedTextColor)
            Text(
                text = guide.title,
                style = BrandTypography.ScreenTitle,
                color = TextColor,
                modifier = Modifier.semantics { heading() }
            )
            Text(text = guide.summary, fontSize = 15.sp, lineHeight = 22.sp, color = MutedTextColor)
        }

        if (personal != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(InfoPanel, RoundedCornerShape(RadiusM))
                    .border(1.dp, BorderColor, RoundedCornerShape(RadiusM))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = "FOR YOU", style = BrandTypography.Eyebrow, color = PrimaryAccent)
                Text(text = personal, fontSize = 14.sp, lineHeight = 20.sp, color = TextColor)
            }
        }

        guide.sections.forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                section.heading?.let {
                    Text(
                        text = it,
                        style = BrandTypography.SectionTitle,
                        fontWeight = FontWeight.Bold,
                        color = TextColor,
                        modifier = Modifier.padding(top = 4.dp).semantics { heading() }
                    )
                }
                section.paragraphs.forEach { p ->
                    Text(text = p, fontSize = 15.sp, lineHeight = 23.sp, color = TextColor)
                }
                section.bullets.forEach { b ->
                    Row {
                        Text(text = "•", fontSize = 15.sp, lineHeight = 23.sp, color = MutedTextColor, modifier = Modifier.width(18.dp))
                        Text(text = b, fontSize = 15.sp, lineHeight = 23.sp, color = TextColor)
                    }
                }
            }
        }

        // Sources and the medical note close every guide.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .background(CardBackground, RoundedCornerShape(RadiusM))
                .border(1.dp, BorderColor, RoundedCornerShape(RadiusM))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = "SOURCES", style = BrandTypography.Eyebrow, color = MutedTextColor)
            guide.sources.forEach { s ->
                Text(text = s, fontSize = 12.sp, lineHeight = 17.sp, color = MutedTextColor)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "General information, not medical advice. Values are averages and estimates. " +
                    "Speak to a doctor or registered dietitian about your own needs.",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = DangerTextStrong
            )
        }

        val related = guide.related.mapNotNull { Guides.byId(it) }
        if (related.isNotEmpty()) {
            Text(text = "KEEP READING", style = BrandTypography.Eyebrow, color = MutedTextColor, modifier = Modifier.padding(top = 4.dp))
            related.forEach { r -> GuideCard(guide = r, onClick = { onOpenGuide(r.id) }) }
        }
    }
    }
}

/** A guide in a list: title, one-line summary, reading time. */
@Composable
fun GuideCard(guide: Guide, onClick: () -> Unit) {
    PressableBox(
        onTap = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackground, RoundedCornerShape(RadiusM))
            .border(1.dp, BorderColor, RoundedCornerShape(RadiusM))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = guide.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextColor)
                Text(text = guide.summary, fontSize = 13.sp, lineHeight = 18.sp, color = MutedTextColor)
                Text(text = "${guide.readMinutes} min read", fontSize = 12.sp, color = PrimaryAccent)
            }
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MutedTextColor)
        }
    }
}
