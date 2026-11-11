package com.lucasanso.naorola.core

/** Source of wall-clock time in epoch milliseconds. Injected everywhere so tests control time. */
fun interface Clock {
    fun nowMillis(): Long
}
