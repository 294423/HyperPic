package app.hyperpic

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import app.hyperpic.data.ImageData
import app.hyperpic.loader.Loader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderContent(
    folderId: String,
    folderName: String,
    onImageClick: (Uri, String, String) -> Unit,
    onBack: () -> Unit
) {
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
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
        ) {
            items(images) { image ->
                if (image.thumbnail != null) {
                    Image(
                        bitmap = image.thumbnail.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable { onImageClick(image.uri, image.name, image.path) },
                        contentScale = ContentScale.Crop
                    )
                } else {
                    AsyncImage(
                        model = image.uri,
                        contentDescription = null,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable { onImageClick(image.uri, image.name, image.path) },
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}
