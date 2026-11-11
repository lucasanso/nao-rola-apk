package com.lucasanso.naorola.detection.rules

import com.lucasanso.naorola.detection.DetectionConfig
import com.lucasanso.naorola.detection.signal.Polarity
import com.lucasanso.naorola.detection.signal.Signal
import com.lucasanso.naorola.detection.tree.ScreenSummary
import com.lucasanso.naorola.detection.tree.ScrollableInfo
import com.lucasanso.naorola.domain.model.ScreenContext

/**
 * Detection rules are data: ids, prefixes and weights live in rule sets (YouTubeRules,
 * InstagramRules…), and this file holds the generic way of evaluating them.
 */
sealed interface Rule {
    val signalId: String
    val polarity: Polarity
    val weight: Int
}

/** Fires when any of [ids] is on screen with at least [minHeight] of the screen height. */
data class AnyIdRule(
    override val signalId: String,
    override val polarity: Polarity,
    override val weight: Int,
    val ids: Set<String>,
    val minHeight: Float = 0f,
) : Rule

/** Fires when at least [minDistinct] different ids start with [prefix] (ignoring [exclude]). */
data class IdPrefixRule(
    override val signalId: String,
    override val polarity: Polarity,
    override val weight: Int,
    val prefix: String,
    val minDistinct: Int = 1,
    val exclude: Set<String> = emptySet(),
) : Rule

/** Fires when one of [ids] is in the selected state (e.g. the Reels tab). */
data class SelectedIdRule(
    override val signalId: String,
    override val polarity: Polarity,
    override val weight: Int,
    val ids: Set<String>,
) : Rule

/** Fires when a selected element is labelled with one of [labels] (lower case). */
data class SelectedLabelRule(
    override val signalId: String,
    override val polarity: Polarity,
    override val weight: Int,
    val labels: Set<String>,
) : Rule

/**
 * Fires when a vertical scrolling container fills the screen and its current page also fills
 * the screen: the shape of every short-video feed, independent of ids.
 */
data class FullScreenPagerRule(
    override val signalId: String,
    override val polarity: Polarity,
    override val weight: Int,
    val minWidth: Float = DetectionConfig.PAGER_MIN_WIDTH,
    val minHeight: Float = DetectionConfig.PAGER_MIN_HEIGHT,
    val minItemHeight: Float = DetectionConfig.PAGER_ITEM_MIN_HEIGHT,
) : Rule

/** Maps evidence to a [ScreenContext]; the first matching rule wins. */
data class ContextRule(
    val context: ScreenContext,
    val anyIds: Set<String> = emptySet(),
    val anyIdPrefixes: Set<String> = emptySet(),
    val anySelectedIds: Set<String> = emptySet(),
)

/** Version information shown in the app and written into diagnostic dumps. */
data class RuleSetInfo(val name: String, val version: String, val status: String)

data class RuleEvaluation(val signals: List<Signal>, val pager: ScrollableInfo?)

object RuleEvaluator {

    fun evaluate(summary: ScreenSummary, rules: List<Rule>): RuleEvaluation {
        val signals = ArrayList<Signal>()
        var pager: ScrollableInfo? = null
        for (rule in rules) {
            val fired = when (rule) {
                is AnyIdRule -> rule.ids.any { id -> summary.ids[id]?.let { it.height >= rule.minHeight } == true }
                is IdPrefixRule -> distinctWithPrefix(summary, rule.prefix, rule.exclude) >= rule.minDistinct
                is SelectedIdRule -> rule.ids.any { it in summary.selectedIds }
                is SelectedLabelRule -> rule.labels.any { it in summary.selectedLabels }
                is FullScreenPagerRule -> {
                    val match = findPager(summary, rule)
                    if (match != null && pager == null) pager = match
                    match != null
                }
            }
            if (fired) signals += Signal(rule.signalId, rule.polarity, rule.weight)
        }
        return RuleEvaluation(signals, pager)
    }

    fun contextOf(summary: ScreenSummary, rules: List<ContextRule>): ScreenContext {
        for (rule in rules) {
            val byId = rule.anyIds.any { it in summary.ids }
            val byPrefix = rule.anyIdPrefixes.any { prefix -> summary.ids.keys.any { it.startsWith(prefix) } }
            val bySelected = rule.anySelectedIds.any { it in summary.selectedIds }
            if (byId || byPrefix || bySelected) return rule.context
        }
        return ScreenContext.UNKNOWN
    }

    private fun distinctWithPrefix(summary: ScreenSummary, prefix: String, exclude: Set<String>): Int =
        summary.ids.keys.count { it.startsWith(prefix) && it !in exclude }

    private fun findPager(summary: ScreenSummary, rule: FullScreenPagerRule): ScrollableInfo? =
        summary.scrollables
            .filter { s ->
                s.bounds.width >= rule.minWidth &&
                    s.bounds.height >= rule.minHeight &&
                    s.dominantChildHeight >= rule.minItemHeight &&
                    !s.isHorizontal
            }
            .maxByOrNull { it.bounds.height }
}
