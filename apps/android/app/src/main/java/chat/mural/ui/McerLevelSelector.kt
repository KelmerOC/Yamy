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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chat.mural.R
import chat.mural.core.TopicEcosystemStore

/**
 * Compact, interactive CEFR (MCER) level selector pill.
 * In rest state: Compact pill displaying the active level (e.g., "B1") with ChevronDown,
 * with single-line non-wrapping guarantee to prevent visual squishing/line-breaking.
 * In open state: Elevated popover with rounded corners showing all CEFR levels (A1 to C2).
 */
@Composable
fun McerLevelSelector(
    currentLevel: String,
    onSelectLevel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            shape = CircleShape,
            color = MuralColors.Ink.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, MuralColors.Ink.copy(alpha = 0.10f)),
            modifier = Modifier
                .clip(CircleShape)
                .clickable(role = Role.Button) { expanded = true }
                .testTag("mcer-level-selector")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = currentLevel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MuralColors.Secondary,
                    maxLines = 1,
                    softWrap = false
                )
                MuralIcon(
                    symbol = MuralSymbol.ChevronDown,
                    modifier = Modifier.size(11.dp),
                    color = MuralColors.Secondary.copy(alpha = 0.75f)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = MuralColors.Surface,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, MuralColors.Ink.copy(alpha = 0.08f)),
            modifier = Modifier
                .widthIn(min = 190.dp)
                .testTag("mcer-level-dropdown")
        ) {
            TopicEcosystemStore.MCER_LEVELS.forEach { level ->
                val isSelected = level.equals(currentLevel, ignoreCase = true)
                val labelRes = when (level) {
                    "A1" -> R.string.mcer_level_a1
                    "A2" -> R.string.mcer_level_a2
                    "B1" -> R.string.mcer_level_b1
                    "B2" -> R.string.mcer_level_b2
                    "C1" -> R.string.mcer_level_c1
                    "C2" -> R.string.mcer_level_c2
                    else -> null
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (labelRes != null) stringResource(labelRes) else level,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = MuralColors.Ink
                        )
                    },
                    trailingIcon = if (isSelected) ({
                        MuralIcon(MuralSymbol.Check, modifier = Modifier.size(16.dp), color = MuralColors.Ink)
                    }) else null,
                    onClick = {
                        onSelectLevel(level)
                        expanded = false
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = MuralColors.Ink
                    )
                )
            }
        }
    }
}
