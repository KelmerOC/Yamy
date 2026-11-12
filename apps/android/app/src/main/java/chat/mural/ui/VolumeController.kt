package chat.mural.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chat.mural.R
import kotlin.math.roundToInt

/**
 * Compact, expandable volume controller for Yamy's independent voice output.
 * Styled to match MuralColors and soft rounded controls without altering system/media volume.
 */
@Composable
fun YamyVolumePill(
    appVolume: Float,
    onVolumeChange: (Float) -> Unit,
    mediaOutputEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val clampedVolume = appVolume.coerceIn(0f, 1f)
    val percent = (clampedVolume * 100f).roundToInt()
    val isMuted = clampedVolume <= 0.005f

    var expanded by remember { mutableStateOf(false) }
    var previousNonZeroVolume by remember {
        mutableFloatStateOf(if (clampedVolume > 0.01f) clampedVolume else 1f)
    }
    if (clampedVolume > 0.01f) {
        previousNonZeroVolume = clampedVolume
    }

    val volumeSymbol = when {
        isMuted -> MuralSymbol.VolumeMuted
        clampedVolume <= 0.5f -> MuralSymbol.VolumeLow
        else -> MuralSymbol.VolumeHigh
    }
    val iconTint = if (isMuted) MuralColors.Secondary else MuralColors.Orange

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier
                .testTag("yamy-volume-pill")
                .clickable { expanded = !expanded },
            shape = RoundedCornerShape(999.dp),
            color = MuralColors.Surface.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, MuralColors.ControlBorder),
            tonalElevation = 1.dp,
            shadowElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MuralIcon(
                    symbol = volumeSymbol,
                    color = iconTint,
                    modifier = Modifier.size(16.dp),
                    description = stringResource(R.string.volume_yamy_pill_label),
                )
                Text(
                    text = stringResource(R.string.volume_yamy_percent_format, percent),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MuralColors.Ink,
                )
                if (mediaOutputEnabled) {
                    MuralIcon(
                        symbol = MuralSymbol.SpeakerOutput,
                        color = MuralColors.Secondary,
                        modifier = Modifier.size(14.dp),
                        description = stringResource(R.string.settings_media_output_title),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MuralColors.Surface,
                border = BorderStroke(1.dp, MuralColors.ControlBorder),
                tonalElevation = 2.dp,
                shadowElevation = 4.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.volume_yamy_pill_label),
                                style = MaterialTheme.typography.titleSmall,
                                color = MuralColors.Ink,
                            )
                            Text(
                                text = stringResource(R.string.volume_yamy_independent_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MuralColors.Secondary,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Surface(
                            modifier = Modifier
                                .testTag("yamy-volume-mute-toggle")
                                .clickable {
                                    if (isMuted) {
                                        onVolumeChange(previousNonZeroVolume.coerceIn(0.15f, 1f))
                                    } else {
                                        previousNonZeroVolume = clampedVolume
                                        onVolumeChange(0f)
                                    }
                                },
                            shape = RoundedCornerShape(999.dp),
                            color = if (isMuted) MuralColors.WatercolorWash else MuralColors.CreamRaised,
                            border = BorderStroke(
                                1.dp,
                                if (isMuted) MuralColors.Orange else MuralColors.ControlBorder,
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                MuralIcon(
                                    symbol = if (isMuted) MuralSymbol.VolumeHigh else MuralSymbol.VolumeMuted,
                                    color = if (isMuted) MuralColors.Orange else MuralColors.Ink,
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    text = stringResource(
                                        if (isMuted) R.string.volume_yamy_unmute_ai
                                        else R.string.volume_yamy_mute_ai
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isMuted) MuralColors.Orange else MuralColors.Ink,
                                )
                            }
                        }
                    }

                    Slider(
                        value = clampedVolume,
                        onValueChange = { next ->
                            val clamped = next.coerceIn(0f, 1f)
                            if (clamped > 0.01f) {
                                previousNonZeroVolume = clamped
                            }
                            onVolumeChange(clamped)
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("yamy-volume-slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = MuralColors.Orange,
                            activeTrackColor = MuralColors.Orange,
                            inactiveTrackColor = MuralColors.WatercolorWash,
                        ),
                    )
                }
            }
        }
    }
}
