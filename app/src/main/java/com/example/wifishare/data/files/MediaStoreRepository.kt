package com.example.wifishare.data.files

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.wifishare.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreRepository(private val context: Context) {

    suspend fun images(): List<MediaItem> = queryCollection(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.MIME_TYPE,
        MediaStore.Images.Media.DATE_MODIFIED,
        Source.IMAGES
    )

    suspend fun videos(): List<MediaItem> = queryCollection(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.MIME_TYPE,
        MediaStore.Video.Media.DATE_MODIFIED,
        Source.VIDEOS
    )

    suspend fun audio(): List<MediaItem> = queryCollection(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.SIZE,
        MediaStore.Audio.Media.MIME_TYPE,
        MediaStore.Audio.Media.DATE_MODIFIED,
        Source.AUDIO
    )

    suspend fun documents(): List<MediaItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<MediaItem>()
        try {
            val collection = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.DATE_MODIFIED
            )
            val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
            val args = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_NONE.toString())
            val sort = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

            context.contentResolver.query(collection, projection, selection, args, sort)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val mimeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    result.add(
                        MediaItem(
                            uri = uri,
                            name = c.getString(nameCol) ?: "file",
                            sizeBytes = c.getLong(sizeCol),
                            mimeType = c.getString(mimeCol) ?: "application/octet-stream",
                            modifiedMs = c.getLong(dateCol) * 1000L,
                            source = Source.DOCUMENTS
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Logger.e("documents query failed", e)
        }
        result.sortedByDescending { it.modifiedMs }
    }

    private suspend fun queryCollection(
        collection: Uri,
        idCol: String,
        nameCol: String,
        sizeCol: String,
        mimeCol: String,
        dateCol: String,
        source: Source
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<MediaItem>()
        try {
            val projection = arrayOf(idCol, nameCol, sizeCol, mimeCol, dateCol)
            val sort = "$dateCol DESC"
            context.contentResolver.query(collection, projection, null, null, sort)?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(idCol)
                val nameIdx = c.getColumnIndexOrThrow(nameCol)
                val sizeIdx = c.getColumnIndexOrThrow(sizeCol)
                val mimeIdx = c.getColumnIndexOrThrow(mimeCol)
                val dateIdx = c.getColumnIndexOrThrow(dateCol)
                while (c.moveToNext()) {
                    val id = c.getLong(idIdx)
                    val uri = ContentUris.withAppendedId(collection, id)
                    result.add(
                        MediaItem(
                            uri = uri,
                            name = c.getString(nameIdx) ?: "file",
                            sizeBytes = c.getLong(sizeIdx),
                            mimeType = c.getString(mimeIdx) ?: "*/*",
                            modifiedMs = c.getLong(dateIdx) * 1000L,
                            source = source
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Logger.e("query $source failed", e)
        }
        result
    }
}
