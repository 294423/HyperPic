package app.hyperpic

import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.net.URLEncoder
import app.hyperpic.loader.Loader
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import android.util.Log
import app.hyperpic.utils.RequestStoragePermissions
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.runtime.LaunchedEffect
import app.hyperpic.data.MediaData
import coil.ImageLoader
import kotlinx.coroutines.flow.first
import android.content.Intent
import android.net.Uri
import app.hyperpic.settings.AppSettings
import app.hyperpic.settings.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HyperPic(imageLoader: ImageLoader) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    val loader = remember { Loader(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val appSettings = remember { AppSettings(context) }

    var hasPermissions by remember { mutableStateOf(false) }

    if (!hasPermissions) {
        RequestStoragePermissions(
            onGranted = { hasPermissions = true },
            onDenied = {
                Log.e("HyperPic", "Storage permissions denied")
            }
        )
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = "folder",
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
            ) {
                composable(route = "folder") {
                    val hideZeroDimensionMedia = appSettings.hideZeroDimensionMedia
                    Folder(
                        onFolderClick = { folderId, folderName ->
                            val encodedFolderName = URLEncoder.encode(folderName, "UTF-8")
                            navController.navigate("folder_content/$folderId/$encodedFolderName")
                        },
                        onNavigateToSettings = {
                            navController.navigate("settings")
                        },
                        imageLoader = imageLoader,
                        hideZeroDimensionMedia = hideZeroDimensionMedia
                    )
                }
                composable(
                    route = "folder_content/{folderId}/{folderName}",
                    arguments = listOf(
                        navArgument("folderId") { type = NavType.StringType },
                        navArgument("folderName") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val folderId = backStackEntry.arguments!!.getString("folderId")!!
                    val folderName = backStackEntry.arguments!!.getString("folderName")!!
                    val hideZeroDimensionMedia = appSettings.hideZeroDimensionMedia
                    FolderContent(
                        folderId = folderId,
                        folderName = folderName,
                        onMediaClick = { mediaItem ->
                            navController.navigate("media_view/${URLEncoder.encode(mediaItem.folderId, "UTF-8")}/${mediaItem.id}")
                        },
                        onBack = { navController.popBackStack() },
                        imageLoader = imageLoader,
                        hideZeroDimensionMedia = hideZeroDimensionMedia
                    )
                }
                composable(
                    route = "media_view/{folderId}/{mediaId}",
                    arguments = listOf(
                        navArgument("folderId") { type = NavType.StringType },
                        navArgument("mediaId") { type = NavType.LongType }
                    )
                ) { backStackEntry ->
                    val folderId = backStackEntry.arguments!!.getString("folderId")!!
                    val mediaId = backStackEntry.arguments!!.getLong("mediaId")
                    val hideZeroDimensionMedia = appSettings.hideZeroDimensionMedia

                    var allMedia by remember { mutableStateOf<List<MediaData>?>(null) }
                    var initialIndex by remember { mutableStateOf(-1) }

                    LaunchedEffect(folderId) {
                        allMedia = loader.getMediaForFolder(folderId, hideZeroDimensionMedia)
                        initialIndex = allMedia?.indexOfFirst { it.id == mediaId } ?: -1
                    }
                    
                    if (allMedia != null && initialIndex != -1) {
                        MediaView(
                            media = allMedia!!,
                            startIndex = initialIndex,
                            onBack = { navController.popBackStack() },
                            onDelete = { mediaItem ->
                                coroutineScope.launch {
                                    val mediaType = if (mediaItem.mimeType?.startsWith("image/") == true) {
                                        "Image"
                                    } else if (mediaItem.mimeType?.startsWith("video/") == true) {
                                        "Video"
                                    } else {
                                        "Media"
                                    }
                                    val deleted = loader.deleteMedia(mediaItem)
                                    if (deleted) {
                                        snackbarHostState.showSnackbar("$mediaType deleted.")
                                    } else {
                                        snackbarHostState.showSnackbar("Failed to delete $mediaType.")
                                    }
                                }
                            },
                            onEdit = { uri ->
                                val editIntent = Intent(Intent.ACTION_EDIT).apply {
                                    setDataAndType(uri, context.contentResolver.getType(uri))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                if (editIntent.resolveActivity(context.packageManager) != null) {
                                    context.startActivity(editIntent)
                                } else {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("No app found to edit this media.")
                                    }
                                }
                            },
                            onShare = { uri ->
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = context.contentResolver.getType(uri)
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Media"))
                            }
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                composable(route = "settings") {
                    Settings(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
