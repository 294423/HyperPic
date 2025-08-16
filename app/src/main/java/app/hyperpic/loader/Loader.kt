package app.hyperpic.loader

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import app.hyperpic.data.FolderData
import app.hyperpic.data.ImageData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.FileOutputStream
import android.app.RecoverableSecurityException
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import android.content.IntentSender

class Loader(private val context: Context) {

    suspend fun loadFolders(): List<FolderData> = withContext(Dispatchers.IO) {
        val folders = mutableListOf<FolderData>()
        val folderBuckets = mutableSetOf<String>()

        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media._ID,
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val folderIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val folderNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val folderPathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val thumbnailIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)

            while (cursor.moveToNext()) {
                val id = cursor.getString(folderIdColumn)
                val name = cursor.getString(folderNameColumn) ?: "Unknown Folder"
                val path = cursor.getString(folderPathColumn)
                val thumbnailId = cursor.getLong(thumbnailIdColumn)

                if (folderBuckets.add(id)) {
                    val thumbnailUri: Uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        thumbnailId
                    )
                    val count = getFolderCount(id)
                    val parentPath = File(path).parent ?: ""

                    folders.add(FolderData(id, name, count, parentPath, thumbnailUri))
                }
            }
        }
        folders
    }

    suspend fun loadImagesInFolder(folderId: String): List<ImageData> = withContext(Dispatchers.IO) {
        val images = mutableListOf<ImageData>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA
        )

        val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(folderId)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val path = cursor.getString(pathColumn)
                val contentUri: Uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                val cacheUri = getCachedThumbnailUri(contentUri)
                images.add(ImageData(contentUri, name, path, cacheUri))
            }
        }
        images
    }

    suspend fun deleteImageByPath(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            } else {
                return@withContext false
            }

            val uri = Uri.fromFile(file)
            val cacheFile = File(context.cacheDir, "${uri.toString().hashCode()}.jpg")
            if (cacheFile.exists()) {
                cacheFile.delete()
            }

            true
        } catch (e: Exception) {
            return@withContext false
        }
    }

    private fun getCachedThumbnailUri(originalUri: Uri): Uri? {
        val fileName = "${originalUri.toString().hashCode()}.jpg"
        val cacheFile = File(context.cacheDir, fileName)

        if (cacheFile.exists()) {
            return Uri.fromFile(cacheFile)
        }
        val thumbnailBitmap = createThumbnail(originalUri, 150, 150)

        if (thumbnailBitmap == null) {
            return null
        }

        saveBitmapToFile(thumbnailBitmap, cacheFile)

        return if (cacheFile.exists()) {
            Uri.fromFile(cacheFile)
        } else {
            null
        }
    }

    private fun createThumbnail(originalUri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            context.contentResolver.openInputStream(originalUri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)

                options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)

                options.inJustDecodeBounds = false
                context.contentResolver.openInputStream(originalUri)?.use { finalInputStream ->
                    BitmapFactory.decodeStream(finalInputStream, null, options)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun saveBitmapToFile(bitmap: Bitmap, file: File) {
        try {
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                outputStream.flush()
            }
        } catch (e: Exception) {
            
        }
    }

    private fun getFolderCount(bucketId: String): Int {
        val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(bucketId)
        var count = 0

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            count = cursor.count
        }
        return count
    }
}
