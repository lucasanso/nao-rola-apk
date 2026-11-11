package com.lucasanso.naorola.presentation.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucasanso.naorola.R
import com.lucasanso.naorola.core.SystemSettings
import com.lucasanso.naorola.domain.model.Platform
import com.lucasanso.naorola.domain.stats.AvoidedExposureEstimator.Estimate
import com.lucasanso.naorola.domain.stats.DayStats
import com.lucasanso.naorola.presentation.common.containerViewModel
import com.lucasanso.naorola.presentation.components.ScreenScaffold
import com.lucasanso.naorola.presentation.components.SectionCard
import com.lucasanso.naorola.presentation.components.durationText
import com.lucasanso.naorola.presentation.theme.chartBarColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LOCALE = Locale.forLanguageTag("pt-BR")
private val WEEKDAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", LOCALE)
private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM", LOCALE)

private fun weekday(day: LocalDate) = WEEKDAY.format(day).trimEnd('.')

@Composable
fun StatsScreen() {
    val viewModel = containerViewModel { StatsViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }

    ScreenScaffold(title = stringResource(R.string.stats_title)) {
        SectionCard(title = stringResource(R.string.stats_week)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(state.totalBlocked.toString(), style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.today_blocked_label),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(
                    R.string.today_breakdown,
                    state.blockedOn(Platform.YOUTUBE),
                    state.blockedOn(Platform.INSTAGRAM),
                    state.blockedOn(Platform.OTHER),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = stringResource(R.string.stats_per_day)) {
            DailyBarsChart(state.days, state.selected, state.today, onSelect = viewModel::select)
            Text(
                stringResource(R.string.stats_tap_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = stringResource(R.string.stats_day_by_day)) {
            // Table view of the same data (newest first): every number the chart shows, and more.
            state.days.asReversed().forEach { day ->
                DayRow(day, selected = day.date == state.selected, isToday = day.date == state.today) {
                    viewModel.select(day.date)
                }
            }
            if (!state.usageGranted) {
                Text(stringResource(R.string.stats_usage_permission), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { SystemSettings.openUsageAccess(context) }) { Text(stringResource(R.string.usage_grant)) }
            }
        }

        SectionCard(title = stringResource(R.string.stats_estimate_title)) {
            when (val estimate = state.estimate) {
                is Estimate.Available -> {
                    Text(
                        stringResource(R.string.stats_estimate_value, durationText(estimate.millis)),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        stringResource(
                            R.string.stats_estimate_formula,
                            pluralStringResource(R.plurals.count_blocks, estimate.blocked, estimate.blocked),
                            sessionLength(estimate.medianSessionMillis),
                            pluralStringResource(R.plurals.count_sessions, estimate.sessions, estimate.sessions),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is Estimate.Insufficient -> Text(
                    stringResource(
                        R.string.stats_estimate_insufficient,
                        pluralStringResource(R.plurals.count_sessions, estimate.needed, estimate.needed),
                        estimate.sessions,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                null -> Unit
            }
        }

        Text(
            stringResource(R.string.stats_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun sessionLength(millis: Long): String =
    if (millis < 60_000L) stringResource(R.string.seconds_long, (millis / 1000).toInt()) else durationText(millis)

/**
 * Single-series column chart (one hue, validated tones): thin bars with rounded data ends and a
 * square baseline, a hairline axis, and the value printed only above the selected day. Each
 * column is a full-height touch target and is described for screen readers.
 */
@Composable
private fun DailyBarsChart(days: List<DayStats>, selected: LocalDate?, today: LocalDate?, onSelect: (LocalDate) -> Unit) {
    val barColor = chartBarColor()
    val max = (days.maxOfOrNull { it.blockedTotal } ?: 0).coerceAtLeast(1)
    Column {
        BoxWithConstraints(Modifier.fillMaxWidth().height(CHART_HEIGHT)) {
            val labelSpace = 22.dp
            val plotHeight = maxHeight - labelSpace
            Row(Modifier.fillMaxSize()) {
                days.forEach { day ->
                    val isSelected = day.date == selected
                    val label = pluralStringResource(R.plurals.stats_bar_description, day.blockedTotal, dayLabel(day.date, today), day.blockedTotal)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onSelect(day.date) }
                            .semantics {
                                contentDescription = label
                                this.selected = isSelected
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        if (isSelected) {
                            Text(
                                day.blockedTotal.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        val barHeight = plotHeight * (day.blockedTotal.toFloat() / max) * BAR_MAX_FRACTION
                        if (day.blockedTotal > 0) {
                            Box(
                                Modifier
                                    .width(BAR_WIDTH)
                                    .height(barHeight)
                                    .background(barColor, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            days.forEach { day ->
                Text(
                    weekday(day.date),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (day.date == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (day.date == selected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun DayRow(day: DayStats, selected: Boolean, isToday: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerLow
    Column(
        Modifier
            .fillMaxWidth()
            .background(background, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                dayLabel(day.date, if (isToday) day.date else null),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(pluralStringResource(R.plurals.stats_blocked_count, day.blockedTotal, day.blockedTotal), style = MaterialTheme.typography.bodyLarge)
        }
        val short = (day.shortVideoMillis[Platform.YOUTUBE] ?: 0L) + (day.shortVideoMillis[Platform.INSTAGRAM] ?: 0L) +
            (day.shortVideoMillis[Platform.OTHER] ?: 0L)
        Text(
            stringResource(R.string.stats_short_video, durationText(short)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val usage = day.appUsageMillis
        Text(
            if (usage == null) {
                stringResource(R.string.stats_app_usage_unknown)
            } else {
                stringResource(
                    R.string.stats_app_usage,
                    durationText(usage[Platform.YOUTUBE] ?: 0L),
                    durationText(usage[Platform.INSTAGRAM] ?: 0L),
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun dayLabel(day: LocalDate, today: LocalDate?): String {
    val base = "${weekday(day)}, ${DAY_MONTH.format(day)}"
    return if (day == today) "$base · ${stringResource(R.string.stats_today)}" else base
}

private val CHART_HEIGHT = 168.dp
private val BAR_WIDTH = 20.dp
private const val BAR_MAX_FRACTION = 0.82f
