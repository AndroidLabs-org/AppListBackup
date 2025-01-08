package org.androidlabs.applistbackup.settings

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import org.androidlabs.applistbackup.BackupService
import org.androidlabs.applistbackup.R
import org.androidlabs.applistbackup.docs.DocsViewerActivity
import org.androidlabs.applistbackup.faq.InstructionsActivity

class SettingsFragment : Fragment() {
    private val viewModel: SettingsViewModel by viewModels()

    private lateinit var setFolderLauncher: ActivityResultLauncher<Intent>

    private var version: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val context = requireContext()

        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        version = "${packageInfo.versionName} (${packageInfo.longVersionCode})"

        setFolderLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    val takeFlags = (result.data?.flags ?: 0) and
                            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)

                    viewModel.saveBackupUri(uri)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                SettingsScreen(
                    viewModel = viewModel,
                    version = version,
                    onChangeDestination = ::onChangeDestination,
                    onFAQ = ::onFAQ,
                    openDoc = ::openDoc
                )
            }
        }
    }

    private fun openDoc(nameId: Int, fileName: String) {
        val intent = Intent(requireContext(), DocsViewerActivity::class.java).apply {
            putExtra("name", getString(nameId))
            putExtra("filename", fileName)
        }
        startActivity(intent)
    }

    private fun onChangeDestination() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        setFolderLauncher.launch(intent)
    }

    private fun onFAQ() {
        val intent = Intent(requireContext(), InstructionsActivity::class.java)
        startActivity(intent)
    }
}

@Composable
private fun SettingsScreen(
    viewModel: SettingsViewModel,
    version: String,
    onChangeDestination: () -> Unit,
    onFAQ: () -> Unit,
    openDoc: (nameId: Int, fileName: String) -> Unit
) {
    val backupUri = viewModel.backupUri.observeAsState()

    LaunchedEffect(key1 = true) {
        viewModel.refreshBackupUri()
    }

    val scrollState = rememberScrollState()

    Column(modifier = Modifier.verticalScroll(scrollState)) {
        SettingsRow(
            title = stringResource(id = R.string.destination),
            subtitle = null,
            iconView = {
                Image(
                    painter = painterResource(id = R.drawable.ic_folder_24),
                    contentDescription = stringResource(id = R.string.destination),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                )
            },
            rightView = {
                Button(onClick = onChangeDestination) {
                    Text(text = stringResource(if (backupUri.value == null) R.string.choose else R.string.change))
                }
            },
            footerView = {
                Text(
                    text = if (backupUri.value !== null) BackupService.getReadablePathFromUri(backupUri.value) else stringResource(R.string.none),
                    fontSize = 12.sp,
                )
            }
        )

        SettingsRow(
            title = stringResource(id = R.string.faq),
            subtitle = null,
            iconView = {
                Image(
                    painter = painterResource(id = R.drawable.ic_faq_24),
                    contentDescription = stringResource(id = R.string.faq),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                )
            },
            rightView = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                )
            },
            onClick = onFAQ
        )

        SettingsRow(
            title = stringResource(id = R.string.terms),
            subtitle = null,
            iconView = {
                Image(
                    painter = painterResource(id = R.drawable.ic_article_24),
                    contentDescription = stringResource(id = R.string.terms),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                )
            },
            rightView = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                )
            },
            onClick = {
                openDoc(R.string.terms, "terms")
            }
        )

        SettingsRow(
            title = stringResource(id = R.string.privacy),
            subtitle = null,
            iconView = {
                Image(
                    painter = painterResource(id = R.drawable.ic_article_24),
                    contentDescription = stringResource(id = R.string.privacy),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                )
            },
            rightView = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                )
            },
            onClick = {
                openDoc(R.string.privacy, "privacy")
            }
        )

        SettingsRow(
            title = stringResource(id = R.string.version_title),
            subtitle = null,
            iconView = {
                Image(
                    imageVector = Icons.Default.Info,
                    contentDescription = stringResource(id = R.string.version_title),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                )
            },
            rightView = { Text(text = version) }
        )
    }
}