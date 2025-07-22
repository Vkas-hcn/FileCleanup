package com.live.life.intoxication.filecleanup.image

import android.content.ContentResolver
import android.content.Context
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.*

object PhotoUtils {

    fun getAllPhotos(context: Context): List<PhotoDateGroup> {
        val photos = mutableListOf<PhotoItem>()
        val contentResolver: ContentResolver = context.contentResolver

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val path = cursor.getString(dataColumn)
                val size = cursor.getLong(sizeColumn)
                val dateAdded = cursor.getLong(dateColumn) * 1000 // Convert to milliseconds

                photos.add(PhotoItem(id, path, size, dateAdded))
            }
        }

        return groupPhotosByDate(photos)
    }

    private fun groupPhotosByDate(photos: List<PhotoItem>): List<PhotoDateGroup> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()

        return photos.groupBy { photo ->
            calendar.timeInMillis = photo.dateAdded
            dateFormat.format(calendar.time)
        }.map { (dateString, photoList) ->
            // Use the first photo's date as the group date
            val groupDate = photoList.first().dateAdded
            PhotoDateGroup(
                date = dateString,
                dateMillis = groupDate,
                photos = photoList.toMutableList()
            )
        }.sortedByDescending { it.dateMillis }
    }

    fun formatFileSize(sizeInBytes: Long): Pair<String, String> {
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024

        return when {
            sizeInBytes >= gb -> {
                val size = sizeInBytes / gb
                Pair(String.format("%.1f", size), "GB")
            }
            sizeInBytes >= mb -> {
                val size = sizeInBytes / mb
                Pair(String.format("%.1f", size), "MB")
            }
            sizeInBytes >= kb -> {
                val size = sizeInBytes / kb
                Pair(String.format("%.1f", size), "KB")
            }
            else -> {
                Pair(sizeInBytes.toString(), "B")
            }
        }
    }

    fun deletePhotos(context: Context, photos: List<PhotoItem>): Boolean {
        return try {
            val contentResolver = context.contentResolver
            photos.forEach { photo ->
                val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                contentResolver.delete(uri, "${MediaStore.Images.Media._ID}=?", arrayOf(photo.id.toString()))
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}