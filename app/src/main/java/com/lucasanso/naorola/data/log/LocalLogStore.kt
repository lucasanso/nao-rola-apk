package com.lucasanso.naorola.data.log

import android.util.Log
import com.lucasanso.naorola.core.AppLogger
import com.lucasanso.naorola.core.Clock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Technical log kept only on the device (rotating file, never sent anywhere). Callers must pass
 * identifiers and errors only — never on-screen content. Writes happen off the caller's thread.
 */
class LocalLogStore(
    private val directory: File,
    private val clock: Clock,
    private val alsoLogcat: Boolean,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) : AppLogger {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "naorola-log") }
    private val current get() = File(directory, FILE_NAME)
    private val previous get() = File(directory, "$FILE_NAME.1")

    override fun info(tag: String, message: String) = log('I', tag, message, null)
    override fun warn(tag: String, message: String, error: Throwable?) = log('W', tag, message, error)
    override fun error(tag: String, message: String, error: Throwable?) = log('E', tag, message, error)

    /** Both files, oldest first, for viewing or sharing. */
    fun files(): List<File> = listOf(previous, current).filter { it.exists() }

    fun readAll(): String = files().joinToString("") { runCatching { it.readText() }.getOrDefault("") }

    fun clear() {
        executor.execute { files().forEach { it.delete() } }
    }

    private fun log(level: Char, tag: String, message: String, error: Throwable?) {
        val time = clock.nowMillis()
        if (alsoLogcat) {
            when (level) {
                'E' -> Log.e("NaoRola/$tag", message, error)
                'W' -> Log.w("NaoRola/$tag", message, error)
                else -> Log.i("NaoRola/$tag", message)
            }
        }
        val errorText = error?.let { " | ${it.javaClass.simpleName}: ${it.message}" }.orEmpty()
        val line = "${format(time)} $level/$tag: $message$errorText\n"
        executor.execute {
            runCatching {
                directory.mkdirs()
                if (current.length() > maxBytes) {
                    previous.delete()
                    current.renameTo(previous)
                }
                current.appendText(line)
            }
        }
    }

    private fun format(millis: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(Date(millis))

    companion object {
        const val FILE_NAME = "naorola.log"
        const val DEFAULT_MAX_BYTES = 256 * 1024L
    }
}
