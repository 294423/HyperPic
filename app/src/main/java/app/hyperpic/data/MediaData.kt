package app.hyperpic.data

import android.net.Uri

data class MediaData(
    val id: Long,
    val uri: Uri,
    val name: String?,
    val path: String?,
    val folderId: String?,
    val size: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val mimeType: String? = null,
    val dateAdded: Long? = null,
    val duration: Long? = null,
)
