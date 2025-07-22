package com.live.life.intoxication.filecleanup.file

import java.io.File

data class FileInfo(
    val file: File,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val type: FileType,
    var isSelected: Boolean = false
) {
    val sizeInMB: Double
        get() = size / (1024.0 * 1024.0)

    val formattedSize: String
        get() = when {
            size < 1024 -> "${size} B"
            size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0))
        }
}

enum class FileType {
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    DOWNLOAD,
    ZIP,
    OTHER;

    companion object {
        fun fromExtension(extension: String): FileType {
            return when (extension.lowercase()) {
                "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg" -> IMAGE
                "mp4", "avi", "mkv", "mov", "wmv", "flv", "3gp", "webm" -> VIDEO
                "mp3", "wav", "flac", "aac", "ogg", "m4a", "wma" -> AUDIO
                "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf" -> DOCUMENT
                "zip", "rar", "7z", "tar", "gz", "bz2" -> ZIP
                "apk", "deb", "dmg", "exe", "msi" -> DOWNLOAD
                else -> OTHER
            }
        }
    }
}

enum class FilterType(val displayName: String) {
    ALL("All Type"),
    IMAGE("Image"),
    VIDEO("Video"),
    AUDIO("Audio"),
    DOCS("Docs"),
    DOWNLOAD("Download"),
    ZIP("Zip")
}

enum class FilterSize(val displayName: String, val minSizeBytes: Long) {
    ALL("All Size", 0),
    SIZE_10MB(">10MB", 10 * 1024 * 1024),
    SIZE_20MB(">20MB", 20 * 1024 * 1024),
    SIZE_50MB(">50MB", 50 * 1024 * 1024),
    SIZE_100MB(">100MB", 100 * 1024 * 1024),
    SIZE_200MB(">200MB", 200 * 1024 * 1024),
    SIZE_500MB(">500MB", 500 * 1024 * 1024)
}

enum class FilterTime(val displayName: String, val daysAgo: Int) {
    ALL("All Time", 0),
    DAY_1("Within 1 day", 1),
    WEEK_1("Within 1 week", 7),
    MONTH_1("Within 1 month", 30),
    MONTH_3("Within 3 month", 90),
    MONTH_6("Within 6 month", 180)
}