package chat.mural.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.mural.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object MuralColors {
    var isDark by mutableStateOf(false)

    // Background surfaces (Paper watercolor in light, deep nocturnal slate in dark)
    val Cream: Color get() = if (isDark) Color(0xFF0B131E) else Color(0xFFF5F8FC)
    val CreamRaised: Color get() = if (isDark) Color(0xFF131F30) else Color(0xFFEAF1F9)
    val Surface: Color get() = if (isDark) Color(0xFF192638) else Color(0xFFFFFFFF)
    val SurfaceBright: Color get() = if (isDark) Color(0xFF21324A) else Color(0xFFF9FBFE)
    val ControlBorder: Color get() = if (isDark) Color(0xFF2E435E) else Color(0xFFD8E4F2)

    // Ink / Text
    val Ink: Color get() = if (isDark) Color(0xFFECF3FA) else Color(0xFF162231)
    val Secondary: Color get() = if (isDark) Color(0xFFA9BED4) else Color(0xFF526478)

    // Primary Watercolor Cerulean Blue (maps to Orange for system-wide seamless replacement)
    val Orange: Color get() = if (isDark) Color(0xFF4DA3FF) else Color(0xFF2484E0)
    val WatercolorDeep: Color get() = if (isDark) Color(0xFF2B78D4) else Color(0xFF1659A2)
    val WatercolorWash: Color get() = if (isDark) Color(0xFF1E3654) else Color(0xFFDCEBFA)

    // Warm Golden Watercolor Yellow Accent
    val YellowAccent: Color get() = if (isDark) Color(0xFFF4C842) else Color(0xFFEAB328)
    val YellowWash: Color get() = if (isDark) Color(0xFF3A3118) else Color(0xFFFFF3C4)

    // Semantic Alert
    val Red: Color get() = if (isDark) Color(0xFFFF6B6B) else Color(0xFFD94848)

    // Theme-aware Option / Chip / Callout surfaces:
    // Slightly darker nocturnal tints in Dark Mode so light text (MuralColors.Ink / Secondary) has high contrast.
    val Peach: Color get() = if (isDark) Color(0xFF342420) else Color(0xFFFFE3CF)
    val Lilac: Color get() = if (isDark) Color(0xFF26223A) else Color(0xFFEEE6FA)
    val Sage: Color get() = if (isDark) Color(0xFF1F3128) else Color(0xFFEAEFD6)
    val Butter: Color get() = if (isDark) Color(0xFF2E291A) else Color(0xFFFFF1C7)

    // Theme Cards (Panels) - Keep classic pastel identity, slightly darker/toned-down in Dark Mode to prevent glare
    private val LightPanels = listOf(
        Color(0xFFFFE3CF),
        Color(0xFFEEE6FA),
        Color(0xFFEAEFD6),
        Color(0xFFFFF1C7),
    )
    private val DarkPanels = listOf(
        Color(0xFFD8B49C),
        Color(0xFFC1B6DF),
        Color(0xFFB8C5A2),
        Color(0xFFD8C590),
    )
    val Panels: List<Color> get() = if (isDark) DarkPanels else LightPanels

    // High-contrast text & icons inside pastel cards (WCAG AAA compliant in both themes)
    val CardInk: Color get() = if (isDark) Color(0xFF121820) else Color(0xFF1E242B)
    val CardSecondary: Color get() = if (isDark) Color(0xFF384250) else Color(0xFF525B66)
}

private val MuralLightScheme = lightColorScheme(
    primary = Color(0xFF2484E0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCEBFA),
    onPrimaryContainer = Color(0xFF162231),
    secondary = Color(0xFF526478),
    background = Color(0xFFF5F8FC),
    onBackground = Color(0xFF162231),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF162231),
    surfaceVariant = Color(0xFFF9FBFE),
    onSurfaceVariant = Color(0xFF526478),
    error = Color(0xFFD94848),
)

private val MuralDarkScheme = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFF4DA3FF),
    onPrimary = Color(0xFF0B131E),
    primaryContainer = Color(0xFF1E3654),
    onPrimaryContainer = Color(0xFFECF3FA),
    secondary = Color(0xFFA9BED4),
    background = Color(0xFF0B131E),
    onBackground = Color(0xFFECF3FA),
    surface = Color(0xFF192638),
    onSurface = Color(0xFFECF3FA),
    surfaceVariant = Color(0xFF21324A),
    onSurfaceVariant = Color(0xFFA9BED4),
    error = Color(0xFFFF6B6B),
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val MuralRounded = FontFamily(
    *listOf(400, 500, 600, 700, 800).map { weight ->
        androidx.compose.ui.text.font.Font(R.font.nunito, FontWeight(weight),
            variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(
                androidx.compose.ui.text.font.FontVariation.weight(weight)))
    }.toTypedArray(),
)

private fun rounded(size: Int, height: Int, weight: FontWeight = FontWeight.Normal, tracking: Float = 0f) =
    TextStyle(fontFamily = MuralRounded, fontSize = size.sp, lineHeight = height.sp, fontWeight = weight, letterSpacing = tracking.sp)

private val MuralTypography = Typography(
    displayLarge = rounded(60, 72, FontWeight.Medium, -2f),
    displayMedium = rounded(48, 56, FontWeight.Medium, -1.5f),
    displaySmall = rounded(38, 44, FontWeight.Medium, -1f),
    headlineLarge = rounded(34, 39, FontWeight.SemiBold, -1f),
    headlineMedium = rounded(27, 33, FontWeight.Medium, -.5f),
    headlineSmall = rounded(24, 30, FontWeight.SemiBold, -.4f),
    titleLarge = rounded(22, 28, FontWeight.SemiBold, -.35f),
    titleMedium = rounded(17, 23, FontWeight.SemiBold),
    titleSmall = rounded(15, 21, FontWeight.SemiBold),
    bodyLarge = rounded(17, 25),
    bodyMedium = rounded(15, 22),
    bodySmall = rounded(13, 18),
    labelLarge = rounded(16, 22, FontWeight.SemiBold),
    labelMedium = rounded(13, 18, FontWeight.SemiBold),
    labelSmall = rounded(11, 15, FontWeight.Medium),
)

@Composable
fun MuralTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MuralColors.isDark = darkTheme
    val scheme = if (darkTheme) MuralDarkScheme else MuralLightScheme
    MaterialTheme(colorScheme = scheme, typography = MuralTypography,
        shapes = androidx.compose.material3.Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(36.dp)), content = content)
}

/** Draws a subtle astronomical star glint with a soft Gaussian-like halo, delicate cross-flare whisper, and pinpoint core. */
internal fun DrawScope.drawStellarGlint(
    center: Offset,
    radius: Float,
    coreColor: Color = Color.White,
    glowColor: Color = Color(0xFFFFF3C4),
    alpha: Float = 1f
) {
    val safeAlpha = alpha.coerceIn(0f, 1f)
    if (safeAlpha <= 0.01f || radius <= 0.5f) return

    // Soft outer optical bloom
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                glowColor.copy(alpha = 0.65f * safeAlpha),
                glowColor.copy(alpha = 0.22f * safeAlpha),
                Color.Transparent
            ),
            center = center,
            radius = radius * 1.15f
        ),
        radius = radius * 1.15f,
        center = center
    )

    // Delicate horizontal and vertical optical lens-flare whispers
    val strokeW = (radius * 0.14f).coerceAtLeast(1f)
    drawLine(
        brush = Brush.linearGradient(
            colors = listOf(Color.Transparent, coreColor.copy(alpha = 0.72f * safeAlpha), Color.Transparent),
            start = Offset(center.x - radius, center.y),
            end = Offset(center.x + radius, center.y)
        ),
        start = Offset(center.x - radius, center.y),
        end = Offset(center.x + radius, center.y),
        strokeWidth = strokeW,
        cap = StrokeCap.Round
    )
    val vRadius = radius * 0.78f
    drawLine(
        brush = Brush.linearGradient(
            colors = listOf(Color.Transparent, coreColor.copy(alpha = 0.62f * safeAlpha), Color.Transparent),
            start = Offset(center.x, center.y - vRadius),
            end = Offset(center.x, center.y + vRadius)
        ),
        start = Offset(center.x, center.y - vRadius),
        end = Offset(center.x, center.y + vRadius),
        strokeWidth = strokeW * 0.9f,
        cap = StrokeCap.Round
    )

    // Bright pinpoint star core
    drawCircle(
        color = Color.White.copy(alpha = safeAlpha),
        radius = (radius * 0.20f).coerceAtLeast(1.2f),
        center = center
    )
}

@Composable
fun Brand(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "Yamy" },
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(24.dp)) {
            val c = Offset(size.width * 0.5f, size.height * 0.5f)
            val r = minOf(size.width, size.height) * 0.44f

            // Outer gaseous nebula halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF4DA3FF).copy(alpha = 0.42f),
                        Color(0xFFF4C842).copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    center = c,
                    radius = r * 1.24f
                ),
                radius = r * 1.24f,
                center = c
            )

            // Spherical cosmic cobalt & cerulean nebula body
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF091838),
                        Color(0xFF13468B),
                        Color(0xFF2484E0),
                        Color(0xFF68B7FF)
                    ),
                    center = Offset(c.x - r * 0.08f, c.y - r * 0.08f),
                    radius = r * 1.05f
                ),
                radius = r,
                center = c
            )

            // Tilted luminous golden-yellow & cyan Helix nebula ring
            rotate(degrees = -22f, pivot = c) {
                drawOval(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFFF6D04F).copy(alpha = 0.92f),
                            Color(0xFFFFF4C2).copy(alpha = 0.88f),
                            Color(0xFF73C3FC).copy(alpha = 0.84f),
                            Color(0xFFF6D04F).copy(alpha = 0.90f),
                            Color(0xFF2484E0).copy(alpha = 0.78f),
                            Color(0xFFF6D04F).copy(alpha = 0.92f)
                        ),
                        center = c
                    ),
                    topLeft = Offset(c.x - r * 0.68f, c.y - r * 0.52f),
                    size = Size(r * 1.36f, r * 1.04f),
                    style = Stroke(width = r * 0.28f)
                )
            }

            // Deep cosmic inner eye core + central star
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF091838),
                        Color(0xFF13468B).copy(alpha = 0.85f),
                        Color.Transparent
                    ),
                    center = c,
                    radius = r * 0.46f
                ),
                radius = r * 0.46f,
                center = c
            )

            // Central pinpoint star & upper-right ring glint
            drawStellarGlint(
                center = c,
                radius = r * 0.24f,
                coreColor = Color.White,
                glowColor = Color(0xFF73C3FC)
            )
            drawStellarGlint(
                center = Offset(c.x + r * 0.34f, c.y - r * 0.38f),
                radius = r * 0.28f,
                coreColor = Color.White,
                glowColor = Color(0xFFFFF4C2)
            )
        }
        Text("Yamy", style = rounded(30, 38, FontWeight.ExtraBold, -1.6f), color = MuralColors.Ink)
    }
}

@Composable
fun PageHeading(eyebrow: String, title: String, subtitle: String = "", modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = MuralColors.Secondary, letterSpacing = 1.5.sp)
        Text(title, style = MaterialTheme.typography.headlineLarge)
        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MuralColors.Secondary)
    }
}

/** Motion follows the app lifecycle and Android's system animation setting. */
@Composable
internal fun muralPhase(active: Boolean = true, slow: Boolean = false): Float {
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var foreground by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(
        owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) }
    var animations by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(android.animation.ValueAnimator.areAnimatorsEnabled()) }
    androidx.compose.runtime.DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, _ ->
            foreground = owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
            animations = android.animation.ValueAnimator.areAnimatorsEnabled()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if (!active || !foreground || !animations || chat.mural.BuildConfig.BUILD_TYPE == "uiTest") return 0f
    val phase by rememberInfiniteTransition(label = "Mural motion").animateFloat(
        initialValue = 0f, targetValue = (PI * 40).toFloat(),
        animationSpec = infiniteRepeatable(tween(if (slow) 897_597 else 174_533,
            easing = androidx.compose.animation.core.LinearEasing), RepeatMode.Restart), label = "Mural phase")
    return phase
}

@Composable
fun SoftAnimatedBackground(modifier: Modifier = Modifier, phaseOverride: Float? = null) {
    val phase = phaseOverride ?: muralPhase(slow = true)
    val isDark = MuralColors.isDark
    Canvas(modifier) {
        drawRect(MuralColors.Cream)
        val alphaMultiplier = if (isDark) 0.38f else 0.55f
        drawRect(Brush.radialGradient(listOf(MuralColors.Orange.copy(alpha = .40f * alphaMultiplier), Color.Transparent),
            Offset(size.width * (.32f + sin(phase) * .10f), size.height * .22f), size.width * .85f))
        drawRect(Brush.radialGradient(listOf(MuralColors.YellowAccent.copy(alpha = .35f * alphaMultiplier), Color.Transparent),
            Offset(size.width * (.52f + cos(phase) * .12f), size.height * (.47f + sin(phase) * .04f)), size.width * .9f))
        drawRect(Brush.radialGradient(listOf(MuralColors.WatercolorDeep.copy(alpha = .30f * alphaMultiplier), Color.Transparent),
            Offset(size.width * .95f, size.height * (.56f + cos(phase) * .06f)), size.width * .8f))
    }
}

@Composable
fun MuralOrb(
    energy: Float = 0f,
    listening: Boolean = false,
    active: Boolean = true,
    phaseOverride: Float? = null,
    modifier: Modifier = Modifier
) {
    val phase = phaseOverride ?: muralPhase(active)
    val power = if (android.animation.ValueAnimator.areAnimatorsEnabled()) energy.coerceIn(0f, 1f) else 0f
    val shader = androidx.compose.runtime.remember {
        if (android.os.Build.VERSION.SDK_INT >= 33) OrbMesh() else null
    }
    Canvas(modifier) {
        val side = minOf(size.width, size.height)
        val center = Offset(size.width / 2, size.height / 2 - 5.dp.toPx() + sin(phase * 1.25f) * 4.dp.toPx())
        val orbR = side * 0.47f * (1f + power * 0.045f)

        // Broad feathered cosmic shadow beneath the nebula orb
        withTransform({
            translate(size.width / 2, size.height * .94f); scale(1f, .17f, Offset.Zero)
        }) {
            drawCircle(
                Brush.radialGradient(
                    listOf(
                        MuralColors.Orange.copy(alpha = .22f),
                        MuralColors.YellowAccent.copy(alpha = .08f),
                        Color.Transparent
                    ),
                    center = Offset.Zero,
                    radius = side * .42f
                ),
                side * .42f,
                Offset.Zero
            )
        }

        // Soft outer gaseous nebula corona surrounding the sphere
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF4DA3FF).copy(alpha = 0.22f + power * 0.10f),
                    Color(0xFFF6D04F).copy(alpha = 0.10f + power * 0.06f),
                    Color.Transparent
                ),
                center = center,
                radius = orbR * 1.20f
            ),
            radius = orbR * 1.20f,
            center = center
        )

        if (listening) {
            drawCircle(MuralColors.Orange.copy(alpha = .18f), side * .50f, center, style = Stroke(1.2.dp.toPx()))
            drawCircle(MuralColors.YellowAccent.copy(alpha = .12f), side * .54f, center, style = Stroke(1.dp.toPx()))
        }

        // Characteristic organic spherical boundary path
        val points = (0 until 12).map { index ->
            val a = index / 12f * PI.toFloat() * 2
            val wave = sin(a * 3 + phase) * .018f + cos(a * 2 - phase * .7f) * (.011f + power * .025f)
            val r = side * (.47f + wave) * (1 + power * .045f)
            Offset(center.x + cos(a) * r, center.y + sin(a) * r)
        }
        val path = Path().apply {
            val start = (points.last() + points.first()) / 2f
            moveTo(start.x, start.y)
            points.forEachIndexed { index, p ->
                val next = (p + points[(index + 1) % 12]) / 2f
                quadraticTo(p.x, p.y, next.x, next.y)
            }
            close()
        }

        if (android.os.Build.VERSION.SDK_INT >= 33 && shader != null) {
            drawPath(path, shader.brush(size, phase))
        } else {
            clipPath(path) {
                // Deep cosmic cobalt & cerulean base
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF091838),
                            Color(0xFF13468B),
                            Color(0xFF2484E0),
                            Color(0xFF5AB0FF)
                        ),
                        center = center,
                        radius = orbR * 1.08f
                    )
                )
            }
        }

        // Internal Helix Nebula structural accents & subtle astronomical star glints
        clipPath(path) {
            rotate(degrees = -20f + sin(phase * 0.4f) * 4f, pivot = center) {
                // Luminous golden-yellow & pale-gold Helix inner ring
                drawOval(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFFF6D04F).copy(alpha = 0.62f),
                            Color(0xFFFFF4C2).copy(alpha = 0.58f),
                            Color(0xFFE8AD29).copy(alpha = 0.52f),
                            Color(0xFFF6D04F).copy(alpha = 0.65f),
                            Color(0xFF73C3FC).copy(alpha = 0.42f),
                            Color(0xFFF6D04F).copy(alpha = 0.62f)
                        ),
                        center = center
                    ),
                    topLeft = Offset(center.x - orbR * 0.62f, center.y - orbR * 0.48f),
                    size = Size(orbR * 1.24f, orbR * 0.96f),
                    style = Stroke(width = orbR * 0.22f)
                )

                // Inner ice-cyan cometary rim
                drawOval(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFFBCE8FF).copy(alpha = 0.45f),
                            Color(0xFF73C3FC).copy(alpha = 0.38f),
                            Color(0xFFFFF4C2).copy(alpha = 0.42f),
                            Color(0xFFBCE8FF).copy(alpha = 0.45f)
                        ),
                        center = center
                    ),
                    topLeft = Offset(center.x - orbR * 0.44f, center.y - orbR * 0.35f),
                    size = Size(orbR * 0.88f, orbR * 0.70f),
                    style = Stroke(width = orbR * 0.14f)
                )
            }

            // Fine radial cometary filaments streaming outward from the inner rim
            val filamentCount = 28
            for (i in 0 until filamentCount) {
                val baseAngle = (i.toFloat() / filamentCount) * (PI.toFloat() * 2f) + sin(phase * 0.35f + i * 1.7f) * 0.04f
                val innerR = orbR * (0.31f + (i % 4) * 0.025f)
                val outerR = orbR * (0.58f + ((i * 5) % 5) * 0.035f + power * 0.05f)
                val startPt = Offset(center.x + cos(baseAngle) * innerR, center.y + sin(baseAngle) * innerR * 0.84f)
                val endPt = Offset(center.x + cos(baseAngle) * outerR, center.y + sin(baseAngle) * outerR * 0.84f)
                val filColor = if (i % 3 == 0) Color(0xFFFFF4C2) else Color(0xFFBCE8FF)
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            filColor.copy(alpha = 0.46f),
                            filColor.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        start = startPt,
                        end = endPt
                    ),
                    start = startPt,
                    end = endPt,
                    strokeWidth = (orbR * 0.022f).coerceAtLeast(1.0f),
                    cap = StrokeCap.Round
                )
            }

            // Deep cosmic cobalt-indigo central nebula "eye" core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF091838).copy(alpha = 0.78f),
                        Color(0xFF13468B).copy(alpha = 0.52f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = orbR * 0.38f
                ),
                radius = orbR * 0.38f,
                center = center
            )

            // Subtle stellar glints ("esos toques de brillo estelar sutiles")
            val twinkle1 = 0.80f + 0.20f * sin(phase * 2.1f)
            val twinkle2 = 0.78f + 0.22f * cos(phase * 1.7f + 1.1f)
            val twinkle3 = 0.82f + 0.18f * sin(phase * 1.4f + 2.3f)

            // 1. Central white-dwarf star in the heart of the nebula eye
            drawStellarGlint(
                center = Offset(center.x + orbR * 0.015f, center.y + orbR * 0.01f),
                radius = orbR * 0.095f,
                coreColor = Color.White,
                glowColor = Color(0xFF73C3FC),
                alpha = twinkle1 * 0.92f
            )
            // 2. Luminous star nestled in the upper-right inner ring
            drawStellarGlint(
                center = Offset(center.x + orbR * 0.22f, center.y - orbR * 0.44f),
                radius = orbR * 0.115f,
                coreColor = Color.White,
                glowColor = Color(0xFFBCE8FF),
                alpha = twinkle2
            )
            // 3. Subtle golden-white star on the lower-left outer ring
            drawStellarGlint(
                center = Offset(center.x - orbR * 0.54f, center.y + orbR * 0.40f),
                radius = orbR * 0.10f,
                coreColor = Color(0xFFFFF9DF),
                glowColor = Color(0xFFF6D04F),
                alpha = twinkle3 * 0.90f
            )
            // 4. Subtle star on the lower-right ring
            drawStellarGlint(
                center = Offset(center.x + orbR * 0.42f, center.y + orbR * 0.34f),
                radius = orbR * 0.075f,
                coreColor = Color.White,
                glowColor = Color(0xFFFFF4C2),
                alpha = twinkle1 * 0.82f
            )
            // 5. Micro-stars inside the eye and along the ring
            drawCircle(
                color = Color.White.copy(alpha = 0.75f * twinkle2),
                radius = (orbR * 0.013f).coerceAtLeast(1.0f),
                center = Offset(center.x - orbR * 0.13f, center.y - orbR * 0.14f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.70f * twinkle3),
                radius = (orbR * 0.012f).coerceAtLeast(1.0f),
                center = Offset(center.x + orbR * 0.15f, center.y + orbR * 0.11f)
            )
            drawCircle(
                color = Color(0xFFFFF4C2).copy(alpha = 0.80f * twinkle1),
                radius = (orbR * 0.015f).coerceAtLeast(1.1f),
                center = Offset(center.x - orbR * 0.30f, center.y - orbR * 0.25f)
            )
        }

        // Exterior satellite nebula glints
        val satUpperRight = Offset(center.x + side * .54f, center.y - side * .24f)
        drawStellarGlint(
            center = satUpperRight,
            radius = 7.dp.toPx(),
            coreColor = Color.White,
            glowColor = Color(0xFF73C3FC),
            alpha = 0.90f
        )

        val satLowerLeft = Offset(center.x - side * .54f, center.y + side * .26f)
        drawStellarGlint(
            center = satLowerLeft,
            radius = 5.5.dp.toPx(),
            coreColor = Color(0xFFFFF9DF),
            glowColor = MuralColors.YellowAccent,
            alpha = 0.88f
        )
    }
}

@androidx.annotation.RequiresApi(33)
private class OrbMesh {
    private val shader = android.graphics.RuntimeShader("""
        uniform float2 resolution;
        uniform float phase;

        float hash(float2 p) {
            return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453123);
        }

        float noise(float2 p) {
            float2 i = floor(p);
            float2 f = fract(p);
            float2 u = f * f * (3.0 - 2.0 * f);
            return mix(
                mix(hash(i + float2(0.0, 0.0)), hash(i + float2(1.0, 0.0)), u.x),
                mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x),
                u.y
            );
        }

        float fbm(float2 p) {
            float v = 0.0;
            float a = 0.5;
            for (int i = 0; i < 4; i++) {
                v += a * noise(p);
                p = p * 2.04 + float2(1.7, 9.2);
                a *= 0.5;
            }
            return v;
        }

        half4 main(float2 point) {
            float2 uv = point / resolution;
            float2 c = (uv - float2(0.5, 0.5)) * 2.0;

            // Tilted elliptical coordinates matching the Helix Nebula (-22 deg)
            float2 tilted = float2(
                (c.x * 0.927 + c.y * 0.375) * 0.92,
                (-c.x * 0.375 + c.y * 0.927) * 1.14
            );
            float r = length(tilted);
            float angle = atan(tilted.y, tilted.x);

            // Domain-warped FBM clouds for authentic gaseous nebula texture
            float2 drift = float2(sin(phase * 0.25) * 0.35, cos(phase * 0.20) * 0.35);
            float wx = fbm(c * 2.4 + drift);
            float wy = fbm(c * 2.4 - drift + float2(4.1, 2.3));
            float clouds = fbm(c * 3.0 + float2(wx, wy) * 1.2);
            float fineClouds = fbm(c * 6.5 + float2(wy, wx) * 1.6);

            // Radial cometary streaks around the inner ring
            float angCoord = (angle / 6.2831853 + 0.5) * 42.0;
            float streaks = pow(clamp(fbm(float2(angCoord + wx * 2.5, r * 3.2 - phase * 0.15)) * 1.45 - 0.30, 0.0, 1.0), 1.6);

            float warpedR = r + (clouds - 0.5) * 0.14 + (fineClouds - 0.5) * 0.06;

            // Palette colors (Deep Cosmic Cobalt, Cerulean Blue, Sky Cyan, Warm Golden Yellow)
            float3 voidNavy   = float3(0.035, 0.095, 0.220);
            float3 deepCobalt = float3(0.075, 0.275, 0.545);
            float3 cerulean   = float3(0.141, 0.518, 0.878);
            float3 skyBlue    = float3(0.450, 0.765, 0.990);
            float3 iceCyan    = float3(0.740, 0.910, 1.000);
            float3 amberGold  = float3(0.910, 0.680, 0.160);
            float3 warmGold   = float3(0.965, 0.815, 0.310);
            float3 paleGold   = float3(1.000, 0.955, 0.760);

            // 1. Central Nebula Eye
            float3 color = mix(voidNavy, deepCobalt, smoothstep(0.0, 0.38, warpedR));
            float centerMist = exp(-pow(r / 0.24, 2.0)) * (0.4 + 0.6 * clouds);
            color = mix(color, cerulean, centerMist * 0.48);

            // 2. Inner Cometary Rim (ice-cyan & sky-blue radial knots)
            float innerRimMask = exp(-pow((warpedR - 0.39) / 0.11, 2.0));
            float3 innerRimCol = mix(cerulean, skyBlue, fineClouds);
            innerRimCol = mix(innerRimCol, iceCyan, streaks * 0.85);
            color = mix(color, innerRimCol, clamp(innerRimMask * (0.65 + 0.55 * streaks), 0.0, 1.0));

            // 3. Main Luminous Golden-Yellow Helix Ring
            float goldRingMask = exp(-pow((warpedR - 0.55) / 0.15, 2.0));
            float goldTex = clamp(fineClouds * 0.65 + streaks * 0.55, 0.0, 1.0);
            float3 goldCol = mix(amberGold, warmGold, smoothstep(0.25, 0.65, goldTex));
            goldCol = mix(goldCol, paleGold, smoothstep(0.55, 0.90, goldTex) * exp(-pow((warpedR - 0.50) / 0.10, 2.0)));
            color = mix(color, goldCol, clamp(goldRingMask * (0.74 + 0.36 * clouds), 0.0, 1.0));

            // 4. Outer Billowing Cerulean & Cobalt Nebula Clouds with Golden Wisps
            float outerMask = smoothstep(0.58, 0.88, warpedR);
            float3 outerCol = mix(deepCobalt, cerulean, smoothstep(0.25, 0.75, clouds));
            outerCol = mix(outerCol, skyBlue, smoothstep(0.60, 0.90, fineClouds) * 0.52);
            outerCol = mix(outerCol, warmGold, smoothstep(0.52, 0.85, clouds * fineClouds * 1.6) * 0.58);
            color = mix(color, outerCol, clamp(outerMask * 0.84, 0.0, 1.0));

            // Subtle upper-left spherical sheen
            float2 glow = (uv - float2(0.32, 0.26)) / float2(0.28, 0.20);
            color = mix(color, iceCyan, 0.16 * exp(-dot(glow, glow) * 1.5));

            return half4(color, 1.0);
        }
    """.trimIndent())
    private val cachedBrush = androidx.compose.ui.graphics.ShaderBrush(shader)
    fun brush(size: Size, phase: Float): Brush {
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("phase", phase)
        return cachedBrush
    }
}

@Composable
fun RecallBars(count: Int, modifier: Modifier = Modifier) {
    val desc = stringResource(R.string.words_recall_bars_desc, count)
    Row(
        modifier.semantics { contentDescription = desc },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(3) { index ->
            Spacer(
                Modifier
                    .size(width = 18.dp, height = 6.dp)
                    .background(if (index < count) MuralColors.Orange else MuralColors.WatercolorWash, CircleShape),
            )
        }
    }
}
