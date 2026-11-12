package chat.mural.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chat.mural.R

val DefaultSessionDurationOptions = listOf(5, 10, 15, 20, 30, 60)

/**
 * Flat pill dropdown allowing the learner to pick the target call duration directly from the main
 * voice call screen instead of navigating to Settings.
 */
@Composable
fun SessionDurationSelector(
    currentMinutes: Int,
    onSelectMinutes: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = remember(currentMinutes) {
        (DefaultSessionDurationOptions + currentMinutes).distinct().sorted()
    }
    val accessibilityLabel = stringResource(R.string.talk_duration_selector_desc, currentMinutes)

    Box(modifier = modifier) {
        Surface(
            shape = CircleShape,
            color = MuralColors.Ink.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, MuralColors.Ink.copy(alpha = 0.10f)),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
            modifier = Modifier
                .alpha(if (enabled) 1f else 0.65f)
                .clip(CircleShape)
                .clickable(enabled = enabled, role = Role.Button) { expanded = true }
                .semantics { contentDescription = accessibilityLabel }
                .testTag("session-duration-selector"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MuralIcon(
                    symbol = MuralSymbol.Clock,
                    modifier = Modifier.size(13.dp),
                    color = MuralColors.Secondary,
                )
                Text(
                    text = stringResource(R.string.talk_duration_chip_format, currentMinutes),
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                    color = MuralColors.Secondary,
                    maxLines = 1,
                    softWrap = false,
                )
                if (enabled) {
                    MuralIcon(
                        symbol = MuralSymbol.ChevronDown,
                        modifier = Modifier.size(11.dp),
                        color = MuralColors.Secondary,
                    )
                }
            }
        }

        DropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = MuralColors.Surface,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, MuralColors.Ink.copy(alpha = 0.08f)),
            modifier = Modifier
                .widthIn(min = 148.dp)
                .testTag("session-duration-dropdown"),
        ) {
            options.forEach { minutes ->
                val isSelected = minutes == currentMinutes
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.talk_duration_option_format, minutes),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = MuralColors.Ink,
                        )
                    },
                    trailingIcon = if (isSelected) ({
                        MuralIcon(MuralSymbol.Check, modifier = Modifier.size(16.dp), color = MuralColors.Ink)
                    }) else null,
                    onClick = {
                        expanded = false
                        onSelectMinutes(minutes)
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = MuralColors.Ink,
                    ),
                    modifier = Modifier.testTag("session-duration-option-$minutes"),
                )
            }
        }
    }
}
