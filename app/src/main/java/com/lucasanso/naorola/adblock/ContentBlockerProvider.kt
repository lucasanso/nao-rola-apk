package com.lucasanso.naorola.adblock

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.lucasanso.naorola.NaoRolaApp

/**
 * Hands the ad-block list to Samsung Internet (content-blocker interface "API_1.0"): the browser
 * opens any uri of this provider and reads a text file in AdBlock syntax. Read-only; holds no
 * personal data — the list is public ad domains plus what the user chose to import.
 */
class ContentBlockerProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val app = context?.applicationContext as? NaoRolaApp ?: return null
        return try {
            ParcelFileDescriptor.open(app.container.browserFilters.servedFile(), ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: Exception) {
            app.container.logger.error("Anuncios", "Falha ao entregar a lista ao navegador", e)
            null
        }
    }

    override fun getType(uri: Uri): String = "text/plain"

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
