package chat.mural.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import chat.mural.core.GuestMinuteState
import chat.mural.core.GuestMinuteStatus

/**
 * Modal de aviso de Conexión Requerida.
 * Notifica al usuario que para utilizar Yamy debe conectarse a su propia API Key de Google AI Studio.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuestMinutesSheet(
    state: GuestMinuteState = GuestMinuteState(GuestMinuteStatus.UNAVAILABLE),
    signedIn: Boolean = false,
    busy: Boolean = false,
    ready: Boolean = false,
    memberRemaining: Long? = null,
    onContinue: () -> Unit = {},
    onSignIn: (() -> Unit)? = null,
    onBuy: (() -> Unit)? = null,
    onRetry: () -> Unit = {},
    onSettings: () -> Unit,
    onDismiss: () -> Unit,
    onGenerateKey: (() -> Unit)? = null
) {
    val uriHandler = LocalUriHandler.current
    val openGoogleAiStudio: () -> Unit = onGenerateKey ?: {
        runCatching { uriHandler.openUri("https://aistudio.google.com/app/apikey") }
        Unit
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MuralColors.Cream,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .testTag("guest-minutes-sheet")
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
                MuralOrb(modifier = Modifier.size(100.dp))

                Text(
                    text = stringResource(R.string.api_key_required_title),
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .semantics { heading() }
                        .testTag("guest-minutes-title")
                )

                Text(
                    text = stringResource(R.string.api_key_required_detail),
                    color = MuralColors.Secondary,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(Modifier.height(8.dp))

                // Acción 1: "Generar mi API Key" -> Abre enlace en navegador a Google AI Studio
                Button(
                    onClick = openGoogleAiStudio,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MuralColors.Orange,
                        contentColor = MuralColors.Ink
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
                        .testTag("btn-generate-api-key")
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MuralIcon(MuralSymbol.ExternalLink, Modifier.size(18.dp), color = MuralColors.Ink)
                        Text(
                            text = stringResource(R.string.api_key_generate_button),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Acción 2: "Ya tengo mi API Key" -> Cierra modal y redirecciona a Ajustes
                OutlinedButton(
                    onClick = onSettings,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.5.dp, MuralColors.ControlBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MuralColors.Ink),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
                        .testTag("btn-have-api-key")
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MuralIcon(MuralSymbol.Key, Modifier.size(18.dp), color = MuralColors.Ink)
                        Text(
                            text = stringResource(R.string.api_key_have_key_button),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Acción 3: Secundaria limpia ("Listo")
                MuralTextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("guest-dismiss")
                ) {
                    Text(
                        text = stringResource(R.string.api_key_dismiss_button),
                        color = MuralColors.Secondary
                    )
                }
            }
        }
    }
}
