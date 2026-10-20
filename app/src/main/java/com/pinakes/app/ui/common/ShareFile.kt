package com.pinakes.app.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a staff export (receipt, labels, error CSV) from `cache/exports/` to other apps through
 * the FileProvider, with a read grant on that one URI. Nothing else in the cache is exposed.
 */
object ShareFile {

    private fun uri(context: Context, file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** The system share sheet (print, mail, drive, ...). */
    fun share(context: Context, file: File, mime: String, chooserTitle: String) {
        val intent = ShareCompat.IntentBuilder(context)
            .setType(mime)
            .setStream(uri(context, file))
            .setChooserTitle(chooserTitle)
            .createChooserIntent()
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Open in a viewer app; false when none is installed (the caller then shares instead). */
    fun open(context: Context, file: File, mime: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri(context, file), mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
