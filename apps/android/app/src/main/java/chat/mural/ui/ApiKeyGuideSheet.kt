package chat.mural.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import chat.mural.R

/**
 * Manual de Bienvenida Interactivo (First-Time Onboarding Guide).
 * Explica de forma visual y con iconos vectoriales cómo conseguir la API Key
 * en Google AI Studio y cómo introducirla en Yamy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeyGuideSheet(
    onDismiss: () -> Unit,
    onConfigureKey: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var step by rememberSaveable { mutableIntStateOf(0) }

    fun openGoogleAiStudio() {
        runCatching { uriHandler.openUri("https://aistudio.google.com/app/apikey") }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MuralColors.Cream,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .testTag("api-key-guide-sheet")
        ) {
            SoftAnimatedBackground(Modifier.matchParentSize())
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Indicador de Progreso (Paso 1, 2, 3)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    repeat(3) { index ->
                        Box(
                            Modifier
                                .size(width = if (index == step) 28.dp else 8.dp, height = 6.dp)
                                .background(
                                    if (index == step) MuralColors.Orange else MuralColors.WatercolorWash,
                                    CircleShape
                                )
                        )
                    }
                }

                AnimatedContent(
                    targetState = step,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "GuideStepTransition"
                ) { currentStep ->
                    when (currentStep) {
                        0 -> GuideStep1(onNext = { step = 1 }, onSkip = onDismiss)
                        1 -> GuideStep2(
                            onNext = { step = 2 },
                            onBack = { step = 0 },
                            onOpenStudio = ::openGoogleAiStudio
                        )
                        else -> GuideStep3(
                            onConfigure = onConfigureKey,
                            onBack = { step = 1 },
                            onSkip = onDismiss
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideStep1(onNext: () -> Unit, onSkip: () -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MuralOrb(modifier = Modifier.size(92.dp))

        Text(
            text = stringResource(R.string.api_key_guide_step1_badge),
            style = MaterialTheme.typography.labelMedium,
            color = MuralColors.Orange,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = stringResource(R.string.api_key_guide_step1_title),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )

        Text(
            text = stringResource(R.string.api_key_guide_step1_body),
            color = MuralColors.Secondary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MuralColors.Surface.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, MuralColors.ControlBorder),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MuralIcon(MuralSymbol.Check, Modifier.size(18.dp), color = MuralColors.Orange)
                    Text(
                        text = "100% Privado: tu historial se queda en tu dispositivo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MuralColors.Ink
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MuralIcon(MuralSymbol.Sparkles, Modifier.size(18.dp), color = MuralColors.Orange)
                    Text(
                        text = "Directo con Google Gemini: sin intermediarios ni cobros",
                        style = MaterialTheme.typography.bodySmall,
                        color = MuralColors.Ink
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = onNext,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = MuralColors.Orange,
                contentColor = MuralColors.Ink
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("guide-step1-next")
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.api_key_guide_next), fontWeight = FontWeight.SemiBold)
                MuralIcon(MuralSymbol.ChevronRight, Modifier.size(16.dp), color = MuralColors.Ink)
            }
        }

        MuralTextButton(onClick = onSkip) {
            Text(stringResource(R.string.api_key_guide_skip), color = MuralColors.Secondary)
        }
    }
}

@Composable
private fun GuideStep2(onNext: () -> Unit, onBack: () -> Unit, onOpenStudio: () -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MuralColors.Butter,
            modifier = Modifier.size(76.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MuralIcon(MuralSymbol.Globe, Modifier.size(40.dp), color = MuralColors.Ink)
            }
        }

        Text(
            text = stringResource(R.string.api_key_guide_step2_badge),
            style = MaterialTheme.typography.labelMedium,
            color = MuralColors.Orange,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = stringResource(R.string.api_key_guide_step2_title),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )

        // Tarjetas de pasos
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MuralColors.Surface.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, MuralColors.ControlBorder),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GuideBulletRow(symbol = MuralSymbol.Globe, text = "1. Accede a Google AI Studio con tu cuenta de Google.")
                GuideBulletRow(symbol = MuralSymbol.Key, text = "2. Haz clic en \"Get API key\" (o Crear clave de API).")
                GuideBulletRow(symbol = MuralSymbol.Check, text = "3. Copia la clave generada al portapapeles.")
            }
        }

        // Botón directo a Google AI Studio
        Button(
            onClick = onOpenStudio,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = MuralColors.Sage.copy(alpha = 0.65f),
                contentColor = MuralColors.Ink
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("guide-open-ai-studio")
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MuralIcon(MuralSymbol.ExternalLink, Modifier.size(18.dp), color = MuralColors.Ink)
                Text(
                    text = stringResource(R.string.api_key_guide_step2_action),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1f).heightIn(min = 52.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MuralIcon(MuralSymbol.Back, Modifier.size(16.dp), color = MuralColors.Ink)
                    Text(stringResource(R.string.api_key_guide_back))
                }
            }

            Button(
                onClick = onNext,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MuralColors.Orange,
                    contentColor = MuralColors.Ink
                ),
                modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("guide-step2-next")
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.api_key_guide_next), fontWeight = FontWeight.SemiBold)
                    MuralIcon(MuralSymbol.ChevronRight, Modifier.size(16.dp), color = MuralColors.Ink)
                }
            }
        }
    }
}

@Composable
private fun GuideStep3(onConfigure: () -> Unit, onBack: () -> Unit, onSkip: () -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MuralColors.Butter,
            modifier = Modifier.size(76.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MuralIcon(MuralSymbol.Key, Modifier.size(40.dp), color = MuralColors.Ink)
            }
        }

        Text(
            text = stringResource(R.string.api_key_guide_step3_badge),
            style = MaterialTheme.typography.labelMedium,
            color = MuralColors.Orange,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = stringResource(R.string.api_key_guide_step3_title),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )

        Text(
            text = stringResource(R.string.api_key_guide_step3_body),
            color = MuralColors.Secondary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MuralColors.Surface.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, MuralColors.ControlBorder),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MuralIcon(MuralSymbol.Settings, Modifier.size(24.dp), color = MuralColors.Orange)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Ruta directa en la app:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MuralColors.Secondary
                    )
                    Text(
                        text = "Ajustes ➔ Avanzado ➔ Clave de API",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MuralColors.Ink
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Botón primario: Configurar mi clave ahora (abre Settings con KeyDialog)
        Button(
            onClick = onConfigure,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = MuralColors.Orange,
                contentColor = MuralColors.Ink
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("guide-configure-now")
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MuralIcon(MuralSymbol.Key, Modifier.size(18.dp), color = MuralColors.Ink)
                Text(
                    stringResource(R.string.api_key_guide_step3_action),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MuralTextButton(onClick = onBack) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MuralIcon(MuralSymbol.Back, Modifier.size(16.dp), color = MuralColors.Secondary)
                    Text(stringResource(R.string.api_key_guide_back), color = MuralColors.Secondary)
                }
            }

            MuralTextButton(onClick = onSkip) {
                Text(stringResource(R.string.api_key_guide_skip), color = MuralColors.Secondary)
            }
        }
    }
}

@Composable
private fun GuideBulletRow(symbol: MuralSymbol, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MuralIcon(symbol, Modifier.size(18.dp), color = MuralColors.Orange)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MuralColors.Ink)
    }
}
