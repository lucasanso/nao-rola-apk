package com.lucasanso.naorola.detection

/** Tunables for detection. Kept in one place so no magic numbers spread through the code. */
object DetectionConfig {
    /** Upper bound of nodes read per evaluation: each read may be a call into the target app. */
    const val MAX_NODES = 800
    const val MAX_DEPTH = 64

    /** Children inspected per scrollable container to find the page that fills the screen. */
    const val MAX_CHILDREN_PER_SCROLLABLE = 8

    /** Children read per node (protects against huge flat containers). */
    const val MAX_CHILDREN_PER_NODE = 200

    /** Visible children of one list tracked as feed items. */
    const val MAX_ITEMS_PER_SCROLLABLE = 40

    /**
     * "Portrait card": a tall thumbnail (real height/width between these ratios) with a plausible
     * card size. Two of them side by side form a Shorts shelf or grid row.
     */
    const val CARD_MIN_WIDTH = 0.18f
    const val CARD_MAX_WIDTH = 0.62f
    const val CARD_MIN_HEIGHT = 0.12f
    const val CARD_MIN_ASPECT = 1.3f
    const val CARD_MAX_ASPECT = 2.4f
    const val CARD_DUPLICATE_IOU = 0.7f
    const val PAIR_MIN_CENTER_DISTANCE = 0.2f
    const val PAIR_MIN_VERTICAL_OVERLAP = 0.5f

    /** A list whose visible children are all at least this fraction of its height is a pager. */
    const val PAGER_PAGE_FRACTION = 0.9f

    /** Covers smaller than this (fraction of the screen height) are not worth drawing. */
    const val MIN_REGION_HEIGHT = 0.02f

    /** Bottom navigation bar: tab buttons sit below this line and are at most this tall. */
    const val NAV_MIN_TOP = 0.8f
    const val NAV_MAX_HEIGHT = 0.12f

    /** Labels longer than this are not UI labels (tabs, buttons) and are ignored. */
    const val MAX_LABEL_LENGTH = 40

    /** A "full-screen pager" covers at least this fraction of the screen… */
    const val PAGER_MIN_WIDTH = 0.9f
    const val PAGER_MIN_HEIGHT = 0.8f

    /** …and its current page covers at least this fraction of the screen height. */
    const val PAGER_ITEM_MIN_HEIGHT = 0.8f

    /** Editable fields above this line are treated as possible browser address bars. */
    const val ADDRESS_BAR_MAX_TOP = 0.3f

    const val MAX_SCORE = 100
    const val CLASSIFIER_THRESHOLD = 60

    /** A negative signal at least this strong cancels a positive classification. */
    const val CLASSIFIER_VETO = 60

    /**
     * Health check: on the Shorts/Reels tab without the player being recognised for this long
     * (and at least this many evaluations), the detector is probably outdated.
     */
    const val SUSPECT_MIN_MILLIS = 4_000L
    const val SUSPECT_MIN_OBSERVATIONS = 3
}
