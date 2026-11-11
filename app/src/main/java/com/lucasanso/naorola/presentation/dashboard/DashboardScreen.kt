package com.lucasanso.naorola.presentation.dashboard

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucasanso.naorola.R
import com.lucasanso.naorola.core.SystemSettings
import com.lucasanso.naorola.domain.model.Platform
import com.lucasanso.naorola.presentation.common.containerViewModel
import com.lucasanso.naorola.presentation.components.PillTone
import com.lucasanso.naorola.presentation.components.ScreenScaffold
import com.lucasanso.naorola.presentation.components.SectionCard
import com.lucasanso.naorola.presentation.components.StatusPill
import com.lucasanso.naorola.presentation.components.durationText
import java.util.Date

@Composable
fun DashboardScreen(onOpenPermissions: () -> Unit, onOpenDiagnostics: () -> Unit) {
    val viewModel = containerViewModel { DashboardViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    ScreenScaffold(title = stringResource(R.string.app_name)) {
        Text(
            stringResource(R.string.dashboard_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StatusCard(state, onOpenPermissions)
        state.suspectedDetection.sortedBy { it.ordinal }.forEach { platform ->
            SuspicionCard(platform, onOpenDiagnostics) { viewModel.dismissSuspicion(platform) }
        }
        TodayCard(state)
        PlatformsCard(state)
        UsageCard(state)
    }
}

@Composable
private fun StatusCard(state: DashboardState, onOpenPermissions: () -> Unit) {
    val positive = !state.needsAttention
    val (title, body) = when (state.status) {
        ProtectionStatus.ACTIVE -> stringResource(R.string.status_active) to stringResource(R.string.status_active_body)
        ProtectionStatus.PAUSED -> {
            val time = state.pausedUntil?.let { DateFormat.getTimeFormat(LocalContext.current).format(Date(it)) }.orEmpty()
            stringResource(R.string.status_paused, time) to stringResource(R.string.status_paused_body)
        }
        ProtectionStatus.SERVICE_OFF -> stringResource(R.string.status_off) to stringResource(R.string.status_off_body)
        ProtectionStatus.SERVICE_NOT_RUNNING ->
            stringResource(R.string.status_not_running) to stringResource(R.string.status_not_running_body)
        ProtectionStatus.ADVANCED_PROTECTION ->
            stringResource(R.string.status_advanced_protection) to stringResource(R.string.status_advanced_protection_body)
    }
    SectionCard(
        containerColor = if (positive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
    ) {
        val onColor = if (positive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(if (positive) R.drawable.ic_mark else R.drawable.ic_warning),
                contentDescription = null,
                tint = onColor,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = onColor)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = onColor)
            }
        }
        if (state.totalMode || state.disciplineActive) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.totalMode) StatusPill(stringResource(R.string.badge_total), PillTone.NEUTRAL)
                if (state.disciplineActive) StatusPill(stringResource(R.string.badge_discipline), PillTone.NEUTRAL)
            }
        }
        if (state.needsAttention) {
            Button(onClick = onOpenPermissions, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.status_action_fix))
            }
        }
    }
}

@Composable
private fun SuspicionCard(platform: Platform, onOpenDiagnostics: () -> Unit, onDismiss: () -> Unit) {
    val where = stringResource(if (platform == Platform.INSTAGRAM) R.string.suspect_instagram else R.string.suspect_youtube)
    SectionCard(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
        val onColor = MaterialTheme.colorScheme.onTertiaryContainer
        Text(stringResource(R.string.suspect_title), style = MaterialTheme.typography.titleMedium, color = onColor)
        Text(stringResource(R.string.suspect_body, where), style = MaterialTheme.typography.bodyMedium, color = onColor)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOpenDiagnostics) { Text(stringResource(R.string.suspect_open_diagnostics)) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.suspect_dismiss)) }
        }
    }
}

@Composable
private fun TodayCard(state: DashboardState) {
    SectionCard(title = stringResource(R.string.today_title)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(state.blockedTotal.toString(), style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.today_blocked_label),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(R.string.today_breakdown, state.blockedYoutube, state.blockedInstagram, state.blockedOther),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlatformsCard(state: DashboardState) {
    SectionCard(title = stringResource(R.string.platforms_title)) {
        state.platforms.forEach { row ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(platformName(row.key), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                when (val status = row.status) {
                    PlatformStatus.Protected -> StatusPill(stringResource(R.string.platform_protected), PillTone.POSITIVE)
                    PlatformStatus.NotInstalled -> StatusPill(stringResource(R.string.platform_not_installed), PillTone.NEUTRAL)
                    is PlatformStatus.Unprotected -> StatusPill(
                        stringResource(R.string.platform_unprotected) + " · " + reasonText(status.reason),
                        if (status.reason == UnprotectedReason.SERVICE_OFF) PillTone.NEGATIVE else PillTone.NEUTRAL,
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageCard(state: DashboardState) {
    val context = LocalContext.current
    SectionCard(title = stringResource(R.string.usage_title)) {
        when (val usage = state.usage) {
            UsageState.NeedsPermission -> {
                Text(stringResource(R.string.usage_no_permission), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { SystemSettings.openUsageAccess(context) }) { Text(stringResource(R.string.usage_grant)) }
                ShortVideoLines(state)
            }
            is UsageState.Available -> {
                UsageLine(stringResource(R.string.app_youtube), usage.youtubeMillis, state.shortVideoYoutubeMillis)
                UsageLine(stringResource(R.string.app_instagram), usage.instagramMillis, state.shortVideoInstagramMillis)
            }
        }
        Text(
            stringResource(R.string.usage_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ShortVideoLines(state: DashboardState) {
    UsageLine(stringResource(R.string.app_youtube), inAppMillis = null, shortVideoMillis = state.shortVideoYoutubeMillis)
    UsageLine(stringResource(R.string.app_instagram), inAppMillis = null, shortVideoMillis = state.shortVideoInstagramMillis)
}

@Composable
private fun UsageLine(name: String, inAppMillis: Long?, shortVideoMillis: Long) {
    Column {
        Text(name, style = MaterialTheme.typography.bodyLarge)
        val parts = listOfNotNull(
            inAppMillis?.let { stringResource(R.string.usage_in_app, durationText(it)) },
            stringResource(R.string.usage_short_video, durationText(shortVideoMillis)),
        )
        Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun platformName(key: PlatformKey) = stringResource(
    when (key) {
        PlatformKey.YOUTUBE -> R.string.platform_youtube
        PlatformKey.INSTAGRAM -> R.string.platform_instagram
        PlatformKey.BROWSER -> R.string.platform_browser
        PlatformKey.OTHER_APPS -> R.string.platform_other_apps
    },
)

@Composable
private fun reasonText(reason: UnprotectedReason) = stringResource(
    when (reason) {
        UnprotectedReason.PLATFORM_OFF -> R.string.reason_platform_off
        UnprotectedReason.OUTSIDE_SCHEDULE -> R.string.reason_outside_schedule
        UnprotectedReason.EXCEPTION -> R.string.reason_exception
        UnprotectedReason.SNOOZED -> R.string.reason_snoozed
        UnprotectedReason.SERVICE_OFF -> R.string.reason_service_off
    },
)
