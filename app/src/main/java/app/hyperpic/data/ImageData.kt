package app.hyperpic.data

import android.net.Uri
import android.graphics.Bitmap

/**
 * A data class to hold information about a single image.
 *
 * @property id The MediaStore ID for the image (from MediaStore.Images.Media._ID).
 * @property uri The content Uri for the image, which is used to display it.
 * @property name The display name of the image file.
 * @property path The absolute path of the image file on the device.
 * @property thumbnail Optional cached thumbnail bitmap.
 */
data class ImageData(
    val id: Long,
    val uri: Uri,
    val name: String,
    val path: String,
    val thumbnail: Bitmap? = null
)
