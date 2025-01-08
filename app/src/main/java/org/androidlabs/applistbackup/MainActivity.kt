package org.androidlabs.applistbackup

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import org.androidlabs.applistbackup.settings.SettingsFragment
import org.androidlabs.applistbackup.ui.theme.AppListBackupTheme

class MainActivity : FragmentActivity() {
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
                    appName
                )
            }
        }
    }
}

sealed class Screen(val route: String, val icon: ImageVector, val titleResId: Int) {
    data object Backup : Screen("backup", Icons.Default.Home, R.string.backup)
    data object Settings : Screen("settings", Icons.Default.Settings, R.string.settings)
}

@Composable
fun BottomNavigationBar(navController: NavController) {
    val items = listOf(
        Screen.Backup,
        Screen.Settings
    )
    NavigationBar {
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
        items.forEach { screen ->
            NavigationBarItem(
                modifier = Modifier.navigationBarsPadding(),
                icon = { Icon(screen.icon, contentDescription = stringResource(screen.titleResId)) },
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
fun MainScreen(
    title: String
) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val titleForScreen = when (currentRoute) {
        Screen.Backup.route -> stringResource(R.string.backup)
        Screen.Settings.route -> stringResource(R.string.settings)
        else -> "Unknown Screen"
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
                                drawable = LocalContext.current.getDrawable(
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
            composable(Screen.Settings.route) {
                FragmentScreen(SettingsFragment())
            }
        }
    }
}

@Composable
fun FragmentScreen(fragment: Fragment) {
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