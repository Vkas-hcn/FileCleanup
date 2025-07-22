package com.live.life.intoxication.filecleanup.file

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.live.life.intoxication.filecleanup.R
import com.live.life.intoxication.filecleanup.databinding.ItemFileBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FilesAdapter(
    private val context: Context,
    private val onItemClick: (FileInfo) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<FileInfo, FilesAdapter.FileViewHolder>(FilesDiffCallback()) {

    inner class FileViewHolder(private val binding: ItemFileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(fileInfo: FileInfo) {
            binding.tvFileName.text = fileInfo.name
            binding.tvFileSize.text = fileInfo.formattedSize

            // 设置选中状态
            binding.ivSelectStatus.setImageResource(
                if (fileInfo.isSelected) R.drawable.ic_image_selected
                else R.drawable.ic_image_not_selected
            )

            // 设置文件图标或缩略图
            setFileIcon(fileInfo)

            // 点击事件
            binding.root.setOnClickListener {
                fileInfo.isSelected = !fileInfo.isSelected
                binding.ivSelectStatus.setImageResource(
                    if (fileInfo.isSelected) R.drawable.ic_image_selected
                    else R.drawable.ic_image_not_selected
                )
                onItemClick(fileInfo)

                // 通知选中数量变化
                val selectedCount = currentList.count { it.isSelected }
                onSelectionChanged(selectedCount)
            }
        }

        private fun setFileIcon(fileInfo: FileInfo) {
            when (fileInfo.type) {
                FileType.IMAGE -> {
                    loadImageThumbnail(fileInfo.file)
                }
                FileType.VIDEO -> {
                    loadVideoThumbnail(fileInfo.file)
                }
                else -> {
                    binding.ivFileIcon.setImageResource(R.drawable.ic_file_item)
                }
            }
        }

        private fun loadImageThumbnail(file: File) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 4
                        inJustDecodeBounds = false
                    }
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)

                    withContext(Dispatchers.Main) {
                        if (bitmap != null) {
                            binding.ivFileIcon.setImageBitmap(bitmap)
                        } else {
                            binding.ivFileIcon.setImageResource(R.drawable.ic_file_item)
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        binding.ivFileIcon.setImageResource(R.drawable.ic_file_item)
                    }
                }
            }
        }

        private fun loadVideoThumbnail(file: File) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val bitmap = ThumbnailUtils.createVideoThumbnail(
                        file.absolutePath,
                        MediaStore.Images.Thumbnails.MICRO_KIND
                    )

                    withContext(Dispatchers.Main) {
                        if (bitmap != null) {
                            binding.ivFileIcon.setImageBitmap(bitmap)
                        } else {
                            binding.ivFileIcon.setImageResource(R.drawable.ic_file_item)
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        binding.ivFileIcon.setImageResource(R.drawable.ic_file_item)
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemFileBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun getSelectedFiles(): List<FileInfo> {
        return currentList.filter { it.isSelected }
    }

    fun selectAll() {
        currentList.forEach { it.isSelected = true }
        notifyDataSetChanged()
        onSelectionChanged(currentList.size)
    }

    fun deselectAll() {
        currentList.forEach { it.isSelected = false }
        notifyDataSetChanged()
        onSelectionChanged(0)
    }
}

class FilesDiffCallback : DiffUtil.ItemCallback<FileInfo>() {
    override fun areItemsTheSame(oldItem: FileInfo, newItem: FileInfo): Boolean {
        return oldItem.file.absolutePath == newItem.file.absolutePath
    }

    override fun areContentsTheSame(oldItem: FileInfo, newItem: FileInfo): Boolean {
        return oldItem == newItem
    }
}