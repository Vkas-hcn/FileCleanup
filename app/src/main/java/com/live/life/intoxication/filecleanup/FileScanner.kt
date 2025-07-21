package com.live.life.intoxication.filecleanup.scanner

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
        val otherFiles: MutableList<JunkFile> = mutableListOf()
    ) {
        fun getTotalSize(): Long {
            return appCache.sumOf { it.size } +
                    apkFiles.sumOf { it.size } +
                    logFiles.sumOf { it.size } +
                    adJunk.sumOf { it.size } +
                    tempFiles.sumOf { it.size } +
                    otherFiles.sumOf { it.size }
        }

        fun getTotalCount(): Int {
            return appCache.size + apkFiles.size + logFiles.size +
                    adJunk.size + tempFiles.size + otherFiles.size
        }
    }

    data class JunkFile(
        val name: String,
        val path: String,
        val size: Long,
        val category: String,
        var isSelected: Boolean = true,
        val lastModified: Long = System.currentTimeMillis()
    ) {
        // 确保正确的equals和hashCode实现
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is JunkFile) return false
            return path == other.path // 使用文件路径作为唯一标识
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

    suspend fun startScan(callback: ScanProgressCallback): ScanResult {
        return withContext(Dispatchers.IO) {
            val result = ScanResult()

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
        return listOf(
            // Android应用数据目录
            File(externalDir, "Android/data"),
            File(externalDir, "Android/obb"),

            // 下载目录
            File(externalDir, "Download"),
            File(externalDir, "Downloads"),

            // 缓存目录
            File(externalDir, ".cache"),
            File(externalDir, "cache"),

            // 临时文件目录
            File(externalDir, ".tmp"),
            File(externalDir, "tmp"),
            File(externalDir, "temp"),

            // 缩略图目录
            File(externalDir, "DCIM/.thumbnails"),
            File(externalDir, ".thumbnails"),

            // 日志目录
            File(externalDir, "logs"),
            File(externalDir, "log"),

            // 根目录（最后扫描）
            externalDir
        ).filter { it.exists() && it.isDirectory }
    }

    private suspend fun scanDirectory(
        directory: File,
        result: ScanResult,
        callback: ScanProgressCallback,
        depth: Int = 0
    ) {
        // 限制扫描深度防止过深递归
        if (depth > 5) return

        try {
            val files = directory.listFiles() ?: return

            files.forEach { file ->

                scannedFiles.incrementAndGet()

                // 每处理100个文件更新一次进度
                if (scannedFiles.get() % 100L == 0L) {
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
                        // 跳过特定系统目录
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
                if (scannedFiles.get() % 50L == 0L) {
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
            name.startsWith(".") && name != ".cache" && name != ".tmp" && name != ".thumbnails" -> true
            name == "system" || name == "proc" || name == "dev" -> true

            // 大型媒体目录（避免扫描过久）
            name == "movies" || name == "music" || name == "pictures" -> true

            // 已知的非垃圾目录
            name == "documents" || name == "dcim" -> true

            else -> false
        }
    }

    private fun classifyFile(file: File): JunkFile? {
        val fileName = file.name.lowercase()
        val filePath = file.absolutePath.lowercase()
        val fileSize = file.length()

        // 忽略小于1KB的文件
        if (fileSize < 1024) return null

        // 忽略当前正在使用的文件
        if (System.currentTimeMillis() - file.lastModified() < 60000) return null

        val category = when {
            // APK文件
            fileName.endsWith(".apk") -> "Apk Files"

            // 缓存文件
            isAppCacheFile(filePath, fileName) -> "App Cache"

            // 日志文件
            isLogFile(fileName, filePath) -> "Log Files"

            // 临时文件
            isTempFile(fileName, filePath) -> "Temp Files"

            // 广告垃圾
            isAdJunk(fileName, filePath) -> "AD Junk"

            // 其他可清理文件
            isOtherJunk(fileName, filePath) -> "Other"

            else -> null
        }

        return if (category != null) {
            JunkFile(
                name = file.name,
                path = file.absolutePath,
                size = fileSize,
                category = category,
                isSelected = true, // 默认选中
                lastModified = file.lastModified()
            )
        } else null
    }

    private fun isAppCacheFile(path: String, name: String): Boolean {
        return path.contains("/cache/") ||
                path.contains("/.cache/") ||
                path.contains("/tmp/") ||
                path.contains("/.tmp/") ||
                name.startsWith("cache_") ||
                name.contains("_cache") ||
                name.startsWith("tmp_") ||
                path.endsWith("/cache") ||
                (path.contains("android/data") && path.contains("cache"))
    }

    private fun isLogFile(name: String, path: String): Boolean {
        return name.endsWith(".log") ||
                name.endsWith(".txt") && (name.contains("log") || name.contains("debug")) ||
                name.endsWith(".trace") ||
                path.contains("/logs/") ||
                path.contains("/log/")
    }

    private fun isTempFile(name: String, path: String): Boolean {
        return name.endsWith(".tmp") ||
                name.endsWith(".temp") ||
                name.endsWith(".bak") ||
                name.startsWith("~") ||
                name.endsWith("~") ||
                path.contains("/temp/") ||
                path.contains("/.tmp/")
    }

    private fun isAdJunk(name: String, path: String): Boolean {
        return path.contains("/ads/") ||
                path.contains("/ad_") ||
                path.contains("advertisement") ||
                name.contains("ads_") ||
                name.contains("_ad_") ||
                name.contains("banner") ||
                path.contains("googleads")
    }

    private fun isOtherJunk(name: String, path: String): Boolean {
        return name.endsWith(".dmp") ||
                name.endsWith(".old") ||
                name.endsWith(".backup") ||
                (name.endsWith(".db") && name.contains("cache")) ||
                name.startsWith("crash_") ||
                name.contains("_crash")
    }

    private fun addToResult(result: ScanResult, junkFile: JunkFile) {
        when (junkFile.category) {
            "App Cache" -> result.appCache.add(junkFile)
            "Apk Files" -> result.apkFiles.add(junkFile)
            "Log Files" -> result.logFiles.add(junkFile)
            "AD Junk" -> result.adJunk.add(junkFile)
            "Temp Files" -> result.tempFiles.add(junkFile)
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
                        if (file.exists() && file.canWrite() && file.delete()) {
                            deletedCount++
                            deletedSize += junkFile.size
                        }
                    } catch (e: Exception) {
                        // 忽略删除失败的文件
                    }

                    progressCallback?.invoke(index + 1, files.size)
                    delay(10) // 短暂延迟，避免阻塞
                }

                Pair(deletedCount, deletedSize)
            }
        }
    }
}