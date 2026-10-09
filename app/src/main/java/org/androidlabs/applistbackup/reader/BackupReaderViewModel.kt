package org.androidlabs.applistbackup.reader

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.androidlabs.applistbackup.BackupService
import org.androidlabs.applistbackup.BackupTempStore
import org.androidlabs.applistbackup.data.BackupItem
import org.androidlabs.applistbackup.settings.Settings
import org.androidlabs.applistbackup.utils.Utils.hexToRgba
import org.androidlabs.applistbackup.utils.Utils.isTV
import org.androidlabs.applistbackup.utils.Utils.markdownHeading
import org.androidlabs.applistbackup.utils.Utils.renderCell

class BackupReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val _uri = MutableStateFlow<Uri?>(null)
    val uri = _uri.asStateFlow()

    private val _selectedTemp = MutableStateFlow<BackupItem.Temp?>(null)
    val selectedTemp = _selectedTemp.asStateFlow()

    fun selectTemp(temp: BackupItem.Temp?) {
        _uri.value = null
        _selectedTemp.value = temp
    }

    fun resetSelection() {
        _selectedTemp.value = null
        _uri.value = null
    }

    private val _isLoadingBackup = MutableStateFlow(false)
    val isLoadingBackup = _isLoadingBackup.asStateFlow()
    private val _installedPackages = MutableStateFlow<List<String>>(emptyList())
    val installedPackages: StateFlow<List<String>> = _installedPackages.asStateFlow()

    private var backupSettingsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null


    init {
        viewModelScope.launch(Dispatchers.IO) {
            initializeViewModel()
        }
    }

    // Startup calls loadLastBackup() from two independent places (BackupReaderFragment's
    // BackupService.isRunning collector, and DisplayContent's activeScreen==READER
    // effect), which normally race harmlessly. But the old code reset _convertState to
    // empty unconditionally, before checking whether a selection already existed - and
    // setUri()'s own uri assignment happens in a *separate* detached coroutine. That left
    // a window where a second concurrent call could see _uri still null, wipe the
    // just-populated convertState a second time, and then never reload it, because uri
    // itself never actually changed (issue E3: most-recent backup opens blank on first
    // entering "View backups", recoverable only by picking a different backup and back).
    // The mutex serializes these calls and setUriLocked() is awaited directly - not
    // fire-and-forget - so a queued second call only proceeds once _uri.value is really
    // set, and its guard check then correctly short-circuits without touching convertState.
    private val loadLastBackupMutex = Mutex()

    fun loadLastBackup(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            loadLastBackupMutex.withLock {
                if (_selectedTemp.value != null || _uri.value != null) return@withLock
                _isLoadingBackup.value = true
                try {
                    val temp = BackupTempStore.data.value
                    val lastUri = BackupService.getLastCreatedFileUri(context)

                    if (_selectedTemp.value != null || _uri.value != null) return@withLock

                    _convertState.value = ConvertState()

                    when {
                        temp.isNotEmpty() -> {
                            val first = temp.entries.first()
                            selectTemp(BackupItem.Temp(first.key, first.value))
                        }

                        lastUri != null -> setUriLocked(context, lastUri)

                        else -> {
                            _uri.value = null
                            _selectedTemp.value = null
                        }
                    }

                } finally {
                    _isLoadingBackup.value = false
                }
            }
        }
    }

    private suspend fun initializeViewModel() {
            loadInstalledPackages()
            withContext(Dispatchers.Main) {
                backupSettingsListener = Settings.observeBackupUri(
                    context = getApplication(),
                    onChangeBackupUri = {
                        viewModelScope.launch(Dispatchers.IO) {
                            val lastUri = BackupService.getLastCreatedFileUri(getApplication())
                            _uri.value = lastUri
                        }
                    }
                )
            }
    }

    private suspend fun loadInstalledPackages() {
        withContext(Dispatchers.IO) {
            try {
                val packageManager = getApplication<Application>().packageManager
                val packages = packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
                val packageNames = packages.map { it.packageName }
                _installedPackages.value = packageNames
            } catch (e: Exception) {
                _installedPackages.value = emptyList()
            }
        }
    }

    fun setUri(context: Context, newUri: Uri?) {
        if (newUri==null) {
            _selectedTemp.value = null
            _uri.value = null
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            setUriLocked(context, newUri)
        }
    }

    /** Body of setUri(), factored out so loadLastBackup() can await the assignment
     * directly inside its mutex instead of firing a detached coroutine - see the
     * comment on loadLastBackupMutex for why that gap mattered. */
    private suspend fun setUriLocked(context: Context, newUri: Uri) {
        val finalUri = if (isTV(context)) {
            val isFileProvider = newUri.scheme == ContentResolver.SCHEME_CONTENT
            if (!isFileProvider) {
                try {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        newUri.toFile()
                    )
                } catch (e: Exception) {
                    newUri
                }
            } else {
                newUri
            }
        } else {
            newUri
        }

        _uri.value = finalUri
    }

    override fun onCleared() {
        backupSettingsListener?.let {
            Settings.unregisterListener(getApplication(), it)
        }
    }

    private val _convertState = MutableStateFlow(ConvertState())
    val convertState = _convertState.asStateFlow()

    fun loadMD(
        context: Context,
        uri: Uri? = null,
        content: String? = null,
        textColor: String,
        linkColor: String,
        codeBgColor: String,
        bgColor: String
    ) {
        launchLoad {
            _convertState.value = _convertState.value.copy(isLoading = true)
            val html = withContext(Dispatchers.IO) {

                val mdText = content ?: uri?.let {
                    context.contentResolver.openInputStream(it)
                        ?.bufferedReader()
                        ?.use { reader -> reader.readText() }
                } ?: return@withContext null

                val lines = mdText.lines()

                val body = buildMarkdownHtml(
                    lines,
                    textColor,
                    linkColor,
                )

                buildHtml(body, textColor, linkColor, codeBgColor, bgColor)
            }
            ensureActive()
            _convertState.value = _convertState.value.copy(
                isLoading = false,
                html = html
            )
        }
    }

    private fun buildMarkdownHtml(
        lines: List<String>,
        textColor: String,
        linkColor: String
    ): String {

        val html = StringBuilder()
        var inTable = false
        var rowIndex = 0

        for (line in lines) {

            val noBold = line.replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
            val isTable = noBold.contains("|")

            if (isTable) {

                val cols = noBold.split("|")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                val isSeparator = cols.all { it.all { c -> c == '-' } }
                if (isSeparator) continue

                if (!inTable) {
                    html.append("<div class='table-wrapper'><table><tbody>")
                    inTable = true
                    rowIndex = 0
                }

                val zebraBg =
                    if (rowIndex % 2 == 0)
                        hexToRgba(textColor, 0.08f)
                    else
                        "transparent"


                rowIndex++

                html.append(
                    """
                <tr 
                    style="background:$zebraBg"
                    onpointerdown="startPress(this)"
                    onpointerup="cancelPress()"
                    onpointercancel="cancelPress()"
                    onpointerleave="cancelPress()"
                >
                """.trimIndent()
                )

                cols.forEach { cell ->
                    html.append("<td>${renderCell(cell, linkColor)}</td>")
                }

                html.append("</tr>")

            } else {

                if (inTable) {
                    html.append("</tbody></table></div>")
                    inTable = false
                }

                val heading = markdownHeading(noBold)
                if (heading != null) {
                    val (level, text) = heading
                    html.append(
                        "<h$level style=\"color:$textColor;margin:12px 0 4px 0;\">" +
                            "${renderCell(text, linkColor)}</h$level>"
                    )
                    continue
                }

                html.append("""
                <p style="color:$textColor;margin:4px 0;">
                    ${renderCell(noBold, linkColor)}
                </p>
            """.trimIndent())
            }
        }
        if (inTable) {
            html.append("</tbody></table></div>")
        }
        return html.toString()
    }

    private fun buildHtml(
        body: String,
        textColor: String,
        linkColor: String,
        codeBg: String,
        bgColor: String
    ): String {

        return """
            <html>
            <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
            
            <script>
            
            let pressTimer = null;
            let lastSelectedRow = null;
            
            function startPress(row) {
                pressTimer = setTimeout(() => {
                    selectRow(row);
                }, 500);
            }
            
            function cancelPress() {
                clearTimeout(pressTimer);
            }
            
            function selectRow(row) {
            
                if (lastSelectedRow) {
                    lastSelectedRow.classList.remove("selected");
                }
            
                lastSelectedRow = row;
                row.classList.add("selected");
            
                let text = "";
                let links = [];
            
                for (let i = 0; i < row.children.length; i++) {
            
                    let cell = row.children[i];
            
                    text += cell.innerText + "\\n";
            
                    let a = cell.querySelectorAll("a");
            
                    for (let j = 0; j < a.length; j++) {
                        links.push(a[j].href);
                    }
                }
            
                let result =
                    text +
                    "\\n--- LINKS ---\\n" +
                    links.join("\\n");
            
                Android.copyRow(result);
            
                setTimeout(() => {
                    if (row === lastSelectedRow) {
                        row.classList.remove("selected");
                        lastSelectedRow = null;
                    }
                }, 200);
            }
            
            </script>
            
            <style>
            
            body {
                font-family: sans-serif;
                padding: 12px;
                background: $bgColor;
                color: $textColor;
            }
            
            * {
                -webkit-user-select: none;
                user-select: none;
                -webkit-tap-highlight-color: transparent;
            }
            
            .table-wrapper {
                overflow-x: auto;
            }
            
            table {
                border-collapse: collapse;
                width: max-content;
                table-layout: auto;
            }
            
            td {
                border: 1px solid ${textColor}22;
                padding: 8px 10px;
                white-space: nowrap;
            }
            
            tbody tr:hover {
                background: ${textColor}12;
            }
            
            tr.selected {
                background: ${textColor}22 !important;
            }
            
            a {
                color: $linkColor;
                text-decoration: none;
            }        
            </style>            
            </head>            
            <body>
            $body
            </body>
            </html>
            """.trimIndent()
    }


    fun loadCsv(
        context: Context,
        uri: Uri? = null,
        content: String? = null,
        textColor: String,
        linkColor: String,
        codeBgColor: String,
        bgColor: String
    ) {
        launchLoad {
            _convertState.value = _convertState.value.copy(isLoading = true)

            val html = withContext(Dispatchers.IO) {
                val csv = content ?: uri?.let {
                    context.contentResolver.openInputStream(it)
                        ?.bufferedReader()
                        ?.use { reader -> reader.readText() }
                } ?: return@withContext null

                val body = buildCsvHtml(
                    csv,
                    textColor,
                    linkColor,
                )

                buildHtml(body, textColor, linkColor, codeBgColor, bgColor)
            }
            ensureActive()
            _convertState.value = _convertState.value.copy(
                isLoading = false,
                html = html
            )
        }
    }

    private fun buildCsvHtml(
        csv: String,
        textColor: String,
        linkColor: String
    ): String {

        val html = StringBuilder()
        val lines = csv.lines()
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return ""
        }
        html.append("<div class='table-wrapper'><table><tbody>")
        var rowIndex = 0
        lines.forEach { line ->
            val cols = parseCsvLine(line)
            val zebraBg =
                if (rowIndex % 2 == 0)
                    hexToRgba(textColor, 0.08f)
                else
                    "transparent"
            rowIndex++
            html.append(
                """
            <tr
                style="background:$zebraBg"
                onpointerdown="startPress(this)"
                onpointerup="cancelPress()"
                onpointercancel="cancelPress()"
                onpointerleave="cancelPress()"
            >
            """.trimIndent()
            )

            cols.forEachIndexed { index, rawCell ->
                val cell = rawCell
                    .trim()
                    .removeSurrounding("\"")

                val rendered = when {

                    index == cols.lastIndex - 1 &&
                            cell.startsWith("http") -> {

                        """<a href="$cell"
                        style="color:$linkColor;text-decoration:none;">
                        Play
                    </a>""".trimIndent()
                    }

                    index == cols.lastIndex &&
                            cell.startsWith("http") -> {

                        """<a href="$cell"
                        style="color:$linkColor;text-decoration:none;">
                        F-Droid
                    </a>""".trimIndent()
                    }

                    else -> {
                        renderCell(cell, linkColor)
                    }
                }
                html.append("<td>$rendered</td>")
            }
            html.append("</tr>")
        }
        html.append("</tbody></table></div>")
        return html.toString()
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (char in line) {
            when {
                char == '"' -> {
                    inQuotes = !inQuotes
                }
                char == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> {
                    current.append(char)
                }
            }
        }
        result.add(current.toString())
        return result
    }

    fun loadHtml(
        context: Context,
        uri: Uri? = null,
        content: String? = null,
    ) {
        launchLoad {
            _convertState.value = _convertState.value.copy(isLoading = true)
            val html = content ?: withContext(Dispatchers.IO) {
                uri?.let {
                    context.contentResolver.openInputStream(it)
                        ?.bufferedReader()
                        ?.use { reader -> reader.readText() }
                }
            }
            ensureActive()
            _convertState.value = _convertState.value.copy(
                isLoading = false,
                html = html
            )
        }
    }

    fun clearHtml() {
        _convertState.value = ConvertState()
    }

    data class ConvertState(
        val isLoading: Boolean = false,
        val html: String? = null
    )

    private var loadJob: Job? = null

    private fun launchLoad(
        block: suspend CoroutineScope.() -> Unit
    ) {
        loadJob?.cancel()

        loadJob = viewModelScope.launch {
            block()
        }
    }
}