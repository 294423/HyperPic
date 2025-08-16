package app.hyperpic.loader

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
import app.hyperpic.data.FolderData
import app.hyperpic.data.ImageData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import java.io.File
import java.io.FileOutputStream
import java.io.BufferedOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles all image and folder loading operations from the device's MediaStore.
 * Implements a memory and disk cache for efficient thumbnail loading.
 */
class Loader(private val context: Context) {

    // In-memory cache for bitmaps, using LruCache to manage memory efficiently.
    private val memoryCache: LruCache<String, Bitmap> = object :
        LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    // A tracker to prevent concurrent loading of the same thumbnail.
    private val loadingTracker = ConcurrentHashMap<String, Boolean>()
    
    // Limits the number of concurrent thumbnail decodes to prevent performance issues.
    private val thumbnailSemaphore = Semaphore(4)

    // Disk cache directory for storing compressed thumbnail files.
    private val diskCacheDir = File(context.cacheDir, "thumbnails").apply {
        if (!exists()) mkdirs()
    }
    
    // The maximum size for the disk cache, set at 100MB.
    private val maxDiskCacheSize = 100L * 1024L * 1024L
    
    // Cache for folder item counts to avoid re-querying MediaStore.
    private val folderCountCache = mutableMapOf<String, Int>()

    /**
     * Loads a list of all image folders from the device's MediaStore.
     * Uses a single query to get the latest image from each folder.
     */
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

            // First pass to get all folder IDs for a subsequent count query.
            val folderIds = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                folderIds.add(cursor.getString(folderIdColumn))
            }
            
            calculateFolderCounts(folderIds)
            cursor.moveToPosition(-1)
            
            // Second pass to build the FolderData objects.
            while (cursor.moveToNext()) {
                val id = cursor.getString(folderIdColumn)
                if (folderBuckets.add(id)) {
                    val name = cursor.getString(folderNameColumn) ?: "Unknown Folder"
                    val path = cursor.getString(folderPathColumn)
                    val thumbnailId = cursor.getLong(thumbnailIdColumn)
                    val thumbnailUri: Uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        thumbnailId
                    )
                    val count = folderCountCache[id] ?: 0
                    val parentPath = File(path).parent ?: ""

                    folders.add(FolderData(id, name, count, parentPath, thumbnailUri))
                }
            }
        }
        folders
    }

    /**
     * Executes a separate query to get an accurate count of items in each folder.
     */
    private suspend fun calculateFolderCounts(folderIds: Set<String>) = withContext(Dispatchers.IO) {
        val selection = "${MediaStore.Images.Media.BUCKET_ID} IN (${folderIds.joinToString(",") { "?" }})"
        val selectionArgs = folderIds.toTypedArray()
        
        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media._ID
        )
        
        val countMap = mutableMapOf<String, Int>()
        
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            
            while (cursor.moveToNext()) {
                val bucketId = cursor.getString(bucketIdColumn)
                countMap[bucketId] = (countMap[bucketId] ?: 0) + 1
            }
        }
        
        folderCountCache.putAll(countMap)
    }

    /**
     * Loads all images within a specific folder, sorted by date added.
     * Filters out files smaller than 1KB.
     */
    suspend fun loadImagesInFolder(folderId: String): List<ImageData> = withContext(Dispatchers.IO) {
        val images = mutableListOf<ImageData>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.SIZE
        )

        val selection = "${MediaStore.Images.Media.BUCKET_ID} = ? AND ${MediaStore.Images.Media.SIZE} > ?"
        val selectionArgs = arrayOf(folderId, "1024")
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

                if (File(path).exists()) {
                    images.add(ImageData(id, contentUri, name, path, null))
                }
            }
        }
        return@withContext images
    }

    /**
     * Retrieves a single ImageData object by its MediaStore ID.
     */
    suspend fun getImageById(imageId: Long): ImageData? = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA
        )
        
        val selection = "${MediaStore.Images.Media._ID} = ?"
        val selectionArgs = arrayOf(imageId.toString())
        
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                
                return@withContext ImageData(id, contentUri, name, path, null)
            }
        }
        return@withContext null
    }

    /**
     * Asynchronously loads a batch of thumbnails, chunking the list to optimize for parallel execution.
     */
    suspend fun loadThumbnailBatch(uris: List<Uri>, reqWidth: Int, reqHeight: Int): Map<Uri, Bitmap?> =
        withContext(Dispatchers.IO) {
            uris.chunked(8).map { chunk ->
                async {
                    chunk.associateWith { uri ->
                        loadThumbnail(uri, reqWidth, reqHeight)
                    }
                }
            }.awaitAll().reduce { acc, map -> acc + map }
        }

    /**
     * Loads a single thumbnail, checking both memory and disk caches before decoding.
     * Uses a semaphore to limit concurrent disk and CPU-intensive operations.
     */
    suspend fun loadThumbnail(uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            val key = generateCacheKey(uri, reqWidth, reqHeight)
            if (loadingTracker[key] == true) {
                // Return cached bitmap if another coroutine is already loading it.
                return@withContext memoryCache.get(key)
            }
            memoryCache.get(key)?.let { return@withContext it }
            loadingTracker[key] = true
            
            try {
                thumbnailSemaphore.acquire()
                // Check cache again after acquiring lock to handle race conditions.
                memoryCache.get(key)?.let { return@withContext it }
                val cacheFile = File(diskCacheDir, "$key.jpg")
                if (cacheFile.exists()) {
                    val options = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    BitmapFactory.decodeFile(cacheFile.absolutePath, options)?.let { bmp ->
                        memoryCache.put(key, bmp)
                        cacheFile.setLastModified(System.currentTimeMillis())
                        return@withContext bmp
                    }
                }
                val bmp = createThumbnail(uri, reqWidth, reqHeight) ?: return@withContext null
                memoryCache.put(key, bmp)
                saveBitmapToDisk(bmp, cacheFile)
                return@withContext bmp
            } finally {
                thumbnailSemaphore.release()
                loadingTracker.remove(key)
                if (System.currentTimeMillis() % 10 == 0L) {
                    trimDiskCache()
                }
            }
        }

    /**
     * Generates a unique key for caching based on the URI and requested dimensions.
     */
    private fun generateCacheKey(uri: Uri, width: Int, height: Int): String {
        return "${uri.toString().hashCode()}_${width}x${height}"
    }

    /**
     * Decodes a bitmap from a URI, scaling it down to the requested size to save memory.
     */
    private fun createThumbnail(uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                options.inJustDecodeBounds = false
                context.contentResolver.openInputStream(uri)?.use { finalStream ->
                    BitmapFactory.decodeStream(finalStream, null, options)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Saves a compressed bitmap to the disk cache.
     */
    private fun saveBitmapToDisk(bitmap: Bitmap, file: File) {
        try {
            BufferedOutputStream(FileOutputStream(file), 8192).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
            }
            file.setLastModified(System.currentTimeMillis())
        } catch (e: Exception) {
            // Ignore for now, no need to clutter logs with cache failures.
        }
    }

    /**
     * Trims the disk cache by deleting the oldest files if the cache size exceeds the limit.
     */
    private fun trimDiskCache() {
        val files = diskCacheDir.listFiles() ?: return
        var totalSize = files.sumOf { it.length() }
        if (totalSize <= maxDiskCacheSize) return
        val sortedFiles = files.sortedBy { it.lastModified() }
        val targetSize = (maxDiskCacheSize * 0.8).toLong()
        for (file in sortedFiles) {
            if (totalSize <= targetSize) break
            totalSize -= file.length()
            file.delete()
        }
    }

    /**
     * Calculates the `inSampleSize` for decoding a bitmap to efficiently scale it.
     */
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

    /**
     * Deletes an image from the file system and its MediaStore entry by its ID.
     */
    suspend fun deleteImageById(imageId: Long): Boolean = withContext(Dispatchers.IO) {
        val imageData = getImageById(imageId)
        return@withContext if (imageData != null) {
            deleteImageByPath(imageData.path)
        } else {
            false
        }
    }

    /**
     * Deletes an image by its file path and removes its corresponding MediaStore entry.
     */
    suspend fun deleteImageByPath(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.exists()) {
                return@withContext false
            }
            if (file.delete()) {
                // Clean up MediaStore entry to avoid orphaned records.
                context.contentResolver.delete(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Images.Media.DATA} = ?",
                    arrayOf(path)
                )
                clearCachesByPath(path)
                return@withContext true
            } else {
                return@withContext false
            }
        } catch (e: Exception) {
            return@withContext false
        }
    }

    /**
     * Attempts to batch-delete multiple images by their IDs.
     */
    suspend fun deleteMultipleImagesById(imageIds: List<Long>): List<Long> = withContext(Dispatchers.IO) {
        val failedIds = mutableListOf<Long>()
        imageIds.forEach { imageId ->
            if (!deleteImageById(imageId)) {
                failedIds.add(imageId)
            }
        }
        return@withContext failedIds
    }

    /**
     * Clears the in-memory and disk caches for a specific image path.
     */
    private fun clearCachesByPath(path: String) {
        try {
            val uri = Uri.fromFile(File(path))
            val keyPrefix = uri.toString().hashCode().toString()
            
            // Delete corresponding files from disk cache.
            diskCacheDir.listFiles { _, name ->
                name.startsWith(keyPrefix)
            }?.forEach { file ->
                file.delete()
            }
            
            // No direct way to clear LruCache entries by prefix, so the keys will expire naturally.
        } catch (e: Exception) {
            // Ignore
        }
    }

    /**
     * Checks if the app has the necessary permission to delete files.
     */
    fun canDeleteFiles(): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
