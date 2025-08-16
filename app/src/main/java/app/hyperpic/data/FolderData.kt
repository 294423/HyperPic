package app.hyperpic.data

import android.net.Uri

/**
 * A data class to hold information about a photo folder.
 * The names have been simplified from the original.
 *
 * @property id The unique ID of the folder (MediaStore.Images.Media.BUCKET_ID).
 * @property name The display name of the folder.
 * @property count The number of images contained within this folder.
 * @property path The absolute path of the folder on the device.
 * @property thumbnailUri The content Uri of the most recent image, to be used as a thumbnail.
 */
data class FolderData(
    val id: String,
    val name: String,
    val count: Int,
    val path: String,
    val thumbnailUri: Uri
)
