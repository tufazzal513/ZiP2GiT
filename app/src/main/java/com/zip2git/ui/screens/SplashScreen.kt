package com.zip2git.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zip2git.BuildConfig

/** Splash palette, sampled from the original launch screen. */
private val SplashBackground = Color(0xFF2A2B2D)
private val SplashTile = Color(0xFF15161A)
private val WordmarkOrange = Color(0xFFF2603C)
private val WordmarkBlue = Color(0xFF3E8EE8)
private val SplashSubtitle = Color(0xFF9AA0A6)
private val GlyphPurple = Color(0xFF9B6BF2)

/**
 * Animated launch screen: the icon and wordmark fade/scale in, the surrounding
 * glow breathes, and the three dots pulse as a loading indicator until the home
 * page fades in. Shown for [com.zip2git.ui.MainViewModel]'s minimum splash hold.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    // Entry animation for the icon + wordmark block.
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enter.animateTo(1f, animationSpec = tween(durationMillis = 750))
    }

    // Breathing glow behind the icon.
    val glow = rememberInfiniteTransition(label = "glow")
    val glowAlpha by glow.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowAlpha",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SplashBackground),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        Column(
            modifier = Modifier
                .alpha(enter.value)
                .scale(0.85f + 0.15f * enter.value),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GlowingIcon(glowAlpha = glowAlpha)

            Spacer(Modifier.height(30.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "Zip",
                    color = Color.White,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "2",
                    color = WordmarkOrange,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Git",
                    color = WordmarkBlue,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Direct Archive to GitHub Repository",
                color = SplashSubtitle,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(44.dp))

        LoadingDots()

        Spacer(Modifier.weight(1f))

        Text(
            text = "v${BuildConfig.VERSION_NAME}  •  On-Device Git Engine",
            color = SplashSubtitle,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 34.dp),
        )
    }
}

/** The rounded icon tile with an orange-to-blue glow ring pulsing around it. */
@Composable
private fun GlowingIcon(glowAlpha: Float) {
    Box(
        modifier = Modifier.size(132.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .alpha(glowAlpha)
                .background(
                    brush = Brush.linearGradient(
                        listOf(WordmarkOrange, GlyphPurple, WordmarkBlue),
                    ),
                    shape = RoundedCornerShape(36.dp),
                ),
        )
        Box(
            modifier = Modifier
                .size(106.dp)
                .background(SplashTile, RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            // The current app glyph, drawn large: a folder with a branch mark.
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    tint = GlyphPurple,
                    modifier = Modifier.size(54.dp),
                )
                Icon(
                    imageVector = Icons.Filled.CallSplit,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(top = 12.dp, start = 12.dp),
                )
            }
        }
    }
}

/** Three staggered pulsing dots (orange, orange, blue) reading as "loading". */
@Composable
private fun LoadingDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Dot(transition, color = WordmarkOrange, delayMillis = 0)
        Dot(transition, color = WordmarkOrange, delayMillis = 180)
        Dot(transition, color = WordmarkBlue, delayMillis = 360)
    }
}

@Composable
private fun Dot(
    transition: androidx.compose.animation.core.InfiniteTransition,
    color: Color,
    delayMillis: Int,
) {
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 620, delayMillis = delayMillis),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot$delayMillis",
    )
    Box(
        modifier = Modifier
            .size(9.dp)
            .alpha(alpha)
            .background(color, CircleShape),
    )
}