package com.live.life.intoxication.filecleanup.image

// 照片数据模型
data class PhotoItem(
    val id: Long,
    val path: String,
    val size: Long,
    val dateAdded: Long,
    var isSelected: Boolean = false
)

// 日期分组数据模型
data class PhotoDateGroup(
    val date: String,
    val dateMillis: Long,
    val photos: MutableList<PhotoItem>,
    var isAllSelected: Boolean = false
) {
    fun getTotalSize(): Long = photos.sumOf { it.size }
    fun getSelectedSize(): Long = photos.filter { it.isSelected }.sumOf { it.size }
    fun getSelectedCount(): Int = photos.count { it.isSelected }
}

// 适配器中的项目类型
sealed class PhotoListItem {
    data class DateHeader(val group: PhotoDateGroup) : PhotoListItem()
    data class PhotoImage(val photo: PhotoItem, val group: PhotoDateGroup) : PhotoListItem()
}