package com.lucasanso.naorola.blocking

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.lucasanso.naorola.domain.model.Surface
import com.lucasanso.naorola.domain.session.UserAction
import com.lucasanso.naorola.presentation.theme.NaoRolaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BlockScreenTest {
    @get:Rule val compose = createComposeRule()
    private val actions = mutableListOf<UserAction>()

    private fun show(surface: Surface, snooze: Boolean = true, discipline: Boolean = false) {
        compose.setContent {
            NaoRolaTheme {
                BlockScreen(BlockScreenContent(surface, snooze, 10, discipline, dynamicColor = false)) { actions += it }
            }
        }
    }

    @Test fun youtubeButtonsSendTheRightActions() {
        show(Surface.SHORTS_PLAYER)
        compose.onNodeWithText("Você realmente queria assistir a isso?").assertIsDisplayed()
        compose.onNodeWithText("Voltar para o YouTube").performClick()
        compose.onNodeWithText("Sair do YouTube").performClick()
        compose.onNodeWithText("Desativar por 10 minutos").performClick()
        assertEquals(listOf(UserAction.GO_BACK, UserAction.LEAVE_APP, UserAction.SNOOZE), actions)
    }

    @Test fun noSnoozeButtonWhenNotAllowed() {
        show(Surface.REELS_VIEWER, snooze = false, discipline = true)
        compose.onNodeWithText("Voltar para o Instagram").assertIsDisplayed()
        compose.onNodeWithText("Modo Disciplina ativo").assertIsDisplayed()
        compose.onNodeWithTag(TAG_SNOOZE).assertDoesNotExist()
    }

    @Test fun shortVideoAppsOfferOnlyLeaving() {
        show(Surface.SHORT_VIDEO_APP)
        compose.onNodeWithTag(TAG_BACK).assertDoesNotExist()
        compose.onNodeWithText("Sair do app").performClick()
        assertEquals(listOf(UserAction.LEAVE_APP), actions)
    }
}
