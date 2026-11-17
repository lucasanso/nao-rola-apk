package com.lucasanso.naorola.detection.rules

/**
 * "Other formats": apps that are entirely short videos and can be blocked as a whole,
 * identified by package name. Package names to be confirmed on a device.
 */
object ShortVideoApps {
    val KNOWN: Map<String, String> = linkedMapOf(
        "com.zhiliaoapp.musically" to "TikTok",
        "com.ss.android.ugc.trill" to "TikTok",
        "com.zhiliaoapp.musically.go" to "TikTok Lite",
        "com.kwai.video" to "Kwai",
    )
}
