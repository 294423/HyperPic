package app.hyperpic

import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import app.hyperpic.utils.RequestManageExternalStoragePermission
import androidx.compose.material3.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HyperPic() {
    RequestManageExternalStoragePermission()

    val context = LocalContext.current
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    val loader = remember { Loader(context) }
    val snackbarHostState = remember { SnackbarHostState() }

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
                        val encodedImagePath = URLEncoder.encode(imagePath, "UTF-8")
                        navController.navigate("image_view/${Uri.encode(imageUri.toString())}/${URLEncoder.encode(imageName, "UTF-8")}/$encodedImagePath")
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "image_view/{imageUri}/{imageName}/{imagePath}",
                arguments = listOf(
                    navArgument("imageUri") { type = NavType.StringType},
                    navArgument("imageName") { type = NavType.StringType },
                    navArgument("imagePath") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val imageUri = Uri.parse(
                    Uri.decode(backStackEntry.arguments!!.getString("imageUri")!!)
                )
                val imageName = backStackEntry.arguments!!.getString("imageName")!!
                val imagePath = Uri.decode(backStackEntry.arguments!!.getString("imagePath")!!)
                ImageView(
                    imageUri = imageUri,
                    imageName = imageName,
                    imagePath = imagePath,
                    onBack = { navController.popBackStack() },
                    onDelete = {
                        coroutineScope.launch {
                            val deleted = loader.deleteImageByPath(imagePath)
                            if (deleted) {
                            	navController.popBackStack()
                                snackbarHostState.showSnackbar("Image deleted successfully.")
                            }
                        }
                    }
                )
            }
        }
    }
}