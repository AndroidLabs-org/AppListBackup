package org.androidlabs.applistbackup.settings

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import org.androidlabs.applistbackup.BackupService
import org.androidlabs.applistbackup.R
import org.androidlabs.applistbackup.data.BackupApp
import org.androidlabs.applistbackup.docs.DocsViewerActivity
import org.androidlabs.applistbackup.faq.InstructionsActivity
import org.androidlabs.applistbackup.settings.data.BackupDataActivity
import org.androidlabs.applistbackup.settings.excluded.ExcludedAppsActivity
import org.androidlabs.applistbackup.settings.format.BackupFormatActivity
import org.androidlabs.applistbackup.settings.sort.BackupSortActivity
import org.androidlabs.applistbackup.settings.tvpicker.TvFolderPickerActivity
import org.androidlabs.applistbackup.ui.CheckboxRow
import org.androidlabs.applistbackup.utils.Utils.isTV
import java.io.File
import kotlin.String

class SettingsFragment : Fragment() {
    private val viewModel: SettingsViewModel by viewModels()

    private lateinit var setFolderLauncher: ActivityResultLauncher<Intent>

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val selectedFolder = result.data?.data?.path?.let { File(it) }
            selectedFolder?.let {
                viewModel.saveBackupUri(selectedFolder.toUri())
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            onPermissionGranted?.invoke()
        }
    }

    private val allFilesPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            onPermissionGranted?.invoke()
        }
    }

    private var onPermissionGranted: (() -> Unit)? = null

    private var version: String = ""

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    @SuppressLint("WrongConstant")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val context = requireContext()

        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        version = "${packageInfo.versionName} (${packageInfo.longVersionCode})"

        setFolderLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
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
                    openDoc = ::openDoc,
                    onBackupDataSettings = ::onBackupDataSettings,
                    onBackupFormatSettings = ::onBackupFormatSettings,
                    onBackupSortSettings = ::onBackupSortSettings,
                    onBackupExcludedAppsSettings = ::onBackupExcludedAppsSettings
                )
            }
        }
    }

    private fun onBackupDataSettings() {
        val intent = Intent(requireContext(), BackupDataActivity::class.java)
        startActivity(intent)
    }

    private fun onBackupFormatSettings() {
        val intent = Intent(requireContext(), BackupFormatActivity::class.java)
        startActivity(intent)
    }

    private fun onBackupSortSettings() {
        val intent = Intent(requireContext(), BackupSortActivity::class.java)
        startActivity(intent)
    }

    private fun onBackupExcludedAppsSettings() {
        val intent = Intent(requireContext(), ExcludedAppsActivity::class.java)
        startActivity(intent)
    }

    private fun openDoc(nameId: Int, fileName: String) {
        val intent = Intent(requireContext(), DocsViewerActivity::class.java).apply {
            putExtra("name", getString(nameId))
            putExtra("filename", fileName)
        }
        startActivity(intent)
    }

    private fun checkStoragePermissions(onGranted: () -> Unit) {
        onPermissionGranted = onGranted

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                onGranted()
            } else {
                try {
                    val intent =
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            addCategory(Intent.CATEGORY_DEFAULT)
                            data =
                                "package:${requireContext().applicationContext.packageName}".toUri()
                        }
                    allFilesPermissionLauncher.launch(intent)
                } catch (e: Exception) {
                    requestBasicStoragePermissions()
                }
            }
        } else {
            requestBasicStoragePermissions()
        }
    }

    private fun requestBasicStoragePermissions() {
        val permissions = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        if (permissions.all { permission ->
                checkSelfPermission(
                    requireContext(),
                    permission
                ) == PackageManager.PERMISSION_GRANTED
            }) {
            onPermissionGranted?.invoke()
        } else {
            requestPermissionLauncher.launch(permissions)
        }
    }

    private fun onChangeDestination() {
        if (isTV(requireContext())) {
            checkStoragePermissions {
                folderPickerLauncher.launch(
                    Intent(requireContext(), TvFolderPickerActivity::class.java)
                )
            }
        } else {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            setFolderLauncher.launch(intent)
        }
    }

    private fun onFAQ() {
        val intent = Intent(requireContext(), InstructionsActivity::class.java)
        startActivity(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    viewModel: SettingsViewModel,
    version: String,
    onChangeDestination: () -> Unit,
    onFAQ: () -> Unit,
    openDoc: (nameId: Int, fileName: String) -> Unit,
    onBackupDataSettings: () -> Unit,
    onBackupFormatSettings: () -> Unit,
    onBackupSortSettings: () -> Unit,
    onBackupExcludedAppsSettings: () -> Unit
) {
    val backupUri = viewModel.backupUri.observeAsState()
    val backupFormat by viewModel.backupFormats.collectAsState()
    val backupLimit = viewModel.backupLimit.observeAsState(initial = -1)
    val isUnlimited = backupLimit.value == -1
    val backupApps by viewModel.backupApps.observeAsState(
        initial = setOf(
            BackupApp.USER,
            BackupApp.SYSTEM,
            BackupApp.DISABLED
        )
    )
    val includeSystemInfo by viewModel.includeSystemInfo.collectAsState()
    var backupLimitFloat by remember { mutableFloatStateOf(backupLimit.value.toFloat()) }

    val createLatestEnabled = viewModel.createLatestBackupEnabled.collectAsState()

    val (inputText, setInputText) = remember {
        mutableStateOf(
            backupLimitFloat.toInt().toString()
        )
    }

    LaunchedEffect(backupLimit) {
        backupLimitFloat = backupLimit.value.toFloat()
    }

    val localContext = LocalContext.current

    LaunchedEffect(key1 = true) {
        viewModel.refresh()
    }

    val scrollState = rememberScrollState()

    Column(modifier = Modifier.verticalScroll(scrollState)) {

        Text(
            text = stringResource(R.string.settings),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                SettingsRow(
                    title = stringResource(id = R.string.save_location),
                    subtitle = null,
                    onClick = onChangeDestination,
                    iconView = {
                        Image(
                            painter = painterResource(id = R.drawable.ic_folder_24),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                        )
                    },
                    rightView = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                    footerView = {
                        Text(
                            text = if (backupUri.value !== null) BackupService.getReadablePathFromUri(
                                localContext,
                                backupUri.value
                            ) else stringResource(R.string.none),
                            fontSize = 12.sp,
                        )
                    }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                SettingsRow(
                    title = stringResource(id = R.string.backup_format),
                    subtitle = null,
                    iconView = {
                        Image(
                            painter = painterResource(id = R.drawable.ic_file_24),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                        )
                    },
                    rightView = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                    footerView = {
                        Text(
                            text = backupFormat.joinToString(", "),
                            fontSize = 12.sp,
                        )
                    },
                    onClick = onBackupFormatSettings
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                SettingsRow(
                    title = stringResource(id = R.string.backup_data_settings),
                    subtitle = null,
                    iconView = {
                        Image(
                            painter = painterResource(id = R.drawable.ic_dataset_24),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                        )
                    },
                    rightView = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                    onClick = onBackupDataSettings
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                SettingsRow(
                    title = stringResource(id = R.string.backup_sort_settings),
                    subtitle = null,
                    iconView = {
                        Image(
                            painter = painterResource(id = R.drawable.ic_sort_24),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                        )
                    },
                    rightView = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                    onClick = onBackupSortSettings
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                CheckboxRow(
                    checked = includeSystemInfo,
                    onCheckedChange = { isChecked ->
                        viewModel.saveIncludeSystemInfo(isChecked)
                    },
                    label = stringResource(R.string.include_system_info)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.apps_to_backup),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))


        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 0.dp)) {

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.app_types),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                CheckboxRow(
                    checked = backupApps.contains(BackupApp.USER),
                    onCheckedChange = { isChecked ->
                        val newSet = backupApps.toMutableSet().apply {
                            if (isChecked) add(BackupApp.USER)
                            else remove(BackupApp.USER)
                        }
                        viewModel.saveBackupApps(newSet)
                    },
                    label = stringResource(R.string.user_apps)
                )

                CheckboxRow(
                    checked = backupApps.contains(BackupApp.SYSTEM),
                    onCheckedChange = { isChecked ->
                        val newSet = backupApps.toMutableSet().apply {
                            if (isChecked) add(BackupApp.SYSTEM)
                            else remove(BackupApp.SYSTEM)
                        }
                        viewModel.saveBackupApps(newSet)
                    },
                    label = stringResource(R.string.system_apps)
                )

                SettingsRow(
                    title = stringResource(id = R.string.excluded_apps),
                    subtitle = null,
                    iconView = {
                        Image(
                            imageVector = Icons.Default.Clear,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                        )
                    },
                    rightView = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                    onClick = onBackupExcludedAppsSettings
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.options),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                CheckboxRow(
                    checked = backupApps.contains(BackupApp.DISABLED),
                    onCheckedChange = { isChecked ->
                        val newSet = backupApps.toMutableSet().apply {
                            if (isChecked) add(BackupApp.DISABLED)
                            else remove(BackupApp.DISABLED)
                        }
                        viewModel.saveBackupApps(newSet)
                    },
                    label = stringResource(R.string.include_disabled_apps)
                )

                Text(
                    text = stringResource(R.string.disabled_descr),
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }


        CheckboxRow(
            checked = createLatestEnabled.value,
            onCheckedChange = { isChecked ->
                viewModel.saveCreateLatestBackupEnabled(isChecked)
            },
            label = stringResource(R.string.also_latest)
        )

        SettingsRow(
            title = stringResource(id = R.string.keep_backups),
            subtitle = null,
            iconView = {
                Image(
                    painter = painterResource(id = R.drawable.ic_history_24),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                )
            },
            rightView = {
                val unlimitedBackupsLabel = stringResource(R.string.unlimited_backups_toggle)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Follows the switch. Rendered unconditionally it kept saying
                    // "Unlimited" while a numeric limit was in force, directly
                    // contradicting the field below it.
                    Text(
                        if (isUnlimited) {
                            stringResource(R.string.unlimited)
                        } else {
                            backupLimit.value.toString()
                        }
                    )

                    Switch(
                        checked = isUnlimited,
                        onCheckedChange = {
                            val newValue = if (isUnlimited) 1 else -1
                            setInputText(newValue.toString())
                            viewModel.saveBackupLimit(newValue)
                        },
                        // This row uses mergeSemantics = false, unlike CheckboxRow, because
                        // it also shows a dynamic value (the count or "Unlimited") that needs
                        // to stay independently readable. Without its own name here TalkBack
                        // reached an unlabelled switch with no idea what it controlled.
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .semantics {
                                contentDescription = unlimitedBackupsLabel
                            }
                    )
                }
            },
            mergeSemantics = false
        )

        AnimatedVisibility(visible = !isUnlimited) {
            val focusManager = LocalFocusManager.current

            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.backup_limit_description_prefix),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { newValue ->
                            val newValueInt = newValue.toIntOrNull()
                            if (newValue.isEmpty() || (newValueInt != null && newValueInt > 0 && newValueInt <= 1000)) {
                                setInputText(newValue)

                                if (newValueInt != null) {
                                    val boundedValue = newValueInt.coerceIn(1, 1000)
                                    backupLimitFloat = boundedValue.toFloat()
                                    viewModel.saveBackupLimit(boundedValue)
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.width(72.dp),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                setInputText(backupLimitFloat.toInt().toString())
                                focusManager.clearFocus()
                            }
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            textAlign = TextAlign.Center
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = 0.3f
                            )
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = stringResource(R.string.backup_limit_description_suffix),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.backup_limit_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = stringResource(R.string.about),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {


                SettingsRow(
                    title = stringResource(id = R.string.faq),
                    subtitle = null,
                    iconView = {
                        Image(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
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
                            contentDescription = null,
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
                    title = stringResource(id = R.string.license),
                    subtitle = null,
                    iconView = {
                        Image(
                            painter = painterResource(id = R.drawable.ic_article_24),
                            contentDescription = null,
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
                        openDoc(R.string.license, "license")
                    }
                )

                SettingsRow(
                    title = stringResource(id = R.string.privacy),
                    subtitle = null,
                    iconView = {
                        Image(
                            painter = painterResource(id = R.drawable.ic_article_24),
                            contentDescription = null,
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

                // Its own row. Rendered in the Privacy Policy row's rightView it
                // read as the version *of the policy*.
                SettingsRow(
                    title = stringResource(id = R.string.version_title),
                    subtitle = null,
                    iconView = {
                        Image(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                        )
                    },
                    rightView = {
                        Text(version)
                    }
                )

            }
        }

        Spacer(modifier = Modifier.height(18.dp))
    }
}