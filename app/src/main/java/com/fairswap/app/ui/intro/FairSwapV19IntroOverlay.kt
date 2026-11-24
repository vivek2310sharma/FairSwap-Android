package com.fairswap.app.ui.intro

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fairswap.core.designsystem.R as DesignR
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.sin

@Composable
fun FairSwapV19IntroOverlay(
    modifier: Modifier = Modifier,
    onDismissed: () -> Unit = {},
) {
    val context = LocalContext.current
    val isInspection = LocalInspectionMode.current
    val isReducedMotion = remember(context, isInspection) {
        if (isInspection) false
        else try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1f
            )
            scale == 0f
        } catch (_: Exception) {
            false
        }
    }

    val shouldPlay = remember { IntroSessionController.shouldPlayIntro(isReducedMotion) }
    if (!shouldPlay) return

    var isVisible by remember { mutableStateOf(true) }
    if (!isVisible) {
        LaunchedEffect(Unit) { onDismissed() }
        return
    }

    BackHandler { isVisible = false }

    val palette = remember { IntroData.selectPalette() }
    val greetings = remember { IntroData.selectGreetings() }
    val tagline = remember { IntroData.selectTagline() }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = remember(configuration, density) { with(density) { configuration.screenWidthDp.dp.toPx() } }
    val screenHeightPx = remember(configuration, density) { with(density) { configuration.screenHeightDp.dp.toPx() } }

    val placements = remember(palette, screenWidthPx, screenHeightPx) {
        MotifLayoutEngine.computePlacements(screenWidthPx, screenHeightPx, palette)
    }

    var currentGreetingIndex by remember { mutableIntStateOf(0) }
    var isBrandReveal by remember { mutableStateOf(false) }

    // Start frame 1 at 1f so first greeting ('नमस्ते') is immediately 100% visible
    val textAlpha = remember { Animatable(1f) }
    val overlayAlpha = remember { Animatable(1f) }
    val floatAnim = remember { Animatable(0f) }

    // Floating bobbing animation for background motifs
    LaunchedEffect(Unit) {
        while (true) {
            floatAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(3000, easing = LinearEasing)
            )
            floatAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(3000, easing = LinearEasing)
            )
        }
    }

    // Master playback animation sequence with 9-second fail-open safety timeout
    LaunchedEffect(Unit) {
        withTimeoutOrNull(9000L) {
            // Step 1: Play 15 greeting slides
            for (i in greetings.indices) {
                currentGreetingIndex = i
                if (i > 0) {
                    delay(79L)
                    textAlpha.animateTo(1f, animationSpec = tween(120, easing = LinearEasing))
                }
                delay(120L)
                textAlpha.animateTo(0f, animationSpec = tween(56, easing = LinearEasing))
            }

            // Step 2: Final Brand Reveal & Tagline
            isBrandReveal = true
            textAlpha.animateTo(1f, animationSpec = tween(300, easing = LinearEasing))
            delay(900L)

            // Step 3: Fade release
            overlayAlpha.animateTo(0f, animationSpec = tween(300, easing = LinearEasing))
        }
        isVisible = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(overlayAlpha.value)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(palette.bg2, palette.bg),
                    center = Offset(0.5f, 0.46f),
                    radius = 1200f
                )
            )
    ) {
        // Motif Background Layer (Pre-calculated placements drawn natively)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val floatPhase = floatAnim.value * 6.28318f
            placements.forEach { placement ->
                val floatOffset = sin(floatPhase + placement.phaseOffset) * 8f
                MotifLayoutEngine.drawMotif(this, placement, floatOffset)
            }
        }

        // Center Protected Content Band (Greetings OR Final Brand Reveal)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!isBrandReveal) {
                val greeting = greetings.getOrNull(currentGreetingIndex)
                if (greeting != null) {
                    Text(
                        text = greeting.text,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.alpha(textAlpha.value)
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.alpha(textAlpha.value)
                ) {
                    Image(
                        painter = painterResource(id = DesignR.drawable.fairswap_wordmark_light_background),
                        contentDescription = "FairSwap",
                        modifier = Modifier.height(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = tagline,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.accent,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }

        // Accessible "Skip intro" Button (Top Right, min 48x48 dp touch target)
        Surface(
            color = Color.Black.copy(alpha = 0.3f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 16.dp, end = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable(
                    onClick = { isVisible = false },
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                )
        ) {
            Text(
                text = stringResource(id = DesignR.string.intro_skip),
                color = palette.textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}
