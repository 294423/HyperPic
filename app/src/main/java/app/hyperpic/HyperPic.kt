package app.hyperpic

import android.net.Uri
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
import app.hyperpic.utils.RequestStoragePermissions
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment

import androidx.compose.runtime.LaunchedEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HyperPic() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    val loader = remember { Loader(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    
    var hasPermissions by remember { mutableStateOf(false) }

    if (!hasPermissions) {
        RequestStoragePermissions(
            onGranted = { hasPermissions = true },
            onDenied = {}
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
                    Folder(
                        onFolderClick = { folderId, folderName ->
                            val encodedFolderName = URLEncoder.encode(folderName, "UTF-8")
                            navController.navigate("folder_content/$folderId/$encodedFolderName")
                        }
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
                    FolderContent(
                        folderId = folderId,
                        folderName = folderName,
                        onImageClick = { imageUri, imageName, imagePath ->
                            val imageId = imageUri.lastPathSegment?.toLongOrNull() ?: 0L
                            navController.navigate("image_view/$imageId/${URLEncoder.encode(imageName, "UTF-8")}")
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = "image_view/{imageId}/{imageName}",
                    arguments = listOf(
                        navArgument("imageId") { type = NavType.LongType },
                        navArgument("imageName") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val imageId = backStackEntry.arguments!!.getLong("imageId")
                    val imageName = backStackEntry.arguments!!.getString("imageName")!!
                    
                    var imageData by remember { mutableStateOf<app.hyperpic.data.ImageData?>(null) }
                    
                    LaunchedEffect(imageId) {
                        imageData = loader.getImageById(imageId)
                    }
                    
                    imageData?.let { data ->
                        ImageView(
                            imageUri = data.uri,
                            imageName = data.name,
                            imagePath = data.path,
                            onBack = { navController.popBackStack() },
                            onDelete = {
                                coroutineScope.launch {
                                    val deleted = loader.deleteImageById(imageId)
                                    if (deleted) {
                                        navController.popBackStack()
                                        snackbarHostState.showSnackbar("Image deleted successfully.")
                                    } else {
                                        snackbarHostState.showSnackbar("Failed to delete image.")
                                    }
                                }
                            }
                        )
                    } ?: run {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
