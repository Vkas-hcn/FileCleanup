package com.live.life.intoxication.filecleanup

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class FileScanner(private val context: Context) {

    data class ScanResult(
        val appCache: MutableList<JunkFile> = mutableListOf(),
        val apkFiles: MutableList<JunkFile> = mutableListOf(),
        val logFiles: MutableList<JunkFile> = mutableListOf(),
        val adJunk: MutableList<JunkFile> = mutableListOf(),
        val tempFiles: MutableList<JunkFile> = mutableListOf(),
        val emptyFiles: MutableList<JunkFile> = mutableListOf(),
        val duplicateFiles: MutableList<JunkFile> = mutableListOf(),
        val largeFiles: MutableList<JunkFile> = mutableListOf(),
        val otherFiles: MutableList<JunkFile> = mutableListOf()
    ) {
        fun getTotalSize(): Long {
            return appCache.sumOf { it.size } +
                    apkFiles.sumOf { it.size } +
                    logFiles.sumOf { it.size } +
                    adJunk.sumOf { it.size } +
                    tempFiles.sumOf { it.size } +
                    emptyFiles.sumOf { it.size } +
                    duplicateFiles.sumOf { it.size } +
                    largeFiles.sumOf { it.size } +
                    otherFiles.sumOf { it.size }
        }

        fun getTotalCount(): Int {
            return appCache.size + apkFiles.size + logFiles.size +
                    adJunk.size + tempFiles.size + emptyFiles.size +
                    duplicateFiles.size + largeFiles.size + otherFiles.size
        }
    }

    data class JunkFile(
        val name: String,
        val path: String,
        val size: Long,
        val category: String,
        var isSelected: Boolean = true,
        val lastModified: Long = System.currentTimeMillis(),
        val fileHash: String? = null // 用于重复文件检测
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is JunkFile) return false
            return path == other.path
        }

        override fun hashCode(): Int {
            return path.hashCode()
        }
    }

    interface ScanProgressCallback {
        fun onProgressUpdate(currentPath: String, scannedFiles: Int, foundJunk: Int)
        fun onCategoryUpdate(category: String, files: List<JunkFile>)
        fun onScanComplete(result: ScanResult)
        fun onError(error: String)
    }

    private var scanJob: Job? = null
    private val scannedFiles = AtomicLong(0)
    private val foundJunkFiles = AtomicLong(0)
    private val fileHashMap = mutableMapOf<String, MutableList<JunkFile>>() // 用于重复文件检测

    suspend fun startScan(callback: ScanProgressCallback): ScanResult {
        return withContext(Dispatchers.IO) {
            val result = ScanResult()
            fileHashMap.clear()

            try {
                val scanDirs = getScanDirectories()

                scanDirs.forEach { dir ->
                    if (!isActive) return@withContext result

                    withContext(Dispatchers.Main) {
                        callback.onProgressUpdate(
                            dir.absolutePath,
                            scannedFiles.get().toInt(),
                            foundJunkFiles.get().toInt()
                        )
                    }

                    scanDirectory(dir, result, callback)
                }

                // 扫描完成后处理重复文件
                processDuplicateFiles(result, callback)

                withContext(Dispatchers.Main) {
                    callback.onScanComplete(result)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback.onError("扫描错误: ${e.message}")
                }
            }

            result
        }
    }

    private fun getScanDirectories(): List<File> {
        val externalDir = Environment.getExternalStorageDirectory()
        val internalDir = context.filesDir.parentFile // 应用内部目录

        val directories = mutableListOf<File>()

        // 外部存储目录
        directories.addAll(listOf(
            // Android应用数据目录
            File(externalDir, "Android/data"),
            File(externalDir, "Android/obb"),
            File(externalDir, "Android/media"),

            // 下载目录
            File(externalDir, "Download"),
            File(externalDir, "Downloads"),
            File(externalDir, "download"),

            // 缓存目录
            File(externalDir, ".cache"),
            File(externalDir, "cache"),
            File(externalDir, "Cache"),

            // 临时文件目录
            File(externalDir, ".tmp"),
            File(externalDir, "tmp"),
            File(externalDir, "temp"),
            File(externalDir, "Temp"),

            // 缩略图目录
            File(externalDir, "DCIM/.thumbnails"),
            File(externalDir, ".thumbnails"),
            File(externalDir, "thumbnails"),

            // 日志目录
            File(externalDir, "logs"),
            File(externalDir, "log"),
            File(externalDir, "Log"),

            // 浏览器相关
            File(externalDir, ".com.android.chrome"),
            File(externalDir, "browser"),
            File(externalDir, ".mozilla"),

            // 应用崩溃日志
            File(externalDir, "crash"),
            File(externalDir, "crashes"),
            File(externalDir, "tombstones"),

            // 备份目录
            File(externalDir, "backup"),
            File(externalDir, "Backup"),
            File(externalDir, ".backup"),

            // 其他常见垃圾目录
            File(externalDir, "lost+found"),
            File(externalDir, ".Trash"),
            File(externalDir, "Trash"),
            File(externalDir, ".recycle"),

            // 根目录（最后扫描）
            externalDir
        ))

        // 内部存储目录（如果可访问）
        internalDir?.let { internal ->
            directories.addAll(listOf(
                File(internal, "cache"),
                File(internal, "files"),
                File(internal, "databases")
            ))
        }

        return directories.filter { it.exists() && it.isDirectory && it.canRead() }
    }

    private suspend fun scanDirectory(
        directory: File,
        result: ScanResult,
        callback: ScanProgressCallback,
        depth: Int = 0
    ) {
        // 增加扫描深度限制，但不要太严格
        if (depth > 8) return

        try {
            val files = directory.listFiles() ?: return

            files.forEach { file ->

                scannedFiles.incrementAndGet()

                // 每处理50个文件更新一次进度
                if (scannedFiles.get() % 50L == 0L) {
                    withContext(Dispatchers.Main) {
                        callback.onProgressUpdate(
                            file.absolutePath,
                            scannedFiles.get().toInt(),
                            foundJunkFiles.get().toInt()
                        )
                    }
                }

                when {
                    file.isDirectory -> {
                        if (!shouldSkipDirectory(file)) {
                            scanDirectory(file, result, callback, depth + 1)
                        }
                    }
                    file.isFile -> {
                        val junkFile = classifyFile(file)
                        if (junkFile != null) {
                            foundJunkFiles.incrementAndGet()
                            addToResult(result, junkFile)

                            // 实时更新分类
                            withContext(Dispatchers.Main) {
                                callback.onCategoryUpdate(junkFile.category, listOf(junkFile))
                            }
                        }
                    }
                }

                // 短暂延迟避免阻塞UI
                if (scannedFiles.get() % 100L == 0L) {
                    delay(1)
                }
            }
        } catch (e: SecurityException) {
            // 忽略权限错误
        } catch (e: Exception) {
            // 忽略其他错误
        }
    }

    private fun shouldSkipDirectory(dir: File): Boolean {
        val name = dir.name.lowercase()
        val path = dir.absolutePath.lowercase()

        return when {
            // 系统目录
            name.startsWith(".") && !isKnownJunkDirectory(name) -> true
            name == "system" || name == "proc" || name == "dev" || name == "sys" -> true

            // 重要的用户目录（但要检查子目录）
            name == "dcim" && !path.contains("thumbnails") -> true
            name == "pictures" && !path.contains("cache") -> true
            name == "music" && !path.contains("cache") -> true
            name == "movies" && !path.contains("cache") -> true
            name == "documents" && !path.contains("cache") -> true

            // 应用目录但跳过缓存
            path.contains("/android/data/") && !path.contains("cache") -> false

            else -> false
        }
    }

    private fun isKnownJunkDirectory(name: String): Boolean {
        val junkDirectories = setOf(
            ".cache", ".tmp", ".thumbnails", ".backup", ".trash",
            ".recycle", ".com.android.chrome", ".mozilla"
        )
        return junkDirectories.contains(name)
    }

    private fun classifyFile(file: File): JunkFile? {
        val fileName = file.name.lowercase()
        val filePath = file.absolutePath.lowercase()
        val fileSize = file.length()

        // 放宽文件大小限制，但排除0字节文件（除非是空文件类别）
        if (fileSize == 0L) {
            return JunkFile(
                name = file.name,
                path = file.absolutePath,
                size = fileSize,
                category = "Empty Files",
                lastModified = file.lastModified()
            )
        }

        // 降低最小文件大小限制
        if (fileSize < 100) return null

        // 放宽时间限制，只排除最近10秒内创建的文件
        if (System.currentTimeMillis() - file.lastModified() < 10000) return null

        val category = when {
            // APK文件（包括临时APK）
            fileName.endsWith(".apk") || fileName.endsWith(".apk.tmp") -> "Apk Files"

            // 缓存文件（扩展识别）
            isAppCacheFile(filePath, fileName) -> "App Cache"

            // 日志文件（扩展识别）
            isLogFile(fileName, filePath) -> "Log Files"

            // 临时文件（扩展识别）
            isTempFile(fileName, filePath) -> "Temp Files"

            // 广告垃圾（扩展识别）
            isAdJunk(fileName, filePath) -> "AD Junk"

            // 大文件（超过100MB的非媒体文件）
            isLargeJunkFile(fileName, filePath, fileSize) -> "Large Files"

            // 其他可清理文件（扩展识别）
            isOtherJunk(fileName, filePath) -> "Other"

            else -> null
        }

        return if (category != null) {
            val hash = if (category == "Duplicate Files") {
                calculateFileHash(file)
            } else null

            JunkFile(
                name = file.name,
                path = file.absolutePath,
                size = fileSize,
                category = category,
                isSelected = true,
                lastModified = file.lastModified(),
                fileHash = hash
            )
        } else null
    }

    private fun isAppCacheFile(path: String, name: String): Boolean {
        return path.contains("/cache/") ||
                path.contains("/.cache/") ||
                path.contains("/tmp/") ||
                path.contains("/.tmp/") ||
                path.contains("/temp/") ||
                name.startsWith("cache_") ||
                name.contains("_cache") ||
                name.startsWith("tmp_") ||
                name.contains("_tmp") ||
                path.endsWith("/cache") ||
                (path.contains("android/data") && (path.contains("cache") || path.contains("temp"))) ||
                // 浏览器缓存
                path.contains("browser/cache") ||
                path.contains("chrome/cache") ||
                path.contains("firefox/cache") ||
                // 应用特定缓存
                name.contains("glide") ||
                name.contains("picasso") ||
                name.contains("fresco") ||
                name.contains("image_manager_disk_cache") ||
                // 缩略图缓存
                path.contains("thumbnails") ||
                name.startsWith(".thumbnails")
    }

    private fun isLogFile(name: String, path: String): Boolean {
        return name.endsWith(".log") ||
                name.endsWith(".txt") && (name.contains("log") || name.contains("debug") || name.contains("error")) ||
                name.endsWith(".trace") ||
                name.endsWith(".crash") ||
                name.endsWith(".anr") ||
                name.endsWith(".dmp") && name.contains("crash") ||
                path.contains("/logs/") ||
                path.contains("/log/") ||
                path.contains("crash") ||
                path.contains("tombstone") ||
                // Android系统日志
                name.startsWith("system_") && name.endsWith(".log") ||
                name.startsWith("kernel") && name.endsWith(".log") ||
                name.contains("logcat") ||
                // 应用崩溃日志
                name.contains("crash_dump") ||
                name.contains("stack_trace")
    }

    private fun isTempFile(name: String, path: String): Boolean {
        return name.endsWith(".tmp") ||
                name.endsWith(".temp") ||
                name.endsWith(".bak") ||
                name.endsWith(".backup") ||
                name.endsWith(".old") ||
                name.startsWith("~") ||
                name.endsWith("~") ||
                name.startsWith(".#") ||
                name.endsWith(".swp") ||
                name.endsWith(".swo") ||
                path.contains("/temp/") ||
                path.contains("/.tmp/") ||
                path.contains("/temporary/") ||
                // 下载临时文件
                name.endsWith(".download") ||
                name.endsWith(".part") ||
                name.endsWith(".crdownload") ||
                // 系统临时文件
                name.startsWith("tmp") && name.length > 10 ||
                // 编辑器临时文件
                name.contains("~$") ||
                (name.startsWith(".") && name.contains("temp"))
    }

    private fun isAdJunk(name: String, path: String): Boolean {
        return path.contains("/ads/") ||
                path.contains("/ad_") ||
                path.contains("advertisement") ||
                path.contains("admob") ||
                path.contains("googleads") ||
                path.contains("facebook_ads") ||
                name.contains("ads_") ||
                name.contains("_ad_") ||
                name.contains("banner") ||
                name.contains("popup") ||
                name.contains("adview") ||
                // 广告SDK相关
                path.contains("mopub") ||
                path.contains("applovin") ||
                path.contains("unity3d/ads") ||
                path.contains("ironsource") ||
                path.contains("vungle") ||
                name.contains("ad_cache") ||
                name.contains("ad_data")
    }

    private fun isLargeJunkFile(name: String, path: String, size: Long): Boolean {
        val largeFileThreshold = 100 * 1024 * 1024 // 100MB

        if (size < largeFileThreshold) return false

        // 排除常见的媒体文件和文档
        val mediaExtensions = setOf("mp4", "avi", "mkv", "mp3", "wav", "flac", "jpg", "png", "gif", "pdf", "doc", "docx")
        val extension = name.substringAfterLast('.', "").lowercase()

        return !mediaExtensions.contains(extension) &&
                !path.contains("/dcim/") &&
                !path.contains("/pictures/") &&
                !path.contains("/movies/") &&
                !path.contains("/music/") &&
                !path.contains("/documents/")
    }

    private fun isOtherJunk(name: String, path: String): Boolean {
        return name.endsWith(".dmp") ||
                name.endsWith(".old") ||
                name.endsWith(".backup") ||
                (name.endsWith(".db") && (name.contains("cache") || name.contains("temp"))) ||
                name.startsWith("crash_") ||
                name.contains("_crash") ||
                name.endsWith(".lock") ||
                name.endsWith(".pid") ||
                name.contains("lost+found") ||
                path.contains("/.trash/") ||
                path.contains("/trash/") ||
                path.contains("/.recycle/") ||
                // 包管理器临时文件
                name.endsWith(".odex.tmp") ||
                name.endsWith(".vdex.tmp") ||
                name.endsWith(".art.tmp") ||
                // 系统更新残留
                name.contains("update_") && name.endsWith(".tmp") ||
                // 卸载残留
                name.contains("uninstall") ||
                // 空文件夹标记
                name == ".nomedia" && path.contains("empty")
    }

    private suspend fun processDuplicateFiles(result: ScanResult, callback: ScanProgressCallback) {
        // 简单的重复文件检测（基于文件大小和名称）
        val sizeGroupMap = mutableMapOf<Long, MutableList<JunkFile>>()

        // 收集所有文件按大小分组
        val allFiles = result.appCache + result.apkFiles + result.logFiles +
                result.adJunk + result.tempFiles + result.otherFiles

        allFiles.forEach { file ->
            if (file.size > 1024) { // 只检查大于1KB的文件
                sizeGroupMap.getOrPut(file.size) { mutableListOf() }.add(file)
            }
        }

        // 找出可能的重复文件
        sizeGroupMap.values.forEach { filesWithSameSize ->
            if (filesWithSameSize.size > 1) {
                // 按名称进一步分组
                val nameGroups = filesWithSameSize.groupBy { it.name }
                nameGroups.values.forEach { sameNameFiles ->
                    if (sameNameFiles.size > 1) {
                        // 保留最新的文件，其他标记为重复
                        val sortedFiles = sameNameFiles.sortedByDescending { it.lastModified }
                        sortedFiles.drop(1).forEach { duplicateFile ->
                            result.duplicateFiles.add(duplicateFile.copy(category = "Duplicate Files"))
                        }
                    }
                }
            }
        }
    }

    private fun calculateFileHash(file: File): String? {
        return try {
            // 简单的哈希计算，只读取文件的前1KB
            if (file.length() > 1024) {
                val bytes = file.readBytes().take(1024).toByteArray()
                bytes.contentHashCode().toString()
            } else {
                file.readBytes().contentHashCode().toString()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun addToResult(result: ScanResult, junkFile: JunkFile) {
        when (junkFile.category) {
            "App Cache" -> result.appCache.add(junkFile)
            "Apk Files" -> result.apkFiles.add(junkFile)
            "Log Files" -> result.logFiles.add(junkFile)
            "AD Junk" -> result.adJunk.add(junkFile)
            "Temp Files" -> result.tempFiles.add(junkFile)
            "Empty Files" -> result.emptyFiles.add(junkFile)
            "Duplicate Files" -> result.duplicateFiles.add(junkFile)
            "Large Files" -> result.largeFiles.add(junkFile)
            "Other" -> result.otherFiles.add(junkFile)
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
    }

    companion object {
        /**
         * 删除文件列表
         */
        suspend fun deleteFiles(files: List<JunkFile>, progressCallback: ((Int, Int) -> Unit)? = null): Pair<Int, Long> {
            return withContext(Dispatchers.IO) {
                var deletedCount = 0
                var deletedSize = 0L

                files.forEachIndexed { index, junkFile ->
                    try {
                        val file = File(junkFile.path)
                        if (file.exists() && file.canWrite()) {
                            // 对于空文件夹，尝试删除整个文件夹
                            if (file.isDirectory && (file.listFiles()?.isEmpty() == true)) {
                                if (file.delete()) {
                                    deletedCount++
                                    deletedSize += junkFile.size
                                }
                            } else if (file.isFile && file.delete()) {
                                deletedCount++
                                deletedSize += junkFile.size

                                // 删除文件后检查父目录是否为空
                                val parentDir = file.parentFile
                                if (parentDir != null && parentDir.listFiles()?.isEmpty() == true) {
                                    try {
                                        parentDir.delete()
                                    } catch (e: Exception) {
                                        // 忽略删除空文件夹失败
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // 忽略删除失败的文件
                    }

                    progressCallback?.invoke(index + 1, files.size)
                    delay(5) // 缩短延迟
                }

                Pair(deletedCount, deletedSize)
            }
        }
    }
}