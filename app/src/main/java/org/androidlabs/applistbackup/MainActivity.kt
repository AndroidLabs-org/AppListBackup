package org.androidlabs.applistbackup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat.getDrawable
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import org.androidlabs.applistbackup.BackupService.Companion.FILE_NAME_PREFIX
import org.androidlabs.applistbackup.backupnow.BackupFragment
import org.androidlabs.applistbackup.data.BackupFormat
import org.androidlabs.applistbackup.reader.BackupReaderFragment
import org.androidlabs.applistbackup.settings.SettingsFragment
import org.androidlabs.applistbackup.ui.theme.AppListBackupTheme
import java.io.File

class MainActivity : FragmentActivity() {
    private val viewModel: MainActivityViewModel by viewModels()

    private val pickHtmlFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                val contentResolver = contentResolver
                val fileName = contentResolver.query(it, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    cursor.moveToFirst()
                    cursor.getString(nameIndex)
                }

                val fileExtensions = BackupFormat.entries.map { format -> format.fileExtension() }
                if (fileName != null && fileName.startsWith(FILE_NAME_PREFIX) && fileExtensions.any { ext ->
                        fileName.endsWith(
                            ".$ext"
                        )
                    }) {
                    try {
                        val tempFile = File(cacheDir, fileName)
                        contentResolver.openInputStream(it)?.use { inputStream ->
                            tempFile.outputStream().use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        val tempFileUri =
                            FileProvider.getUriForFile(this, "$packageName.provider", tempFile)
                        viewModel.setUri(tempFileUri)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(
                            this,
                            getString(R.string.error_message, e.message),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.wrong_file),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appName =
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0))
                .toString()

        if (intent?.extras?.getBoolean("RUN_BACKUP") == true) {
            BackupService.run(this)
        }

        setContent {
            AppListBackupTheme {
                MainScreen(
                    title = appName,
                    viewModel = viewModel,
                    onBrowse = ::onBrowse
                )
            }
        }
    }

    private fun onBrowse() {
        pickHtmlFile.launch(arrayOf("text/html"))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val uriString = intent.getStringExtra("uri")
        uriString?.let {
            viewModel.setUri(Uri.parse(it))
            viewModel.navigateToBrowse()
        }
    }
}

private sealed class Screen(val route: String, val titleResId: Int) {
    class WithDrawableIcon(route: String, val iconResId: Int, titleResId: Int) :
        Screen(route, titleResId)

    class WithImageVector(route: String, val icon: ImageVector, titleResId: Int) :
        Screen(route, titleResId)

    companion object {
        val Backup = WithImageVector("backup", Icons.Default.Home, R.string.backup_now)
        val Browse = WithDrawableIcon("browse", R.drawable.ic_browse, R.string.view_backups)
        val Settings = WithImageVector("settings", Icons.Default.Settings, R.string.settings)
    }
}

@Composable
private fun BottomNavigationBar(navController: NavController) {
    val items = listOf(
        Screen.Backup,
        Screen.Browse,
        Screen.Settings
    )
    NavigationBar {
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
        items.forEach { screen ->
            NavigationBarItem(
                modifier = Modifier.navigationBarsPadding(),
                icon = {
                    when (screen) {
                        is Screen.WithDrawableIcon -> Icon(
                            painter = painterResource(id = screen.iconResId),
                            contentDescription = stringResource(screen.titleResId)
                        )

                        is Screen.WithImageVector -> Icon(
                            imageVector = screen.icon,
                            contentDescription = stringResource(screen.titleResId)
                        )
                    }
                },
                label = { Text(stringResource(screen.titleResId)) },
                selected = currentRoute == screen.route,
                onClick = {
                    navController.navigate(screen.route) {
                        // Prevent multiple copies of the same destination
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        // Avoid creating multiple instances of the same destination
                        launchSingleTop = true
                        // Restore state when re-selecting a previously selected item
                        restoreState = true
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    title: String,
    viewModel: MainActivityViewModel,
    onBrowse: () -> Unit,
) {
    val navController = rememberNavController()
    val shouldNavigate by viewModel.shouldNavigateToBrowse.collectAsState()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val titleForScreen = when (currentRoute) {
        Screen.Backup.route -> stringResource(R.string.backup)
        Screen.Browse.route -> stringResource(R.string.view_backups)
        Screen.Settings.route -> stringResource(R.string.settings)
        else -> "Unknown Screen"
    }

    val isBrowse = currentRoute == Screen.Browse.route

    LaunchedEffect(shouldNavigate) {
        if (shouldNavigate) {
            navController.navigate(Screen.Browse.route) {
                popUpTo(navController.graph.startDestinationId) {
                    inclusive = true
                }
                launchSingleTop = true
            }
            viewModel.onNavigationHandled()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(
                            painter = rememberDrawablePainter(
                                drawable = getDrawable(
                                    LocalContext.current,
                                    R.mipmap.ic_launcher
                                )
                            ),
                            contentDescription = "App Icon",
                            modifier = Modifier.size(32.dp)
                        )
                        Column {
                            Text(
                                title,
                                fontSize = 20.sp,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(titleForScreen, fontSize = 16.sp, lineHeight = 16.sp)
                        }
                    }
                },
                actions = {
                    if (isBrowse) {
                        IconButton(onClick = onBrowse) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_browse),
                                contentDescription = stringResource(R.string.browse),
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            BottomNavigationBar(navController)
        }
    ) { innerPadding ->
        NavHost(
            navController, startDestination = Screen.Backup.route, modifier = Modifier
                .padding(innerPadding)
        ) {
            composable(Screen.Backup.route) {
                FragmentScreen(BackupFragment())
            }
            composable(Screen.Browse.route) {
                FragmentScreen(BackupReaderFragment(viewModel))
            }
            composable(Screen.Settings.route) {
                FragmentScreen(SettingsFragment())
            }
        }
    }
}

@Composable
private fun FragmentScreen(fragment: Fragment) {
    val activity = LocalContext.current as FragmentActivity

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            val frameLayout = FrameLayout(context).apply {
                id = View.generateViewId()
            }

            activity.supportFragmentManager.commit {
                replace(frameLayout.id, fragment)
            }

            frameLayout
        }
    )
}