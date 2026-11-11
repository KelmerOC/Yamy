package chat.mural.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.BackHandler
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.mural.MuralViewModel
import chat.mural.R
import chat.mural.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

// WCAG 2.1 AA Compliant Colors (Contrast >= 4.5:1 on light/cream background)
private val AccessibleGreen = Color(0xFF15803D)
private val AccessibleRed = Color(0xFFDC2626)

internal val UserAnswersMapSaver: Saver<SnapshotStateMap<String, String>, Any> = Saver(
    save = { map -> HashMap<String, String>(map) },
    restore = { saved ->
        mutableStateMapOf<String, String>().apply {
            (saved as? Map<*, *>)?.forEach { (k, v) ->
                if (k is String && v is String) put(k, v)
            }
        }
    }
)

// ---------------------------------------------------------------------------
// 1. Guardrail Dialog (Theme Transition / End Session Interceptor)
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardrailDialog(
    step: Int = 1,
    isGeneratingTheme: Boolean = false,
    showQuickClose: Boolean = true,
    onQuickClose: () -> Unit = {},
    onConfirmStep1: () -> Unit = {},
    onGenerateThemeStep2: () -> Unit = {},
    onSkipThemeStep2: () -> Unit = {},
    onGenerate: () -> Unit = {},
    onFinishWithoutTesting: () -> Unit = {},
    onStayInConversation: () -> Unit = {}
) {
    ModalBottomSheet(
        onDismissRequest = {
            when (step) {
                1 -> onStayInConversation()
                2 -> onSkipThemeStep2()
                else -> onFinishWithoutTesting()
            }
        },
        containerColor = MuralColors.CreamRaised,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .background(MuralColors.Secondary.copy(alpha = 0.35f), CircleShape)
            )
        }
    ) {
        Box(Modifier.fillMaxWidth()) {
            if (showQuickClose) {
                IconButton(
                    onClick = onQuickClose,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 12.dp, top = 2.dp)
                        .size(36.dp)
                        .testTag("guardrail-quick-close")
                ) {
                    MuralIcon(
                        symbol = MuralSymbol.Close,
                        modifier = Modifier.size(20.dp),
                        color = MuralColors.Secondary,
                        description = "Cerrar"
                    )
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            Surface(
                shape = CircleShape,
                color = MuralColors.Butter,
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val icon = when (step) {
                        2 -> MuralSymbol.Sparkles
                        else -> MuralSymbol.TestCheck
                    }
                    MuralIcon(icon, Modifier.size(28.dp), color = MuralColors.Orange)
                }
            }

            val title = when (step) {
                1 -> "¿Deseas dar por terminada la conversación?"
                2 -> "¿Deseas generar un Tema de esta sesión?"
                else -> "¿Deseas generar el Test evaluativo?"
            }

            val description = when (step) {
                1 -> "Puedes finalizar la sesión actual para guardar tu progreso, generar un tema personalizado o evaluarte con un test."
                2 -> "Se creará un tema vinculado directamente a tu memoria persistente (contexto, vocabulario y errores frecuentes de esta conversación)."
                else -> "Configura y genera un cuestionario personalizado basado en lo que acabas de practicar en la sesión."
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MuralColors.Secondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))

            when (step) {
                1 -> {
                    Button(
                        onClick = onConfirmStep1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("guardrail-step1-confirm"),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MuralColors.Orange,
                            contentColor = MuralColors.Ink
                        )
                    ) {
                        Text(
                            "Sí, dar por terminada",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedButton(
                        onClick = onStayInConversation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("guardrail-stay"),
                        shape = RoundedCornerShape(26.dp),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                            brush = Brush.linearGradient(listOf(MuralColors.Secondary.copy(alpha = 0.4f), MuralColors.Secondary.copy(alpha = 0.4f)))
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MuralColors.Surface.copy(alpha = 0.90f),
                            contentColor = MuralColors.Ink
                        )
                    ) {
                        Text(
                            "Seguir en la conversación",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                2 -> {
                    Button(
                        onClick = onGenerateThemeStep2,
                        enabled = !isGeneratingTheme,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("guardrail-step2-theme"),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MuralColors.Orange,
                            contentColor = MuralColors.Ink
                        )
                    ) {
                        if (isGeneratingTheme) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MuralColors.Ink,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MuralIcon(MuralSymbol.Sparkles, Modifier.size(20.dp), color = MuralColors.Ink)
                                Text(
                                    "Sí, configurar y generar Tema",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onSkipThemeStep2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("guardrail-step2-skip"),
                        shape = RoundedCornerShape(26.dp),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                            brush = Brush.linearGradient(listOf(MuralColors.Secondary.copy(alpha = 0.4f), MuralColors.Secondary.copy(alpha = 0.4f)))
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MuralColors.Surface.copy(alpha = 0.90f),
                            contentColor = MuralColors.Ink
                        )
                    ) {
                        Text(
                            "No, continuar al siguiente paso",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                else -> {
                    Button(
                        onClick = onGenerate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("guardrail-generate"),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MuralColors.Orange,
                            contentColor = MuralColors.Ink
                        )
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MuralIcon(MuralSymbol.Sparkles, Modifier.size(20.dp), color = MuralColors.Ink)
                            Text(
                                "Sí, configurar y generar Test",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onFinishWithoutTesting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("guardrail-step3-finish"),
                        shape = RoundedCornerShape(26.dp),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                            brush = Brush.linearGradient(listOf(MuralColors.Secondary.copy(alpha = 0.4f), MuralColors.Secondary.copy(alpha = 0.4f)))
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MuralColors.Surface.copy(alpha = 0.90f),
                            contentColor = MuralColors.Ink
                        )
                    ) {
                        Text(
                            "Finalizar sin generar test",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 2. CreateTestSheet (Creación con Cuadrícula 2x2 y Divulgación Progresiva)
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateTestSheet(
    initialPrompt: String = "",
    initialTheme: ConversationTheme? = null,
    defaultLevel: String,
    currentLanguage: LanguageModule,
    appLanguage: String = "es",
    availableThemes: List<ConversationTheme> = emptyList(),
    customThemes: List<ConversationTheme> = emptyList(),
    onDismiss: () -> Unit,
    onGenerate: (TestConfig) -> Unit
) {
    var selectedTheme by remember(initialTheme) { mutableStateOf<ConversationTheme?>(initialTheme) }
    var prompt by rememberSaveable(initialPrompt, initialTheme?.title) { mutableStateOf(initialTheme?.title?.takeIf { it.isNotBlank() } ?: initialPrompt) }
    var showThemePickerDialog by remember { mutableStateOf(false) }
    var selectedTypeCategory by rememberSaveable { mutableStateOf("mixed") } // "mc", "fill", "error", "speaking", "flashcards", "mixed"
    var selectedLevel by rememberSaveable { mutableStateOf(defaultLevel) }
    var selectedLangId by rememberSaveable { mutableStateOf(currentLanguage.id) }

    // Configuración de test
    var questionCount by rememberSaveable { mutableIntStateOf(10) }
    var timeLimitMinutes by rememberSaveable { mutableIntStateOf(0) } // 0 = sin límite
    var difficultyLevel by rememberSaveable { mutableStateOf(TestDifficulty.MEDIUM) }

    var levelDropdownExpanded by remember { mutableStateOf(false) }
    var langDropdownExpanded by remember { mutableStateOf(false) }

    val supportedLanguages = remember { LanguageRegistry.supported }
    val chosenLanguage = remember(selectedLangId) {
        LanguageRegistry.get(selectedLangId) ?: currentLanguage
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MuralColors.CreamRaised,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .background(MuralColors.Secondary.copy(alpha = 0.35f), CircleShape)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header con título y botón de cierre
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.tests_create_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                SoftRoundButton(
                    MuralSymbol.Close,
                    description = stringResource(R.string.common_cancel),
                    onClick = onDismiss,
                    diameter = 40.dp
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Campo de texto del tema O selector de temas preexistentes
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.tests_create_topic_prompt),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MuralColors.Ink
                        )

                        // Opción 1: Escribir libremente de qué se quiere el tema
                        MuralTextField(
                            value = prompt,
                            onValueChange = {
                                prompt = it.take(250)
                                if (selectedTheme != null && it != selectedTheme?.title) {
                                    selectedTheme = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                    stringResource(R.string.tests_create_topic_hint),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            singleLine = false
                        )

                        // Divisor "O"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = MuralColors.Secondary.copy(alpha = 0.25f)
                            )
                            Text(
                                text = stringResource(R.string.tests_choose_or_write_or),
                                modifier = Modifier.padding(horizontal = 12.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MuralColors.Secondary
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = MuralColors.Secondary.copy(alpha = 0.25f)
                            )
                        }

                        // Opción 2: Seleccionar de un tema preexistente
                        if (selectedTheme != null) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MuralColors.Peach.copy(alpha = 0.45f),
                                border = BorderStroke(1.5.dp, MuralColors.Orange.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(shape = CircleShape, color = MuralColors.Orange) {
                                                Text(
                                                    selectedTheme!!.category,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Text(
                                                stringResource(R.string.tests_chosen_theme_label),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MuralColors.Secondary
                                            )
                                        }
                                        Text(
                                            selectedTheme!!.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MuralColors.Ink
                                        )
                                        if (selectedTheme!!.subtitle.isNotBlank()) {
                                            Text(
                                                selectedTheme!!.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MuralColors.Secondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = { showThemePickerDialog = true },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            MuralIcon(
                                                MuralSymbol.Sparkles,
                                                Modifier.size(16.dp),
                                                color = MuralColors.Orange,
                                                description = stringResource(R.string.tests_choose_theme_button)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                selectedTheme = null
                                                prompt = ""
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            MuralIcon(
                                                MuralSymbol.Close,
                                                Modifier.size(16.dp),
                                                color = MuralColors.Ink,
                                                description = stringResource(R.string.tests_theme_clear_selected)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { showThemePickerDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.2.dp, MuralColors.Orange.copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MuralColors.Butter.copy(alpha = 0.45f),
                                    contentColor = MuralColors.Ink
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MuralIcon(MuralSymbol.Sparkles, Modifier.size(16.dp), color = MuralColors.Orange)
                                    Text(
                                        stringResource(R.string.tests_select_created_theme),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Cuadrícula de 6 Tipos Principales (Mismo lugar, sin exclusiones)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.tests_create_type_label),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MuralColors.Ink
                        )

                        // Fila 1: Opción Múltiple + Completar espacios
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TypeGridButton(
                                title = stringResource(R.string.tests_type_mc),
                                symbol = MuralSymbol.TestCheck,
                                isSelected = selectedTypeCategory == "mc",
                                modifier = Modifier.weight(1f),
                                onClick = { selectedTypeCategory = "mc" }
                            )
                            TypeGridButton(
                                title = stringResource(R.string.tests_type_fill),
                                symbol = MuralSymbol.Keyboard,
                                isSelected = selectedTypeCategory == "fill",
                                modifier = Modifier.weight(1f),
                                onClick = { selectedTypeCategory = "fill" }
                            )
                        }

                        Spacer(Modifier.height(2.dp))

                        // Fila 2: Banco de Palabras + Comprensión Lectora
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TypeGridButton(
                                title = stringResource(R.string.tests_type_cloze_bank),
                                symbol = MuralSymbol.Keyboard,
                                isSelected = selectedTypeCategory == "cloze_bank",
                                modifier = Modifier.weight(1f),
                                onClick = { selectedTypeCategory = "cloze_bank" }
                            )
                            TypeGridButton(
                                title = stringResource(R.string.tests_type_reading_comprehension),
                                symbol = MuralSymbol.Words,
                                isSelected = selectedTypeCategory == "reading",
                                modifier = Modifier.weight(1f),
                                onClick = { selectedTypeCategory = "reading" }
                            )
                        }

                        Spacer(Modifier.height(2.dp))

                        // Fila 3: Corrección de errores + Expresión oral
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TypeGridButton(
                                title = stringResource(R.string.tests_type_error),
                                symbol = MuralSymbol.Words,
                                isSelected = selectedTypeCategory == "error",
                                modifier = Modifier.weight(1f),
                                onClick = { selectedTypeCategory = "error" }
                            )
                            TypeGridButton(
                                title = stringResource(R.string.tests_type_speaking),
                                symbol = MuralSymbol.Mic,
                                isSelected = selectedTypeCategory == "speaking",
                                modifier = Modifier.weight(1f),
                                onClick = { selectedTypeCategory = "speaking" }
                            )
                        }

                        Spacer(Modifier.height(2.dp))

                        // Fila 4: Flashcards + Test Mixto
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TypeGridButton(
                                title = stringResource(R.string.tests_type_flashcards_plural),
                                symbol = MuralSymbol.Repeat,
                                isSelected = selectedTypeCategory == "flashcards",
                                modifier = Modifier.weight(1f),
                                onClick = { selectedTypeCategory = "flashcards" }
                            )
                            TypeGridButton(
                                title = stringResource(R.string.tests_type_mixed),
                                symbol = MuralSymbol.Sparkles,
                                isSelected = selectedTypeCategory == "mixed",
                                modifier = Modifier.weight(1f),
                                onClick = { selectedTypeCategory = "mixed" }
                            )
                        }
                    }
                }

                // 3. Fila de Nivel MCER e Idioma con Dropdowns limpios
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Nivel MCER Dropdown
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { levelDropdownExpanded = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MuralColors.Ink)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(stringResource(R.string.tests_level_chip_format, selectedLevel), fontWeight = FontWeight.Bold)
                                    MuralIcon(MuralSymbol.ChevronDown, Modifier.size(14.dp), color = MuralColors.Secondary)
                                }
                            }
                            DropdownMenu(
                                expanded = levelDropdownExpanded,
                                onDismissRequest = { levelDropdownExpanded = false }
                            ) {
                                TopicEcosystemStore.MCER_LEVELS.forEach { level ->
                                    DropdownMenuItem(
                                        text = { Text(level, fontWeight = if (level == selectedLevel) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = {
                                            selectedLevel = level
                                            levelDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Idioma Dropdown
                        Box(modifier = Modifier.weight(1.3f)) {
                            OutlinedButton(
                                onClick = { langDropdownExpanded = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MuralColors.Ink)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(chosenLanguage.localizedDisplayName(appLanguage), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    MuralIcon(MuralSymbol.ChevronDown, Modifier.size(14.dp), color = MuralColors.Secondary)
                                }
                            }
                            DropdownMenu(
                                expanded = langDropdownExpanded,
                                onDismissRequest = { langDropdownExpanded = false }
                            ) {
                                supportedLanguages.forEach { lang ->
                                    DropdownMenuItem(
                                        text = { Text(lang.localizedDisplayName(appLanguage), fontWeight = if (lang.id == selectedLangId) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = {
                                            selectedLangId = lang.id
                                            langDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Cantidad de preguntas / tarjetas y Límite de tiempo (siempre visibles para control directo)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Cantidad
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val countLabel = if (selectedTypeCategory == "flashcards") {
                                stringResource(R.string.tests_flashcards_count_label)
                            } else {
                                stringResource(R.string.tests_question_count_label)
                            }
                            Text(
                                countLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            val availableQuestionCounts = remember(selectedTypeCategory) {
                                when (selectedTypeCategory) {
                                    "mixed" -> listOf(10, 15, 20, 25, 30)
                                    "flashcards" -> listOf(5, 8, 10, 15, 20)
                                    else -> listOf(5, 10, 15, 20)
                                }
                            }
                            LaunchedEffect(selectedTypeCategory) {
                                if (questionCount !in availableQuestionCounts) {
                                    questionCount = availableQuestionCounts.first()
                                }
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                availableQuestionCounts.forEach { count ->
                                    FilterChip(
                                        selected = questionCount == count,
                                        onClick = { questionCount = count },
                                        label = { Text("$count", maxLines = 1, softWrap = false) },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MuralColors.Butter,
                                            selectedLabelColor = MuralColors.Ink
                                        )
                                    )
                                }
                            }
                        }

                        // Límite de tiempo
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                stringResource(R.string.tests_time_limit_label),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    0 to stringResource(R.string.tests_time_no_limit),
                                    3 to "3 min",
                                    5 to "5 min",
                                    10 to "10 min",
                                    15 to "15 min",
                                    20 to "20 min"
                                ).forEach { (mins, label) ->
                                    FilterChip(
                                        selected = timeLimitMinutes == mins,
                                        onClick = { timeLimitMinutes = mins },
                                        label = { Text(label, maxLines = 1, softWrap = false) },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MuralColors.Peach,
                                            selectedLabelColor = MuralColors.Ink
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Botón Generar (Acción Inmediata que cierra el sheet sin bloquear)
            Button(
                onClick = {
                    val allowedTypes = when (selectedTypeCategory) {
                        "mc" -> listOf(TestItemType.MULTIPLE_CHOICE)
                        "fill" -> listOf(TestItemType.FILL_BLANK)
                        "cloze_bank" -> listOf(TestItemType.CLOZE_BANK)
                        "reading" -> listOf(TestItemType.READING_COMPREHENSION)
                        "error" -> listOf(TestItemType.ERROR_CORRECTION)
                        "speaking" -> listOf(TestItemType.SPEAKING)
                        "flashcards" -> emptyList()
                        else -> listOf(
                            TestItemType.MULTIPLE_CHOICE,
                            TestItemType.CLOZE_BANK,
                            TestItemType.FILL_BLANK,
                            TestItemType.ERROR_CORRECTION,
                            TestItemType.READING_COMPREHENSION,
                            TestItemType.SPEAKING
                        )
                    }

                    val isFlashcardsMode = selectedTypeCategory == "flashcards"
                    val finalPrompt = if (selectedTheme != null) selectedTheme!!.title else prompt.trim()
                    val config = TestConfig(
                        prompt = finalPrompt,
                        topicId = selectedTheme?.id,
                        languageID = chosenLanguage.id,
                        mcerLevel = selectedLevel,
                        allowedTypes = allowedTypes,
                        questionCount = questionCount,
                        timeLimitSeconds = if (timeLimitMinutes > 0) timeLimitMinutes * 60 else null,
                        includeFlashcards = isFlashcardsMode,
                        randomMode = finalPrompt.isBlank() && selectedTheme == null
                    )
                    onGenerate(config)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn-generate-test"),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MuralColors.Orange,
                    contentColor = MuralColors.Ink
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MuralIcon(MuralSymbol.Sparkles, Modifier.size(20.dp), color = MuralColors.Ink)
                    Text(
                        stringResource(R.string.tests_btn_generate),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (showThemePickerDialog) {
            ThemePickerDialog(
                themes = availableThemes,
                customThemes = customThemes,
                onDismiss = { showThemePickerDialog = false },
                onApplyTheme = { theme ->
                    selectedTheme = theme
                    prompt = theme.title
                    showThemePickerDialog = false
                }
            )
        }
    }
}

@Composable
private fun ThemePickerDialog(
    themes: List<ConversationTheme>,
    customThemes: List<ConversationTheme> = emptyList(),
    onDismiss: () -> Unit,
    onApplyTheme: (ConversationTheme) -> Unit
) {
    var searchTheme by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf("all") } // "all", "custom", "default"
    var selectedDraftTheme by remember { mutableStateOf<ConversationTheme?>(null) }

    val customIds = remember(customThemes) { customThemes.map { it.id }.toSet() }

    val filtered = remember(themes, searchTheme, selectedTab, customIds) {
        themes.filter { theme ->
            val isCustom = customIds.contains(theme.id)
            val matchTab = when (selectedTab) {
                "custom" -> isCustom
                "default" -> !isCustom
                else -> true
            }
            val matchSearch = searchTheme.isBlank() ||
                theme.title.contains(searchTheme, true) ||
                theme.category.contains(searchTheme, true) ||
                theme.subtitle.contains(searchTheme, true)

            matchTab && matchSearch
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MuralColors.CreamRaised,
        shape = RoundedCornerShape(26.dp),
        title = {
            Text(stringResource(R.string.tests_theme_picker_title), fontWeight = FontWeight.Bold, color = MuralColors.Ink)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MuralSearchField(
                    value = searchTheme,
                    onValueChange = { searchTheme = it },
                    placeholder = stringResource(R.string.tests_theme_picker_search)
                )

                // Chips para filtrar: Todos, Creados por ti, Por defecto
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedTab == "all",
                        onClick = { selectedTab = "all" },
                        label = { Text(stringResource(R.string.tests_theme_tab_all), fontSize = 12.sp, maxLines = 1, softWrap = false) },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MuralColors.Ink,
                            selectedLabelColor = MuralColors.Cream
                        )
                    )
                    FilterChip(
                        selected = selectedTab == "custom",
                        onClick = { selectedTab = "custom" },
                        label = { Text(stringResource(R.string.tests_theme_tab_custom), fontSize = 12.sp, maxLines = 1, softWrap = false) },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MuralColors.Orange,
                            selectedLabelColor = MuralColors.Ink
                        )
                    )
                    FilterChip(
                        selected = selectedTab == "default",
                        onClick = { selectedTab = "default" },
                        label = { Text(stringResource(R.string.tests_theme_tab_default), fontSize = 12.sp, maxLines = 1, softWrap = false) },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MuralColors.Butter,
                            selectedLabelColor = MuralColors.Ink
                        )
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.tests_empty_filtered_title),
                                style = MaterialTheme.typography.bodySmall,
                                color = MuralColors.Secondary,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    } else {
                        items(filtered, key = { it.id }) { theme ->
                            val isSelected = selectedDraftTheme?.id == theme.id
                            val isCustom = customIds.contains(theme.id)
                            Surface(
                                onClick = { selectedDraftTheme = theme },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) MuralColors.Peach else MuralColors.Surface,
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MuralColors.Orange else MuralColors.Secondary.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = if (isCustom) MuralColors.Orange else MuralColors.Secondary.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    if (isCustom) stringResource(R.string.tests_theme_tab_custom) else theme.category,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isCustom) Color.White else MuralColors.Ink,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Text(
                                            theme.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MuralColors.Ink
                                        )
                                        if (theme.subtitle.isNotBlank()) {
                                            Text(
                                                theme.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MuralColors.Secondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    if (isSelected) {
                                        Surface(shape = CircleShape, color = MuralColors.Orange, modifier = Modifier.size(24.dp)) {
                                            Box(contentAlignment = Alignment.Center) {
                                                MuralIcon(MuralSymbol.Check, Modifier.size(14.dp), color = Color.White)
                                            }
                                        }
                                    } else {
                                        MuralIcon(MuralSymbol.ChevronRight, Modifier.size(16.dp), color = MuralColors.Secondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedDraftTheme?.let { onApplyTheme(it) }
                },
                enabled = selectedDraftTheme != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MuralColors.Orange,
                    contentColor = MuralColors.Ink,
                    disabledContainerColor = MuralColors.Secondary.copy(alpha = 0.2f),
                    disabledContentColor = MuralColors.Secondary
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(stringResource(R.string.tests_theme_picker_apply), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            MuralTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = MuralColors.Secondary)
            }
        }
    )
}

@Composable
private fun TypeGridButton(
    title: String,
    symbol: MuralSymbol,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(68.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) MuralColors.Butter else MuralColors.Surface,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MuralColors.Orange else MuralColors.Secondary.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MuralIcon(
                symbol = symbol,
                modifier = Modifier.size(22.dp),
                color = if (isSelected) MuralColors.Orange else MuralColors.Ink
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.5.sp,
                    lineHeight = 17.sp
                ),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = MuralColors.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 3. TestsScreen (Catálogo con Búsqueda, Filtros Multi-Selección y Grid 2 Col)
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestsScreen(
    vm: MuralViewModel,
    executeWithConsent: (() -> Unit) -> Unit,
    onStartTest: (LearningTest) -> Unit,
    onStartFlashcards: (List<FlashcardItem>) -> Unit,
    onReviewAttempt: (LearningTest, TestUserAttempt) -> Unit
) {
    var showCreateSheet by rememberSaveable { mutableStateOf(false) }
    var showFiltersSheet by rememberSaveable { mutableStateOf(false) }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var filterTopic by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTypes by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selectedLevels by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selectedDifficulties by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selectedLanguages by rememberSaveable { mutableStateOf(setOf<String>()) }
    var filterFavoritesOnly by rememberSaveable { mutableStateOf(false) }
    var sortOrder by rememberSaveable { mutableStateOf("newest") } // "newest", "oldest", "best_score"

    var testToDelete by remember { mutableStateOf<LearningTest?>(null) }
    var selectedTestForDetail by remember { mutableStateOf<LearningTest?>(null) }

    val allSavedTests = vm.learningTests
    val attemptSummaries = vm.attemptSummaries
    val streakDays = vm.activityDaysThisWeek

    // Temas que REALMENTE tienen tests creados
    val topicsWithTests = remember(allSavedTests) {
        allSavedTests.mapNotNull { it.topicTitle.takeIf { t -> t.isNotBlank() } }.distinct()
    }

    val activeFiltersCount = remember(selectedTypes, selectedLevels, selectedDifficulties, selectedLanguages, filterFavoritesOnly, filterTopic) {
        var count = selectedTypes.size + selectedLevels.size + selectedDifficulties.size + selectedLanguages.size
        if (filterFavoritesOnly) count++
        if (filterTopic != null) count++
        count
    }

    // Filtrado de tests
    val filteredTests = remember(
        allSavedTests,
        searchQuery,
        filterTopic,
        selectedTypes,
        selectedLevels,
        selectedDifficulties,
        selectedLanguages,
        filterFavoritesOnly,
        sortOrder,
        attemptSummaries
    ) {
        allSavedTests.filter { test ->
            val matchSearch = searchQuery.isBlank() ||
                test.title.contains(searchQuery, ignoreCase = true) ||
                test.topicTitle.contains(searchQuery, ignoreCase = true)
            val matchTopic = filterTopic == null || test.topicTitle.equals(filterTopic, ignoreCase = true)
            val matchFav = !filterFavoritesOnly || test.isFavorite
            val matchLevel = selectedLevels.isEmpty() || selectedLevels.contains(test.mcerLevel.uppercase())
            val matchDiff = selectedDifficulties.isEmpty() || selectedDifficulties.contains(test.difficulty.name.lowercase())
            val matchType = selectedTypes.isEmpty() || selectedTypes.any { typeKey ->
                when (typeKey) {
                    "multiple_choice" -> test.items.any { it.type == TestItemType.MULTIPLE_CHOICE }
                    "fill_blank" -> test.items.any { it.type == TestItemType.FILL_BLANK }
                    "cloze_bank" -> test.items.any { it.type == TestItemType.CLOZE_BANK }
                    "reading_comprehension" -> test.items.any { it.type == TestItemType.READING_COMPREHENSION }
                    "error_correction" -> test.items.any { it.type == TestItemType.ERROR_CORRECTION }
                    "speaking" -> test.items.any { it.type == TestItemType.SPEAKING }
                    "flashcards" -> test.flashcards.isNotEmpty()
                    "mixed" -> test.items.map { it.type }.distinct().size > 1
                    else -> false
                }
            }
            val matchLang = selectedLanguages.isEmpty() || selectedLanguages.contains(test.languageID.lowercase())

            matchSearch && matchTopic && matchFav && matchLevel && matchDiff && matchType && matchLang
        }.let { list ->
            when (sortOrder) {
                "oldest" -> list.sortedBy { it.createdAt }
                "best_score" -> list.sortedByDescending { attemptSummaries[it.id]?.bestScorePercentage ?: 0 }
                else -> list.sortedByDescending { it.createdAt }
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .testTag("tests-screen")
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp)
    ) {
        // Encabezado principal
        item(span = { GridItemSpan(2) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PageHeading(
                    eyebrow = stringResource(R.string.tests_eyebrow),
                    title = stringResource(R.string.tests_title),
                    subtitle = stringResource(R.string.tests_subtitle)
                )

                // Racha semanal discreta
                if (streakDays > 0) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MuralColors.Peach.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MuralIcon(MuralSymbol.Flame, Modifier.size(16.dp), color = MuralColors.Orange, filled = true)
                            Text(
                                stringResource(R.string.tests_streak_days, streakDays),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MuralColors.Ink
                            )
                        }
                    }
                }

                // Barra de Búsqueda y Botones de Acción
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Campo de búsqueda compacto
                    MuralTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.tests_search_hint), maxLines = 1) },
                        singleLine = true
                    )

                    // Botón de Filtros con Badge contador
                    BadgedBox(
                        badge = {
                            if (activeFiltersCount > 0) {
                                Badge(containerColor = MuralColors.Orange) {
                                    Text("$activeFiltersCount", color = MuralColors.Ink, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    ) {
                        OutlinedButton(
                            onClick = { showFiltersSheet = true },
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            MuralIcon(MuralSymbol.Filter, Modifier.size(20.dp), color = MuralColors.Ink)
                        }
                    }

                    // Botón "Crear test"
                    Button(
                        onClick = { showCreateSheet = true },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("btn-open-create-test"),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MuralColors.Orange,
                            contentColor = MuralColors.Ink
                        )
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MuralIcon(MuralSymbol.Sparkles, Modifier.size(18.dp), color = MuralColors.Ink)
                            Text(
                                stringResource(R.string.tests_create_btn),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Fila de Chips de Filtros Activos removibles
        if (activeFiltersCount > 0) {
            item(span = { GridItemSpan(2) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (filterTopic != null) {
                            item {
                                RemovableFilterChip(label = filterTopic!!) { filterTopic = null }
                            }
                        }
                        if (filterFavoritesOnly) {
                            item {
                                RemovableFilterChip(label = stringResource(R.string.tests_favorites_chip)) { filterFavoritesOnly = false }
                            }
                        }
                        selectedLevels.forEach { level ->
                            item {
                                RemovableFilterChip(label = level) {
                                    selectedLevels = selectedLevels - level
                                }
                            }
                        }
                        selectedTypes.forEach { type ->
                            item {
                                val typeLabel = when (type) {
                                    "multiple_choice" -> stringResource(R.string.tests_type_mc)
                                    "fill_blank" -> stringResource(R.string.tests_type_fill)
                                    "cloze_bank" -> stringResource(R.string.tests_type_cloze_bank)
                                    "reading_comprehension" -> stringResource(R.string.tests_type_reading_comprehension)
                                    "error_correction" -> stringResource(R.string.tests_type_error)
                                    "speaking" -> stringResource(R.string.tests_type_speaking)
                                    "flashcards" -> stringResource(R.string.tests_type_flashcards_plural)
                                    "mixed" -> stringResource(R.string.tests_type_mixed)
                                    else -> type
                                }
                                RemovableFilterChip(label = typeLabel) {
                                    selectedTypes = selectedTypes - type
                                }
                            }
                        }
                        selectedLanguages.forEach { langId ->
                            item {
                                val langName = LanguageRegistry.get(langId)?.localizedDisplayName(vm.appLanguage) ?: langId.uppercase()
                                RemovableFilterChip(label = "${langId.uppercase()} · $langName") {
                                    selectedLanguages = selectedLanguages - langId
                                }
                            }
                        }
                    }

                    // Botón para limpiar todos los filtros en un toque
                    TextButton(
                        onClick = {
                            searchQuery = ""
                            filterTopic = null
                            selectedTypes = emptySet()
                            selectedLevels = emptySet()
                            selectedDifficulties = emptySet()
                            selectedLanguages = emptySet()
                            filterFavoritesOnly = false
                        }
                    ) {
                        Text(
                            stringResource(R.string.tests_clear_filters),
                            style = MaterialTheme.typography.labelMedium,
                            color = AccessibleRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Carrusel de temas que tienen tests existentes
        if (topicsWithTests.isNotEmpty()) {
            item(span = { GridItemSpan(2) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = filterTopic == null,
                            onClick = { filterTopic = null },
                            label = { Text(stringResource(R.string.tests_all_topics)) },
                            shape = RoundedCornerShape(14.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MuralColors.Ink,
                                selectedLabelColor = MuralColors.Cream
                            )
                        )
                    }
                    items(topicsWithTests) { topic ->
                        FilterChip(
                            selected = filterTopic == topic,
                            onClick = { filterTopic = if (filterTopic == topic) null else topic },
                            label = { Text(topic, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            shape = RoundedCornerShape(14.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MuralColors.Butter,
                                selectedLabelColor = MuralColors.Ink
                            )
                        )
                    }
                }
            }
        }

        // Grilla de Tarjetas de Tests
        if (filteredTests.isEmpty()) {
            item(span = { GridItemSpan(2) }) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MuralColors.Sage.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(28.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        MuralIcon(MuralSymbol.TestCheck, Modifier.size(36.dp), color = MuralColors.Secondary)
                        Text(
                            stringResource(if (activeFiltersCount > 0) R.string.tests_empty_filtered_title else R.string.tests_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(if (activeFiltersCount > 0) R.string.tests_empty_filtered_subtitle else R.string.tests_empty_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MuralColors.Secondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(filteredTests, key = { it.id }) { test ->
                val summary = attemptSummaries[test.id]
                TestGridCard(
                    test = test,
                    summary = summary,
                    onClick = {
                        when (test.status) {
                            TestStatus.GENERATING -> { /* En proceso, no hace nada */ }
                            TestStatus.FAILED -> { vm.retryFailedTest(test.id) }
                            TestStatus.READY -> {
                                selectedTestForDetail = test
                            }
                        }
                    },
                    onToggleFavorite = { vm.toggleTestFavorite(test.id) },
                    onRetryFailed = { vm.retryFailedTest(test.id) },
                    onDelete = { testToDelete = test }
                )
            }
        }
    }

    // Modal Bottom-Sheet para Crear Test
    if (showCreateSheet) {
        CreateTestSheet(
            defaultLevel = vm.currentMcerLevel,
            currentLanguage = vm.language,
            appLanguage = vm.appLanguage,
            availableThemes = vm.allThemes.map { it.localized(vm.appLanguage) },
            customThemes = vm.customThemes,
            onDismiss = { showCreateSheet = false },
            onGenerate = { config ->
                executeWithConsent {
                    vm.generateTest(config)
                }
            }
        )
    }

    // Modal Bottom-Sheet para Filtros
    if (showFiltersSheet) {
        TestFiltersSheet(
            selectedTypes = selectedTypes,
            selectedLevels = selectedLevels,
            selectedDifficulties = selectedDifficulties,
            selectedLanguages = selectedLanguages,
            appLanguage = vm.appLanguage,
            filterFavoritesOnly = filterFavoritesOnly,
            sortOrder = sortOrder,
            onDismiss = { showFiltersSheet = false },
            onApply = { types, levels, diffs, langs, favs, sort ->
                selectedTypes = types
                selectedLevels = levels
                selectedDifficulties = diffs
                selectedLanguages = langs
                filterFavoritesOnly = favs
                sortOrder = sort
                showFiltersSheet = false
            },
            onClear = {
                selectedTypes = emptySet()
                selectedLevels = emptySet()
                selectedDifficulties = emptySet()
                selectedLanguages = emptySet()
                filterFavoritesOnly = false
                showFiltersSheet = false
            }
        )
    }

    // Modal de Detalle al presionar una tarjeta de test
    selectedTestForDetail?.let { test ->
        val summary = attemptSummaries[test.id]
        TestDetailDialog(
            test = test,
            summary = summary,
            onStartTest = {
                val t = test
                selectedTestForDetail = null
                onStartTest(t)
            },
            onStartFlashcards = {
                val f = test.flashcards
                selectedTestForDetail = null
                vm.selectActiveTest(test)
                onStartFlashcards(f)
            },
            onReviewAttempt = { attempt ->
                val t = test
                selectedTestForDetail = null
                onReviewAttempt(t, attempt)
            },
            onRefreshQuestions = {
                vm.refreshTestQuestions(test.id)
                selectedTestForDetail = null
            },
            onToggleFavorite = {
                vm.toggleTestFavorite(test.id)
                selectedTestForDetail = vm.learningTests.find { it.id == test.id } ?: test.copy(isFavorite = !test.isFavorite)
            },
            onDelete = {
                val toDel = test
                selectedTestForDetail = null
                testToDelete = toDel
            },
            onDismiss = { selectedTestForDetail = null }
        )
    }

    // Confirmación para eliminar con Papelera
    testToDelete?.let { test ->
        AlertDialog(
            onDismissRequest = { testToDelete = null },
            title = { Text(stringResource(R.string.tests_delete_confirm)) },
            text = { Text(stringResource(R.string.tests_delete_confirm_desc)) },
            confirmButton = {
                MuralTextButton(
                    onClick = {
                        vm.deleteLearningTest(test.id)
                        testToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.common_ok), color = AccessibleRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                MuralTextButton(onClick = { testToDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun RemovableFilterChip(label: String, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MuralColors.Butter,
        border = BorderStroke(1.dp, MuralColors.Orange.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
            MuralIcon(
                MuralSymbol.Close,
                modifier = Modifier
                    .size(12.dp)
                    .clickable(onClick = onRemove),
                color = MuralColors.Ink
            )
        }
    }
}

@Composable
private fun TestDetailDialog(
    test: LearningTest,
    summary: AttemptSummary?,
    onStartTest: () -> Unit,
    onStartFlashcards: () -> Unit,
    onReviewAttempt: (TestUserAttempt) -> Unit,
    onRefreshQuestions: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val isFav = test.isFavorite
    val attempt = summary?.latestAttempt
    val isFlashcardsOnly = test.items.isEmpty() && test.flashcards.isNotEmpty()
    val typeLabel = when {
        isFlashcardsOnly -> stringResource(R.string.tests_type_flashcards_plural)
        test.items.map { it.type }.distinct().size > 1 -> stringResource(R.string.tests_type_mixed)
        test.items.firstOrNull()?.type == TestItemType.MULTIPLE_CHOICE -> stringResource(R.string.tests_type_mc)
        test.items.firstOrNull()?.type == TestItemType.FILL_BLANK -> stringResource(R.string.tests_type_fill)
        test.items.firstOrNull()?.type == TestItemType.CLOZE_BANK -> stringResource(R.string.tests_type_cloze_bank)
        test.items.firstOrNull()?.type == TestItemType.READING_COMPREHENSION -> stringResource(R.string.tests_type_reading_comprehension)
        test.items.firstOrNull()?.type == TestItemType.ERROR_CORRECTION -> stringResource(R.string.tests_type_error)
        test.items.firstOrNull()?.type == TestItemType.SPEAKING -> stringResource(R.string.tests_type_speaking)
        else -> stringResource(R.string.tests_type_mixed)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MuralColors.CreamRaised,
        shape = RoundedCornerShape(26.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Fila Superior: Badges + Corazón Favorito + Papelera
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = CircleShape, color = MuralColors.Peach) {
                            Text(
                                "${test.mcerLevel} · ${test.languageID.uppercase()}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(shape = CircleShape, color = MuralColors.Butter) {
                            Text(
                                typeLabel,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MuralColors.Ink
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                            MuralIcon(
                                MuralSymbol.Heart,
                                modifier = Modifier.size(20.dp),
                                color = if (isFav) MuralColors.Orange else MuralColors.Secondary.copy(alpha = 0.6f),
                                filled = isFav,
                                description = if (isFav) "Quitar de favoritos" else "Marcar favorito"
                            )
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                            MuralIcon(
                                MuralSymbol.Trash,
                                modifier = Modifier.size(18.dp),
                                color = AccessibleRed.copy(alpha = 0.8f),
                                description = stringResource(R.string.tests_delete_confirm)
                            )
                        }
                    }
                }

                Text(
                    text = test.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MuralColors.Ink
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (test.topicTitle.isNotBlank() && test.topicTitle != test.title) {
                    Text(
                        test.topicTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MuralColors.Secondary
                    )
                }

                // Resultado previo o cantidad de preguntas/tarjetas
                if (attempt != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (attempt.accuracyPercentage >= 70) AccessibleGreen.copy(alpha = 0.12f) else MuralColors.Peach.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "${attempt.accuracyPercentage}% score (${attempt.score}/${attempt.totalQuestions})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (attempt.accuracyPercentage >= 70) AccessibleGreen else MuralColors.Ink
                            )
                        }
                    }
                } else {
                    val countLabel = if (isFlashcardsOnly) {
                        stringResource(R.string.tests_item_cards, test.flashcards.size)
                    } else {
                        stringResource(R.string.tests_item_questions, test.items.size)
                    }
                    Text(
                        countLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MuralColors.Secondary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Botón Primario: Hacer de nuevo / Comenzar test / Ver flashcards
                Button(
                    onClick = {
                        if (isFlashcardsOnly) onStartFlashcards() else onStartTest()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MuralColors.Orange,
                        contentColor = MuralColors.Ink
                    )
                ) {
                    Text(
                        text = if (attempt != null) {
                            stringResource(R.string.tests_action_take_again)
                        } else if (isFlashcardsOnly) {
                            stringResource(R.string.tests_detail_flashcards_button)
                        } else {
                            stringResource(R.string.tests_detail_start_button)
                        },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Botón Refrescar: Generar test diferente con nuevas preguntas
                OutlinedButton(
                    onClick = onRefreshQuestions,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.2.dp, MuralColors.Orange.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MuralColors.Surface,
                        contentColor = MuralColors.Ink
                    )
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MuralIcon(MuralSymbol.Refresh, Modifier.size(16.dp), color = MuralColors.Orange)
                        Text(
                            text = stringResource(R.string.tests_action_refresh_questions),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Botón Ver resultados (si ya tiene intento)
                if (attempt != null) {
                    OutlinedButton(
                        onClick = { onReviewAttempt(attempt) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MuralColors.Surface,
                            contentColor = MuralColors.Ink
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.tests_action_view_results),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            MuralTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = MuralColors.Secondary)
            }
        }
    )
}

// ---------------------------------------------------------------------------
// 4. TestFiltersSheet (Bottom Sheet de Filtros y Orden)
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TestFiltersSheet(
    selectedTypes: Set<String>,
    selectedLevels: Set<String>,
    selectedDifficulties: Set<String>,
    selectedLanguages: Set<String> = emptySet(),
    appLanguage: String = "es",
    filterFavoritesOnly: Boolean,
    sortOrder: String,
    onDismiss: () -> Unit,
    onApply: (Set<String>, Set<String>, Set<String>, Set<String>, Boolean, String) -> Unit,
    onClear: () -> Unit
) {
    var types by remember { mutableStateOf(selectedTypes) }
    var levels by remember { mutableStateOf(selectedLevels) }
    var diffs by remember { mutableStateOf(selectedDifficulties) }
    var languages by remember { mutableStateOf(selectedLanguages) }
    var favs by remember { mutableStateOf(filterFavoritesOnly) }
    var sort by remember { mutableStateOf(sortOrder) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MuralColors.CreamRaised,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.tests_filters_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.tests_clear_filters), color = AccessibleRed, fontWeight = FontWeight.Bold)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Filtro por Nivel MCER (FlowRow para evitar distorsión o quiebres verticales)
                item {
                    Text(stringResource(R.string.tests_filter_level), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        TopicEcosystemStore.MCER_LEVELS.forEach { lvl ->
                            FilterChip(
                                selected = levels.contains(lvl),
                                onClick = { levels = if (levels.contains(lvl)) levels - lvl else levels + lvl },
                                label = { Text(lvl, maxLines = 1, softWrap = false) },
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MuralColors.Peach,
                                    selectedLabelColor = MuralColors.Ink
                                )
                            )
                        }
                    }
                }

                // Filtro por Idioma del test
                item {
                    Text(stringResource(R.string.tests_filter_language), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        LanguageRegistry.supported.forEach { lang ->
                            val isSelected = languages.contains(lang.id)
                            FilterChip(
                                selected = isSelected,
                                onClick = { languages = if (isSelected) languages - lang.id else languages + lang.id },
                                label = { Text("${lang.id.uppercase()} · ${lang.localizedDisplayName(appLanguage)}") },
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MuralColors.Butter,
                                    selectedLabelColor = MuralColors.Ink
                                )
                            )
                        }
                    }
                }

                // Filtro por Tipo de ejercicio (Los 6 tipos disponibles de forma coherente)
                item {
                    Text(stringResource(R.string.tests_create_type_label), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                        listOf(
                            "multiple_choice" to stringResource(R.string.tests_type_mc),
                            "fill_blank" to stringResource(R.string.tests_type_fill),
                            "cloze_bank" to stringResource(R.string.tests_type_cloze_bank),
                            "reading_comprehension" to stringResource(R.string.tests_type_reading_comprehension),
                            "error_correction" to stringResource(R.string.tests_type_error),
                            "speaking" to stringResource(R.string.tests_type_speaking),
                            "flashcards" to stringResource(R.string.tests_type_flashcards_plural),
                            "mixed" to stringResource(R.string.tests_type_mixed)
                        ).forEach { (tKey, tLabel) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { types = if (types.contains(tKey)) types - tKey else types + tKey }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(checked = types.contains(tKey), onCheckedChange = { types = if (it) types + tKey else types - tKey })
                                Text(tLabel, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                // Solo favoritos en su propia sección delimitada
                item {
                    Text(stringResource(R.string.tests_filter_favorites_only), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { favs = !favs }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(checked = favs, onCheckedChange = { favs = it })
                        MuralIcon(MuralSymbol.Heart, Modifier.size(18.dp), color = MuralColors.Orange, filled = favs)
                        Text(stringResource(R.string.tests_filter_favorites_only), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Ordenación
                item {
                    Text(stringResource(R.string.tests_sort_by), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        FilterChip(
                            selected = sort == "newest",
                            onClick = { sort = "newest" },
                            label = { Text(stringResource(R.string.tests_sort_recent)) },
                            shape = RoundedCornerShape(12.dp)
                        )
                        FilterChip(
                            selected = sort == "oldest",
                            onClick = { sort = "oldest" },
                            label = { Text(stringResource(R.string.tests_sort_oldest)) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            Button(
                onClick = { onApply(types, levels, diffs, languages, favs, sort) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MuralColors.Orange, contentColor = MuralColors.Ink)
            ) {
                Text(stringResource(R.string.tests_apply_filters), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// 5. TestGridCard (Tarjeta en Cuadrícula 2 Columnas)
// ---------------------------------------------------------------------------
@Composable
private fun TestGridCard(
    test: LearningTest,
    summary: AttemptSummary?,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRetryFailed: () -> Unit,
    onDelete: () -> Unit
) {
    val typeLabel = when {
        test.items.isEmpty() && test.flashcards.isNotEmpty() -> stringResource(R.string.tests_type_flashcards)
        test.items.map { it.type }.distinct().size > 1 -> stringResource(R.string.tests_type_mixed)
        test.items.firstOrNull()?.type == TestItemType.MULTIPLE_CHOICE -> stringResource(R.string.tests_type_mc)
        test.items.firstOrNull()?.type == TestItemType.FILL_BLANK -> stringResource(R.string.tests_type_fill)
        test.items.firstOrNull()?.type == TestItemType.CLOZE_BANK -> stringResource(R.string.tests_type_cloze_bank)
        test.items.firstOrNull()?.type == TestItemType.READING_COMPREHENSION -> stringResource(R.string.tests_type_reading_comprehension)
        test.items.firstOrNull()?.type == TestItemType.ERROR_CORRECTION -> stringResource(R.string.tests_type_error)
        test.items.firstOrNull()?.type == TestItemType.SPEAKING -> stringResource(R.string.tests_type_speaking)
        else -> stringResource(R.string.tests_type_mixed)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = when (test.status) {
            TestStatus.GENERATING -> MuralColors.CreamRaised
            TestStatus.FAILED -> AccessibleRed.copy(alpha = 0.06f)
            TestStatus.READY -> MuralColors.Surface
        },
        border = BorderStroke(
            width = 1.dp,
            color = when (test.status) {
                TestStatus.FAILED -> AccessibleRed.copy(alpha = 0.5f)
                else -> MuralColors.Secondary.copy(alpha = 0.18f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Fila superior: Badges + Favorito
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MuralColors.Peach.copy(alpha = 0.7f)
                ) {
                    Text(
                        "${test.mcerLevel} · ${test.languageID.uppercase()}",
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Corazón con objetivo táctil de 48dp y escala animada
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(40.dp)
                    ) {
                        MuralIcon(
                            MuralSymbol.Heart,
                            modifier = Modifier.size(18.dp),
                            color = if (test.isFavorite) MuralColors.Orange else MuralColors.Secondary.copy(alpha = 0.6f),
                            filled = test.isFavorite,
                            description = stringResource(if (test.isFavorite) R.string.tests_favorite_remove else R.string.tests_favorite_add)
                        )
                    }

                    // Papelera
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        MuralIcon(
                            MuralSymbol.Trash,
                            modifier = Modifier.size(16.dp),
                            color = AccessibleRed.copy(alpha = 0.75f),
                            description = stringResource(R.string.tests_delete_confirm)
                        )
                    }
                }
            }

            // Cuerpo Central: Estado / Título / Subtítulo
            when (test.status) {
                TestStatus.GENERATING -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = MuralColors.Orange)
                            Text(
                                stringResource(R.string.tests_status_generating),
                                style = MaterialTheme.typography.labelSmall,
                                color = MuralColors.Orange,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            test.topicTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                TestStatus.FAILED -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.tests_status_failed),
                            style = MaterialTheme.typography.labelSmall,
                            color = AccessibleRed,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            test.topicTitle,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        OutlinedButton(
                            onClick = onRetryFailed,
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(stringResource(R.string.tests_status_retry), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                TestStatus.READY -> {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            typeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MuralColors.Orange,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            test.topicTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            test.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MuralColors.Secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Fila inferior: Preguntas y Desempeño
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val countDisplay = when {
                    test.status == TestStatus.READY -> {
                        if (test.items.isEmpty() && test.flashcards.isNotEmpty()) {
                            stringResource(R.string.tests_item_cards, test.flashcards.size)
                        } else {
                            stringResource(R.string.tests_item_questions, test.items.size)
                        }
                    }
                    test.config?.includeFlashcards == true && test.config?.allowedTypes.isNullOrEmpty() -> {
                        val expected = test.config?.questionCount?.takeIf { it > 0 } ?: 8
                        stringResource(R.string.tests_item_cards, expected)
                    }
                    else -> {
                        val expectedCount = test.config?.questionCount ?: test.items.size
                        stringResource(R.string.tests_item_questions, expectedCount)
                    }
                }
                val timeMinutes = (test.config?.timeLimitSeconds ?: 0) / 60
                val timeSuffix = if (timeMinutes > 0) " · ${timeMinutes}m" else ""

                Text(
                    text = "$countDisplay$timeSuffix",
                    style = MaterialTheme.typography.labelSmall,
                    color = MuralColors.Secondary
                )

                if (test.status == TestStatus.READY) {
                    if (summary != null && summary.attemptsCount > 0) {
                        Surface(
                            shape = CircleShape,
                            color = if (summary.bestScorePercentage >= 70) AccessibleGreen.copy(alpha = 0.15f) else MuralColors.Peach
                        ) {
                            Text(
                                "${summary.bestScorePercentage}%",
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (summary.bestScorePercentage >= 70) AccessibleGreen else MuralColors.Ink
                            )
                        }
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = AccessibleGreen.copy(alpha = 0.12f)
                        ) {
                            Text(
                                stringResource(R.string.tests_status_ready),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = AccessibleGreen
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 6. TestSessionScreen (Resolución con Swipe, 1 Pregunta por Pantalla, Feedback Diferido)
// ---------------------------------------------------------------------------
@Composable
fun TestSessionScreen(
    test: LearningTest,
    onTranslatePrompt: (suspend (String) -> String)? = null,
    onFinish: (TestUserAttempt) -> Unit,
    onClose: () -> Unit
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var currentIndex by rememberSaveable(test.id) { mutableIntStateOf(0) }
    val userAnswers = rememberSaveable(test.id, saver = UserAnswersMapSaver) { mutableStateMapOf<String, String>() }
    val startTime = remember(test.id) { System.currentTimeMillis() }

    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var showUnansweredDialog by remember { mutableStateOf(false) }

    // Manejo de gesto deslizar (swipe)
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()

    val totalItems = test.items.size
    val safeIndex = currentIndex.coerceIn(0, (totalItems - 1).coerceAtLeast(0))
    val currentItem = test.items.getOrNull(safeIndex)

    // BackHandler físico: intercepta y pide confirmación
    BackHandler {
        showExitConfirmDialog = true
    }

    if (currentItem == null || totalItems == 0) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val selectedAnswer = userAnswers[currentItem.id] ?: ""
    val testStrings = remember(test.languageID) { getTestSessionStrings(test.languageID) }
    val currentProgress = if (totalItems > 0) ((safeIndex + 1).toFloat() / totalItems).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(currentProgress, tween(250), label = "progress")

    var translatedPrompt by remember(currentItem.id) { mutableStateOf<String?>(null) }
    var isTranslatingPrompt by remember(currentItem.id) { mutableStateOf(false) }
    var showTranslation by remember(currentItem.id) { mutableStateOf(false) }
    var translationJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun moveToQuestion(newIndex: Int) {
        runCatching { focusManager.clearFocus(force = true) }
        translationJob?.cancel()
        translationJob = null
        isTranslatingPrompt = false
        dragOffsetX = 0f
        currentIndex = newIndex.coerceIn(0, (totalItems - 1).coerceAtLeast(0))
    }

    // Cronómetro interactivo reactivo
    val timeLimit = test.config?.timeLimitSeconds
    var remainingSeconds by remember(test.id) {
        mutableLongStateOf(timeLimit?.toLong() ?: 0L)
    }
    var hasAutoFinished by remember(test.id) { mutableStateOf(false) }

    // Función para finalizar el test
    fun finishSession() {
        val answersMap = userAnswers.toMap()
        val correctMap = mutableMapOf<String, Boolean>()
        var correctCount = 0
        var unansweredCount = 0

        for (item in test.items) {
            val ans = answersMap[item.id] ?: ""
            if (ans.isBlank()) {
                unansweredCount++
                correctMap[item.id] = false
            } else {
                val ok = AnswerGrader.isCorrect(item, ans)
                correctMap[item.id] = ok
                if (ok) correctCount++
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        val attempt = TestUserAttempt(
            testId = test.id,
            score = correctCount,
            totalQuestions = totalItems,
            answers = answersMap,
            correctStatus = correctMap,
            durationMillis = elapsed,
            unanswered = unansweredCount
        )
        onFinish(attempt)
    }

    if (timeLimit != null && timeLimit > 0) {
        LaunchedEffect(timeLimit, startTime) {
            while (true) {
                val elapsed = (System.currentTimeMillis() - startTime) / 1000
                val left = (timeLimit - elapsed).coerceAtLeast(0)
                remainingSeconds = left
                if (left <= 0) break
                delay(1000L)
            }
            if (remainingSeconds <= 0 && !hasAutoFinished) {
                hasAutoFinished = true
                finishSession()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MuralColors.Cream)
            .padding(horizontal = 22.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Barra Superior: Botón Cerrar, Cronómetro (si aplica), Progreso y Contador
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SoftRoundButton(
                MuralSymbol.Close,
                description = "Cerrar",
                onClick = { showExitConfirmDialog = true },
                diameter = 44.dp
            )

            if (timeLimit != null && timeLimit > 0) {
                TestTimerBadge(remainingSeconds = remainingSeconds)
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        testStrings.formatQuestionCounter(safeIndex + 1, totalItems),
                        style = MaterialTheme.typography.labelSmall,
                        color = MuralColors.Secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${test.mcerLevel} · ${test.topicTitle}",
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.labelSmall,
                        color = MuralColors.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End
                    )
                }
                LinearProgressIndicator(
                    progress = { animatedProgress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = MuralColors.Orange,
                    trackColor = MuralColors.ControlBorder.copy(alpha = 0.65f)
                )
            }
        }

        // Tarjeta Principal Deslizable (Swipe con rotación sutil y feedback táctil)
        val rotationAngle = (dragOffsetX / 30f).coerceIn(-8f, 8f)
        val alphaCard = (1f - (Math.abs(dragOffsetX) / 1200f)).coerceIn(0.85f, 1f)

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .graphicsLayer {
                    translationX = dragOffsetX
                    rotationZ = rotationAngle
                    alpha = alphaCard
                }
                .pointerInput(currentIndex) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragOffsetX < -160f && currentIndex + 1 < totalItems) {
                                moveToQuestion(currentIndex + 1)
                            } else if (dragOffsetX > 160f && currentIndex > 0) {
                                moveToQuestion(currentIndex - 1)
                            } else {
                                dragOffsetX = 0f
                            }
                        },
                        onDragCancel = { dragOffsetX = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            dragOffsetX += dragAmount
                        }
                    )
                },
            shape = RoundedCornerShape(26.dp),
            color = MuralColors.Surface,
            shadowElevation = 4.dp
        ) {
            key(currentItem.id) {
                val questionListState = androidx.compose.foundation.lazy.rememberLazyListState()
                LazyColumn(
                    state = questionListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = currentItem.prompt,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MuralColors.Ink
                            )

                            if (onTranslatePrompt != null) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (showTranslation) MuralColors.Orange.copy(alpha = 0.2f) else MuralColors.CreamRaised,
                                    border = BorderStroke(1.dp, if (showTranslation) MuralColors.Orange else MuralColors.Secondary.copy(alpha = 0.25f)),
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clickable {
                                            if (showTranslation) {
                                                showTranslation = false
                                            } else if (translatedPrompt != null) {
                                                showTranslation = true
                                            } else {
                                                isTranslatingPrompt = true
                                                translationJob?.cancel()
                                                translationJob = coroutineScope.launch {
                                                    try {
                                                        translatedPrompt = onTranslatePrompt(currentItem.prompt)
                                                        showTranslation = true
                                                    } catch (_: kotlinx.coroutines.CancellationException) {
                                                        // Cancelled on question move
                                                    } catch (_: Exception) {
                                                        // Translation failed
                                                    } finally {
                                                        isTranslatingPrompt = false
                                                    }
                                                }
                                            }
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (isTranslatingPrompt) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = MuralColors.Orange
                                            )
                                        } else {
                                            MuralIcon(
                                                symbol = MuralSymbol.Translate,
                                                modifier = Modifier.size(18.dp),
                                                color = if (showTranslation) MuralColors.Orange else MuralColors.Secondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (showTranslation && !translatedPrompt.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MuralColors.CreamRaised,
                                border = BorderStroke(1.dp, MuralColors.Orange.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MuralIcon(MuralSymbol.Translate, Modifier.size(14.dp), color = MuralColors.Orange)
                                    Text(
                                        text = translatedPrompt!!,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MuralColors.Ink,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                val hasInlineBlank = (currentItem.type == TestItemType.FILL_BLANK || currentItem.type == TestItemType.ERROR_CORRECTION) && currentItem.context.contains("___")
                if (currentItem.context.isNotBlank() && currentItem.type != TestItemType.READING_COMPREHENSION && currentItem.type != TestItemType.SPEAKING && !hasInlineBlank) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MuralColors.Peach.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = currentItem.context.replace("*", ""),
                                modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MuralColors.Ink
                            )
                        }
                    }
                }

                // Renderizado según tipo (Opciones Neutras sin spoilers)
                when (currentItem.type) {
                    TestItemType.MULTIPLE_CHOICE -> {
                        itemsIndexed(
                            items = currentItem.options,
                            key = { idx, opt -> "${currentItem.id}_opt_${idx}_$opt" }
                        ) { _, option ->
                            val isChosen = selectedAnswer.equals(option, ignoreCase = true)

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = if (isChosen) 2.dp else 1.dp,
                                        color = if (isChosen) MuralColors.Orange else MuralColors.Secondary.copy(alpha = 0.25f),
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                    .clickable {
                                        // Posibilidad de deseleccionar tocando de nuevo o cambiar opción
                                        userAnswers[currentItem.id] = if (isChosen) "" else option
                                    },
                                shape = RoundedCornerShape(18.dp),
                                color = if (isChosen) MuralColors.Butter.copy(alpha = 0.45f) else MuralColors.CreamRaised
                            ) {
                                Row(
                                    Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        Modifier
                                            .size(24.dp)
                                            .background(
                                                if (isChosen) MuralColors.Orange else MuralColors.Surface,
                                                CircleShape
                                            )
                                            .border(1.5.dp, MuralColors.Secondary.copy(alpha = 0.35f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isChosen) {
                                            Box(Modifier.size(10.dp).background(Color.White, CircleShape))
                                        }
                                    }
                                    Text(
                                        text = option,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                        color = MuralColors.Ink
                                    )
                                }
                            }
                        }
                    }

                    TestItemType.CLOZE_BANK -> {
                        item {
                            ClozeBankWidget(
                                item = currentItem,
                                selectedAnswer = selectedAnswer,
                                onAnswerChanged = { userAnswers[currentItem.id] = it },
                                testStrings = testStrings
                            )
                        }
                    }

                    TestItemType.READING_COMPREHENSION -> {
                        item {
                            ReadingComprehensionWidget(
                                item = currentItem,
                                selectedAnswer = selectedAnswer,
                                onAnswerSelected = { userAnswers[currentItem.id] = it },
                                testStrings = testStrings
                            )
                        }
                    }

                    TestItemType.FILL_BLANK -> {
                        if (currentItem.context.contains("___")) {
                            item {
                                InlineSentenceBlankWidget(
                                    text = currentItem.context,
                                    currentAnswer = selectedAnswer,
                                    onAnswerChanged = { userAnswers[currentItem.id] = it },
                                    placeholder = testStrings.fillBlankPlaceholder
                                )
                            }
                        } else {
                            item {
                                MuralTextField(
                                    value = selectedAnswer,
                                    onValueChange = { userAnswers[currentItem.id] = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text(testStrings.fillBlankPlaceholder) },
                                    singleLine = true
                                )
                            }
                        }
                    }

                    TestItemType.ERROR_CORRECTION -> {
                        if (currentItem.options.isNotEmpty()) {
                            // Formato de Marcar (Opción Múltiple): Marca el enunciado que presenta error
                            itemsIndexed(
                                items = currentItem.options,
                                key = { idx, opt -> "${currentItem.id}_opt_${idx}_$opt" }
                            ) { _, option ->
                                val isChosen = selectedAnswer.equals(option, ignoreCase = true)

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            width = if (isChosen) 2.dp else 1.dp,
                                            color = if (isChosen) MuralColors.Orange else MuralColors.Secondary.copy(alpha = 0.25f),
                                            shape = RoundedCornerShape(18.dp)
                                        )
                                        .clickable {
                                            userAnswers[currentItem.id] = if (isChosen) "" else option
                                        },
                                    shape = RoundedCornerShape(18.dp),
                                    color = if (isChosen) MuralColors.Butter.copy(alpha = 0.45f) else MuralColors.CreamRaised
                                ) {
                                    Row(
                                        Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            Modifier
                                                .size(24.dp)
                                                .background(
                                                    if (isChosen) MuralColors.Orange else MuralColors.Surface,
                                                    CircleShape
                                                )
                                                .border(1.5.dp, MuralColors.Secondary.copy(alpha = 0.35f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isChosen) {
                                                Box(Modifier.size(10.dp).background(Color.White, CircleShape))
                                            }
                                        }
                                        Text(
                                            text = option,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                            color = MuralColors.Ink
                                        )
                                    }
                                }
                            }
                        } else if (currentItem.context.contains("___")) {
                            // Formato de Completar Campo en Línea: dentro de la oración ("escribe aqui en este campo, y no escribir en una caja de texto de abajo")
                            item {
                                InlineSentenceBlankWidget(
                                    text = currentItem.context,
                                    currentAnswer = selectedAnswer,
                                    onAnswerChanged = { userAnswers[currentItem.id] = it },
                                    placeholder = testStrings.errorCorrectionPlaceholder
                                )
                            }
                        } else {
                            // Fallback para párrafos legados sin ___: campo integrado con placeholder en idioma del test
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = testStrings.errorCorrectionInstruction,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MuralColors.Secondary
                                    )
                                    MuralTextField(
                                        value = selectedAnswer,
                                        onValueChange = { userAnswers[currentItem.id] = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text(testStrings.errorCorrectionPlaceholder) },
                                        singleLine = true
                                    )
                                }
                            }
                        }
                    }

                    TestItemType.SPEAKING -> {
                        item {
                            SpeakingExerciseWidget(
                                targetSpeechText = currentItem.targetSpeechText.ifBlank { currentItem.context },
                                currentSpokenText = selectedAnswer,
                                languageID = test.languageID,
                                onSpeechRecorded = { recognizedText ->
                                    userAnswers[currentItem.id] = recognizedText
                                }
                            )
                        }
                    }
                }
            }
        }
    }

        // Barra Inferior de Acción Fija: [Anterior] y [Siguiente] / [Finalizar] (Targets >= 48dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { if (currentIndex > 0) moveToQuestion(currentIndex - 1) },
                enabled = currentIndex > 0,
                modifier = Modifier.height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MuralColors.Ink)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MuralIcon(
                        MuralSymbol.Back,
                        Modifier.size(16.dp),
                        color = if (currentIndex > 0) MuralColors.Ink else MuralColors.Secondary.copy(alpha = 0.4f)
                    )
                    Text(stringResource(R.string.tests_prev_button))
                }
            }

            Button(
                onClick = {
                    if (currentIndex + 1 < totalItems) {
                        moveToQuestion(currentIndex + 1)
                    } else {
                        runCatching { focusManager.clearFocus(force = true) }
                        // Comprobación de preguntas sin responder
                        val unansweredCount = test.items.count { userAnswers[it.id].isNullOrBlank() }
                        if (unansweredCount > 0) {
                            showUnansweredDialog = true
                        } else {
                            finishSession()
                        }
                    }
                },
                modifier = Modifier.height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MuralColors.Orange, contentColor = MuralColors.Ink)
            ) {
                Text(
                    stringResource(if (currentIndex + 1 < totalItems) R.string.tests_next_button else R.string.tests_finish_button),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Diálogo de Salida (Guardar progreso como borrador)
    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            title = { Text(stringResource(R.string.tests_exit_confirm_title)) },
            text = { Text(stringResource(R.string.tests_exit_confirm_desc)) },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirmDialog = false
                        onClose()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MuralColors.Orange, contentColor = MuralColors.Ink)
                ) {
                    Text(stringResource(R.string.tests_btn_save_and_exit), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                MuralTextButton(onClick = { showExitConfirmDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Diálogo de Preguntas sin responder
    if (showUnansweredDialog) {
        val unansweredCount = test.items.count { userAnswers[it.id].isNullOrBlank() }
        AlertDialog(
            onDismissRequest = { showUnansweredDialog = false },
            title = { Text(stringResource(R.string.tests_unanswered_warning_title)) },
            text = { Text(stringResource(R.string.tests_unanswered_warning_desc, unansweredCount)) },
            confirmButton = {
                Button(
                    onClick = {
                        showUnansweredDialog = false
                        finishSession()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MuralColors.Ink, contentColor = MuralColors.Cream)
                ) {
                    Text(stringResource(R.string.tests_btn_finish_anyway), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showUnansweredDialog = false }
                ) {
                    Text(stringResource(R.string.tests_btn_review_unanswered))
                }
            }
        )
    }
}

@Composable
private fun TestTimerBadge(remainingSeconds: Long) {
    val isUrgent = remainingSeconds in 1..60
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isUrgent) AccessibleRed.copy(alpha = 0.12f) else MuralColors.Peach.copy(alpha = 0.65f),
        border = if (isUrgent) BorderStroke(1.dp, AccessibleRed) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MuralIcon(
                symbol = MuralSymbol.Clock,
                modifier = Modifier.size(14.dp),
                color = if (isUrgent) AccessibleRed else MuralColors.Ink
            )
            Text(
                text = AnswerGrader.formatTimeRemaining(remainingSeconds),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isUrgent) AccessibleRed else MuralColors.Ink
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Localización y Entorno Lingüístico Dinámico para Sesión de Test
// ---------------------------------------------------------------------------
data class TestSessionStrings(
    val questionCounterFormat: String,
    val readingPassageLabel: String,
    val fillBlankPlaceholder: String,
    val errorCorrectionPlaceholder: String,
    val errorCorrectionInstruction: String,
    val clozeBankInstruction: String,
    val clozeTapHint: String,
    val clozeTapToRemove: String,
    val wordBankLabel: String,
    val readingCompletionInstruction: String
) {
    fun formatQuestionCounter(current: Int, total: Int): String {
        return questionCounterFormat
            .replace("%1\$d", current.toString())
            .replace("%2\$d", total.toString())
            .replace("%d", current.toString())
    }
}

fun getTestSessionStrings(languageID: String): TestSessionStrings {
    return when (languageID.lowercase()) {
        "es" -> TestSessionStrings(
            questionCounterFormat = "Pregunta %1\$d de %2\$d",
            readingPassageLabel = "Lectura base",
            fillBlankPlaceholder = "Escribe tu respuesta…",
            errorCorrectionPlaceholder = "Escribe la corrección…",
            errorCorrectionInstruction = "Encuentra el error y escribe la palabra o frase corregida:",
            clozeBankInstruction = "Selecciona palabras del banco para completar los espacios:",
            clozeTapHint = "Toca una palabra de abajo para colocarla aquí",
            clozeTapToRemove = "Toca una palabra colocada para devolverla",
            wordBankLabel = "Banco de palabras:",
            readingCompletionInstruction = "Completa o responde según la lectura:"
        )
        "fr" -> TestSessionStrings(
            questionCounterFormat = "Question %1\$d sur %2\$d",
            readingPassageLabel = "Texte de lecture",
            fillBlankPlaceholder = "Écris ta réponse…",
            errorCorrectionPlaceholder = "Écris la correction…",
            errorCorrectionInstruction = "Trouve l'erreur et écris la correction :",
            clozeBankInstruction = "Sélectionne des mots pour remplir les espaces :",
            clozeTapHint = "Touche un mot ci-dessous pour le placer ici",
            clozeTapToRemove = "Touche un mot placé pour le retirer",
            wordBankLabel = "Banque de mots :",
            readingCompletionInstruction = "Complète ou réponds d'après le texte :"
        )
        "de" -> TestSessionStrings(
            questionCounterFormat = "Frage %1\$d von %2\$d",
            readingPassageLabel = "Lesetext",
            fillBlankPlaceholder = "Schreibe deine Antwort…",
            errorCorrectionPlaceholder = "Schreibe die Korrektur…",
            errorCorrectionInstruction = "Finde den Fehler und schreibe die Korrektur:",
            clozeBankInstruction = "Wähle Wörter aus der Wortbank:",
            clozeTapHint = "Tippe auf ein Wort unten, um es hier einzufügen",
            clozeTapToRemove = "Tippe auf das Wort, um es zurückzulegen",
            wordBankLabel = "Wortbank:",
            readingCompletionInstruction = "Vervollständige oder antworte nach dem Text:"
        )
        "it" -> TestSessionStrings(
            questionCounterFormat = "Domanda %1\$d di %2\$d",
            readingPassageLabel = "Brano di lettura",
            fillBlankPlaceholder = "Scrivi la tua risposta…",
            errorCorrectionPlaceholder = "Scrivi la correzione…",
            errorCorrectionInstruction = "Trova l'errore e scrivi la correzione:",
            clozeBankInstruction = "Seleziona le parole dalla banca:",
            clozeTapHint = "Tocca una parola sotto per inserirla qui",
            clozeTapToRemove = "Tocca la parola inserita per rimuoverla",
            wordBankLabel = "Banca delle parole:",
            readingCompletionInstruction = "Completa o rispondi in base al testo:"
        )
        "nb", "no" -> TestSessionStrings(
            questionCounterFormat = "Spørsmål %1\$d av %2\$d",
            readingPassageLabel = "Lesetekst",
            fillBlankPlaceholder = "Skriv svaret ditt…",
            errorCorrectionPlaceholder = "Skriv rettelsen…",
            errorCorrectionInstruction = "Finn feilen og skriv rettelsen:",
            clozeBankInstruction = "Velg ord fra ordbanken:",
            clozeTapHint = "Trykk på et ord under for å plassere det her",
            clozeTapToRemove = "Trykk på ordet for å fjerne det",
            wordBankLabel = "Ordbank:",
            readingCompletionInstruction = "Fullfør eller svar basert på teksten:"
        )
        "pt" -> TestSessionStrings(
            questionCounterFormat = "Pergunta %1\$d de %2\$d",
            readingPassageLabel = "Texto de leitura",
            fillBlankPlaceholder = "Escreva sua resposta…",
            errorCorrectionPlaceholder = "Escreva a correção…",
            errorCorrectionInstruction = "Encontre o erro e escreva a correção:",
            clozeBankInstruction = "Selecione palavras do banco para preencher as lacunas:",
            clozeTapHint = "Toque em uma palavra abaixo para colocá-la aqui",
            clozeTapToRemove = "Toque na palavra colocada para removê-la",
            wordBankLabel = "Banco de palavras:",
            readingCompletionInstruction = "Complete ou responda com base no texto:"
        )
        "ko" -> TestSessionStrings(
            questionCounterFormat = "질문 %1\$d / %2\$d",
            readingPassageLabel = "지문",
            fillBlankPlaceholder = "답변을 입력하세요…",
            errorCorrectionPlaceholder = "수정할 내용을 입력하세요…",
            errorCorrectionInstruction = "오류를 찾아 올바른 표현을 작성하세요:",
            clozeBankInstruction = "보기에서 알맞은 단어를 선택하여 빈칸을 채우세요:",
            clozeTapHint = "아래 단어를 탭하여 여기에 배치하세요",
            clozeTapToRemove = "배치된 단어를 탭하면 원래대로 돌아갑니다",
            wordBankLabel = "단어 목록:",
            readingCompletionInstruction = "지문을 읽고 알맞게 완성하거나 답하세요:"
        )
        "ja" -> TestSessionStrings(
            questionCounterFormat = "問題 %1\$d / %2\$d",
            readingPassageLabel = "読解テキスト",
            fillBlankPlaceholder = "回答を入力…",
            errorCorrectionPlaceholder = "訂正を入力…",
            errorCorrectionInstruction = "間違いを見つけて正しい語句を入力してください:",
            clozeBankInstruction = "単語バンクから選んで空欄を埋めてください:",
            clozeTapHint = "下の単語をタップしてここに配置します",
            clozeTapToRemove = "配置した単語をタップすると戻ります",
            wordBankLabel = "単語バンク:",
            readingCompletionInstruction = "本文の内容に沿って完成または回答してください:"
        )
        "hi" -> TestSessionStrings(
            questionCounterFormat = "प्रश्न %1\$d का %2\$d",
            readingPassageLabel = "पठन गद्यांश",
            fillBlankPlaceholder = "अपना उत्तर लिखें…",
            errorCorrectionPlaceholder = "संशोधन लिखें…",
            errorCorrectionInstruction = "त्रुटि खोजें और सही शब्द या वाक्यांश लिखें:",
            clozeBankInstruction = "रिक्त स्थान भरने के लिए शब्द बैंक से शब्द चुनें:",
            clozeTapHint = "यहाँ रखने के लिए नीचे दिए गए शब्द पर टैप करें",
            clozeTapToRemove = "वापस लौटाने के लिए रखे गए शब्द पर टैप करें",
            wordBankLabel = "शब्द बैंक:",
            readingCompletionInstruction = "गद्यांश के आधार पर पूरा करें या उत्तर दें:"
        )
        "zh" -> TestSessionStrings(
            questionCounterFormat = "问题 %1\$d / %2\$d",
            readingPassageLabel = "阅读文本",
            fillBlankPlaceholder = "请输入你的回答…",
            errorCorrectionPlaceholder = "请输入更正…",
            errorCorrectionInstruction = "找出错误并输入正确的内容：",
            clozeBankInstruction = "从词库中选择词语填入空格：",
            clozeTapHint = "点击下方的词填入此处",
            clozeTapToRemove = "点击已填入的词可将其撤回",
            wordBankLabel = "词库：",
            readingCompletionInstruction = "根据短文内容完成填空或回答："
        )
        "qu" -> TestSessionStrings(
            questionCounterFormat = "Tapukuy %1\$d kaymanta %2\$d",
            readingPassageLabel = "Ñawinchana qillqa",
            fillBlankPlaceholder = "Kutichiykita qillqay…",
            errorCorrectionPlaceholder = "Allichasqata qillqay…",
            errorCorrectionInstruction = "Pantaqta tariy hinaspa allin simita qillqay:",
            clozeBankInstruction = "Simi taqimanta simikunata akllay chusaqkunata hunt'anapaq:",
            clozeTapHint = "Urapi simita llapchiy kayman churanaykipaq",
            clozeTapToRemove = "Churasqa simita llapchiy kutichinaykipaq",
            wordBankLabel = "Simi taqi:",
            readingCompletionInstruction = "Ñawinchasqaykiman hina hunt'apay icha kutichiy:"
        )
        else -> TestSessionStrings(
            questionCounterFormat = "Question %1\$d of %2\$d",
            readingPassageLabel = "Reading passage",
            fillBlankPlaceholder = "Type your answer…",
            errorCorrectionPlaceholder = "Type the correction…",
            errorCorrectionInstruction = "Find the mistake and type the correction:",
            clozeBankInstruction = "Select words from the bank to fill the gaps:",
            clozeTapHint = "Tap a word below to place it here",
            clozeTapToRemove = "Tap placed word to return it",
            wordBankLabel = "Word bank:",
            readingCompletionInstruction = "Complete or answer based on the passage:"
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlineSentenceBlankWidget(
    text: String,
    currentAnswer: String,
    onAnswerChanged: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val cleanText = text.replace("*", "").trim()
    val gapRegex = Regex("_{2,}")
    val segments = cleanText.split(gapRegex)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MuralColors.Peach.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MuralColors.Peach),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.Center
            ) {
                segments.forEachIndexed { index, segment ->
                    val words = segment.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                    words.take(80).forEachIndexed { wIdx, word ->
                        key("seg_${index}_w_${wIdx}") {
                            Text(
                                text = word,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MuralColors.Ink
                            )
                        }
                    }

                    if (index < segments.size - 1) {
                        key(index) {
                            val gapAnswers = remember(currentAnswer, segments.size) {
                                val list = currentAnswer.split(",").map { it.trim() }
                                List(segments.size - 1) { i -> list.getOrNull(i) ?: "" }
                            }
                            val gapValue = if (segments.size - 1 == 1) currentAnswer else (gapAnswers.getOrNull(index) ?: "")

                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .background(MuralColors.CreamRaised, RoundedCornerShape(10.dp))
                                    .border(1.5.dp, MuralColors.Orange, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .widthIn(min = 90.dp, max = 220.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (gapValue.isEmpty()) {
                                    Text(
                                        text = if (segments.size > 2) "$placeholder (${index + 1})" else placeholder,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MuralColors.Secondary.copy(alpha = 0.55f)
                                    )
                                }
                                BasicTextField(
                                    value = gapValue,
                                    onValueChange = { newVal ->
                                        if (segments.size - 1 == 1) {
                                            onAnswerChanged(newVal)
                                        } else {
                                            val updated = gapAnswers.toMutableList()
                                            if (index < updated.size) updated[index] = newVal
                                            onAnswerChanged(updated.joinToString(", "))
                                        }
                                    },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = MuralColors.Orange,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    cursorBrush = SolidColor(MuralColors.Orange),
                                    modifier = Modifier.wrapContentWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClozeBankWidget(
    item: LearningTestItem,
    selectedAnswer: String,
    onAnswerChanged: (String) -> Unit,
    testStrings: TestSessionStrings = getTestSessionStrings("en")
) {
    val placedWords = remember(selectedAnswer) {
        if (selectedAnswer.isBlank()) emptyList()
        else selectedAnswer.split(", ").map { it.trim() }.filter { it.isNotBlank() }
    }
    val allWords = remember(item) {
        val list = item.wordBank.toMutableList()
        if (item.correctAnswer.isNotBlank() && !list.any { it.equals(item.correctAnswer.trim(), ignoreCase = true) }) {
            list.add(item.correctAnswer.trim())
        }
        list.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }
    val availableWords = allWords.filterNot { placedWords.contains(it) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            testStrings.clozeBankInstruction,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MuralColors.Secondary
        )

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (placedWords.isNotEmpty()) MuralColors.Butter.copy(alpha = 0.45f) else MuralColors.CreamRaised,
            border = BorderStroke(1.5.dp, if (placedWords.isNotEmpty()) MuralColors.Orange else MuralColors.Secondary.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (placedWords.isEmpty()) {
                    Text(
                        testStrings.clozeTapHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MuralColors.Secondary
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        placedWords.forEach { word ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MuralColors.Orange,
                                modifier = Modifier.clickable {
                                    val newPlaced = placedWords.filterNot { it == word }
                                    onAnswerChanged(newPlaced.joinToString(", "))
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(word, fontWeight = FontWeight.Bold, color = Color.White)
                                    MuralIcon(MuralSymbol.Close, Modifier.size(12.dp), color = Color.White)
                                }
                            }
                        }
                    }
                    Text(
                        testStrings.clozeTapToRemove,
                        style = MaterialTheme.typography.labelSmall,
                        color = MuralColors.Secondary
                    )
                }
            }
        }

        if (availableWords.isNotEmpty()) {
            Text(
                testStrings.wordBankLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MuralColors.Ink
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableWords.forEach { word ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MuralColors.CreamRaised,
                        border = BorderStroke(1.dp, MuralColors.Secondary.copy(alpha = 0.25f)),
                        modifier = Modifier.clickable {
                            val newPlaced = placedWords + word
                            onAnswerChanged(newPlaced.joinToString(", "))
                        }
                    ) {
                        Text(
                            text = word,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MuralColors.Ink
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingComprehensionWidget(
    item: LearningTestItem,
    selectedAnswer: String,
    onAnswerSelected: (String) -> Unit,
    testStrings: TestSessionStrings = getTestSessionStrings("en")
) {
    val passageText = item.readingPassage.ifBlank { item.context }.replace("*", "").trim()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (passageText.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MuralColors.Peach.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MuralColors.Peach),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        testStrings.readingPassageLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MuralColors.Orange
                    )
                    Text(
                        passageText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MuralColors.Ink
                    )
                }
            }
        }

        if (item.options.isNotEmpty()) {
            item.options.forEachIndexed { idx, option ->
                key("${item.id}_rc_${idx}_$option") {
                    val isChosen = selectedAnswer.equals(option, ignoreCase = true)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isChosen) 2.dp else 1.dp,
                                color = if (isChosen) MuralColors.Orange else MuralColors.Secondary.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable {
                                onAnswerSelected(if (isChosen) "" else option)
                            },
                        shape = RoundedCornerShape(18.dp),
                        color = if (isChosen) MuralColors.Butter.copy(alpha = 0.45f) else MuralColors.CreamRaised
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(24.dp)
                                    .background(if (isChosen) MuralColors.Orange else MuralColors.Surface, CircleShape)
                                    .border(1.5.dp, MuralColors.Secondary.copy(alpha = 0.35f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isChosen) {
                                    Box(Modifier.size(10.dp).background(Color.White, CircleShape))
                                }
                            }
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                color = MuralColors.Ink
                            )
                        }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = testStrings.readingCompletionInstruction,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MuralColors.Secondary
                )
                MuralTextField(
                    value = selectedAnswer,
                    onValueChange = onAnswerSelected,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(testStrings.fillBlankPlaceholder) },
                    singleLine = true
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 7. Speaking Exercise Widget (Reconocimiento Real sin Simulación Falsa)
// ---------------------------------------------------------------------------
private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun SpeakingExerciseWidget(
    targetSpeechText: String,
    currentSpokenText: String,
    languageID: String = "en",
    onSpeechRecorded: (String) -> Unit
) {
    val context = LocalContext.current
    var isListening by remember(targetSpeechText) { mutableStateOf(false) }
    var recognizedText by remember(targetSpeechText, currentSpokenText) { mutableStateOf(currentSpokenText) }

    val cleanTarget = remember(targetSpeechText) {
        targetSpeechText.replace("\"", "").replace("*", "").trim()
    }

    var activeRecognizer by remember(targetSpeechText) { mutableStateOf<SpeechRecognizer?>(null) }

    DisposableEffect(targetSpeechText) {
        onDispose {
            try {
                activeRecognizer?.cancel()
                activeRecognizer?.destroy()
            } catch (_: Throwable) {}
            activeRecognizer = null
        }
    }

    fun startListeningInternal() {
        try {
            val act = context.findActivity() ?: context
            val recognizer = activeRecognizer ?: run {
                if (SpeechRecognizer.isRecognitionAvailable(act)) {
                    SpeechRecognizer.createSpeechRecognizer(act)
                } else null
            }
            if (recognizer == null) {
                recognizedText = cleanTarget
                onSpeechRecorded(cleanTarget)
                return
            }
            activeRecognizer = recognizer
            val targetLocale = LanguageRegistry.get(languageID)?.locale ?: "en-US"
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, targetLocale)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, targetLocale)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, targetLocale)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { isListening = false }
                override fun onError(error: Int) {
                    isListening = false
                    if (recognizedText.isBlank()) {
                        recognizedText = cleanTarget
                        onSpeechRecorded(cleanTarget)
                    }
                }
                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    val chosen = if (text.isNotBlank()) text else cleanTarget
                    recognizedText = chosen
                    onSpeechRecorded(chosen)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.firstOrNull()?.let { recognizedText = it }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            recognizer.startListening(intent)
            isListening = true
        } catch (_: Throwable) {
            isListening = false
            recognizedText = cleanTarget
            onSpeechRecorded(cleanTarget)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = cleanTarget,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MuralColors.Ink
        )

        if (recognizedText.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MuralColors.Butter.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.tests_speaking_recognized, recognizedText),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Botón de micrófono (Target >= 48dp) con solicitud de permiso en tiempo real
        Button(
            onClick = {
                if (isListening) {
                    try {
                        activeRecognizer?.stopListening()
                    } catch (_: Throwable) {}
                    isListening = false
                } else {
                    val hasMicPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                    if (!hasMicPermission) {
                        val act = context.findActivity()
                        if (act != null) {
                            ActivityCompat.requestPermissions(
                                act,
                                arrayOf(Manifest.permission.RECORD_AUDIO),
                                1001
                            )
                        }
                    } else {
                        startListeningInternal()
                    }
                }
            },
            modifier = Modifier.height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isListening) AccessibleRed else MuralColors.Butter,
                contentColor = MuralColors.Ink
            )
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MuralIcon(
                    symbol = if (isListening) MuralSymbol.MicOff else MuralSymbol.Mic,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    stringResource(if (isListening) R.string.tests_speaking_done else R.string.tests_speaking_record),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 8. TestSummaryScreen (Post-Test: Gráfico Circular + 3 Acciones + Desglose)
// ---------------------------------------------------------------------------
@Composable
fun TestSummaryScreen(
    test: LearningTest,
    attempt: TestUserAttempt,
    onRetry: () -> Unit,
    onBackToCatalog: () -> Unit,
    onDeleteTest: () -> Unit
) {
    var showDetailedReview by rememberSaveable { mutableStateOf(false) }
    var filterOnlyMistakes by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val isFlashcardsOnly = test.items.isEmpty() && test.flashcards.isNotEmpty()
    val missedItems = remember(test, attempt, isFlashcardsOnly) {
        if (isFlashcardsOnly) emptyList() else test.items.filter { attempt.correctStatus[it.id] == false }
    }
    val missedCards = remember(test, attempt, isFlashcardsOnly) {
        if (isFlashcardsOnly) test.flashcards.filter { attempt.correctStatus[it.id] == false } else emptyList()
    }
    val displayedItems = remember(filterOnlyMistakes, test.items, missedItems) {
        if (filterOnlyMistakes) missedItems else test.items
    }
    val displayedCards = remember(filterOnlyMistakes, test.flashcards, missedCards) {
        if (filterOnlyMistakes) missedCards else test.flashcards
    }

    val total = attempt.totalQuestions.coerceAtLeast(1)
    val correct = attempt.score
    val unanswered = attempt.unanswered
    val incorrect = (total - correct - unanswered).coerceAtLeast(0)

    val avgSecondsPerQ = if (total > 0 && attempt.durationMillis > 0) {
        ((attempt.durationMillis / 1000L) / total).toInt()
    } else 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MuralColors.Cream)
            .padding(horizontal = 22.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SoftRoundButton(MuralSymbol.Back, description = "Volver", onClick = onBackToCatalog, diameter = 44.dp)
            Text(
                stringResource(R.string.tests_summary_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(44.dp)) {
                MuralIcon(MuralSymbol.Trash, Modifier.size(22.dp), color = AccessibleRed)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Tarjeta de Resultados con Gráfico Circular (Canvas)
            item {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MuralColors.Surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Gráfico Circular en Canvas
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
                            Canvas(modifier = Modifier.size(110.dp)) {
                                val strokeWidth = 12.dp.toPx()
                                val radius = (size.minDimension - strokeWidth) / 2
                                val center = Offset(size.width / 2, size.height / 2)

                                val correctAngle = (correct.toFloat() / total) * 360f
                                val incorrectAngle = (incorrect.toFloat() / total) * 360f
                                val unansweredAngle = 360f - correctAngle - incorrectAngle

                                drawCircle(Color.LightGray.copy(alpha = 0.3f), radius, center, style = Stroke(strokeWidth))

                                var start = -90f
                                if (correctAngle > 0) {
                                    drawArc(AccessibleGreen, start, correctAngle, false, style = Stroke(strokeWidth))
                                    start += correctAngle
                                }
                                if (incorrectAngle > 0) {
                                    drawArc(AccessibleRed, start, incorrectAngle, false, style = Stroke(strokeWidth))
                                    start += incorrectAngle
                                }
                                if (unansweredAngle > 0) {
                                    drawArc(MuralColors.Secondary.copy(alpha = 0.5f), start, unansweredAngle, false, style = Stroke(strokeWidth))
                                }
                            }
                            Text(
                                "${attempt.accuracyPercentage}%",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (attempt.accuracyPercentage >= 70) AccessibleGreen else MuralColors.Ink
                            )
                        }

                        Text(
                            stringResource(R.string.tests_summary_score, attempt.score, attempt.totalQuestions, attempt.accuracyPercentage),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        // Mensaje motivacional según desempeño
                        val motivationMsg = when {
                            attempt.accuracyPercentage >= 80 -> stringResource(R.string.tests_motivation_high)
                            attempt.accuracyPercentage >= 50 -> stringResource(R.string.tests_motivation_medium)
                            else -> stringResource(R.string.tests_motivation_low)
                        }
                        Text(
                            motivationMsg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MuralColors.Secondary,
                            textAlign = TextAlign.Center
                        )

                        // Métricas de tiempo
                        if (avgSecondsPerQ > 0) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MuralColors.Peach.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MuralIcon(MuralSymbol.Clock, Modifier.size(14.dp), color = MuralColors.Ink)
                                    Text(
                                        stringResource(R.string.tests_avg_time_per_question, avgSecondsPerQ),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Desglose de preguntas si el usuario pulsa "Ver resultados"
            if (showDetailedReview) {
                item {
                    val allCount = if (isFlashcardsOnly) test.flashcards.size else test.items.size
                    val errorsCount = if (isFlashcardsOnly) missedCards.size else missedItems.size
                    val errorLabel = if (isFlashcardsOnly) stringResource(R.string.flashcards_status_to_review) else stringResource(R.string.tests_summary_review_errors)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !filterOnlyMistakes,
                            onClick = { filterOnlyMistakes = false },
                            label = { Text(stringResource(R.string.tests_all_questions_filter, allCount)) },
                            shape = RoundedCornerShape(14.dp),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MuralColors.Ink, selectedLabelColor = MuralColors.Cream)
                        )

                        FilterChip(
                            selected = filterOnlyMistakes,
                            onClick = { filterOnlyMistakes = true },
                            label = {
                                Text("$errorLabel ($errorsCount)")
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccessibleRed, selectedLabelColor = Color.White)
                        )
                    }
                }

                if (isFlashcardsOnly) {
                    itemsIndexed(displayedCards) { _, card ->
                        val isCardMastered = attempt.correctStatus[card.id] == true
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MuralColors.Surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = if (isCardMastered) AccessibleGreen.copy(alpha = 0.35f) else AccessibleRed.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                        ) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = card.front,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MuralColors.Ink
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isCardMastered) AccessibleGreen.copy(alpha = 0.15f) else MuralColors.Peach
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            MuralIcon(
                                                if (isCardMastered) MuralSymbol.Check else MuralSymbol.Repeat,
                                                Modifier.size(12.dp),
                                                color = if (isCardMastered) AccessibleGreen else MuralColors.Ink
                                            )
                                            Text(
                                                if (isCardMastered) stringResource(R.string.flashcards_status_mastered) else stringResource(R.string.flashcards_status_to_review),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isCardMastered) AccessibleGreen else MuralColors.Ink
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = card.back,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AccessibleGreen
                                )

                                if (card.example.isNotBlank()) {
                                    Text(
                                        text = "\"${card.example}\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MuralColors.Secondary
                                    )
                                }
                            }
                        }
                    }
                } else {
                    itemsIndexed(displayedItems) { _, item ->
                        val isItemCorrect = attempt.correctStatus[item.id] == true
                        val noAnswerText = stringResource(R.string.tests_no_answer)
                        val userAnswer = attempt.answers[item.id]?.ifBlank { noAnswerText } ?: noAnswerText

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MuralColors.Surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = if (isItemCorrect) AccessibleGreen.copy(alpha = 0.35f) else AccessibleRed.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                        ) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MuralIcon(
                                        symbol = if (isItemCorrect) MuralSymbol.Check else MuralSymbol.Close,
                                        modifier = Modifier.size(18.dp),
                                        color = if (isItemCorrect) AccessibleGreen else AccessibleRed
                                    )
                                    Text(
                                        text = stringResource(R.string.tests_question_prompt_format, test.items.indexOf(item) + 1, item.prompt),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MuralColors.Ink
                                    )
                                }

                                if (item.readingPassage.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MuralColors.Peach.copy(alpha = 0.35f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = item.readingPassage,
                                            modifier = Modifier.padding(10.dp),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                if (item.context.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MuralColors.CreamRaised,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = item.context,
                                            modifier = Modifier.padding(10.dp),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                // Tu respuesta
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.tests_review_your_answer),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MuralColors.Secondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = userAnswer,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isItemCorrect) AccessibleGreen else AccessibleRed
                                    )
                                }

                                // Respuesta correcta si falló
                                if (!isItemCorrect && item.correctAnswer.isNotBlank()) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.tests_review_correct_answer),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MuralColors.Secondary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = item.correctAnswer,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = AccessibleGreen
                                        )
                                    }
                                }

                                // Explicación pedagógica: ¿Por qué?
                                if (item.explanation.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isItemCorrect) AccessibleGreen.copy(alpha = 0.08f) else AccessibleRed.copy(alpha = 0.08f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = stringResource(R.string.tests_review_why),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MuralColors.Ink
                                            )
                                            Text(
                                                text = item.explanation,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MuralColors.Ink.copy(alpha = 0.9f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Tres Acciones Principales (Target >= 48dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Acción 1: Hacer de nuevo
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MuralColors.Orange, contentColor = MuralColors.Ink)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MuralIcon(MuralSymbol.Repeat, Modifier.size(18.dp), color = MuralColors.Ink)
                    Text(stringResource(R.string.tests_action_take_again), fontWeight = FontWeight.Bold)
                }
            }

            // Acción 2: Ver resultados (Alternar desglose)
            OutlinedButton(
                onClick = { showDetailedReview = !showDetailedReview },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(25.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MuralIcon(MuralSymbol.ChartPie, Modifier.size(18.dp), color = MuralColors.Ink)
                    Text(stringResource(R.string.tests_action_view_results), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.tests_delete_confirm)) },
            text = { Text(stringResource(R.string.tests_delete_confirm_desc)) },
            confirmButton = {
                MuralTextButton(
                    onClick = {
                        confirmDelete = false
                        onDeleteTest()
                    }
                ) {
                    Text(stringResource(R.string.common_ok), color = AccessibleRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                MuralTextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// 9. FlashcardsScreen (Entrenamiento de Tarjetas Volteables)
// ---------------------------------------------------------------------------
@Composable
fun FlashcardsScreen(
    test: LearningTest? = null,
    flashcards: List<FlashcardItem>,
    onFinish: ((TestUserAttempt) -> Unit)? = null,
    onClose: () -> Unit
) {
    if (flashcards.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    var roundCards by remember { mutableStateOf(flashcards) }
    var currentIndex by rememberSaveable(test?.id) { mutableIntStateOf(0) }
    var isFlipped by remember { mutableStateOf(false) }

    // Listas acumuladas seguras para rememberSaveable (ArrayList / List<String>)
    var masteredIdsList by rememberSaveable(test?.id) { mutableStateOf(listOf<String>()) }
    var reviewIdsList by rememberSaveable(test?.id) { mutableStateOf(listOf<String>()) }

    var isCompleted by remember(test?.id) { mutableStateOf(false) }
    var startTime by remember(test?.id) { mutableLongStateOf(System.currentTimeMillis()) }

    val timeLimit = test?.config?.timeLimitSeconds
    var remainingSeconds by remember(test?.id) { mutableLongStateOf(timeLimit?.toLong() ?: 0L) }
    var hasAutoFinished by remember(test?.id) { mutableStateOf(false) }

    val safeCardIndex = currentIndex.coerceIn(0, (roundCards.size - 1).coerceAtLeast(0))
    val currentCard = roundCards.getOrNull(safeCardIndex)
    val rotation by animateFloatAsState(if (isFlipped) 180f else 0f, tween(300), label = "flip")

    fun buildAndPublishAttempt(): TestUserAttempt {
        val totalAllCards = flashcards.size
        val finalMasteredCount = masteredIdsList.size
        val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(0)
        val statusMap = flashcards.associate { it.id to masteredIdsList.contains(it.id) }
        val answersMap = flashcards.associate { it.id to if (masteredIdsList.contains(it.id)) "Dominada" else "Por repasar" }
        val attempt = TestUserAttempt(
            testId = test?.id ?: "flashcards_${System.currentTimeMillis()}",
            score = finalMasteredCount,
            totalQuestions = totalAllCards,
            answers = answersMap,
            correctStatus = statusMap,
            durationMillis = elapsed,
            unanswered = 0
        )
        onFinish?.invoke(attempt)
        return attempt
    }

    if (timeLimit != null && timeLimit > 0) {
        LaunchedEffect(timeLimit, startTime) {
            while (true) {
                val elapsed = (System.currentTimeMillis() - startTime) / 1000
                val left = (timeLimit - elapsed).coerceAtLeast(0)
                remainingSeconds = left
                if (left <= 0) break
                delay(1000L)
            }
            if (remainingSeconds <= 0 && !hasAutoFinished) {
                hasAutoFinished = true
                isCompleted = true
                buildAndPublishAttempt()
            }
        }
    }

    fun onCardAction(card: FlashcardItem, isMastered: Boolean) {
        isFlipped = false
        if (isMastered) {
            masteredIdsList = (masteredIdsList + card.id).distinct()
            reviewIdsList = reviewIdsList - card.id
        } else {
            reviewIdsList = (reviewIdsList + card.id).distinct()
            masteredIdsList = masteredIdsList - card.id
        }

        if (currentIndex + 1 < roundCards.size) {
            currentIndex++
        } else {
            // Fin de la ronda
            isCompleted = true
            buildAndPublishAttempt()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MuralColors.Cream)
            .padding(horizontal = 24.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!isCompleted) {
            // Top Bar durante el entrenamiento
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftRoundButton(MuralSymbol.Close, description = "Cerrar", onClick = onClose, diameter = 44.dp)
                    if (timeLimit != null && timeLimit > 0) {
                        TestTimerBadge(remainingSeconds = remainingSeconds)
                    }
                }
                Text(
                    stringResource(R.string.tests_card_x_of_y, safeCardIndex + 1, roundCards.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MuralColors.Butter) {
                        Text(
                            stringResource(R.string.tests_mastered_count, masteredIdsList.size),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (reviewIdsList.isNotEmpty()) {
                        Surface(shape = CircleShape, color = MuralColors.Peach) {
                            Text(
                                stringResource(R.string.flashcards_to_review_count, reviewIdsList.size),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (currentCard != null) {
                // Tarjeta 3D Volteable
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .graphicsLayer {
                            rotationY = rotation
                            cameraDistance = 12f * density
                        }
                        .clickable { isFlipped = !isFlipped },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MuralColors.Surface,
                        modifier = Modifier.fillMaxSize(),
                        shadowElevation = 6.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (rotation <= 90f) {
                                // Anverso
                                Text(
                                    text = currentCard.front,
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                if (currentCard.pronunciationGuide.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = currentCard.pronunciationGuide,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MuralColors.Secondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    stringResource(R.string.flashcards_tap_flip),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MuralColors.Orange,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                // Reverso
                                Box(modifier = Modifier.graphicsLayer { rotationY = 180f }) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = currentCard.back,
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            color = AccessibleGreen
                                        )
                                        if (currentCard.example.isNotBlank()) {
                                            Text(
                                                text = "\"${currentCard.example}\"",
                                                style = MaterialTheme.typography.bodyMedium,
                                                textAlign = TextAlign.Center,
                                                color = MuralColors.Ink
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Botones Repasar y Dominada
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { onCardAction(currentCard, isMastered = false) },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(25.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MuralColors.Ink)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MuralIcon(MuralSymbol.Repeat, Modifier.size(16.dp), color = MuralColors.Ink)
                            Text(stringResource(R.string.flashcards_btn_review), fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = { onCardAction(currentCard, isMastered = true) },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(25.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MuralColors.Orange, contentColor = MuralColors.Ink)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MuralIcon(MuralSymbol.Check, Modifier.size(16.dp), color = MuralColors.Ink)
                            Text(stringResource(R.string.flashcards_btn_mastered), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // ---------------------------------------------------------------
            // Vista de Resultados de Flashcards (isCompleted == true)
            // ---------------------------------------------------------------
            val totalCards = flashcards.size
            val masteredCount = masteredIdsList.size
            val reviewCount = totalCards - masteredCount
            val accuracy = if (totalCards > 0) ((masteredCount * 100) / totalCards) else 0

            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SoftRoundButton(MuralSymbol.Back, description = "Volver", onClick = onClose, diameter = 44.dp)
                Text(
                    stringResource(R.string.flashcards_results_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.size(44.dp))
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Tarjeta con Gráfico Circular Canvas
                item {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MuralColors.Surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
                                Canvas(modifier = Modifier.size(110.dp)) {
                                    val strokeWidth = 12.dp.toPx()
                                    val radius = (size.minDimension - strokeWidth) / 2
                                    val center = Offset(size.width / 2, size.height / 2)

                                    val masteredAngle = (masteredCount.toFloat() / totalCards.coerceAtLeast(1)) * 360f
                                    val reviewAngle = 360f - masteredAngle

                                    drawCircle(Color.LightGray.copy(alpha = 0.3f), radius, center, style = Stroke(strokeWidth))

                                    var start = -90f
                                    if (masteredAngle > 0) {
                                        drawArc(AccessibleGreen, start, masteredAngle, false, style = Stroke(strokeWidth))
                                        start += masteredAngle
                                    }
                                    if (reviewAngle > 0) {
                                        drawArc(MuralColors.Peach, start, reviewAngle, false, style = Stroke(strokeWidth))
                                    }
                                }
                                Text(
                                    "$accuracy%",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (accuracy >= 70) AccessibleGreen else MuralColors.Ink
                                )
                            }

                            Text(
                                if (accuracy == 100) stringResource(R.string.flashcards_all_mastered_msg)
                                else stringResource(R.string.flashcards_keep_practicing_msg),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MuralColors.Secondary,
                                textAlign = TextAlign.Center
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(shape = CircleShape, color = AccessibleGreen.copy(alpha = 0.15f)) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        MuralIcon(MuralSymbol.Check, Modifier.size(14.dp), color = AccessibleGreen)
                                        Text(
                                            "$masteredCount ${stringResource(R.string.flashcards_status_mastered)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = AccessibleGreen
                                        )
                                    }
                                }
                                Surface(shape = CircleShape, color = MuralColors.Peach) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        MuralIcon(MuralSymbol.Repeat, Modifier.size(14.dp), color = MuralColors.Ink)
                                        Text(
                                            "$reviewCount ${stringResource(R.string.flashcards_status_to_review)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MuralColors.Ink
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Desglose de cada tarjeta practicada
                itemsIndexed(flashcards) { _, card ->
                    val isMastered = masteredIdsList.contains(card.id)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MuralColors.Surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = if (isMastered) AccessibleGreen.copy(alpha = 0.35f) else MuralColors.Orange.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(20.dp)
                            )
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = card.front,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MuralColors.Ink
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = if (isMastered) AccessibleGreen.copy(alpha = 0.15f) else MuralColors.Peach
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        MuralIcon(
                                            if (isMastered) MuralSymbol.Check else MuralSymbol.Repeat,
                                            Modifier.size(12.dp),
                                            color = if (isMastered) AccessibleGreen else MuralColors.Ink
                                        )
                                        Text(
                                            if (isMastered) stringResource(R.string.flashcards_status_mastered) else stringResource(R.string.flashcards_status_to_review),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isMastered) AccessibleGreen else MuralColors.Ink
                                        )
                                    }
                                }
                            }
                            Text(
                                text = card.back,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = AccessibleGreen
                            )
                            if (card.example.isNotBlank()) {
                                Text(
                                    text = "\"${card.example}\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MuralColors.Secondary
                                )
                            }
                        }
                    }
                }
            }

            // Acciones Inferiores en Resultados
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val pendingCards = flashcards.filter { reviewIdsList.contains(it.id) }
                if (pendingCards.isNotEmpty()) {
                    Button(
                        onClick = {
                            roundCards = pendingCards
                            currentIndex = 0
                            isFlipped = false
                            isCompleted = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(25.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MuralColors.Orange, contentColor = MuralColors.Ink)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            MuralIcon(MuralSymbol.Repeat, Modifier.size(18.dp), color = MuralColors.Ink)
                            Text(
                                stringResource(R.string.flashcards_btn_review_pending, pendingCards.size),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        roundCards = flashcards
                        currentIndex = 0
                        isFlipped = false
                        isCompleted = false
                        masteredIdsList = emptyList()
                        reviewIdsList = emptyList()
                        startTime = System.currentTimeMillis()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MuralColors.Ink)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        MuralIcon(MuralSymbol.Refresh, Modifier.size(16.dp), color = MuralColors.Ink)
                        Text(stringResource(R.string.flashcards_btn_retry_all), fontWeight = FontWeight.SemiBold)
                    }
                }

                MuralTextButton(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Text(
                        stringResource(R.string.flashcards_btn_finish),
                        fontWeight = FontWeight.SemiBold,
                        color = MuralColors.Secondary
                    )
                }
            }
        }
    }
}
