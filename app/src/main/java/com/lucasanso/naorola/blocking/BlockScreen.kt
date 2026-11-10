package com.lucasanso.naorola.blocking

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lucasanso.naorola.R
import com.lucasanso.naorola.domain.model.Surface
import com.lucasanso.naorola.domain.session.UserAction

/** What the block screen shows. Built from a [com.lucasanso.naorola.domain.session.Command.ShowBlockScreen]. */
data class BlockScreenContent(
    val surface: Surface,
    val snoozeAllowed: Boolean,
    val snoozeMinutes: Int,
    val disciplineActive: Boolean,
    val dynamicColor: Boolean,
)

private data class BlockTexts(@StringRes val body: Int, @StringRes val back: Int?, @StringRes val leave: Int)

private fun textsFor(surface: Surface) = when (surface) {
    Surface.SHORTS_PLAYER -> BlockTexts(R.string.block_body_shorts, R.string.block_back_youtube, R.string.block_leave_youtube)
    Surface.REELS_VIEWER, Surface.FEED_REEL_ITEM ->
        BlockTexts(R.string.block_body_reels, R.string.block_back_instagram, R.string.block_leave_instagram)
    Surface.WEB_SHORTS -> BlockTexts(R.string.block_body_web_shorts, R.string.block_back, R.string.block_leave_browser)
    Surface.WEB_REELS -> BlockTexts(R.string.block_body_web_reels, R.string.block_back, R.string.block_leave_browser)
    // Going "back" inside an app made only of short videos lands on more short videos.
    Surface.SHORT_VIDEO_APP -> BlockTexts(R.string.block_body_app, null, R.string.block_leave_app)
}

@Composable
fun BlockScreen(content: BlockScreenContent, onAction: (UserAction) -> Unit) {
    val texts = textsFor(content.surface)
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag(TAG_ROOT),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visibleState = visible,
            enter = fadeIn(tween(ENTER_MILLIS)) + slideInVertically(tween(ENTER_MILLIS)) { it / 12 },
        ) {
            Column(
                modifier = Modifier.widthIn(max = 420.dp).padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_mark),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.block_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(texts.body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (content.disciplineActive) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.block_discipline_note),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(36.dp))
                texts.back?.let { back ->
                    Button(
                        onClick = { onAction(UserAction.GO_BACK) },
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag(TAG_BACK),
                    ) { Text(stringResource(back)) }
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedButton(
                    onClick = { onAction(UserAction.LEAVE_APP) },
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag(TAG_LEAVE),
                ) { Text(stringResource(texts.leave)) }
                if (content.snoozeAllowed) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { onAction(UserAction.SNOOZE) },
                        modifier = Modifier.testTag(TAG_SNOOZE),
                    ) {
                        Text(pluralStringResource(R.plurals.block_snooze, content.snoozeMinutes, content.snoozeMinutes))
                    }
                }
            }
        }
    }
}

const val TAG_ROOT = "block_root"
const val TAG_BACK = "block_back"
const val TAG_LEAVE = "block_leave"
const val TAG_SNOOZE = "block_snooze"
private const val ENTER_MILLIS = 220
