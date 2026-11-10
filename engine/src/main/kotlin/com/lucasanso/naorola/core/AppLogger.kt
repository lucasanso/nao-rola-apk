package com.lucasanso.naorola.core

/**
 * Local, technical logging. Implementations must never receive on-screen content
 * (texts, descriptions, URLs) — only identifiers of detectors, rules and errors.
 */
interface AppLogger {
    fun info(tag: String, message: String)
    fun warn(tag: String, message: String, error: Throwable? = null)
    fun error(tag: String, message: String, error: Throwable? = null)

    object None : AppLogger {
        override fun info(tag: String, message: String) = Unit
        override fun warn(tag: String, message: String, error: Throwable?) = Unit
        override fun error(tag: String, message: String, error: Throwable?) = Unit
    }
}
