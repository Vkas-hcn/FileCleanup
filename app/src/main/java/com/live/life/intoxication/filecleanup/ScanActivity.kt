package com.live.life.intoxication.filecleanup

import android.app.AlertDialog
import android.app.ProgressDialog
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
import androidx.lifecycle.lifecycleScope
import com.live.life.intoxication.filecleanup.databinding.ActivityScanBinding
import com.live.life.intoxication.filecleanup.scanner.FileScanner
import com.live.life.intoxication.filecleanup.utils.PermissionHelper
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
        categoryExpandStates["App Cache"] = false
        categoryExpandStates["Apk Files"] = false
        categoryExpandStates["Log Files"] = false
        categoryExpandStates["AD Junk"] = false
        categoryExpandStates["Temp Files"] = false

        categorySelectStates["App Cache"] = true
        categorySelectStates["Apk Files"] = true
        categorySelectStates["Log Files"] = true
        categorySelectStates["AD Junk"] = true
        categorySelectStates["Temp Files"] = true
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionHelper.STORAGE_PERMISSION_CODE) {
            if (PermissionHelper.hasStoragePermission(this)) {
                startScanning()
            } else {
                Toast.makeText(this, "Storage permissions are required to scan files", Toast.LENGTH_LONG).show()
                finish()
            }
        }
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
                showCleanConfirmDialog()
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

        // 设置分类选中按钮
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

        // 获取该分类下的所有文件
        val categoryFiles = getCategoryFiles(categoryName)

        Log.d("CategorySelection", "Processing $categoryName: ${categoryFiles.size} files")

        categoryFiles.forEach { file ->
            // 先从selectedFiles中移除该文件的所有实例
            selectedFiles.removeAll { it.path == file.path }

            // 设置新的选中状态
            file.isSelected = newSelectState

            // 如果新状态是选中，则添加到selectedFiles
            if (file.isSelected) {
                selectedFiles.add(file)
                Log.d("CategorySelection", "Added file: ${file.name} (${file.path})")
            } else {
                Log.d("CategorySelection", "Removed file: ${file.name} (${file.path})")
            }
        }

        // 更新UI图标
        statusIcon.setImageResource(
            if (newSelectState) R.drawable.ic_check else R.drawable.ic_check_circle
        )

        // 更新清理按钮
        updateCleanButton()

        // 如果分类展开了，更新详情中的选中状态
        if (categoryExpandStates[categoryName] == true) {
            val detailsLayout = getDetailsLayoutForCategory(categoryName)
            if (detailsLayout != null) {
                populateCategoryDetails(categoryName, detailsLayout)
            }
        }

        // 详细的调试日志
        Log.d("CategorySelection", "$categoryName: ${categoryFiles.size} files, selected: $newSelectState")
        Log.d("CategorySelection", "Total selected files: ${selectedFiles.size}")
        Log.d("CategorySelection", "Selected file paths: ${selectedFiles.map { it.path }}")
        Log.d("CategorySelection", "Selected size: ${formatFileSize(selectedFiles.sumOf { it.size })}")
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

        // 添加文件列表
        categoryFiles.take(50).forEach { file -> // 限制显示数量避免卡顿
            val fileView = createFileItemView(file, categoryName)
            detailsLayout.addView(fileView)
        }

        // 如果文件数量太多，显示提示
        if (categoryFiles.size > 50) {
            val moreView = TextView(this).apply {
                text = "... There are still ${categoryFiles. size -50} files available"
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
        tvFilePath.text = file.path
        tvFileSize.text = formatFileSize(file.size)
        ivFileStatus.setImageResource(if (file.isSelected) R.drawable.ic_check else R.drawable.ic_check_circle)

        itemView.setOnClickListener {
            Log.d("FileClick", "Clicked file: ${file.name} (current selected: ${file.isSelected})")

            // 先从selectedFiles中移除该文件的所有实例
            val removedCount = selectedFiles.removeAll { it.path == file.path }
            Log.d("FileClick", "Removed $removedCount instances of ${file.path}")

            // 切换选中状态
            file.isSelected = !file.isSelected
            ivFileStatus.setImageResource(if (file.isSelected) R.drawable.ic_check else R.drawable.ic_check_circle)

            // 如果选中，添加到selectedFiles
            if (file.isSelected) {
                selectedFiles.add(file)
                Log.d("FileClick", "Added file to selected: ${file.path}")
            }

            Log.d("FileClick", "After click - selectedFiles size: ${selectedFiles.size}")

            updateCategorySelectionStatus(categoryName)
            updateCleanButton()
        }

        return itemView
    }

    private fun updateCategorySelectionStatus(categoryName: String) {
        val categoryFiles = getCategoryFiles(categoryName)
        val selectedCount = categoryFiles.count { it.isSelected }
        val totalCount = categoryFiles.size

        categorySelectStates[categoryName] = selectedCount == totalCount

        // 更新分类选中图标
        val statusIcon = when (categoryName) {
            "App Cache" -> binding.ivAppCacheStatus
            "Apk Files" -> binding.ivAppFileStatus
            "Log Files" -> binding.ivLogFileStatus
            "AD Junk" -> binding.ivAdJunkStatus
            "Temp Files" -> binding.ivTempFilesStatus
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
                }
            } catch (e: Exception) {
                onError("The scan failed: ${e.message}")
            }
        }
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
            "Other" to result.otherFiles
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

        Toast.makeText(this, "Scan completed! Find ${result.getTotalCount()} junk files", Toast.LENGTH_SHORT).show()

        // 详细的调试日志
        Log.d("ScanComplete", "=== Scan Complete Summary ===")
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
        Log.e("TAG", "updateCategorySizes: ${scanResult==null}", )
        val result = scanResult ?: return
        Log.e("TAG", "updateCategorySizes: ${formatFileSize(result.appCache.sumOf { it.size })}")
        binding.tvAppCacheSize.text = formatFileSize(result.appCache.sumOf { it.size })
        binding.tvApkSize.text = formatFileSize(result.apkFiles.sumOf { it.size })
        binding.tvLogSize.text = formatFileSize(result.logFiles.sumOf { it.size })
        binding.tvAdSize.text = formatFileSize(result.adJunk.sumOf { it.size })
        binding.tvTempSize.text = formatFileSize(result.tempFiles.sumOf { it.size })
    }

    private fun updateCleanButton() {
        val selectedSize = selectedFiles.sumOf { it.size }
        val selectedCount = selectedFiles.size
        val buttonText = "Clean (${formatFileSize(selectedSize)})"
        binding.butClean.text = buttonText
        binding.butClean.isEnabled = selectedFiles.isNotEmpty()

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

    private fun showCleanConfirmDialog() {
        val selectedSize = selectedFiles.sumOf { it.size }
        val message = "Are you sure you want to clean up the selected ${selectedFiles. size} files?\nTotal size: ${formatFileSize (selectedSize)}"
        AlertDialog.Builder(this)
            .setTitle("Confirm the cleanup")
            .setMessage(message)
            .setPositiveButton("Are you sure") { _, _ ->
                startCleaning()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startCleaning() {
        val progressDialog = ProgressDialog(this).apply {
            setTitle("Cleaning up")
            setMessage("Deleting junk files...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = selectedFiles.size
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                val filesToDelete = selectedFiles.toList()
                val (deletedCount, deletedSize) = FileScanner.deleteFiles(filesToDelete) { current, total ->
                    progressDialog.progress = current
                    progressDialog.setMessage("Cleaned up: $current/$total")
                }

                progressDialog.dismiss()
                showCleanResult(deletedCount, deletedSize)

            } catch (e: Exception) {
                progressDialog.dismiss()
                Toast.makeText(this@ScanActivity, "Something went wrong with the cleanup: ${e.message}", Toast.LENGTH_LONG).show()
            }
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
        val message = "Cleanup is complete！\n" +
                "Clean up your files: $deletedCount \n" +
                "Free up space: ${formatFileSize(deletedSize)}"

        AlertDialog.Builder(this)
            .setTitle("Cleanup is complete")
            .setMessage(message)
            .setPositiveButton("Are you sure") { _, _ ->
                // 清理成功后，从scanResult中移除已删除的文件
                scanResult?.let { result ->
                    result.appCache.removeAll { file -> !java.io.File(file.path).exists() }
                    result.apkFiles.removeAll { file -> !java.io.File(file.path).exists() }
                    result.logFiles.removeAll { file -> !java.io.File(file.path).exists() }
                    result.adJunk.removeAll { file -> !java.io.File(file.path).exists() }
                    result.tempFiles.removeAll { file -> !java.io.File(file.path).exists() }
                    result.otherFiles.removeAll { file -> !java.io.File(file.path).exists() }
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

                setResult(RESULT_OK)
                // 可以选择是否返回主界面，或者让用户继续扫描
                // finish()
            }
            .setCancelable(false)
            .show()
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