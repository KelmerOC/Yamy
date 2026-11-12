package chat.mural.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import chat.mural.core.TopicEcosystemStore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import chat.mural.MuralViewModel
import chat.mural.R
import chat.mural.core.ConversationTheme
import chat.mural.core.localized
import chat.mural.core.TopicBrief
import chat.mural.core.WordState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TopicsScreen(vm: MuralViewModel, onChoose: () -> Unit, onCurrentTopic: (String) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("All") }
    var createCustom by rememberSaveable { mutableStateOf(false) }
    var selectedThemeForDetail by remember { mutableStateOf<ConversationTheme?>(null) }
    val favorites = vm.favoriteThemeIDs
    val customIds = remember(vm.customThemes) { vm.customThemes.map { it.id }.toSet() }
    val customOrder = remember(vm.customThemes) { vm.customThemes.mapIndexed { idx, t -> t.id to idx }.toMap() }
    val allThemes = vm.allThemes
    val localizedThemes = remember(allThemes, vm.appLanguage, vm.themeTranslationsVersion) {
        allThemes.map { it.localized(vm.appLanguage) }
    }
    val categories = remember(vm.language.id, localizedThemes) {
        (listOf("All", "Favorites", "Personalizados") + localizedThemes.map { it.category }.filterNot { it == "Personalizados" || it == "Custom" }).distinct()
    }
    val themes = remember(vm.language.id, category, search, favorites, localizedThemes, customIds, customOrder) {
        localizedThemes.filter {
            (category == "All" ||
                (category == "Favorites" && it.id in favorites) ||
                (category == "Personalizados" && (it.id in customIds || it.category == "Personalizados" || it.category == "Custom")) ||
                it.category == category) &&
                (search.isBlank() || it.title.contains(search, true) || it.subtitle.contains(search, true) || it.category.contains(search, true))
        }.sortedWith(
            compareByDescending<ConversationTheme> { it.id in customIds }
                .thenBy { customOrder[it.id] ?: Int.MAX_VALUE }
                .thenByDescending { it.id in favorites }
                .thenBy { it.title }
        )
    }

    LazyVerticalGrid(columns = GridCells.Adaptive(if (LocalDensity.current.fontScale > 1.3f) 260.dp else 150.dp),
        modifier = Modifier.fillMaxSize().testTag("topics-screen").padding(horizontal = 24.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            MuralSearchField(search, { search = it.take(80) }, stringResource(R.string.topics_search_placeholder))
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            PageHeading(stringResource(R.string.topics_eyebrow), stringResource(R.string.topics_title),
                stringResource(R.string.topics_subtitle), Modifier.padding(top = 20.dp, bottom = 10.dp))
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Surface(Modifier.fillMaxWidth().clickable(role = Role.Button) { if (!vm.hasKey) vm.openMinuteAccess() else vm.requestChooseTheme(null) { onChoose() } },
                shape = RoundedCornerShape(26.dp), color = MuralColors.Surface.copy(alpha = .85f)) {
                Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MuralIcon(MuralSymbol.Wave)
                    Text(stringResource(R.string.topics_just_talk), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    MuralIcon(MuralSymbol.ChevronRight, Modifier.size(19.dp))
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Surface(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { if (!vm.hasKey) vm.openMinuteAccess() else createCustom = true },
                shape = RoundedCornerShape(26.dp),
                color = if (MuralColors.isDark) MuralColors.Butter else MuralColors.Butter.copy(alpha = .75f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MuralColors.YellowAccent.copy(alpha = if (MuralColors.isDark) 0.35f else 0.45f))
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    MuralIcon(MuralSymbol.Sparkles, Modifier.size(24.dp), color = MuralColors.Orange)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.topics_create_custom), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.topics_create_custom_subtitle), style = MaterialTheme.typography.bodySmall, color = MuralColors.Secondary)
                    }
                    MuralIcon(MuralSymbol.ChevronRight, Modifier.size(19.dp))
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { item ->
                    FilterChip(selected = category == item, onClick = { category = item }, label = { Text(categoryLabel(item), style = MaterialTheme.typography.bodySmall) },
                        shape = CircleShape, border = null,
                        colors = FilterChipDefaults.filterChipColors(containerColor = MuralColors.Surface.copy(alpha = .70f),
                            selectedContainerColor = MuralColors.WatercolorWash, selectedLabelColor = MuralColors.Ink),
                        modifier = Modifier.height(44.dp))
                }
            }
        }
        itemsIndexed(themes, key = { _, theme -> theme.id }) { index, theme ->
            ThemeCard(
                theme = theme,
                index = index,
                isFavorite = theme.id in favorites,
                currentMcer = vm.getThemeMcerLevel(theme.id),
                onToggleFavorite = { vm.toggleFavoriteTheme(theme.id) }
            ) {
                if (theme.id == "today") { if (!vm.hasKey) vm.openMinuteAccess() else createCustom = true }
                else selectedThemeForDetail = theme
            }
        }
        if (themes.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Text(stringResource(R.string.topics_no_results), color = MuralColors.Secondary, modifier = Modifier.padding(vertical = 28.dp))
        }
    }
    if (createCustom) {
        CreateCustomThemeDialog(
            vm = vm,
            onDismiss = { createCustom = false },
            onStartVoice = { theme -> if (!vm.hasKey) vm.openMinuteAccess() else
                vm.requestChooseTheme(theme) {
                    createCustom = false
                    onChoose()
                }
            },
            onStartWritten = { theme -> if (!vm.hasKey) vm.openMinuteAccess() else
                vm.requestChooseTheme(theme) {
                    vm.startWritten(theme)
                    createCustom = false
                    onChoose()
                }
            }
        )
    }
    selectedThemeForDetail?.let { theme ->
        ThemeDetailDialog(
            theme = theme,
            vm = vm,
            onDismiss = { selectedThemeForDetail = null },
            onStartVoice = { if (!vm.hasKey) vm.openMinuteAccess() else

                vm.requestChooseTheme(theme) {
                    selectedThemeForDetail = null
                    onChoose()
                }
            },
            onStartWritten = { if (!vm.hasKey) vm.openMinuteAccess() else
                vm.requestChooseTheme(theme) {
                    vm.startWritten(theme)
                    selectedThemeForDetail = null
                    onChoose()
                }
            }
        )
    }
}

@Composable
private fun ThemeCard(
    theme: ConversationTheme,
    index: Int,
    isFavorite: Boolean,
    currentMcer: String,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        color = MuralColors.Panels.getOrElse(theme.colorIndex) { MuralColors.Panels[index % MuralColors.Panels.size] },
    ) {
        Column(Modifier.fillMaxWidth().heightIn(min = 180.dp).padding(19.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                MuralIcon(themeSymbol(theme.id), Modifier.size(31.dp), color = MuralColors.CardSecondary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MuralColors.CardInk.copy(alpha = 0.08f)) {
                        Text(currentMcer, color = MuralColors.CardInk, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onToggleFavorite),
                        contentAlignment = Alignment.Center
                    ) {
                        MuralIcon(
                            MuralSymbol.Heart,
                            Modifier.size(20.dp),
                            color = if (isFavorite) MuralColors.Orange else MuralColors.CardSecondary.copy(alpha = .45f),
                            filled = isFavorite,
                            description = stringResource(R.string.topics_favorite_toggle)
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(theme.title, color = MuralColors.CardInk, style = MaterialTheme.typography.titleMedium)
                Text(theme.subtitle, color = MuralColors.CardSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun ThemeDetailDialog(
    theme: ConversationTheme,
    vm: MuralViewModel,
    onDismiss: () -> Unit,
    onStartVoice: () -> Unit,
    onStartWritten: () -> Unit
) {
    var isFav by rememberSaveable(theme.id) { mutableStateOf(vm.isThemeFavorite(theme.id)) }
    var selectedMcer by rememberSaveable(theme.id) { mutableStateOf(vm.getThemeMcerLevel(theme.id)) }
    var selectedPersonality by rememberSaveable(theme.id) { mutableStateOf(vm.getThemePersonality(theme.id)) }
    var selectedPedagogicalMode by rememberSaveable(theme.id) { mutableStateOf(vm.getThemePedagogicalMode(theme.id)) }
    val pastSessions = remember(theme.id) { vm.getSessionsForTheme(theme.id) }
    val structuredMemory = remember(theme.id) { vm.getThemeMemory(theme.id) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MuralColors.Surface) {
            LazyColumn(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                            MuralIcon(themeSymbol(theme.id), Modifier.size(32.dp), color = MuralColors.Secondary)
                            Column {
                                Text(theme.title, style = MaterialTheme.typography.titleLarge)
                                Text(categoryLabel(theme.category), style = MaterialTheme.typography.bodySmall, color = MuralColors.Secondary)
                            }
                        }
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(MuralColors.Surface.copy(alpha = 0.8f))
                                .clickable(role = Role.Button) {
                                    isFav = vm.toggleFavoriteTheme(theme.id)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            MuralIcon(
                                MuralSymbol.Heart,
                                Modifier.size(24.dp),
                                color = if (isFav) MuralColors.Orange else MuralColors.Secondary.copy(alpha = 0.5f),
                                filled = isFav,
                                description = stringResource(R.string.topics_favorite_toggle)
                            )
                        }
                    }
                }
                item {
                    Text(theme.situation, style = MaterialTheme.typography.bodyMedium, color = MuralColors.Ink.copy(alpha = 0.85f))
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.theme_mcer_label), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(TopicEcosystemStore.MCER_LEVELS) { level ->
                                FilterChip(
                                    selected = selectedMcer == level,
                                    onClick = {
                                        selectedMcer = level
                                        vm.setThemeMcerLevel(theme.id, level)
                                    },
                                    label = { Text(level, fontWeight = if (selectedMcer == level) FontWeight.Bold else FontWeight.Normal) },
                                    shape = CircleShape,
                                    border = null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MuralColors.Surface.copy(alpha = .70f),
                                        selectedContainerColor = MuralColors.Orange,
                                        selectedLabelColor = MuralColors.Ink
                                    )
                                )
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.theme_personality_label), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                        val personalities = listOf(
                            TopicEcosystemStore.PERSONALITY_FRIENDLY to R.string.theme_personality_friendly,
                            TopicEcosystemStore.PERSONALITY_PROFESSIONAL to R.string.theme_personality_professional,
                            TopicEcosystemStore.PERSONALITY_CASUAL to R.string.theme_personality_casual,
                            TopicEcosystemStore.PERSONALITY_BUSY to R.string.theme_personality_busy
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(personalities) { (id, resId) ->
                                FilterChip(
                                    selected = selectedPersonality == id,
                                    onClick = {
                                        selectedPersonality = id
                                        vm.setThemePersonality(theme.id, id)
                                    },
                                    label = { Text(stringResource(resId)) },
                                    shape = CircleShape,
                                    border = null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MuralColors.Surface.copy(alpha = .70f),
                                        selectedContainerColor = MuralColors.Peach,
                                        selectedLabelColor = MuralColors.Ink
                                    )
                                )
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.theme_pedagogical_mode_label), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                        val modes = listOf(
                            TopicEcosystemStore.MODE_EDUCATION to R.string.theme_mode_education,
                            TopicEcosystemStore.MODE_CONVERSATION to R.string.theme_mode_conversation
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(modes) { (id, resId) ->
                                FilterChip(
                                    selected = selectedPedagogicalMode == id,
                                    onClick = {
                                        selectedPedagogicalMode = id
                                        vm.setThemePedagogicalMode(theme.id, id)
                                    },
                                    label = { Text(stringResource(resId)) },
                                    shape = CircleShape,
                                    border = null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MuralColors.Surface.copy(alpha = .70f),
                                        selectedContainerColor = MuralColors.Butter,
                                        selectedLabelColor = MuralColors.Ink
                                    )
                                )
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.theme_history_title), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                        if (!structuredMemory.isNullOrBlank()) {
                            Surface(shape = RoundedCornerShape(14.dp), color = MuralColors.Cream.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    structuredMemory.lines().filter { it.isNotBlank() }.forEach { line ->
                                        val colonIndex = line.indexOf(':')
                                        if (colonIndex != -1) {
                                            val header = line.substring(0, colonIndex).trim()
                                            val body = line.substring(colonIndex + 1).trim()
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(header, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MuralColors.Secondary)
                                                Text(body, style = MaterialTheme.typography.bodySmall, color = MuralColors.Ink)
                                            }
                                        } else {
                                            Text(line, style = MaterialTheme.typography.bodySmall, color = MuralColors.Ink)
                                        }
                                    }
                                }
                            }
                        }
                        if (pastSessions.isEmpty()) {
                            Text(stringResource(R.string.theme_history_empty), style = MaterialTheme.typography.bodySmall, color = MuralColors.Secondary)
                        } else {
                            pastSessions.take(3).forEach { session ->
                                val durationMinutes = ((session.endedAt?.let { it - session.startedAt } ?: session.voiceSeconds) / 60.0).toInt().coerceAtLeast(1)
                                Text(
                                    stringResource(R.string.theme_history_session_item, formatDate(session.startedAt), durationMinutes, session.passages.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MuralColors.Secondary
                                )
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onStartVoice,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MuralColors.Orange, contentColor = MuralColors.Ink)
                        ) {
                            MuralIcon(MuralSymbol.Mic, modifier = Modifier.size(18.dp), color = MuralColors.Ink)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.theme_start_voice), fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = onStartWritten,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MuralColors.Sage.copy(alpha = 0.5f), contentColor = MuralColors.Ink)
                        ) {
                            MuralIcon(MuralSymbol.Keyboard, modifier = Modifier.size(18.dp), color = MuralColors.Ink)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.theme_start_written), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                item {
                    MuralTextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.common_close))
                    }
                }
            }
        }
    }
}

@Composable
internal fun CreateCustomThemeDialog(
    vm: MuralViewModel,
    initialDraft: MuralViewModel.GeneratedCustomTheme? = null,
    onSavedOrDismissed: ((ConversationTheme?) -> Unit)? = null,
    onDismiss: () -> Unit,
    onStartVoice: (ConversationTheme) -> Unit,
    onStartWritten: (ConversationTheme) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var generatedDraft by remember(initialDraft) { mutableStateOf(initialDraft) }
    var isFav by remember(initialDraft) { mutableStateOf(initialDraft?.let { vm.isThemeFavorite(it.theme.id) } ?: false) }
    var selectedMcer by remember(initialDraft) { mutableStateOf(initialDraft?.mcerLevel ?: TopicEcosystemStore.DEFAULT_MCER) }
    var selectedPersonality by remember(initialDraft) { mutableStateOf(initialDraft?.personality ?: TopicEcosystemStore.DEFAULT_PERSONALITY) }
    var selectedPedagogicalMode by remember(initialDraft) {
        mutableStateOf(initialDraft?.let { vm.getThemePedagogicalMode(it.theme.id) } ?: TopicEcosystemStore.DEFAULT_PEDAGOGICAL_MODE)
    }

    LaunchedEffect(initialDraft) {
        initialDraft?.let {
            vm.saveCustomTheme(it.theme)
        }
    }

    Dialog(onDismissRequest = {
        onSavedOrDismissed?.invoke(generatedDraft?.theme)
        onDismiss()
    }) {
        Surface(shape = RoundedCornerShape(28.dp), color = MuralColors.Surface) {
            LazyColumn(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    PageHeading(
                        stringResource(R.string.topics_create_dialog_eyebrow),
                        stringResource(R.string.topics_create_dialog_title),
                        stringResource(R.string.topics_create_dialog_subtitle)
                    )
                }

                if (generatedDraft == null) {
                    item {
                        MuralTextField(
                            query,
                            { query = it.take(200) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.topics_create_placeholder)) },
                            minLines = 3,
                            maxLines = 5,
                        )
                    }
                    item {
                        Button(
                            onClick = {
                                if (!vm.hasKey) { vm.openMinuteAccess() } else vm.createCustomTheme(query) { draft ->
                                    vm.saveCustomTheme(draft.theme)
                                    generatedDraft = draft
                                    selectedMcer = draft.mcerLevel
                                    selectedPersonality = draft.personality
                                    selectedPedagogicalMode = vm.getThemePedagogicalMode(draft.theme.id)
                                    isFav = vm.isThemeFavorite(draft.theme.id)
                                }
                            },
                            enabled = query.isNotBlank() && !vm.working,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MuralColors.Orange, contentColor = MuralColors.Ink)
                        ) {
                            if (vm.working) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MuralColors.Ink)
                                Text("  " + stringResource(R.string.topics_creating_button))
                            } else {
                                Text(stringResource(R.string.topics_create_button), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } else {
                    val draft = generatedDraft!!
                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MuralColors.Sage.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(draft.theme.title, style = MaterialTheme.typography.titleLarge)
                                        Text(stringResource(R.string.topics_generated_eyebrow), style = MaterialTheme.typography.labelSmall, color = MuralColors.Secondary)
                                    }
                                    Box(
                                        Modifier.size(40.dp).clip(CircleShape).background(MuralColors.Surface.copy(alpha = 0.8f))
                                            .clickable(role = Role.Button) {
                                                isFav = vm.toggleFavoriteTheme(draft.theme.id)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        MuralIcon(
                                            MuralSymbol.Heart,
                                            Modifier.size(24.dp),
                                            color = if (isFav) MuralColors.Orange else MuralColors.Secondary.copy(alpha = 0.5f),
                                            filled = isFav,
                                            description = stringResource(R.string.topics_favorite_toggle)
                                        )
                                    }
                                }
                                Text(draft.theme.situation, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.theme_mcer_label), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(TopicEcosystemStore.MCER_LEVELS) { level ->
                                    FilterChip(
                                        selected = selectedMcer == level,
                                        onClick = {
                                            selectedMcer = level
                                            vm.setThemeMcerLevel(draft.theme.id, level)
                                        },
                                        label = { Text(level, fontWeight = if (selectedMcer == level) FontWeight.Bold else FontWeight.Normal) },
                                        shape = CircleShape,
                                        border = null,
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = MuralColors.Surface.copy(alpha = .70f),
                                            selectedContainerColor = MuralColors.Orange,
                                            selectedLabelColor = MuralColors.Ink
                                        )
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.theme_personality_label), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                            val personalities = listOf(
                                TopicEcosystemStore.PERSONALITY_FRIENDLY to R.string.theme_personality_friendly,
                                TopicEcosystemStore.PERSONALITY_PROFESSIONAL to R.string.theme_personality_professional,
                                TopicEcosystemStore.PERSONALITY_CASUAL to R.string.theme_personality_casual,
                                TopicEcosystemStore.PERSONALITY_BUSY to R.string.theme_personality_busy
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(personalities) { (id, resId) ->
                                    FilterChip(
                                        selected = selectedPersonality == id,
                                        onClick = {
                                            selectedPersonality = id
                                            vm.setThemePersonality(draft.theme.id, id)
                                        },
                                        label = { Text(stringResource(resId)) },
                                        shape = CircleShape,
                                        border = null,
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = MuralColors.Surface.copy(alpha = .70f),
                                            selectedContainerColor = MuralColors.Peach,
                                            selectedLabelColor = MuralColors.Ink
                                        )
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.theme_pedagogical_mode_label), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                            val modes = listOf(
                                TopicEcosystemStore.MODE_EDUCATION to R.string.theme_mode_education,
                                TopicEcosystemStore.MODE_CONVERSATION to R.string.theme_mode_conversation
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(modes) { (id, resId) ->
                                    FilterChip(
                                        selected = selectedPedagogicalMode == id,
                                        onClick = {
                                            selectedPedagogicalMode = id
                                            vm.setThemePedagogicalMode(draft.theme.id, id)
                                        },
                                        label = { Text(stringResource(resId)) },
                                        shape = CircleShape,
                                        border = null,
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = MuralColors.Surface.copy(alpha = .70f),
                                            selectedContainerColor = MuralColors.Butter,
                                            selectedLabelColor = MuralColors.Ink
                                        )
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    vm.saveCustomTheme(draft.theme)
                                    vm.chooseTheme(draft.theme)
                                    onSavedOrDismissed?.invoke(draft.theme)
                                    onStartVoice(draft.theme)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MuralColors.Orange, contentColor = MuralColors.Ink)
                            ) {
                                MuralIcon(MuralSymbol.Mic, modifier = Modifier.size(18.dp), color = MuralColors.Ink)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.theme_start_voice), fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = {
                                    vm.saveCustomTheme(draft.theme)
                                    vm.chooseTheme(draft.theme)
                                    onSavedOrDismissed?.invoke(draft.theme)
                                    onStartWritten(draft.theme)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MuralColors.Sage.copy(alpha = 0.5f), contentColor = MuralColors.Ink)
                            ) {
                                MuralIcon(MuralSymbol.Keyboard, modifier = Modifier.size(18.dp), color = MuralColors.Ink)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.theme_start_written), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                vm.saveCustomTheme(draft.theme)
                                vm.chooseTheme(draft.theme)
                                onSavedOrDismissed?.invoke(draft.theme)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MuralColors.Surface.copy(alpha = .85f), contentColor = MuralColors.Ink)
                        ) {
                            Text(stringResource(R.string.topics_save_only))
                        }
                    }
                }

                item {
                    MuralTextButton(
                        onClick = {
                            onSavedOrDismissed?.invoke(generatedDraft?.theme)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.common_close))
                    }
                }
            }
        }
    }
}

@Composable
fun WordsScreen(vm: MuralViewModel) {
    var search by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<WordState?>(null) }
    val words = vm.learner.words.filter { search.isBlank() || it.lemma.contains(search, true) || it.meaning.contains(search, true) }
    LazyColumn(
        Modifier.fillMaxSize().testTag("words-screen").padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        item { PageHeading(stringResource(R.string.words_eyebrow, vm.language.localizedDisplayName(vm.appLanguage)), stringResource(R.string.words_title), stringResource(R.string.words_subtitle), Modifier.padding(top = 20.dp)) }
        item {
            MuralSearchField(search, { search = it.take(80) }, stringResource(R.string.words_search_placeholder))
        }
        if (words.isEmpty()) item {
            Surface(shape = RoundedCornerShape(28.dp), color = MuralColors.Sage) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MuralIcon(MuralSymbol.Leaf, Modifier.size(34.dp), color = MuralColors.Secondary)
                    Text(stringResource(if (search.isBlank()) R.string.words_empty_title_blank else R.string.words_empty_title_search), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(if (search.isBlank()) R.string.words_empty_subtitle_blank else R.string.words_empty_subtitle_search),
                        color = MuralColors.Secondary,
                    )
                }
            }
        } else items(words, key = { it.id }) { word ->
            Surface(Modifier.fillMaxWidth().clickable { selected = word }, shape = RoundedCornerShape(20.dp), color = MuralColors.Surface) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(word.lemma, style = MaterialTheme.typography.titleLarge)
                        Text(word.meaning, color = MuralColors.Secondary)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        RecallBars(word.bars)
                        Text(wordLabel(word.label), style = MaterialTheme.typography.labelSmall, color = MuralColors.Secondary)
                    }
                }
            }
        }
        item {
            Text(stringResource(R.string.words_recall_note), style = MaterialTheme.typography.bodySmall, color = MuralColors.Secondary)
        }
        if (vm.learner.capabilities.isNotEmpty()) item {
            Surface(shape = RoundedCornerShape(24.dp), color = MuralColors.Butter) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(stringResource(R.string.words_capabilities_title), style = MaterialTheme.typography.titleLarge)
                    vm.learner.capabilities.forEach { Text(it) }
                    Text(stringResource(R.string.words_capabilities_note), style = MaterialTheme.typography.bodySmall, color = MuralColors.Secondary)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    selected?.let { word -> WordDialog(word, onRemove = { vm.hideWord(word.id); selected = null }, onDismiss = { selected = null }) }
}

@Composable
private fun WordDialog(word: WordState, onRemove: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MuralColors.Surface) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                Text(word.lemma, style = MaterialTheme.typography.headlineLarge)
                if (word.id.startsWith("zh|")) PinyinHelp(word.lemma)
                Text(word.meaning, style = MaterialTheme.typography.titleLarge, color = MuralColors.Secondary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    RecallBars(word.bars); Text(wordLabel(word.label))
                }
                Text(stringResource(wordExplanationRes(word)))
                Surface(shape = RoundedCornerShape(18.dp), color = MuralColors.Peach) { Text(stringResource(R.string.words_example_quote, word.example), Modifier.padding(17.dp)) }
                Text(
                    "${pluralStringResource(R.plurals.words_independent_uses, word.independentCount, word.independentCount)} · ${stringResource(R.string.words_last_seen, formatDate(word.lastSeen))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MuralColors.Secondary,
                )
                MuralTextButton(onClick = onRemove) { Text(stringResource(R.string.words_remove_button), color = MuralColors.Red) }
                MuralTextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.words_done_button)) }
            }
        }
    }
}

internal fun formatDate(secondsSince2001: Double): String = runCatching {
    val unix = (secondsSince2001 + chat.mural.core.APPLE_EPOCH_UNIX_SECONDS).toLong()
    DateTimeFormatter.ofPattern("d MMM yyyy").format(Instant.ofEpochSecond(unix).atZone(ZoneId.systemDefault()))
}.getOrDefault("—")

@Composable
private fun categoryLabel(value: String) = when (value) {
    "All" -> stringResource(R.string.topics_category_all)
    "Favorites" -> stringResource(R.string.topics_favorites_filter)
    "Everyday" -> stringResource(R.string.topics_category_everyday)
    "Connection" -> stringResource(R.string.topics_category_connection)
    "Local life" -> stringResource(R.string.topics_category_local_life)
    "Interests" -> stringResource(R.string.topics_category_interests)
    "Custom", "Personalizados" -> stringResource(R.string.topics_category_custom)
    else -> value
}

@StringRes
internal fun wordExplanationRes(word: WordState): Int = when {
    word.independentCount == 0 -> R.string.words_explanation_supported
    word.bars == 1 -> R.string.words_explanation_recalled_once
    word.bars == 2 -> R.string.words_explanation_recalled_days
    else -> R.string.words_explanation_steady
}

@Composable
private fun wordLabel(value: String) = when (value) {
    "New" -> stringResource(R.string.words_recall_new)
    "Fragile" -> stringResource(R.string.words_recall_fragile)
    "Growing" -> stringResource(R.string.words_recall_growing)
    "Steady" -> stringResource(R.string.words_recall_steady)
    else -> value
}

private fun themeSymbol(id: String) = when (id) {
    "coffee" -> MuralSymbol.Coffee
    "weekend" -> MuralSymbol.Sun
    "walk", "cabin" -> MuralSymbol.Leaf
    "dinner", "restaurant" -> MuralSymbol.Food
    "music" -> MuralSymbol.Music
    "books" -> MuralSymbol.Words
    "today", "travel", "travelstories" -> MuralSymbol.Globe
    else -> MuralSymbol.Sparkles
}
