package app.hyperpic

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale

import app.hyperpic.data.FolderData
import app.hyperpic.loader.Loader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Folder(onFolderClick: (String, String) -> Unit) {
    val context: Context = LocalContext.current
    val loader = remember { Loader(context) }
    var folders by remember { mutableStateOf<List<FolderData>>(emptyList()) }

    LaunchedEffect(key1 = Unit) {
        folders = loader.loadFolders()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Folders") }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(folders) { folder ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                        .clickable { onFolderClick(folder.id, folder.name) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = folder.thumbnailUri,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        contentScale = ContentScale.Crop
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${folder.count}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}