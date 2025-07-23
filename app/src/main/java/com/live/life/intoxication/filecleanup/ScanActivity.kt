package com.live.life.intoxication.filecleanup

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.live.life.intoxication.filecleanup.databinding.ActivityScanBinding
import com.live.life.intoxication.filecleanup.one.ScanLoadActivity
import kotlinx.coroutines.*
import java.text.DecimalFormat
import kotlin.math.round

class ScanActivity : AppCompatActivity(), FileScanner.ScanProgressCallback {
    private lateinit var binding: ActivityScanBinding
    private lateinit var fileScanner: FileScanner
    private var isScanning = false
    private var scanJob: Job? = null

    // 扫描结果
    private var scanResult: FileScanner.ScanResult? = null
    private val selectedFiles = mutableListOf<FileScanner.JunkFile>()

    // 分类展开状态
    private val categoryExpandStates = mutableMapOf<String, Boolean>()
    private val categorySelectStates = mutableMapOf<String, Boolean>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.scan)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        this.supportActionBar?.hide()

        fileScanner = FileScanner(this)
        initializeStates()
        setupClickListeners()

        // 检查权限后开始扫描
        if (PermissionHelper.hasStoragePermission(this)) {
            startScanning()
        } else {
            requestPermissionAndScan()
        }
    }

    private fun initializeStates() {
        // 初始化所有分类为选中状态
        categorySelectStates["App Cache"] = true
        categorySelectStates["Apk Files"] = true
        categorySelectStates["Log Files"] = true
        categorySelectStates["AD Junk"] = true
        categorySelectStates["Temp Files"] = true
        categorySelectStates["Empty Files"] = true
        categorySelectStates["Duplicate Files"] = true
        categorySelectStates["Large Files"] = false // 大文件默认不选中
        categorySelectStates["Other"] = true
    }

    private fun requestPermissionAndScan() {
        AlertDialog.Builder(this)
            .setTitle("Storage permissions are required")
            .setMessage("In order to scan junk files, permission to access the storage space is required")
            .setPositiveButton("Authorization") { _, _ ->
                PermissionHelper.requestStoragePermission(this)
            }
            .setNegativeButton("Cancel") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun setupClickListeners() {
        binding.textBack.setOnClickListener {
            if (isScanning) {
                showCancelScanDialog()
            } else {
                finish()
            }
        }

        binding.butClean.setOnClickListener {
            if (selectedFiles.isNotEmpty()) {
                startCleaning()
            }
        }

        // 设置分类点击事件
        setupCategoryClickListeners()
    }

    private fun showCancelScanDialog() {
        AlertDialog.Builder(this)
            .setTitle("Cancel the scan")
            .setMessage("Are you sure you want to cancel the current scan？")
            .setPositiveButton("Are you sure") { _, _ ->
                cancelScanning()
                finish()
            }
            .setNegativeButton("Continue scanning", null)
            .show()
    }

    private fun setupCategoryClickListeners() {
        // 展开/收起点击事件
        binding.layoutAppCache.setOnClickListener {
            toggleCategory("App Cache", binding.layoutAppCacheDetails, binding.ivAppCacheExpand)
        }

        binding.layoutApkFiles.setOnClickListener {
            toggleCategory("Apk Files", binding.layoutApkDetails, binding.ivApkExpand)
        }

        binding.layoutLogFiles.setOnClickListener {
            toggleCategory("Log Files", binding.layoutLogDetails, binding.ivLogExpand)
        }

        binding.layoutAdJunk.setOnClickListener {
            toggleCategory("AD Junk", binding.layoutAdDetails, binding.ivAdExpand)
        }

        binding.layoutTempFiles.setOnClickListener {
            toggleCategory("Temp Files", binding.layoutTempDetails, binding.ivTempExpand)
        }

        // 选中/取消选中点击事件
        binding.ivAppCacheStatus.setOnClickListener {
            toggleCategorySelection("App Cache", binding.ivAppCacheStatus)
        }

        binding.ivAppFileStatus.setOnClickListener {
            toggleCategorySelection("Apk Files", binding.ivAppFileStatus)
        }

        binding.ivLogFileStatus.setOnClickListener {
            toggleCategorySelection("Log Files", binding.ivLogFileStatus)
        }

        binding.ivAdJunkStatus.setOnClickListener {
            toggleCategorySelection("AD Junk", binding.ivAdJunkStatus)
        }

        binding.ivTempFilesStatus.setOnClickListener {
            toggleCategorySelection("Temp Files", binding.ivTempFilesStatus)
        }

        // 如果有新增的UI元素，也要添加对应的点击事件
        // 注意：这里假设你的布局文件中已经添加了相应的UI元素
        // binding.ivEmptyFilesStatus?.setOnClickListener {
        //     toggleCategorySelection("Empty Files", binding.ivEmptyFilesStatus)
        // }
        //
        // binding.ivDuplicateFilesStatus?.setOnClickListener {
        //     toggleCategorySelection("Duplicate Files", binding.ivDuplicateFilesStatus)
        // }
        //
        // binding.ivLargeFilesStatus?.setOnClickListener {
        //     toggleCategorySelection("Large Files", binding.ivLargeFilesStatus)
        // }
    }

    private fun toggleCategory(categoryName: String, detailsLayout: LinearLayout, expandIcon: ImageView) {
        val isExpanded = categoryExpandStates[categoryName] ?: false
        categoryExpandStates[categoryName] = !isExpanded

        if (!isExpanded) {
            detailsLayout.visibility = View.VISIBLE
            expandIcon.setImageResource(R.drawable.ic_expand_be)
            populateCategoryDetails(categoryName, detailsLayout)
        } else {
            detailsLayout.visibility = View.GONE
            expandIcon.setImageResource(R.drawable.ic_expand_more)
        }
    }

    private fun toggleCategorySelection(categoryName: String, statusIcon: ImageView) {
        val isCurrentlySelected = categorySelectStates[categoryName] ?: true
        val newSelectState = !isCurrentlySelected
        categorySelectStates[categoryName] = newSelectState

        Log.d("CategorySelection", "Toggling $categoryName: $isCurrentlySelected -> $newSelectState")

        // 获取该分类下的所有文件
        val categoryFiles = getCategoryFiles(categoryName)

        // 确保所有文件状态一致
        categoryFiles.forEach { file ->
            file.isSelected = newSelectState
        }

        // 重新计算选中文件
        recalculateSelectedFiles()

        // 更新UI图标
        statusIcon.setImageResource(
            if (newSelectState) R.drawable.ic_check else R.drawable.ic_check_circle
        )

        // 更新清理按钮
        updateCleanButton()

        // 更新分类大小显示
        updateCategorySizes()

        // 如果分类展开了，更新详情中的选中状态
        if (categoryExpandStates[categoryName] == true) {
            val detailsLayout = getDetailsLayoutForCategory(categoryName)
            if (detailsLayout != null) {
                populateCategoryDetails(categoryName, detailsLayout)
            }
        }

        Log.d("CategorySelection", "$categoryName: ${categoryFiles.size} files, selected: $newSelectState")
        Log.d("CategorySelection", "Total selected files: ${selectedFiles.size}")
    }

    private fun recalculateSelectedFiles() {
        selectedFiles.clear()
        scanResult?.let { result ->
            listOf(
                result.appCache,
                result.apkFiles,
                result.logFiles,
                result.adJunk,
                result.tempFiles,
            ).flatten()
                .filter { it.isSelected }
                .forEach { selectedFiles.add(it) }
        }
    }

    private fun getCategoryFiles(categoryName: String): List<FileScanner.JunkFile> {
        val result = scanResult ?: return emptyList()
        return when (categoryName) {
            "App Cache" -> result.appCache
            "Apk Files" -> result.apkFiles
            "Log Files" -> result.logFiles
            "AD Junk" -> result.adJunk
            "Temp Files" -> result.tempFiles
            else -> emptyList()
        }
    }

    private fun getDetailsLayoutForCategory(categoryName: String): LinearLayout? {
        return when (categoryName) {
            "App Cache" -> binding.layoutAppCacheDetails
            "Apk Files" -> binding.layoutApkDetails
            "Log Files" -> binding.layoutLogDetails
            "AD Junk" -> binding.layoutAdDetails
            "Temp Files" -> binding.layoutTempDetails
            // 新增的分类需要对应的布局，这里先返回null
            "Empty Files" -> null // binding.layoutEmptyFilesDetails
            "Duplicate Files" -> null // binding.layoutDuplicateFilesDetails
            "Large Files" -> null // binding.layoutLargeFilesDetails
            "Other" -> null // binding.layoutOtherDetails
            else -> null
        }
    }

    private fun populateCategoryDetails(categoryName: String, detailsLayout: LinearLayout) {
        val categoryFiles = getCategoryFiles(categoryName)

        // 清除现有视图（保留分割线）
        val childCount = detailsLayout.childCount
        if (childCount > 1) {
            detailsLayout.removeViews(1, childCount - 1)
        }

        // 根据不同类型显示不同数量的文件
        val displayLimit = when (categoryName) {
            "Large Files" -> 20 // 大文件显示较少
            "Duplicate Files" -> 100 // 重复文件可以多显示一些
            else -> 200 // 其他类型显示200个
        }

        // 对文件进行排序
        val sortedFiles = when (categoryName) {
            "Large Files" -> categoryFiles.sortedByDescending { it.size }
            "Duplicate Files" -> categoryFiles.sortedBy { it.name }
            else -> categoryFiles.sortedByDescending { it.size }
        }

        // 添加文件列表
        sortedFiles.take(displayLimit).forEach { file ->
            val fileView = createFileItemView(file, categoryName)
            detailsLayout.addView(fileView)
        }

        // 如果文件数量太多，显示提示
        if (categoryFiles.size > displayLimit) {
            val moreView = TextView(this).apply {
                text = "... There are still ${categoryFiles.size - displayLimit} files available"
                textSize = 12f
                setTextColor(resources.getColor(android.R.color.darker_gray, null))
                setPadding(48, 16, 16, 16)
            }
            detailsLayout.addView(moreView)
        }
    }

    private fun createFileItemView(file: FileScanner.JunkFile, categoryName: String): View {
        val itemView = LayoutInflater.from(this).inflate(R.layout.item_junk_file, null)

        val tvFileName = itemView.findViewById<TextView>(R.id.tv_file_name)
        val tvFilePath = itemView.findViewById<TextView>(R.id.tv_file_path)
        val tvFileSize = itemView.findViewById<TextView>(R.id.tv_file_size)
        val ivFileStatus = itemView.findViewById<ImageView>(R.id.iv_file_status)

        tvFileName.text = file.name

        // 根据文件类型显示不同的路径信息
        when (categoryName) {
            "Large Files" -> {
                tvFilePath.text = "${file.path}\n修改时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(file.lastModified))}"
            }
            "Duplicate Files" -> {
                tvFilePath.text = "${file.path}\n(重复文件)"
            }
            "Empty Files" -> {
                tvFilePath.text = "${file.path}\n(空文件)"
            }
            else -> {
                tvFilePath.text = file.path
            }
        }

        tvFileSize.text = formatFileSize(file.size)
        ivFileStatus.setImageResource(if (file.isSelected) R.drawable.ic_check else R.drawable.ic_check_circle)

        // 为大文件添加特殊颜色标识
        if (categoryName == "Large Files" && file.size > 200 * 1024 * 1024) {
            tvFileSize.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
        }

        itemView.setOnClickListener {
            Log.d("FileClick", "Clicked file: ${file.name} (current selected: ${file.isSelected})")

            // 直接切换文件选中状态
            file.isSelected = !file.isSelected

            // 同步更新分类选中状态
            updateCategorySelectionStatus(categoryName)

            // 重新计算所有选中文件
            recalculateSelectedFiles()

            // 更新UI
            ivFileStatus.setImageResource(if (file.isSelected) R.drawable.ic_check else R.drawable.ic_check_circle)
            updateCleanButton()

            Log.d("FileClick", "After click - selectedFiles size: ${selectedFiles.size}")
        }

        return itemView
    }

    private fun updateCategorySelectionStatus(categoryName: String) {
        val categoryFiles = getCategoryFiles(categoryName)
        val selectedCount = categoryFiles.count { it.isSelected }
        val totalCount = categoryFiles.size

        // 更新分类选中状态
        categorySelectStates[categoryName] = selectedCount == totalCount

        // 更新分类选中图标
        val statusIcon = when (categoryName) {
            "App Cache" -> binding.ivAppCacheStatus
            "Apk Files" -> binding.ivAppFileStatus
            "Log Files" -> binding.ivLogFileStatus
            "AD Junk" -> binding.ivAdJunkStatus
            "Temp Files" -> binding.ivTempFilesStatus
            // 新增分类的图标需要根据实际的UI元素来设置
            // "Empty Files" -> binding.ivEmptyFilesStatus
            // "Duplicate Files" -> binding.ivDuplicateFilesStatus
            // "Large Files" -> binding.ivLargeFilesStatus
            // "Other" -> binding.ivOtherStatus
            else -> null
        }

        statusIcon?.setImageResource(
            if (categorySelectStates[categoryName] == true) R.drawable.ic_check
            else R.drawable.ic_check_circle
        )
    }

    private fun startScanning() {
        isScanning = true
        binding.linScan.visibility = View.VISIBLE
        binding.butClean.visibility = View.GONE
        binding.tvProNum.text = "0"
        binding.tvUnit.text = "MB"

        // 清空之前的状态
        selectedFiles.clear()
        scanResult = FileScanner.ScanResult()

        // 重置分类大小显示
        binding.tvAppCacheSize.text = "0MB"
        binding.tvApkSize.text = "0MB"
        binding.tvLogSize.text = "0MB"
        binding.tvAdSize.text = "0MB"
        binding.tvTempSize.text = "0MB"

        scanJob = lifecycleScope.launch {
            try {
                scanResult = fileScanner.startScan(this@ScanActivity)
                withContext(Dispatchers.Main) {
                    updateCategorySizes()
                    // 更新所有分类的选中状态图标
                    updateAllCategoryIcons()
                }
            } catch (e: Exception) {
                onError("The scan failed: ${e.message}")
            }
        }
    }

    private fun updateAllCategoryIcons() {
        binding.ivAppCacheStatus.setImageResource(
            if (categorySelectStates["App Cache"] == true) R.drawable.ic_check else R.drawable.ic_check_circle
        )
        binding.ivAppFileStatus.setImageResource(
            if (categorySelectStates["Apk Files"] == true) R.drawable.ic_check else R.drawable.ic_check_circle
        )
        binding.ivLogFileStatus.setImageResource(
            if (categorySelectStates["Log Files"] == true) R.drawable.ic_check else R.drawable.ic_check_circle
        )
        binding.ivAdJunkStatus.setImageResource(
            if (categorySelectStates["AD Junk"] == true) R.drawable.ic_check else R.drawable.ic_check_circle
        )
        binding.ivTempFilesStatus.setImageResource(
            if (categorySelectStates["Temp Files"] == true) R.drawable.ic_check else R.drawable.ic_check_circle
        )

        // 新增分类的图标更新
        // binding.ivEmptyFilesStatus?.setImageResource(...)
        // binding.ivDuplicateFilesStatus?.setImageResource(...)
        // binding.ivLargeFilesStatus?.setImageResource(...)
        // binding.ivOtherStatus?.setImageResource(...)
    }

    private fun cancelScanning() {
        isScanning = false
        scanJob?.cancel()
        fileScanner.cancelScan()
        binding.linScan.visibility = View.GONE
    }

    // FileScanner.ScanProgressCallback 实现
    override fun onProgressUpdate(currentPath: String, scannedFiles: Int, foundJunk: Int) {
        val shortPath = currentPath.substringAfterLast("/").take(30)
        binding.tvFile.text = "Scanning: $shortPath"

        // 实时更新已找到的垃圾文件数量
        binding.tvProNum.text = foundJunk.toString()
    }

    override fun onCategoryUpdate(category: String, files: List<FileScanner.JunkFile>) {
        // 实时更新分类大小 - 每当有新文件被分类时调用
        runOnUiThread {
            updateCategorySizes()
        }
    }

    override fun onScanComplete(result: FileScanner.ScanResult) {
        isScanning = false
        binding.linScan.visibility = View.GONE

        val totalSize = result.getTotalSize()
        scanResult = result

        // 清空选中文件集合
        selectedFiles.clear()

        Log.d("ScanComplete", "Initializing selected files...")

        // 根据分类选中状态来初始化selectedFiles和文件的isSelected状态
        val allCategories = listOf(
            "App Cache" to result.appCache,
            "Apk Files" to result.apkFiles,
            "Log Files" to result.logFiles,
            "AD Junk" to result.adJunk,
            "Temp Files" to result.tempFiles,
        )

        allCategories.forEach { (categoryName, files) ->
            val shouldSelect = categorySelectStates[categoryName] ?: true
            Log.d("ScanComplete", "$categoryName: ${files.size} files, shouldSelect: $shouldSelect")

            files.forEach { file ->
                file.isSelected = shouldSelect
                if (shouldSelect) {
                    selectedFiles.add(file)
                }
            }
        }

        if (totalSize > 0) {
            binding.imgBg.setImageResource(R.drawable.bg_laji)
            binding.butClean.visibility = View.VISIBLE
        }

        // 更新总垃圾显示
        val (size, unit) = formatFileSizeParts(totalSize)
        binding.tvProNum.text = size
        binding.tvUnit.text = unit

        updateCategorySizes()
        updateCleanButton()
        // 详细的调试日志
        logScanResults(result)
    }

    private fun logScanResults(result: FileScanner.ScanResult) {
        Log.d("ScanComplete", "=== Enhanced Scan Complete Summary ===")
        Log.d("ScanComplete", "Total files found: ${result.getTotalCount()}")
        Log.d("ScanComplete", "App Cache: ${result.appCache.size} files (${formatFileSize(result.appCache.sumOf { it.size })})")
        Log.d("ScanComplete", "Apk Files: ${result.apkFiles.size} files (${formatFileSize(result.apkFiles.sumOf { it.size })})")
        Log.d("ScanComplete", "Log Files: ${result.logFiles.size} files (${formatFileSize(result.logFiles.sumOf { it.size })})")
        Log.d("ScanComplete", "AD Junk: ${result.adJunk.size} files (${formatFileSize(result.adJunk.sumOf { it.size })})")
        Log.d("ScanComplete", "Temp Files: ${result.tempFiles.size} files (${formatFileSize(result.tempFiles.sumOf { it.size })})")
        Log.d("ScanComplete", "Selected files total: ${selectedFiles.size}")
        Log.d("ScanComplete", "Selected size total: ${formatFileSize(selectedFiles.sumOf { it.size })}")
        Log.d("ScanComplete", "===============================")
    }

    override fun onError(error: String) {
        isScanning = false
        binding.linScan.visibility = View.GONE
        Toast.makeText(this, error, Toast.LENGTH_LONG).show()
    }

    private fun updateCategorySizes() {
        val result = scanResult ?: return
        binding.tvAppCacheSize.text = formatFileSize(result.appCache.sumOf { it.size })
        binding.tvApkSize.text = formatFileSize(result.apkFiles.sumOf { it.size })
        binding.tvLogSize.text = formatFileSize(result.logFiles.sumOf { it.size })
        binding.tvAdSize.text = formatFileSize(result.adJunk.sumOf { it.size })
        binding.tvTempSize.text = formatFileSize(result.tempFiles.sumOf { it.size })
    }

    private fun updateCleanButton() {
        val selectedSize = selectedFiles.sumOf { it.size }
        val selectedCount = selectedFiles.size
        val buttonText = "Clean ($selectedCount files, ${formatFileSize(selectedSize)})"
        binding.butClean.text = buttonText
        binding.butClean.isEnabled = selectedFiles.isNotEmpty()
        AppDataTool.cleanNum = buttonText
        Log.d("CleanButton", "updateCleanButton: $selectedCount files, ${formatFileSize(selectedSize)}")
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes == 0L) return "0B"

        val gb = 1024L * 1024L * 1024L
        val mb = 1024L * 1024L
        val kb = 1024L

        return when {
            bytes >= gb -> "${DecimalFormat("#.##").format(bytes.toDouble() / gb)}GB"
            bytes >= mb -> "${DecimalFormat("#.##").format(bytes.toDouble() / mb)}MB"
            bytes >= kb -> "${DecimalFormat("#.##").format(bytes.toDouble() / kb)}KB"
            else -> "${bytes}B"
        }
    }

    private fun formatFileSizeParts(bytes: Long): Pair<String, String> {
        val gb = 1024 * 1024 * 1024
        val mb = 1024 * 1024

        return when {
            bytes >= gb -> {
                val size = round((bytes.toFloat() / gb) * 100) / 100
                Pair(DecimalFormat("#.##").format(size), "GB")
            }
            bytes >= mb -> {
                val size = round((bytes.toFloat() / mb) * 100) / 100
                Pair(DecimalFormat("#.##").format(size), "MB")
            }
            else -> {
                val size = round((bytes.toFloat() / 1024) * 100) / 100
                Pair(DecimalFormat("#.##").format(size), "KB")
            }
        }
    }

    private fun startCleaning() {
        lifecycleScope.launch {
            try {
                val (deletedCount, deletedSize) = FileScanner.deleteFiles(selectedFiles) { current, total ->

                }
                showCleanResult(deletedCount, deletedSize)

            } catch (e: Exception) {
                Toast.makeText(this@ScanActivity, "Something went wrong with the cleanup: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateAfterDeletion() {
        // 从scanResult中移除已删除的文件
        scanResult?.let { result ->
            result.appCache.removeAll { file -> !java.io.File(file.path).exists() }
            result.apkFiles.removeAll { file -> !java.io.File(file.path).exists() }
            result.logFiles.removeAll { file -> !java.io.File(file.path).exists() }
            result.adJunk.removeAll { file -> !java.io.File(file.path).exists() }
            result.tempFiles.removeAll { file -> !java.io.File(file.path).exists() }
        }

        // 从selectedFiles中移除已删除的文件
        selectedFiles.removeAll { file -> !java.io.File(file.path).exists() }

        // 更新UI显示
        updateCategorySizes()
        updateCleanButton()

        // 刷新已展开的分类详情
        refreshExpandedCategories()

        // 更新总垃圾大小显示
        val remainingSize = scanResult?.getTotalSize() ?: 0L
        val (size, unit) = formatFileSizeParts(remainingSize)
        binding.tvProNum.text = size
        binding.tvUnit.text = unit

        if (remainingSize == 0L) {
            binding.butClean.visibility = View.GONE
            // 恢复原始背景
            binding.imgBg.setImageResource(R.drawable.bj)
        }
    }

    private fun refreshExpandedCategories() {
        categoryExpandStates.forEach { (categoryName, isExpanded) ->
            if (isExpanded) {
                val detailsLayout = getDetailsLayoutForCategory(categoryName)
                if (detailsLayout != null) {
                    populateCategoryDetails(categoryName, detailsLayout)
                }
            }
        }
    }

    private fun showCleanResult(deletedCount: Int, deletedSize: Long) {
        setResult(RESULT_OK)
        AppDataTool.jumpType = 0

        // 设置删除结果信息
        AppDataTool.cleanNum = "${formatFileSize(deletedSize)}"

        startActivity(Intent(this, ScanLoadActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelScanning()
    }

    override fun onBackPressed() {
        if (isScanning) {
            showCancelScanDialog()
        } else {
            super.onBackPressed()
        }
    }
}