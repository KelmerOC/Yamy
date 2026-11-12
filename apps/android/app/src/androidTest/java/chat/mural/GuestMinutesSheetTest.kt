package chat.mural

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import chat.mural.core.*
import chat.mural.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuestMinutesSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun connectionRequiredSheetDisplaysTitleAndApiKeyButtons() {
        var generatedKeyClicks = 0
        var settingsClicks = 0
        var dismissClicks = 0

        compose.setContent {
            MuralTheme {
                GuestMinutesSheet(
                    state = GuestMinuteState(GuestMinuteStatus.UNAVAILABLE),
                    onSettings = { settingsClicks++ },
                    onDismiss = { dismissClicks++ },
                    onGenerateKey = { generatedKeyClicks++ }
                )
            }
        }

        compose.onNodeWithTag("guest-minutes-title").assertTextEquals("Connection required")
        compose.onNodeWithTag("btn-generate-api-key").assertExists().performClick()
        assertEquals(1, generatedKeyClicks)

        compose.onNodeWithTag("btn-have-api-key").assertExists().performClick()
        assertEquals(1, settingsClicks)

        compose.onNodeWithTag("guest-dismiss").assertExists().performClick()
        assertEquals(1, dismissClicks)
    }

    @Test fun consentIncludesAdultConfirmationWithoutDateOfBirthForm() {
        compose.setContent { MuralTheme { AIConsentDialog({}, {}) } }
        compose.onNodeWithTag("adult-confirmation").performScrollTo().assertTextEquals("By continuing, you confirm you’re 18 or older.")
        compose.onNodeWithTag("ai-consent-agree").performScrollTo().assertIsEnabled()
    }
}
