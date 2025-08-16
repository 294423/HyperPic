package app.hyperpic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import android.net.Uri

import app.hyperpic.data.ImageData
import app.hyperpic.loader.Loader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderContent(folderId: String, folderName: String, onImageClick: (Uri, String, String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val loader = remember { Loader(context) }
    var images by remember { mutableStateOf<List<ImageData>>(emptyList()) }

    LaunchedEffect(key1 = folderId) {
        images = loader.loadImagesInFolder(folderId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(folderName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(images) { image ->
                AsyncImage(
                    model = image.cacheUri ?: image.uri,
                    contentDescription = null,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clickable{ onImageClick(image.uri, image.name, image.path) },
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}