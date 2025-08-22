package app.hyperpic

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.hyperpic.data.MediaData
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import androidx.compose.runtime.key

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MediaView(
    media: List<MediaData>,
    startIndex: Int,
    onBack: () -> Unit,
    onDelete: (MediaData) -> Unit,
    onEdit: (Uri) -> Unit,
    onShare: (Uri) -> Unit
) {
    var openDeleteDialog by remember { mutableStateOf(false) }
    var openInfoDialog by remember { mutableStateOf(false) }
    var toolbarVisible by remember { mutableStateOf(true) }
    val view = LocalView.current
    val context = LocalContext.current
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { media.size })
    val currentMedia = media[pagerState.currentPage]

    fun setSystemBarsVisible(visible: Boolean) {
        val window = (view.context as? android.app.Activity)?.window ?: return
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (visible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(toolbarVisible) {
        setSystemBarsVisible(toolbarVisible)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(state = pagerState) { page ->
            val mediaItem = media[page]
            if (mediaItem.mimeType?.startsWith("image/") == true) {
                AsyncImage(
                    model = mediaItem.uri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            toolbarVisible = !toolbarVisible
                        },
                    contentScale = ContentScale.Fit
                )
            } else if (mediaItem.mimeType?.startsWith("video/") == true) {
                key(mediaItem.uri) {
                    VideoPlayer(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { toolbarVisible = !toolbarVisible },
                        videoUri = mediaItem.uri.toString(),
                        toolbarVisible = toolbarVisible
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = toolbarVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = currentMedia.name.orEmpty(),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(currentMedia.uri) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { openInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Info")
                    }
                    IconButton(onClick = { openDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                    IconButton(onClick = { onShare(currentMedia.uri) }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                }
            )
        }
    }

    if (openDeleteDialog) {
        val mediaType = if (currentMedia.mimeType?.startsWith("image/") == true) {
            "Image"
        } else if (currentMedia.mimeType?.startsWith("video/") == true) {
            "Video"
        } else {
            "Media"
        }
        AlertDialog(
            onDismissRequest = { openDeleteDialog = false },
            title = { Text("Delete $mediaType?") },
            text = { Text("This media file will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(currentMedia)
                    onBack()
                    openDeleteDialog = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { openDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (openInfoDialog) {
        AlertDialog(
            onDismissRequest = { openInfoDialog = false },
            title = { Text(text = "Media Info") },
            text = {
                Column {
                    val formattedSize = currentMedia.size?.let { sizeBytes ->
                        val sizeKB = sizeBytes / 1024.0
                        if (sizeKB < 1000) {
                            "${sizeKB.roundToInt()} KB"
                        } else {
                            val sizeMB = sizeKB / 1024.0
                            "%.2f MB".format(sizeMB)
                        }
                    }

                    val mediaDetails = mutableListOf(
                        "Name" to currentMedia.name,
                        "Path" to currentMedia.path,
                        "Size" to formattedSize,
                        "Dimensions" to currentMedia.width?.let { "${it}x${currentMedia.height}" },
                        "Type" to currentMedia.mimeType,
                    )

                    currentMedia.dateAdded?.let {
                        val date = Date(it * 1000L)
                        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        val formattedDate = formatter.format(date)
                        mediaDetails.add("Date Added" to formattedDate)
                    }

                    if (currentMedia.mimeType?.startsWith("video/") == true) {
                        mediaDetails.add(
                            "Duration" to currentMedia.duration?.let {
                                val minutes = TimeUnit.MILLISECONDS.toMinutes(it)
                                val seconds = TimeUnit.MILLISECONDS.toSeconds(it) % 60
                                String.format("%02d:%02d", minutes, seconds)
                            }
                        )
                    }

                    mediaDetails.forEach { (label, value) ->
                        if (value != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "$label: "
                                )
                                Text(
                                    text = value,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        val clipData = ClipData.newPlainText(label, value)
                                        clipboardManager.setPrimaryClip(clipData)
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { openInfoDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}
