package com.lucasanso.naorola.accessibility

import android.view.accessibility.AccessibilityNodeInfo

/** What the protection needs from the accessibility service (kept narrow on purpose). */
interface ScreenAccess {
    val ownPackage: String
    fun activeRoot(): AccessibilityNodeInfo?

    /** Package of the app window currently in front (ignores overlays and system bars). */
    fun foregroundAppPackage(): String?

    /** Used only when the app window reports no bounds. */
    fun displaySize(): Pair<Int, Int>
    fun goBack(): Boolean
    fun goHome(): Boolean
    fun setMonitoredPackages(packages: Set<String>)

    /** Top edge of the on-screen keyboard in screen pixels, or null when it is closed. */
    fun inputMethodTop(): Int? = null

    /** True when a large system panel (notification shade, quick settings) is open over the app. */
    fun systemPanelOpen(): Boolean = false
}
