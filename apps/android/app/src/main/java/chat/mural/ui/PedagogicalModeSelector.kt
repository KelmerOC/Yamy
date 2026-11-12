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
 * Clean pedagogical mode selector without emojis.
 * In rest state: Vector icon of active mode (AcademicCap / ChatBubbles) + ChevronDown, placed next to the mic.
 * In open state: Elevated popover with rounded corners and labeled mode options.
 */
@Composable
fun PedagogicalModeSelector(
    currentMode: String,
    onSelectMode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isEducation = currentMode == TopicEcosystemStore.MODE_EDUCATION
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            shape = CircleShape,
            color = if (isEducation) MuralColors.Orange.copy(alpha = 0.18f) else MuralColors.Sage.copy(alpha = 0.28f),
            border = BorderStroke(1.dp, MuralColors.Ink.copy(alpha = 0.10f)),
            modifier = Modifier
                .clip(CircleShape)
                .clickable(role = Role.Button) { expanded = true }
                .testTag("pedagogical-mode-selector")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MuralIcon(
                    symbol = if (isEducation) MuralSymbol.AcademicCap else MuralSymbol.ChatBubbles,
                    modifier = Modifier.size(19.dp),
                    color = MuralColors.Ink
                )
                MuralIcon(
                    symbol = MuralSymbol.ChevronDown,
                    modifier = Modifier.size(12.dp),
                    color = MuralColors.Secondary
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
                .widthIn(min = 180.dp)
                .testTag("pedagogical-mode-dropdown")
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.theme_mode_education),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isEducation) FontWeight.Bold else FontWeight.Normal,
                        color = MuralColors.Ink
                    )
                },
                leadingIcon = {
                    MuralIcon(
                        MuralSymbol.AcademicCap,
                        modifier = Modifier.size(20.dp),
                        color = if (isEducation) MuralColors.Orange else MuralColors.Secondary
                    )
                },
                trailingIcon = if (isEducation) ({
                    MuralIcon(MuralSymbol.Check, modifier = Modifier.size(16.dp), color = MuralColors.Orange)
                }) else null,
                onClick = {
                    onSelectMode(TopicEcosystemStore.MODE_EDUCATION)
                    expanded = false
                },
                colors = MenuDefaults.itemColors(
                    textColor = MuralColors.Ink
                )
            )

            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.theme_mode_conversation),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (!isEducation) FontWeight.Bold else FontWeight.Normal,
                        color = MuralColors.Ink
                    )
                },
                leadingIcon = {
                    MuralIcon(
                        MuralSymbol.ChatBubbles,
                        modifier = Modifier.size(20.dp),
                        color = if (!isEducation) MuralColors.Secondary else MuralColors.Secondary
                    )
                },
                trailingIcon = if (!isEducation) ({
                    MuralIcon(MuralSymbol.Check, modifier = Modifier.size(16.dp), color = MuralColors.Ink)
                }) else null,
                onClick = {
                    onSelectMode(TopicEcosystemStore.MODE_CONVERSATION)
                    expanded = false
                },
                colors = MenuDefaults.itemColors(
                    textColor = MuralColors.Ink
                )
            )
        }
    }
}
