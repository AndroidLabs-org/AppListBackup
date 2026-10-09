package org.androidlabs.applistbackup

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.*
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
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
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

    private val backupContainerId = View.generateViewId()
    private val browseContainerId = View.generateViewId()
    private val settingsContainerId = View.generateViewId()

    private var backupFragment: BackupFragment? = null
    private var browseFragment: BackupReaderFragment? = null
    private var settingsFragment: SettingsFragment? = null

    private val pickFile =
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

        // The backup-complete notification carries the file it wrote. Its PendingIntent sets
        // CLEAR_TOP|SINGLE_TOP, which reaches onNewIntent only while this activity is already
        // running — and a notification is usually tapped later, once the process is gone. Then
        // the launch arrives here instead, so handling it only in onNewIntent silently dropped
        // the URI and left the user on the default tab rather than the backup they tapped.
        handleViewerIntent(intent)

        if (intent?.extras?.getBoolean("RUN_BACKUP") == true) {
            BackupService.run(this, temporary = false, onComplete = { uri, temp ->
                viewModel.navigateToBrowse()
            })
        }

        setContent {
            AppListBackupTheme {
                MainScreen(
                    title = appName,
                    viewModel = viewModel,
                    onBrowse = ::onBrowse,
                    onShare = ::onShare,
                    onDelete = ::onDelete,
                    backupContainerId = backupContainerId,
                    browseContainerId = browseContainerId,
                    settingsContainerId = settingsContainerId,
                    getBackupFragment = { getOrCreateBackupFragment() },
                    getBrowseFragment = { getOrCreateBrowseFragment() },
                    getSettingsFragment = { getOrCreateSettingsFragment() }
                )
            }
        }
    }

    private fun getOrCreateBackupFragment(): BackupFragment {
        if (backupFragment == null) {
            backupFragment = BackupFragment()
        }
        return backupFragment!!
    }

    private fun getOrCreateBrowseFragment(): BackupReaderFragment {
        if (browseFragment == null) {
            browseFragment = BackupReaderFragment()
        }
        return browseFragment!!
    }

    private fun getOrCreateSettingsFragment(): SettingsFragment {
        if (settingsFragment == null) {
            settingsFragment = SettingsFragment()
        }
        return settingsFragment!!
    }

    private fun onBrowse() {
        val types = BackupFormat.entries.map { it.mimeType() }.plus(
            listOf(
                "application/csv",
                "application/vnd.ms-excel",
                "text/comma-separated-values"
            )
        )
        pickFile.launch(types.toTypedArray())
    }

    private fun onShare() {
        try {
            val format = viewModel.selectedTempFormat.value

            val uri = if (format != null) {
                val content = BackupTempStore.get(format) ?: return

                val file = File(
                    cacheDir,
                    "shared_backup.${format.extension}"
                )

                file.writeText(content)

                FileProvider.getUriForFile(
                    this,
                    "$packageName.provider",
                    file
                )
            } else {
                viewModel.uri.value ?: return
            }

            val finalFormat = format ?: run {
                val extension = uri.path
                    ?.substringAfterLast('.', "")
                    ?.lowercase()

                extension?.let { BackupFormat.fromExtension(it) }
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = finalFormat?.mimeType() ?: "*/*"

                putExtra(Intent.EXTRA_STREAM, uri)

                clipData = ClipData.newRawUri(
                    getString(R.string.backup),
                    uri
                )

                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(
                Intent.createChooser(
                    shareIntent,
                    getString(R.string.share)
                )
            )

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun onDelete() {
        deleteCurrentFile()
        viewModel.refreshBackupFiles()
    }

    private fun deleteCurrentFile() {
        val uri = viewModel.uri.value ?: return
        try {
            val deleted = when (uri.scheme) {
                ContentResolver.SCHEME_CONTENT -> {
                    if (DocumentsContract.isDocumentUri(this, uri)) {
                        try {
                            DocumentsContract.deleteDocument(contentResolver, uri)
                            true
                        } catch (safException: Exception) {
                            contentResolver.delete(uri, null, null) > 0
                        }
                    } else {
                        contentResolver.delete(uri, null, null) > 0
                    }
                }

                ContentResolver.SCHEME_FILE -> {
                    val path = uri.path
                    if (path != null) {
                        File(path).delete()
                    } else {
                        false
                    }
                }

                else -> {
                    val path = uri.toString()
                    File(path).delete()
                }
            }

            if (deleted) {
                Toast.makeText(
                    this,
                    resources.getString(R.string.backup_deleted),
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.setUri(null)
            } else {
                Toast.makeText(
                    this,
                    resources.getString(R.string.unable_delete),
                    Toast.LENGTH_SHORT
                ).show()
            }

        } catch (e: Exception) {
            Toast.makeText(this, resources.getString(R.string.unable_delete), Toast.LENGTH_SHORT)
                .show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleViewerIntent(intent)
    }

    /**
     * Opens the viewer on the backup named by the intent, from either entry point.
     *
     * Called from onCreate for a cold start and from onNewIntent for a warm one, so the
     * notification behaves the same whether or not the app was still running.
     */
    private fun handleViewerIntent(intent: Intent?) {
        val uriString = intent?.getStringExtra("uri") ?: return
        viewModel.setUri(uriString.toUri())
        viewModel.navigateToBrowse()
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
    val items = remember {
        listOf(
            Screen.Backup,
            Screen.Browse,
            Screen.Settings
        )
    }

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    NavigationBar {
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
                    if (currentRoute != screen.route) {
                        navController.navigate(screen.route) {
                            // Pop up to the start destination of the graph to
                            // avoid building up a large stack of destinations
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            // Avoid multiple copies of the same destination when
                            // reselecting the same item
                            launchSingleTop = true
                            // Restore state when reselecting a previously selected item
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}


@Composable
fun DeleteConfirmationDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(text = stringResource(id = R.string.delete_backup))
        },
        text = {
            Text(text = stringResource(id = R.string.sure_to_delete_backup))
        },
        onDismissRequest = {
            onDismissRequest()
        },

        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onDismissRequest() }
                ) {
                    Text(text = stringResource(id = R.string.cancel))
                }

                Button(
                    onClick = { onConfirm() },
                ) {
                    Text(text = stringResource(id = R.string.delete))
                }
            }
        },
        dismissButton = null
    )
}

@SuppressLint("ContextCastToActivity")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("AssignedValueIsNeverRead")
private fun MainScreen(
    title: String,
    viewModel: MainActivityViewModel,
    onBrowse: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    backupContainerId: Int,
    browseContainerId: Int,
    settingsContainerId: Int,
    getBackupFragment: () -> BackupFragment,
    getBrowseFragment: () -> BackupReaderFragment,
    getSettingsFragment: () -> SettingsFragment
) {
    val navController = rememberNavController()
    val shouldNavigate by viewModel.shouldNavigateToBrowse.collectAsState()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    val currentUriState by viewModel.uri.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            onDismissRequest = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            }
        )
    }
    LaunchedEffect(currentRoute) {
        when (currentRoute) {
            Screen.Backup.route -> viewModel.setActiveScreen(MainActivityViewModel.ActiveScreen.BACKUP)
            Screen.Browse.route -> viewModel.setActiveScreen(MainActivityViewModel.ActiveScreen.READER)
            Screen.Settings.route -> viewModel.setActiveScreen(MainActivityViewModel.ActiveScreen.SETTINGS)
        }
    }

    val titleForScreen = when (currentRoute) {
        Screen.Backup.route -> stringResource(R.string.backup)
        Screen.Browse.route -> stringResource(R.string.view_backups)
        Screen.Settings.route -> stringResource(R.string.settings)
        else -> "Unknown Screen"
    }

    val tempState = BackupTempStore.data.collectAsState()
    val isBrowse = currentRoute == Screen.Browse.route

    LaunchedEffect(shouldNavigate) {
        if (shouldNavigate) {
            navController.navigate(Screen.Browse.route) {
                popUpTo(navController.graph.startDestinationId) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
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
                            // Decorative: the app name is right next to it in the same row,
                            // so a screen reader announcing "App Icon" here is pure redundancy.
                            contentDescription = null,
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
                        if (tempState.value.isEmpty())
                            IconButton(onClick = {
                                if (currentUriState != null) {
                                    showDeleteDialog = true
                                }
                            }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete)
                                )
                            }

                        IconButton(onClick = onShare) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.share)
                            )
                        }

                        if (tempState.value.isEmpty())
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
        val activity = LocalContext.current as FragmentActivity

        val containersInitialized = remember { mutableStateOf(false) }

        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            factory = { context ->
                FrameLayout(context).apply {
                    addView(FragmentContainerView(context).apply {
                        id = backupContainerId
                        visibility = View.VISIBLE
                    })

                    addView(FragmentContainerView(context).apply {
                        id = browseContainerId
                        visibility = View.GONE
                    })

                    addView(FragmentContainerView(context).apply {
                        id = settingsContainerId
                        visibility = View.GONE
                    })

                    // Initialize fragments only once
                    post {
                        if (!containersInitialized.value) {
                            activity.supportFragmentManager.commit {
                                add(backupContainerId, getBackupFragment())
                                add(browseContainerId, getBrowseFragment())
                                add(settingsContainerId, getSettingsFragment())
                                setReorderingAllowed(true)
                            }
                            containersInitialized.value = true
                        }
                    }
                }
            },
            update = { parentLayout ->
                val backupContainer = parentLayout.findViewById<View>(backupContainerId)
                val browseContainer = parentLayout.findViewById<View>(browseContainerId)
                val settingsContainer = parentLayout.findViewById<View>(settingsContainerId)

                when (currentRoute) {
                    Screen.Backup.route -> {
                        backupContainer.visibility = View.VISIBLE
                        browseContainer.visibility = View.GONE
                        settingsContainer.visibility = View.GONE
                    }

                    Screen.Browse.route -> {
                        backupContainer.visibility = View.GONE
                        browseContainer.visibility = View.VISIBLE
                        settingsContainer.visibility = View.GONE
                    }

                    Screen.Settings.route -> {
                        backupContainer.visibility = View.GONE
                        browseContainer.visibility = View.GONE
                        settingsContainer.visibility = View.VISIBLE
                    }
                }
            }
        )

        NavHost(
            navController = navController,
            startDestination = Screen.Backup.route,
            modifier = Modifier.fillMaxSize() // This is invisible but needed for navigation
        ) {
            composable(Screen.Backup.route) { }
            composable(Screen.Browse.route) { }
            composable(Screen.Settings.route) { }
        }
    }
}

