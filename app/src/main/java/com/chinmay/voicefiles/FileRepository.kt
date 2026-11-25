package com.chinmay.voicefiles

import android.os.Environment
import java.io.File

object FileRepository {

    private const val MAX_DEPTH = 5
    private const val MAX_FILES = 5000
    /** Half-finished downloads — not worth opening. */
    private val SKIP_EXT = setOf("crdownload", "part", "partial", "tmp", "download", "pending")

    @Suppress("DEPRECATION")
    fun downloadDir(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    /** Every file in Download and its sub-folders, newest first. */
    fun scan(): List<FileItem> {
        val root = downloadDir()
        val out = ArrayList<FileItem>()
        walk(root, root, 0, out)
        out.sortByDescending { it.modified }
        return out
    }

    private fun walk(root: File, dir: File, depth: Int, out: MutableList<FileItem>) {
        val children = dir.listFiles() ?: return
        for (f in children) {
            if (out.size >= MAX_FILES) return
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                if (depth < MAX_DEPTH) walk(root, f, depth + 1, out)
                continue
            }
            if (f.extension.lowercase() in SKIP_EXT) continue
            if (f.name.startsWith(".pending-") || f.name.startsWith(".trashed-")) continue
            val folder = f.parentFile?.let { p -> runCatching { p.relativeTo(root).path }.getOrNull() }.orEmpty()
            out += FileItem.from(f, folder)
        }
    }
}
