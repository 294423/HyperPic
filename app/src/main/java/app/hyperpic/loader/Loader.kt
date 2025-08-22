package app.hyperpic.loader

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import app.hyperpic.data.MediaData
import app.hyperpic.data.FolderData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

class Loader(private val context: Context) {

    private val folderCountCache = mutableMapOf<String, Int>()

    suspend fun loadFolders(excludeHidden: Boolean): List<FolderData> = withContext(Dispatchers.IO) {
        val folders = mutableListOf<FolderData>()
        val folderThumbnails = mutableMapOf<String, MutableList<Uri>>()

        val projection = arrayOf(
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MIME_TYPE
        )

        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        val selection = buildString {
            append("(${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'image/%' OR ")
            append("${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'video/%')")
            if (excludeHidden) {
                append(" AND ${MediaStore.Files.FileColumns.WIDTH} > 0")
                append(" AND ${MediaStore.Files.FileColumns.HEIGHT} > 0")
                append(" AND ${MediaStore.Files.FileColumns.SIZE} > 5000")
            }
        }

        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val folderIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
            val folderNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
            val folderPathColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val mediaIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)

            val folderIds = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                val id = cursor.getString(folderIdColumn)
                folderIds.add(id)

                if (folderThumbnails.getOrPut(id) { mutableListOf() }.size < 3) {
                    val mediaId = cursor.getLong(mediaIdColumn)
                    val thumbnailUri: Uri = ContentUris.withAppendedId(
                        MediaStore.Files.getContentUri("external"),
                        mediaId
                    )
                    folderThumbnails[id]?.add(thumbnailUri)
                }
            }

            calculateFolderCounts(folderIds)

            cursor.moveToPosition(-1)
            val processedFolders = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                val id = cursor.getString(folderIdColumn)
                if (processedFolders.add(id)) {
                    val path = cursor.getString(folderPathColumn)
                    val name = cursor.getString(folderNameColumn) ?: File(path).parentFile?.name ?: "Unknown Folder"
                    val count = folderCountCache[id] ?: 0
                    val parentPath = File(path).parent ?: ""
                    val thumbnailUris = folderThumbnails[id] ?: emptyList()

                    folders.add(FolderData(id, name, count, parentPath, thumbnailUris))
                }
            }
        }
        folders
    }

    private suspend fun calculateFolderCounts(folderIds: Set<String>) = withContext(Dispatchers.IO) {
        val selection = ("${MediaStore.Files.FileColumns.BUCKET_ID} IN (${folderIds.joinToString(",") { "?" }}) " +
                "AND (${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'image/%' OR " +
                "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'video/%')")

        val selectionArgs = folderIds.toTypedArray()

        val projection = arrayOf(
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns._ID
        )

        val countMap = mutableMapOf<String, Int>()

        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)

            while (cursor.moveToNext()) {
                val bucketId = cursor.getString(bucketIdColumn)
                countMap[bucketId] = (countMap[bucketId] ?: 0) + 1
            }
        }

        folderCountCache.putAll(countMap)
    }

    suspend fun getMediaForFolder(folderId: String, excludeHidden: Boolean): List<MediaData> = withContext(Dispatchers.IO) {
        val mediaItems = mutableListOf<MediaData>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DURATION,
            MediaStore.Files.FileColumns.BUCKET_ID
        )

        val selection = buildString {
            append("${MediaStore.Files.FileColumns.BUCKET_ID} = ? ")
            append("AND (${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'image/%' OR ")
            append("${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'video/%')")
            if (excludeHidden) {
                append(" AND ${MediaStore.Files.FileColumns.WIDTH} > 0")
                append(" AND ${MediaStore.Files.FileColumns.HEIGHT} > 0")
                append(" AND ${MediaStore.Files.FileColumns.SIZE} > 5000")
            }
        }
        val selectionArgs = arrayOf(folderId)
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)
            val folderIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri: Uri = ContentUris.withAppendedId(
                    MediaStore.Files.getContentUri("external"),
                    id
                )
                mediaItems.add(
                    MediaData(
                        id = id,
                        uri = uri,
                        name = cursor.getString(nameColumn),
                        path = cursor.getString(pathColumn),
                        folderId = cursor.getString(folderIdColumn),
                        size = cursor.getLong(sizeColumn),
                        width = cursor.getInt(widthColumn),
                        height = cursor.getInt(heightColumn),
                        mimeType = cursor.getString(mimeTypeColumn),
                        dateAdded = cursor.getLong(dateAddedColumn),
                        duration = cursor.getLong(durationColumn)
                    )
                )
            }
        }
        mediaItems
    }

    suspend fun deleteMedia(mediaData: MediaData): Boolean = withContext(Dispatchers.IO) {
        try {
            val rowsDeleted = context.contentResolver.delete(mediaData.uri, null, null)
            rowsDeleted > 0
        } catch (e: Exception) {
            false
        }
    }
}
