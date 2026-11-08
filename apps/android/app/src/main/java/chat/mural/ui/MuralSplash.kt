package chat.mural.ui

import android.animation.ValueAnimator
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import chat.mural.BuildConfig
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/** Only local history gates startup. Account, network and purchase refreshes never hold this screen. */
@Composable
fun MuralStartup(
    loading: Boolean,
    minimumDurationMillis: Long = if (BuildConfig.BUILD_TYPE == "uiTest") 0 else 780,
    content: @Composable () -> Unit,
) {
    var complete by rememberSaveable { mutableStateOf(false) }
    val startedAt = rememberSaveable { SystemClock.uptimeMillis() }
    val motionEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    var minimumElapsed by rememberSaveable { mutableStateOf(!motionEnabled || minimumDurationMillis <= 0) }
    LaunchedEffect(Unit) {
        if (!minimumElapsed) {
            delay((minimumDurationMillis - (SystemClock.uptimeMillis() - startedAt)).coerceAtLeast(0))
            minimumElapsed = true
        }
    }
    LaunchedEffect(loading, minimumElapsed) {
        if (!loading && minimumElapsed) complete = true
    }
    val duration = if (motionEnabled && BuildConfig.BUILD_TYPE != "uiTest") 220 else 0
    AnimatedContent(
        targetState = complete,
        modifier = Modifier.fillMaxSize().background(MuralColors.Cream),
        transitionSpec = { fadeIn(tween(duration)) togetherWith fadeOut(tween(duration)) },
        label = "Mural startup",
    ) { ready ->
        if (ready) content() else MuralSplash()
    }
}

/** Uses the spherical nebula conversation orb with a cosmic watercolor aura and subtle stellar glints. */
@Composable
fun MuralSplash(modifier: Modifier = Modifier, phaseOverride: Float? = null) {
    val phase = phaseOverride ?: muralPhase()
    val pulse = 1f + sin(phase * 2.5f) * .036f
    BoxWithConstraints(
        modifier.fillMaxSize().background(MuralColors.Cream).testTag("mural-splash")
            .semantics(mergeDescendants = true) {
                contentDescription = "Yamy"
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            },
        contentAlignment = Alignment.Center,
    ) {
        SoftAnimatedBackground(Modifier.fillMaxSize(), phaseOverride = phase)
        val side = minOf(216.dp, maxWidth * .58f, maxHeight * .58f)
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val haloR = minOf(size.width, size.height) * 0.42f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MuralColors.Orange.copy(alpha = 0.20f),
                        MuralColors.YellowAccent.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = c,
                    radius = haloR
                ),
                radius = haloR,
                center = c
            )
            // Subtle ambient cosmic star glints around the nebula
            val twinkleA = 0.65f + 0.35f * sin(phase * 2.0f)
            val twinkleB = 0.65f + 0.35f * cos(phase * 2.3f + 1.2f)
            drawStellarGlint(
                center = Offset(c.x + haloR * 0.62f, c.y - haloR * 0.54f),
                radius = 7.dp.toPx(),
                coreColor = Color.White,
                glowColor = Color(0xFF9BE4FF),
                alpha = twinkleA * 0.75f
            )
            drawStellarGlint(
                center = Offset(c.x - haloR * 0.66f, c.y + haloR * 0.48f),
                radius = 6.dp.toPx(),
                coreColor = Color(0xFFFFF9DF),
                glowColor = MuralColors.YellowAccent,
                alpha = twinkleB * 0.75f
            )
        }
        Box(Modifier.size(side).graphicsLayer { scaleX = pulse; scaleY = pulse }) {
            MuralOrb(phaseOverride = phase, modifier = Modifier.fillMaxSize().testTag("mural-splash-orb"))
        }
    }
}
