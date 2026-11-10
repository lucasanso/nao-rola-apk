package com.lucasanso.naorola.detection

/** Tunables for detection. Kept in one place so no magic numbers spread through the code. */
object DetectionConfig {
    /** Upper bound of nodes read per evaluation: each read may be a call into the target app. */
    const val MAX_NODES = 800
    const val MAX_DEPTH = 64

    /** Children inspected per scrollable container to find the page that fills the screen. */
    const val MAX_CHILDREN_PER_SCROLLABLE = 8

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
}
