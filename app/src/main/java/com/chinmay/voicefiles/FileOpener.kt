package com.chinmay.voicefiles

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider

object FileOpener {

    private fun uriFor(activity: Activity, item: FileItem): Uri =
        FileProvider.getUriForFile(activity, activity.packageName + ".files", item.file)

    fun mimeType(item: FileItem): String = when (item.ext) {
        "apk" -> "application/vnd.android.package-archive"
        "md" -> "text/markdown"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(item.ext)
    } ?: "*/*"

    /** Opens the file in whichever app handles it (PDF viewer, Gallery, Docs…). Returns true on success. */
    fun open(activity: Activity, item: FileItem): Boolean {
        if (!item.file.exists()) {
            toast(activity, activity.getString(R.string.open_failed, item.name))
            return false
        }
        val uri = try {
            uriFor(activity, item)
        } catch (e: IllegalArgumentException) {
            toast(activity, activity.getString(R.string.open_failed, e.message ?: item.name))
            return false
        }
        val mime = mimeType(item)
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            activity.startActivity(view)
            return true
        } catch (e: ActivityNotFoundException) {
        } catch (e: SecurityException) {
            toast(activity, activity.getString(R.string.open_failed, e.message ?: ""))
            return false
        }
        // No app claims this exact type — offer anything that can view "some file".
        if (mime != "*/*") {
            val any = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                activity.startActivity(Intent.createChooser(any, activity.getString(R.string.open_with)))
                return true
            } catch (e: ActivityNotFoundException) {
            }
        }
        toast(activity, activity.getString(R.string.no_app, item.ext.ifEmpty { "?" }))
        return false
    }

    fun share(activity: Activity, item: FileItem) {
        val uri = try {
            uriFor(activity, item)
        } catch (e: IllegalArgumentException) {
            toast(activity, activity.getString(R.string.open_failed, e.message ?: item.name))
            return
        }
        val send = Intent(Intent.ACTION_SEND)
            .setType(mimeType(item))
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(item.name, uri)
        try {
            activity.startActivity(Intent.createChooser(send, activity.getString(R.string.share)))
        } catch (e: ActivityNotFoundException) {
            toast(activity, activity.getString(R.string.no_app, item.ext))
        }
    }

    private fun toast(activity: Activity, msg: String) =
        Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
}
