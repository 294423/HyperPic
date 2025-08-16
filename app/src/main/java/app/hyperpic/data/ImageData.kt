package app.hyperpic.data

import android.net.Uri

/**
 * A data class to hold information about a single image.
 *
 * @property uri The content Uri for the image, which is used for display.
 * @property name The display name of the image file.
 * @property path The absolute path of the image file.
 * @property cacheUri An optional Uri for a cached version of the image.
 */
data class ImageData(
    val uri: Uri,
    val name: String,
    val path: String,
    val cacheUri: Uri? = null
)
