package com.chinmay.voicefiles

import java.io.File

enum class FileKind(val extensions: Set<String>, val color: Int) {
    PDF(setOf("pdf"), 0xFFD32F2F.toInt()),
    IMAGE(setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "svg"), 0xFF7B1FA2.toInt()),
    VIDEO(setOf("mp4", "mkv", "webm", "3gp", "mov", "avi", "m4v"), 0xFFC2185B.toInt()),
    AUDIO(setOf("mp3", "m4a", "aac", "wav", "ogg", "opus", "flac", "amr"), 0xFFF57C00.toInt()),
    DOC(setOf("doc", "docx", "odt", "rtf", "txt", "md"), 0xFF1976D2.toInt()),
    SHEET(setOf("xls", "xlsx", "csv", "ods"), 0xFF388E3C.toInt()),
    SLIDES(setOf("ppt", "pptx", "odp", "key"), 0xFFE64A19.toInt()),
    APK(setOf("apk", "apks", "xapk"), 0xFF00897B.toInt()),
    ARCHIVE(setOf("zip", "rar", "7z", "tar", "gz"), 0xFF5D4037.toInt()),
    OTHER(emptySet(), 0xFF607D8B.toInt());

    companion object {
        fun of(ext: String): FileKind = values().firstOrNull { ext in it.extensions } ?: OTHER
    }
}

data class FileItem(
    val file: File,
    val name: String,
    val ext: String,
    val kind: FileKind,
    val size: Long,
    val modified: Long,
    /** Sub-folder inside Download ("" for the Download folder itself). */
    val folder: String,
    /** Normalised words of the file name (without extension). */
    val tokens: List<String>,
    /** All tokens glued together, for matching "labreport" against "lab report". */
    val joined: String,
) {
    /** A name that text-to-speech can read nicely ("Lab_Report_v2.pdf" → "Lab Report v 2"). */
    val spokenName: String
        get() = tokens.joinToString(" ").take(60).ifEmpty { name }

    companion object {
        fun from(file: File, folder: String): FileItem {
            val name = file.name
            val dot = name.lastIndexOf('.')
            val hasExt = dot > 0 && dot < name.length - 1
            val ext = if (hasExt) name.substring(dot + 1).lowercase() else ""
            val base = if (hasExt) name.substring(0, dot) else name
            val tokens = TextNorm.tokenize(base)
            return FileItem(
                file = file,
                name = name,
                ext = ext,
                kind = FileKind.of(ext),
                size = file.length(),
                modified = file.lastModified(),
                folder = folder,
                tokens = tokens,
                joined = tokens.joinToString(""),
            )
        }
    }
}
