package app.hyperpic.data

import android.net.Uri

data class FolderData(
    val id: String,
    val name: String,
    val count: Int,
    val path: String,
    val thumbnailUris: List<Uri>
)
