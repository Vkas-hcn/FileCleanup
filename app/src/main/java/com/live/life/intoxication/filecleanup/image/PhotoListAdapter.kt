package com.live.life.intoxication.filecleanup.image

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.live.life.intoxication.filecleanup.App
import com.live.life.intoxication.filecleanup.R
import java.text.SimpleDateFormat
import java.util.*

class PhotoListAdapter(
    private val onPhotoClick: (PhotoItem, PhotoDateGroup) -> Unit,
    private val onDateSelectAll: (PhotoDateGroup) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_DATE_HEADER = 0
        private const val TYPE_PHOTO_ITEM = 1
        private const val SPAN_COUNT = 3
    }

    private val items = mutableListOf<PhotoListItem>()
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    fun updateData(groups: List<PhotoDateGroup>) {
        items.clear()
        groups.forEach { group ->
            items.add(PhotoListItem.DateHeader(group))
            group.photos.forEach { photo ->
                items.add(PhotoListItem.PhotoImage(photo, group))
            }
        }
        notifyDataSetChanged()
    }

    fun notifyGroupChanged(group: PhotoDateGroup) {
        val startIndex = items.indexOfFirst {
            it is PhotoListItem.DateHeader && it.group == group
        }
        if (startIndex != -1) {
            val endIndex = startIndex + group.photos.size + 1
            notifyItemRangeChanged(startIndex, endIndex - startIndex)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is PhotoListItem.DateHeader -> TYPE_DATE_HEADER
            is PhotoListItem.PhotoImage -> TYPE_PHOTO_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_DATE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_photo_date_header, parent, false)
                DateHeaderViewHolder(view)
            }
            TYPE_PHOTO_ITEM -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_photo_image, parent, false)
                PhotoViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is PhotoListItem.DateHeader -> {
                (holder as DateHeaderViewHolder).bind(item.group)
            }
            is PhotoListItem.PhotoImage -> {
                (holder as PhotoViewHolder).bind(item.photo, item.group)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    inner class DateHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDate: TextView = itemView.findViewById(R.id.tv_date)
        private val tvCount: TextView = itemView.findViewById(R.id.tv_count)
        private val ivSelectAll: ImageView = itemView.findViewById(R.id.iv_select_all)

        fun bind(group: PhotoDateGroup) {
            tvDate.text = dateFormat.format(Date(group.dateMillis))
            tvCount.text = "${group.photos.size} items"

            val selectedCount = group.getSelectedCount()
            group.isAllSelected = selectedCount == group.photos.size && group.photos.isNotEmpty()

            ivSelectAll.setImageResource(
                if (group.isAllSelected) R.drawable.ic_image_selected
                else R.drawable.ic_image_not_selected
            )

            itemView.setOnClickListener {
                onDateSelectAll(group)
            }
        }
    }

    inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivPhoto: AppCompatImageView = itemView.findViewById(R.id.iv_photo)
        private val ivSelected: ImageView = itemView.findViewById(R.id.iv_selected)
        private val tvSize: TextView = itemView.findViewById(R.id.tv_size)

        fun bind(photo: PhotoItem, group: PhotoDateGroup) {
            Glide.with(App.instance)
                .load(photo.path)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .into(ivPhoto)

            ivSelected.setImageResource(
                if (photo.isSelected) R.drawable.ic_image_selected
                else R.drawable.ic_image_not_selected
            )
            tvSize.text = "${PhotoUtils.formatFileSize(photo.size).first}${PhotoUtils.formatFileSize(photo.size).second}"

            itemView.setOnClickListener {
                onPhotoClick(photo, group)
            }
        }
    }
}