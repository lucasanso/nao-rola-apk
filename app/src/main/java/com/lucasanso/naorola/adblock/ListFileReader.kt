package com.lucasanso.naorola.adblock

import android.content.Context
import android.net.Uri
import com.lucasanso.naorola.core.AppLogger
import java.io.ByteArrayOutputStream

/** Reads a list file chosen by the user, with a size limit. */
object ListFileReader {
    const val MAX_BYTES = 8 * 1024 * 1024
    private const val BUFFER_BYTES = 64 * 1024

    sealed interface Read {
        data class Text(val value: String) : Read
        data object TooLarge : Read
        data object Unreadable : Read
    }

    fun read(context: Context, uri: Uri, logger: AppLogger): Read = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                out.write(buffer, 0, count)
                if (out.size() > MAX_BYTES) return Read.TooLarge
            }
            Read.Text(out.toString(Charsets.UTF_8.name()))
        } ?: Read.Unreadable
    } catch (e: Exception) {
        logger.error("Listas", "Falha ao ler a lista importada", e)
        Read.Unreadable
    }
}
