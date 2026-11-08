package chat.mural.ui

import android.os.Build
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.window.Dialog
import chat.mural.MuralViewModel
import chat.mural.R
import chat.mural.core.SessionRecord
import chat.mural.core.Speaker
import chat.mural.core.TopicEcosystemStore
import chat.mural.core.localized

@Composable
fun TalkScreen(
    vm: MuralViewModel,
    microphoneMessage: String?,
    onMicrophone: () -> Unit,
    onOpenAppSettings: (() -> Unit)?,
    onSendTyped: (String) -> Unit,
    onLookup: (String, String) -> Unit,
    onHelp: () -> Unit,
) {
    var typing by rememberSaveable { mutableStateOf(false) }
    var lookup by rememberSaveable { mutableStateOf(false) }
    var lookupWord by rememberSaveable { mutableStateOf("") }
    var lookupSentence by rememberSaveable { mutableStateOf("") }
    var transcript by remember { mutableStateOf<SessionRecord?>(null) }
    val assistantPassage = vm.session?.passages?.lastOrNull { it.speaker == Speaker.assistant }
    val passage = assistantPassage?.text
    val caption = passage?.takeIf { it.isNotBlank() } ?: vm.language.greeting
    val busy = vm.state == "connecting" || vm.state == "closing"
    val targetScroll = remember(assistantPassage?.id) { ScrollState(0) }
    val meaningScroll = remember(assistantPassage?.id, vm.archive.preferences.meaningLanguage) { ScrollState(0) }
    val textMeasurer = rememberTextMeasurer()

    if (vm.state == "active" && !vm.isVoiceSession) {
        WrittenChatView(
            vm = vm,
            onSendTyped = onSendTyped,
            onLookup = { word, sentence ->
                vm.clearLookup()
                lookupWord = word
                lookupSentence = sentence
                lookup = true
                onLookup(word, sentence)
            },
            onSwitchToVoice = { vm.end(); vm.start() },
            onEnd = { vm.requestEndSession() }
        )
    } else {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("talk-screen")) {
    val scrollPage = LocalDensity.current.fontScale > 1.3f || maxHeight < 480.dp
    val compact = !scrollPage && maxHeight < 680.dp
    val captionWidth = with(LocalDensity.current) { (maxWidth - 56.dp).roundToPx().coerceAtLeast(1) }
    val longPassage = passage != null && !scrollPage && textMeasurer.measure(
        caption, style = MaterialTheme.typography.headlineSmall,
        constraints = Constraints(maxWidth = captionWidth),
    ).lineCount > 2
    val readingSpace by animateFloatAsState(
        if (longPassage || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            vm.language.id == "zh" && chat.mural.core.MandarinPinyin.containsHan(caption))) 1f else 0f, spring(dampingRatio = 1f, stiffness = 260f), label = "passage reading space",
    )
    val orbSize = when {
        scrollPage -> 170.dp
        compact -> minOf(150.dp, maxHeight * .24f) - 26.dp * readingSpace
        else -> 220.dp - 64.dp * readingSpace
    }
    Column(
        Modifier
            .fillMaxSize()
            .then(if (scrollPage) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(horizontal = 28.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val activeThemeId = vm.selectedTheme?.id ?: "general"
        val isEducationMode = vm.currentPedagogicalMode == TopicEcosystemStore.MODE_EDUCATION

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
        ) {
            Surface(
                color = if (MuralColors.isDark) MuralColors.SurfaceBright else MuralColors.Butter.copy(alpha = .75f),
                border = BorderStroke(1.dp, MuralColors.ControlBorder.copy(alpha = 0.65f)),
                shape = CircleShape
            ) {
                Text(
                    vm.selectedTheme?.localized(vm.appLanguage)?.title ?: vm.language.localizedTalkTitle(vm.appLanguage),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = if (compact) 5.dp else 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MuralColors.Ink.copy(alpha = 0.88f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                McerLevelSelector(
                    currentLevel = vm.currentMcerLevel,
                    onSelectLevel = { newLevel -> vm.setThemeMcerLevel(activeThemeId, newLevel) }
                )
                PedagogicalModeSelector(
                    currentMode = vm.currentPedagogicalMode,
                    onSelectMode = { newMode -> vm.setThemePedagogicalMode(activeThemeId, newMode) }
                )
            }
        }
        Spacer(Modifier.height(if (compact) 8.dp else 20.dp - 16.dp * readingSpace))
        val micEnabled = !busy && (vm.state != "active" || vm.isVoiceSession)
        MuralOrb(
            energy = maxOf(vm.outputLevel.toFloat(), vm.inputLevel.toFloat() * .45f),
            listening = vm.state == "active" && vm.isVoiceSession && !vm.isMuted,
            active = vm.state != "closing",
            modifier = Modifier.size(orbSize).clip(CircleShape).clickable(enabled = micEnabled, role = Role.Button) {
                if (vm.state == "active" && vm.isVoiceSession) vm.toggleMute() else onMicrophone()
            },
        )
        Box(Modifier.fillMaxWidth().padding(top = if (compact) 8.dp else 12.dp).heightIn(min = 40.dp), contentAlignment = Alignment.Center) {
            val status = statusText(vm.state, vm.isMuted, vm.isVoiceSession, vm.inactivitySeconds)
            val statusCaption = buildAnnotatedString {
                if (vm.inactivitySeconds != null) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")) { append(status.substringBefore('\n')) }
                    append("\n"); append(status.substringAfter('\n'))
                } else append(status)
            }
            Text(statusCaption, style = MaterialTheme.typography.bodySmall,
                color = MuralColors.Secondary, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = if (vm.inactivitySeconds != null && assistantPassage != null) 40.dp else 0.dp)
                    .testTag("conversation-status"))
            if (assistantPassage != null && passage?.isNotBlank() == true) {
                Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                    ReportUtteranceAction(onClick = {
                        vm.session?.id?.let { vm.reportUtterance(it, assistantPassage.id) }
                    }, modifier = Modifier.requiredSize(40.dp).testTag("report-current-utterance"))
                }
            }
        }
        Spacer(Modifier.height(if (compact) 12.dp else 20.dp - 8.dp * readingSpace))
        Column(
            // Each language keeps a share of the available space. A single scroller let
            // long target-language replies push their meaning entirely below the viewport.
            modifier = (if (scrollPage) Modifier else Modifier.weight(1f))
                .fillMaxWidth().testTag("conversation-captions"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
        Column(
            modifier = (if (scrollPage) Modifier else Modifier.weight(1f, fill = false).passageScroll(targetScroll))
                .fillMaxWidth().testTag("target-passage-scroll"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (passage == null) AnnotatedString(caption)
                else captionLinks(caption, vm.language.id) { word ->
                    vm.clearLookup(); lookupWord = word; lookupSentence = caption
                    lookup = true; onLookup(word, caption)
                },
                style = if (passage == null) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().testTag("target-caption"),
            )
            if (vm.language.id == "zh") PinyinHelp(caption)
        }
        if (vm.archive.preferences.meaningVisible) {
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = (if (scrollPage) Modifier else Modifier.weight(.72f, fill = false).passageScroll(meaningScroll))
                    .fillMaxWidth().testTag("meaning-passage-scroll"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            Text(
                when {
                    passage == null -> chat.mural.core.MeaningLanguages.greeting(vm.archive.preferences.meaningLanguage)
                    vm.meaning.isNotBlank() -> vm.meaning
                    vm.translating -> stringResource(R.string.talk_meaning_loading)
                    else -> ""
                },
                color = MuralColors.Secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("meaning-caption"),
            )
            if (vm.meaningFailed) {
                Text(stringResource(if (vm.meaningLimitReached) R.string.talk_meaning_too_long else R.string.talk_meaning_failed), color = MuralColors.Secondary, style = MaterialTheme.typography.bodySmall)
                if (!vm.meaningLimitReached) MuralTextButton(onClick = vm::retryMeaning) { Text(stringResource(R.string.talk_retry_meaning_button)) }
            }
            }
        }
        if (!vm.cleanVoiceMode || (vm.session?.passages?.lastOrNull { it.speaker == Speaker.user }?.fragments?.any { it.typed } == true)) {
            vm.session?.passages?.lastOrNull { it.speaker == Speaker.user }?.let { user ->
                Row(Modifier.padding(top = 3.dp).testTag("user-caption"), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.history_speaker_you), style = MaterialTheme.typography.labelSmall, color = MuralColors.Secondary)
                    Text(user.text.takeLast(160), style = MaterialTheme.typography.bodySmall, color = MuralColors.Secondary,
                        textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (vm.session?.topics?.lastOrNull()?.sources?.isNotEmpty() == true) {
            MuralTextButton(onClick = { transcript = vm.session }) { Text(stringResource(R.string.topics_sources_heading)) }
        }
        }
        if (vm.working) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.talk_checking), style = MaterialTheme.typography.bodySmall, color = MuralColors.Secondary)
            }
        }
        Spacer(Modifier.height(when { scrollPage -> 28.dp; compact -> 10.dp; else -> 24.dp }))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            RoundAction(MuralSymbol.Translate, stringResource(R.string.talk_meaning_label),
                selected = vm.archive.preferences.meaningVisible, onClick = vm::toggleMeaning)
            val micDescription = stringResource(when {
                vm.state == "active" && !vm.isVoiceSession -> R.string.talk_status_written
                vm.state != "active" -> R.string.talk_mic_start_desc
                vm.isMuted -> R.string.talk_mic_unmute_desc
                else -> R.string.talk_mic_mute_desc
            })
            val micEnabled = !busy && (vm.state != "active" || vm.isVoiceSession)
            Box(Modifier.padding(bottom = if (compact) 15.dp else 19.dp).size(if (compact) 66.dp else 76.dp)
                .shadow(18.dp, CircleShape, ambientColor = MuralColors.Orange.copy(alpha = .15f), spotColor = MuralColors.Orange.copy(alpha = .25f))
                .background(Brush.linearGradient(listOf(Color(0xFF68B5FF), MuralColors.Orange)), CircleShape).clip(CircleShape)
                .testTag("start-conversation").semantics { contentDescription = micDescription }
                .clickable(enabled = micEnabled, role = Role.Button) {
                    if (vm.state == "active" && vm.isVoiceSession) vm.toggleMute() else onMicrophone()
                }, contentAlignment = Alignment.Center) {
                if (busy) CircularProgressIndicator(Modifier.size(25.dp), color = MuralColors.Ink, strokeWidth = 2.dp)
                else MuralIcon(if (vm.isMuted && vm.state == "active") MuralSymbol.MicOff else MuralSymbol.Mic,
                    modifier = Modifier.size(30.dp))
            }
            RoundAction(MuralSymbol.Keyboard, stringResource(R.string.talk_type_button),
                selected = typing, enabled = !busy && !vm.working, onClick = { typing = true })
            RoundAction(if (vm.isRunning) MuralSymbol.End else MuralSymbol.Transcript,
                stringResource(if (vm.isRunning) R.string.talk_end_label else R.string.talk_transcript_label),
                enabled = vm.session != null, onClick = { if (vm.isRunning) vm.requestEndSession() else transcript = vm.session })
        }
        val micStatus = if (vm.state == "active" && vm.isVoiceSession && !vm.isMuted) stringResource(R.string.talk_microphone_on)
            else if (vm.state == "active" && !vm.isVoiceSession) stringResource(R.string.talk_status_written)
            else stringResource(R.string.talk_microphone_off)
        Text(micStatus, style = MaterialTheme.typography.bodySmall, color = MuralColors.Secondary, modifier = Modifier.padding(top = 6.dp))
        if (vm.state == "active") {
            MuralTextButton(onClick = onHelp, enabled = !vm.working) {
                MuralIcon(MuralSymbol.Sparkles, Modifier.size(15.dp)); Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.talk_help_button), style = MaterialTheme.typography.bodySmall, color = MuralColors.Ink)
            }
        } else if (vm.session == null) {
            Text(stringResource(R.string.talk_reply_any_language), style = MaterialTheme.typography.bodySmall,
                color = MuralColors.Secondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 7.dp, bottom = 8.dp))
        }

        microphoneMessage?.let {
            Text(it, color = MuralColors.Secondary, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
            if (onOpenAppSettings != null) MuralTextButton(onClick = onOpenAppSettings) { Text(stringResource(R.string.talk_open_phone_settings)) }
        }
        vm.notice?.let { Text(it, color = MuralColors.Secondary, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall) }
        if (vm.session != null && !vm.isRunning) {
            MuralTextButton(onClick = vm::resetConversation) { Text(stringResource(R.string.talk_new_conversation_button)) }
        }
        Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
    }
    }
    }

    if (typing) TypedReplySheet(
        languageName = vm.language.localizedDisplayName(vm.appLanguage),
        working = vm.working,
        onSend = { text ->
            vm.setTyping(false)
            onSendTyped(text)
        },
        onDismiss = {
            vm.setTyping(false)
            typing = false
        },
        error = vm.typedReplyError,
        completedSends = vm.typedRepliesSent,
        onOpen = {
            vm.clearTypedReplyError()
            vm.setTyping(true)
        },
        onTyping = {
            vm.setTyping(true)
            vm.noteTypingActivity()
        }
    )
    if (lookup) WordLookupSheet(lookupWord, lookupSentence, vm.language.id, vm.lookupResult, vm.lookupError, vm.lookupLoading,
        onDismiss = { vm.clearLookup(); lookup = false; lookupWord = "" })
    transcript?.let { TranscriptDialog(vm, it, onDismiss = { transcript = null }) }
}

private fun Modifier.passageScroll(state: ScrollState): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        if (state.maxValue > 0 && size.height > 0) {
            val fade = minOf(10.dp.toPx() / size.height, .15f)
            drawRect(
                Brush.verticalGradient(
                    0f to if (state.value > 0) Color.Transparent else Color.Black,
                    fade to Color.Black,
                    (1f - fade) to Color.Black,
                    1f to if (state.value < state.maxValue) Color.Transparent else Color.Black,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }
    .verticalScroll(state)

@Composable
private fun RoundAction(symbol: MuralSymbol, label: String, selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SoftRoundButton(
            symbol,
            label,
            onClick,
            tint = if (selected) MuralColors.WatercolorWash else MuralColors.Surface.copy(alpha = .92f),
            enabled = enabled,
            filledIcon = selected
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MuralColors.Ink.copy(alpha = if (enabled) 1f else .45f))
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun WrittenChatView(
    vm: MuralViewModel,
    onSendTyped: (String) -> Unit,
    onLookup: (String, String) -> Unit,
    onSwitchToVoice: () -> Unit,
    onEnd: () -> Unit
) {
    val activeThemeId = vm.selectedTheme?.id ?: "general"
    val isEducationMode = vm.currentPedagogicalMode == TopicEcosystemStore.MODE_EDUCATION
    val passages = vm.session?.passages ?: emptyList()
    val listState = rememberLazyListState()
    var inputText by rememberSaveable { mutableStateOf("") }

    val isImeVisible = WindowInsets.isImeVisible
    androidx.compose.runtime.LaunchedEffect(passages.size, vm.working, isImeVisible) {
        if (passages.isNotEmpty()) {
            listState.animateScrollToItem(passages.size - 1)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .testTag("written-chat-view")
    ) {
        // --- Top Bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Surface(
                    color = if (MuralColors.isDark) MuralColors.SurfaceBright else MuralColors.Butter.copy(alpha = .75f),
                    border = BorderStroke(1.dp, MuralColors.ControlBorder.copy(alpha = 0.65f)),
                    shape = CircleShape,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        vm.selectedTheme?.localized(vm.appLanguage)?.title ?: vm.language.localizedTalkTitle(vm.appLanguage),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MuralColors.Ink.copy(alpha = 0.88f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                McerLevelSelector(
                    currentLevel = vm.currentMcerLevel,
                    onSelectLevel = { newLevel -> vm.setThemeMcerLevel(activeThemeId, newLevel) }
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PedagogicalModeSelector(
                    currentMode = vm.currentPedagogicalMode,
                    onSelectMode = { newMode -> vm.setThemePedagogicalMode(activeThemeId, newMode) }
                )
                SoftRoundButton(
                    MuralSymbol.TestCheck,
                    stringResource(R.string.tests_chat_action_title),
                    onClick = { vm.requestEndSession() },
                    diameter = 38.dp
                )
                SoftRoundButton(
                    MuralSymbol.Mic,
                    stringResource(R.string.chat_switch_to_voice),
                    onClick = onSwitchToVoice,
                    diameter = 38.dp
                )
                SoftRoundButton(
                    MuralSymbol.End,
                    stringResource(R.string.talk_end_label),
                    onClick = onEnd,
                    diameter = 38.dp
                )
            }
        }

        // --- Chat Messages List ---
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            items(passages, key = { it.id }) { passage ->
                if (passage.speaker == Speaker.user) {
                    // User Bubble
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp),
                            color = MuralColors.Orange.copy(alpha = 0.18f),
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Text(
                                text = passage.text,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MuralColors.Ink
                            )
                        }
                    }
                } else {
                    // Assistant / Tutor Bubble
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Column(
                            modifier = Modifier.widthIn(max = 310.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp),
                                color = MuralColors.Surface,
                                border = BorderStroke(1.dp, MuralColors.Ink.copy(alpha = 0.08f)),
                                shadowElevation = 1.dp
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    Text(
                                        captionLinks(passage.text, vm.language.id) { word ->
                                            onLookup(word, passage.text)
                                        },
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MuralColors.Ink
                                    )
                                    if (vm.language.id == "zh") {
                                        PinyinHelp(passage.text)
                                    }
                                }
                            }

                            // Translation / Meaning Box below the text
                            if (vm.archive.preferences.meaningVisible) {
                                val translation = vm.getPassageTranslation(passage)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MuralColors.Butter.copy(alpha = 0.35f),
                                    border = BorderStroke(1.dp, MuralColors.Orange.copy(alpha = 0.18f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(bottom = 2.dp)
                                        ) {
                                            MuralIcon(MuralSymbol.Translate, Modifier.size(11.dp), color = MuralColors.Secondary)
                                            Text(
                                                stringResource(R.string.chat_meaning_box_title),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MuralColors.Secondary
                                            )
                                        }
                                        if (translation != null) {
                                            Text(
                                                translation,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MuralColors.Secondary
                                            )
                                        } else if (passage == passages.lastOrNull { it.speaker == Speaker.assistant } && vm.translating) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.5.dp, color = MuralColors.Secondary)
                                                Text(stringResource(R.string.talk_meaning_loading), style = MaterialTheme.typography.bodySmall, color = MuralColors.Secondary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (vm.working) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MuralColors.Surface.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, MuralColors.Ink.copy(alpha = 0.06f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.8.dp, color = MuralColors.Orange)
                                Text(
                                    stringResource(R.string.chat_typing_indicator),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MuralColors.Secondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- Bottom Input Area ---
        Surface(
            color = MuralColors.Cream,
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isImeVisible) Modifier.imePadding() else Modifier)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Translator / Meaning toggle button
                SoftRoundButton(
                    MuralSymbol.Translate,
                    stringResource(R.string.talk_meaning_label),
                    onClick = { vm.toggleMeaning() },
                    diameter = 42.dp,
                    tint = if (vm.archive.preferences.meaningVisible) MuralColors.WatercolorWash else MuralColors.Surface.copy(alpha = 0.92f),
                    filledIcon = vm.archive.preferences.meaningVisible
                )

                // Input field
                MuralTextField(
                    value = inputText,
                    onValueChange = { inputText = it.take(2000) },
                    modifier = Modifier.weight(1f).testTag("chat-input-field"),
                    minLines = 1,
                    maxLines = 4,
                    placeholder = { Text(stringResource(R.string.chat_input_placeholder)) }
                )

                // Send button
                SoftRoundButton(
                    MuralSymbol.ArrowUp,
                    stringResource(R.string.talk_typed_reply_send_button),
                    onClick = {
                        val clean = inputText.trim()
                        if (clean.isNotBlank()) {
                            onSendTyped(clean)
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank() && !vm.working,
                    diameter = 42.dp,
                    tint = if (inputText.isNotBlank() && !vm.working) MuralColors.Orange else MuralColors.Surface.copy(alpha = 0.65f)
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun TypedReplySheet(languageName: String, working: Boolean, onSend: (String) -> Unit, onDismiss: () -> Unit,
    error: String? = null, completedSends: Int = 0, onOpen: () -> Unit = {}, onTyping: () -> Unit = {}) {
    val initialSends = rememberSaveable { completedSends }
    val sendIntoView = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    androidx.compose.runtime.LaunchedEffect(error) { if (error != null) sendIntoView.bringIntoView() }
    androidx.compose.runtime.LaunchedEffect(Unit) { onOpen() }
    androidx.compose.runtime.LaunchedEffect(completedSends) { if (completedSends > initialSends) onDismiss() }
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    var requestedFocus by remember { mutableStateOf(false) }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MuralColors.Cream,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 26.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.talk_typed_reply_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                SoftRoundButton(MuralSymbol.Close, stringResource(R.string.common_close), onDismiss, diameter = 40.dp)
            }
            Text(stringResource(R.string.talk_typed_reply_subtitle, languageName), color = MuralColors.Secondary,
                style = MaterialTheme.typography.bodyMedium)
            MuralTextField(text, { text = it.take(2_000); onTyping() }, modifier = Modifier.fillMaxWidth().testTag("typed-reply-input").focusRequester(focus).onGloballyPositioned {
                    if (!requestedFocus) { requestedFocus = true; focus.requestFocus() }
                },
                minLines = 3, maxLines = 6, label = { Text(stringResource(R.string.talk_typed_reply_field_label)) })
            if (error != null) Text(error, color = MuralColors.Secondary, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("typed-reply-error"))
            Button(onClick = {
                val clean = text.trim()
                if (clean.isNotBlank()) {
                    onSend(clean)
                    onDismiss()
                }
            }, enabled = text.isNotBlank() && !working,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).bringIntoViewRequester(sendIntoView).testTag("typed-reply-send"), shape = CircleShape) {
                Text(stringResource(R.string.talk_typed_reply_send_button)); Spacer(Modifier.width(8.dp))
                MuralIcon(MuralSymbol.ArrowUp, Modifier.size(18.dp))
            }
        }
    }
}


@Composable
private fun statusText(state: String, muted: Boolean, voice: Boolean, inactivitySeconds: Int? = null) = when (state) {
    "connecting" -> stringResource(R.string.talk_status_connecting)
    "active" -> if (inactivitySeconds != null && voice) stringResource(R.string.talk_inactivity_warning, inactivitySeconds) else if (!voice) stringResource(R.string.talk_status_written) else if (muted) stringResource(R.string.talk_status_muted) else stringResource(R.string.talk_status_listening)
    "closing" -> stringResource(R.string.talk_status_closing)
    "ended" -> stringResource(R.string.talk_status_ended)
    "failed" -> stringResource(R.string.talk_status_failed)
    else -> stringResource(R.string.talk_status_idle)
}
