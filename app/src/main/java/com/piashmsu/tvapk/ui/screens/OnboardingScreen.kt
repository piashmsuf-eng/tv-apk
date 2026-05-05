package com.piashmsu.tvapk.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.piashmsu.tvapk.R
import com.piashmsu.tvapk.ui.theme.GradientBackground
import com.piashmsu.tvapk.ui.theme.GradientHero

private data class OnboardSlide(val titleRes: Int, val bodyRes: Int)

private val slides = listOf(
    OnboardSlide(R.string.onboard_title_1, R.string.onboard_body_1),
    OnboardSlide(R.string.onboard_title_2, R.string.onboard_body_2),
    OnboardSlide(R.string.onboard_title_3, R.string.onboard_body_3),
)

/**
 * First-launch tutorial. Three brand-styled slides walk the user through
 * adding a playlist, enabling auto-refresh + EPG, and the player gestures.
 *
 * Auto-shown only when the `onboarded_v5` pref flag is false. Tapping
 * "Get started" sets the flag and the screen never appears again — but
 * the user can re-trigger it from Settings → Reset onboarding.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    var index by remember { mutableStateOf(0) }
    Box(
        Modifier
            .fillMaxSize()
            .background(GradientBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Header logo + skip
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(GradientHero)
                )
                TextButton(onClick = onFinished) {
                    Text(stringResource(R.string.common_skip))
                }
            }

            // Slide body — fade+slide as the user advances
            AnimatedContent(
                targetState = index,
                label = "onboard",
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 6 })
                        .togetherWith(fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { -it / 6 })
                },
            ) { i ->
                val slide = slides[i.coerceIn(0, slides.lastIndex)]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(slide.titleRes),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(slide.bodyRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                    )
                }
            }

            // Pager dots + advance button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    slides.forEachIndexed { i, _ ->
                        val on = i == index
                        Box(
                            Modifier
                                .size(if (on) 12.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (on) MaterialTheme.colorScheme.primary
                                    else Color(0x66FFFFFF)
                                )
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (index < slides.lastIndex) index++ else onFinished()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    val isLast = index == slides.lastIndex
                    Text(
                        text = stringResource(
                            if (isLast) R.string.onboard_get_started else R.string.common_continue
                        ),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
