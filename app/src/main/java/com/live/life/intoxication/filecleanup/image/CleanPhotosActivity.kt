package com.live.life.intoxication.filecleanup.image

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.live.life.intoxication.filecleanup.AppDataTool
import com.live.life.intoxication.filecleanup.R
import com.live.life.intoxication.filecleanup.databinding.ActivityImageBinding
import com.live.life.intoxication.filecleanup.one.ScanLoadActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CleanPhotosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageBinding
    private lateinit var photoAdapter: PhotoListAdapter
    private val photoGroups = mutableListOf<PhotoDateGroup>()
    private var isAllSelected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.image)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        supportActionBar?.hide()
        setupBackPress()
        setupRecyclerView()
        setupClickListeners()
        loadPhotos()
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback {
            finish()
        }

        binding.textBack.setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        photoAdapter = PhotoListAdapter(
            onPhotoClick = { photo, group ->
                togglePhotoSelection(photo, group)
            },
            onDateSelectAll = { group ->
                toggleDateGroupSelection(group)
            }
        )

        val spanCount = 3
        val layoutManager = GridLayoutManager(this, spanCount)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (photoAdapter.getItemViewType(position)) {
                    0 -> spanCount // 日期标题占满一行
                    1 -> 1 // 照片占1/3
                    else -> 1
                }
            }
        }

        binding.rvPhotos.layoutManager = layoutManager
        binding.rvPhotos.adapter = photoAdapter
    }

    private fun setupClickListeners() {
        // 全选按钮
        binding.llSelectAll.setOnClickListener {
            toggleSelectAll()
        }
        // 删除按钮
        binding.btnDelete.setOnClickListener {
            showDeleteConfirmDialog()
        }
    }

    private fun loadPhotos() {
        lifecycleScope.launch {
            try {
                val groups = withContext(Dispatchers.IO) {
                    PhotoUtils.getAllPhotos(this@CleanPhotosActivity)
                }

                photoGroups.clear()
                photoGroups.addAll(groups)
                photoAdapter.updateData(photoGroups)
                updateUI()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@CleanPhotosActivity, "Failed to load photos", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun togglePhotoSelection(photo: PhotoItem, group: PhotoDateGroup) {
        photo.isSelected = !photo.isSelected
        updateGroupSelectionState(group)
        updateGlobalSelectionState()
        photoAdapter.notifyGroupChanged(group)
        updateUI()
    }

    private fun toggleDateGroupSelection(group: PhotoDateGroup) {
        val shouldSelect = !group.isAllSelected
        group.photos.forEach { photo ->
            photo.isSelected = shouldSelect
        }
        group.isAllSelected = shouldSelect
        updateGlobalSelectionState()
        photoAdapter.notifyGroupChanged(group)
        updateUI()
    }

    private fun toggleSelectAll() {
        isAllSelected = !isAllSelected
        photoGroups.forEach { group ->
            group.photos.forEach { photo ->
                photo.isSelected = isAllSelected
            }
            group.isAllSelected = isAllSelected
        }
        photoAdapter.notifyDataSetChanged()
        updateUI()
        updateGlobalSelectionState()
    }

    private fun updateGroupSelectionState(group: PhotoDateGroup) {
        val selectedCount = group.getSelectedCount()
        group.isAllSelected = selectedCount == group.photos.size && group.photos.isNotEmpty()
    }

    private fun updateGlobalSelectionState() {
        val allPhotosCount = photoGroups.sumOf { it.photos.size }
        val selectedPhotosCount = photoGroups.sumOf { it.getSelectedCount() }
        isAllSelected = selectedPhotosCount == allPhotosCount && allPhotosCount > 0

        binding.ivSelectAll.setImageResource(
            if (isAllSelected) R.drawable.ic_image_selected
            else R.drawable.ic_image_not_selected
        )
    }

    private fun updateUI() {
        val totalSelectedSize = photoGroups.sumOf { it.getSelectedSize() }
        val selectedCount = photoGroups.sumOf { it.getSelectedCount() }

        val (sizeText, unit) = PhotoUtils.formatFileSize(totalSelectedSize)
        binding.tvProNum.text = sizeText
        binding.tvUnit.text = unit

        // 更新删除按钮状态
        binding.btnDelete.isEnabled = selectedCount > 0
        binding.btnDelete.alpha = if (selectedCount > 0) 1.0f else 0.5f
    }

    private fun showDeleteConfirmDialog() {
        val selectedPhotos = mutableListOf<PhotoItem>()
        photoGroups.forEach { group ->
            selectedPhotos.addAll(group.photos.filter { it.isSelected })
        }

        if (selectedPhotos.isEmpty()) {
            Toast.makeText(this, "No photos selected", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Delete Photos")
            .setMessage("Are you sure you want to delete ${selectedPhotos.size} selected photos? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteSelectedPhotos(selectedPhotos)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSelectedPhotos(photosToDelete: List<PhotoItem>) {
        lifecycleScope.launch {
            try {
                val totalSelectedSize = photoGroups.sumOf { it.getSelectedSize() }
                val (sizeText, unit) = PhotoUtils.formatFileSize(totalSelectedSize)
                binding.tvProNum.text = sizeText
                binding.tvUnit.text = unit
                AppDataTool.cleanNum = sizeText+unit
                val success = withContext(Dispatchers.IO) {
                    PhotoUtils.deletePhotos(this@CleanPhotosActivity, photosToDelete)
                }

                if (success) {
                    photoGroups.forEach { group ->
                        group.photos.removeAll { photo ->
                            photosToDelete.any { it.id == photo.id }
                        }
                    }

                    // 移除空的日期组
                    photoGroups.removeAll { it.photos.isEmpty() }

                    photoAdapter.updateData(photoGroups)
                    updateGlobalSelectionState()
                    updateUI()
                    AppDataTool.jumpType = 1

                    startActivity(Intent(this@CleanPhotosActivity, ScanLoadActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this@CleanPhotosActivity,
                        "Failed to delete some photos",
                        Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@CleanPhotosActivity,
                    "Error occurred while deleting photos",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }
}