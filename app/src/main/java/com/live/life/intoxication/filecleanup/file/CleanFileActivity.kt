package com.live.life.intoxication.filecleanup.file

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.live.life.intoxication.filecleanup.AppDataTool
import com.live.life.intoxication.filecleanup.R
import com.live.life.intoxication.filecleanup.databinding.ActivityFileBinding
import com.live.life.intoxication.filecleanup.one.ScanLoadActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CleanFileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFileBinding
    private lateinit var filesAdapter: FilesAdapter

    private var allFiles = mutableListOf<FileInfo>()
    private var filteredFiles = mutableListOf<FileInfo>()

    // 使用 Set 来跟踪已扫描的文件路径，避免重复
    private val scannedPaths = mutableSetOf<String>()

    private var currentTypeFilter = FilterType.ALL
    private var currentSizeFilter = FilterSize.ALL
    private var currentTimeFilter = FilterTime.ALL

    var progressJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityFileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        showScaningUi()
        setupWindowInsets()
        setupActionBar()
        setupRecyclerView()
        setupClickListeners()
        scanFiles()
    }
    fun showScaningUi(){
        binding.inDialog.tvBack.setOnClickListener {
            progressJob?.cancel()
            finish()
        }
        binding.inDialog.scaning.setOnClickListener {
        }
        binding.inDialog.scaning.isVisible = true
        var progress = 0
        progressJob = lifecycleScope.launch {
            while (true) {
                progress++
                binding.inDialog.pg.progress = progress
                delay(20)
                if (binding.inDialog.pg.progress >= 100) {
                    binding.inDialog.scaning.isVisible = false
                    break
                }
            }
        }
    }
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.file)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupActionBar() {
        supportActionBar?.hide()
    }

    private fun setupRecyclerView() {
        filesAdapter = FilesAdapter(
            context = this,
            onItemClick = { fileInfo ->
            },
            onSelectionChanged = { selectedCount ->
                updateDeleteButton(selectedCount)
            }
        )

        binding.rvFiles.apply {
            layoutManager = LinearLayoutManager(this@CleanFileActivity)
            adapter = filesAdapter
        }
    }

    private fun setupClickListeners() {
        // 返回按钮
        binding.textBack.setOnClickListener {
            finish()
        }

        // 筛选按钮
        binding.tvType.setOnClickListener { showTypeFilterMenu(it) }
        binding.tvSize.setOnClickListener { showSizeFilterMenu(it) }
        binding.tvTime.setOnClickListener { showTimeFilterMenu(it) }

        // 删除按钮
        binding.btnDelete.setOnClickListener { showDeleteConfirmDialog() }
    }

    private fun scanFiles() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                allFiles.clear()
                scannedPaths.clear() // 清空已扫描路径集合

                // 获取所有需要扫描的根目录
                val directoriesToScan = mutableSetOf<String>()

                // 添加外部存储目录
                val externalStorageDir = Environment.getExternalStorageDirectory()
                if (externalStorageDir.exists() && externalStorageDir.canRead()) {
                    directoriesToScan.add(externalStorageDir.absolutePath)
                }

                // 添加其他可用的存储目录
                val externalDirs = getExternalFilesDirs(null)
                externalDirs?.forEach { dir ->
                    dir?.parentFile?.parentFile?.parentFile?.let { rootDir ->
                        if (rootDir.exists() && rootDir.canRead()) {
                            directoriesToScan.add(rootDir.absolutePath)
                        }
                    }
                }

                // 扫描所有去重后的目录
                directoriesToScan.forEach { path ->
                    scanDirectory(File(path))
                }
            }

            // 在主线程更新UI
            applyFilters()
        }
    }

    private fun scanDirectory(directory: File) {
        try {
            directory.listFiles()?.forEach { file ->
                when {
                    file.isFile -> {
                        val absolutePath = file.absolutePath
                        // 检查是否已经扫描过此文件
                        if (!scannedPaths.contains(absolutePath)) {
                            scannedPaths.add(absolutePath)

                            val extension = file.extension
                            val fileType = FileType.fromExtension(extension)
                            val fileInfo = FileInfo(
                                file = file,
                                name = file.name,
                                size = file.length(),
                                lastModified = file.lastModified(),
                                type = fileType
                            )
                            allFiles.add(fileInfo)
                        }
                    }
                    file.isDirectory && !file.name.startsWith(".") -> {
                        val absolutePath = file.absolutePath
                        // 检查是否已经扫描过此目录（避免循环扫描）
                        if (!scannedPaths.contains(absolutePath)) {
                            scannedPaths.add(absolutePath)
                            // 递归扫描子目录（排除隐藏目录）
                            scanDirectory(file)
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            // 处理权限不足的情况
        }
    }

    private fun showTypeFilterMenu(view: View) {
        val popup = PopupMenu(this, view)

        FilterType.values().forEach { filterType ->
            popup.menu.add(filterType.displayName)
        }

        popup.setOnMenuItemClickListener { menuItem ->
            val selectedFilter = FilterType.values().find {
                it.displayName == menuItem.title
            } ?: FilterType.ALL

            currentTypeFilter = selectedFilter
            binding.tvType.text = selectedFilter.displayName
            applyFilters()
            true
        }

        popup.show()
    }

    private fun showSizeFilterMenu(view: View) {
        val popup = PopupMenu(this, view)

        FilterSize.values().forEach { filterSize ->
            popup.menu.add(filterSize.displayName)
        }

        popup.setOnMenuItemClickListener { menuItem ->
            val selectedFilter = FilterSize.values().find {
                it.displayName == menuItem.title
            } ?: FilterSize.ALL

            currentSizeFilter = selectedFilter
            binding.tvSize.text = selectedFilter.displayName
            applyFilters()
            true
        }

        popup.show()
    }

    private fun showTimeFilterMenu(view: View) {
        val popup = PopupMenu(this, view)

        FilterTime.values().forEach { filterTime ->
            popup.menu.add(filterTime.displayName)
        }

        popup.setOnMenuItemClickListener { menuItem ->
            val selectedFilter = FilterTime.values().find {
                it.displayName == menuItem.title
            } ?: FilterTime.ALL

            currentTimeFilter = selectedFilter
            binding.tvTime.text = selectedFilter.displayName
            applyFilters()
            true
        }

        popup.show()
    }

    private fun applyFilters() {
        filteredFiles.clear()

        val currentTime = System.currentTimeMillis()

        allFiles.forEach { fileInfo ->
            // 类型筛选
            val typeMatch = when (currentTypeFilter) {
                FilterType.ALL -> true
                FilterType.IMAGE -> fileInfo.type == FileType.IMAGE
                FilterType.VIDEO -> fileInfo.type == FileType.VIDEO
                FilterType.AUDIO -> fileInfo.type == FileType.AUDIO
                FilterType.DOCS -> fileInfo.type == FileType.DOCUMENT
                FilterType.DOWNLOAD -> fileInfo.type == FileType.DOWNLOAD
                FilterType.ZIP -> fileInfo.type == FileType.ZIP
            }

            // 大小筛选
            val sizeMatch = fileInfo.size >= currentSizeFilter.minSizeBytes

            // 时间筛选
            val timeMatch = if (currentTimeFilter == FilterTime.ALL) {
                true
            } else {
                val daysDiff = (currentTime - fileInfo.lastModified) / (1000 * 60 * 60 * 24)
                daysDiff <= currentTimeFilter.daysAgo
            }

            if (typeMatch && sizeMatch && timeMatch) {
                filteredFiles.add(fileInfo)
            }
        }

        // 修改排序逻辑：首先按时间倒序排列（新文件在前），然后按大小倒序排列
        filteredFiles.sortWith(compareByDescending<FileInfo> { it.lastModified }.thenByDescending { it.size })

        // 更新UI
        filesAdapter.submitList(filteredFiles.toList())
        updateFileCount()
        updateDeleteButton(0)
    }

    private fun updateFileCount() {
        val count = filteredFiles.size
        binding.tvFileCount.text = "$count files"
    }

    private fun updateDeleteButton(selectedCount: Int) {
        binding.btnDelete.isEnabled = selectedCount > 0
        binding.btnDelete.text = if (selectedCount > 0) {
            "Delete ($selectedCount)"
        } else {
            "Delete"
        }
    }

    private fun showDeleteConfirmDialog() {
        val selectedFiles = filesAdapter.getSelectedFiles()
        if (selectedFiles.isEmpty()) return

        val totalSize = selectedFiles.sumOf { it.size }
        val formattedSize = when {
            totalSize < 1024 -> "${totalSize} B"
            totalSize < 1024 * 1024 -> String.format("%.1f KB", totalSize / 1024.0)
            totalSize < 1024 * 1024 * 1024 -> String.format("%.1f MB", totalSize / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", totalSize / (1024.0 * 1024.0 * 1024.0))
        }
        AppDataTool.cleanNum = formattedSize

        deleteSelectedFiles(selectedFiles)
        AppDataTool.jumpType = 2
        startActivity(Intent(this@CleanFileActivity, ScanLoadActivity::class.java))
        finish()
    }

    private fun deleteSelectedFiles(filesToDelete: List<FileInfo>) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                var deletedCount = 0
                filesToDelete.forEach { fileInfo ->
                    try {
                        if (fileInfo.file.delete()) {
                            deletedCount++
                            // 从已扫描路径集合中移除已删除的文件
                            scannedPaths.remove(fileInfo.file.absolutePath)
                        }
                    } catch (e: Exception) {
                        // 处理删除失败的情况
                    }
                }

                // 从列表中移除已删除的文件
                allFiles.removeAll { fileInfo ->
                    filesToDelete.any { it.file.absolutePath == fileInfo.file.absolutePath }
                }

                withContext(Dispatchers.Main) {
                    applyFilters()
                }
            }
        }
    }
}