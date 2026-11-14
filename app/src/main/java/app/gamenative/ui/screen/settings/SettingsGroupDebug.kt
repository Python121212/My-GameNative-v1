package app.gamenative.ui.screen.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import app.gamenative.CrashHandler
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import app.gamenative.R
import app.gamenative.service.SteamService
import app.gamenative.ui.component.dialog.CrashLogDialog
import app.gamenative.ui.theme.settingsTileColors
import app.gamenative.ui.theme.settingsTileColorsDebug
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.SettingsMenuLink
import com.alorma.compose.settings.ui.SettingsSwitch
import app.gamenative.PrefManager
import app.gamenative.ui.util.SnackbarManager
import app.gamenative.ui.theme.settingsTileColorsAlt
import com.winlator.PrefManager as WinlatorPrefManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import app.gamenative.utils.DiagnosticLog
import app.gamenative.utils.CompressionSelfTest
import app.gamenative.utils.MemoryStatus
import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import app.gamenative.ui.component.dialog.WineDebugChannelsDialog

@Suppress("UnnecessaryOptInAnnotation") // ExperimentalFoundationApi
@OptIn(ExperimentalCoilApi::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsGroupDebug() {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    if (!isPreview) {
        PrefManager.init(context)
        WinlatorPrefManager.init(context)
    }

    // Load Wine debug channels and prepare selection state
    var allWineChannels by remember { mutableStateOf<List<String>>(emptyList()) }
    var showChannelsDialog by remember { mutableStateOf(false) }
    var selectedWineChannels by remember { mutableStateOf(
        if (isPreview) emptyList() else PrefManager.wineDebugChannels.split(",")
    ) }
    LaunchedEffect(Unit) {
        // Read the list of channels from assets
        val json = context.assets.open("wine_debug_channels.json").bufferedReader().use { it.readText() }
        allWineChannels = Json.decodeFromString(json)
    }

    // Dialog for selecting channels
    WineDebugChannelsDialog(
        openDialog = showChannelsDialog,
        allChannels = allWineChannels,
        currentSelection = selectedWineChannels,
        onSave = { newSelection ->
            selectedWineChannels = newSelection
            if (!isPreview) {
                PrefManager.wineDebugChannels = newSelection.joinToString(",")
            }
            showChannelsDialog = false
        },
        onDismiss = { showChannelsDialog = false }
    )

    /* Crash Log stuff */
    var showLogcatDialog by rememberSaveable { mutableStateOf(false) }
    // states for debug toggles
    var enableWineDebugPref by rememberSaveable {
        mutableStateOf(if (isPreview) false else PrefManager.enableWineDebug)
    }
    var enableBox86Logs by rememberSaveable { mutableStateOf(
        if (isPreview) false else WinlatorPrefManager.getBoolean("enable_box86_64_logs", false)
    ) }
    var latestCrashFile: File? by rememberSaveable { mutableStateOf(null) }
    LaunchedEffect(Unit) {
        val crashDir = File(context.getExternalFilesDir(null), "crash_logs")
        latestCrashFile = crashDir.listFiles()
            ?.filter { it.name.startsWith("pluvia_crash_") }
            ?.maxByOrNull { it.lastModified() }
    }

    /* Save crash log */
    val saveResultContract = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { resultUri ->
        try {
            resultUri?.let { uri ->
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    latestCrashFile?.inputStream()?.use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
        } catch (e: Exception) {
            SnackbarManager.show("Failed to save logcat to destination")
        }
    }

    /* Save log cat */
    val saveLogCat = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { resultUri ->
        try {
            resultUri?.let {
                val logs = CrashHandler.getAppLogs(1000)
                context.contentResolver.openOutputStream(resultUri)?.use { outputStream ->
                    outputStream.write(logs.toByteArray())
                }
            }
        } catch (e: Exception) {
            SnackbarManager.show(context.getString(R.string.toast_failed_log_save))
        }
    }

    if (showLogcatDialog && latestCrashFile != null) {
        val crashText by produceState("Loading...", latestCrashFile) {
            value = withContext(Dispatchers.IO) { readTail(latestCrashFile) }
        }
        CrashLogDialog(
            visible = true,
            fileName = latestCrashFile?.name ?: "No Filename",
            fileText = crashText,
            onSave = { latestCrashFile?.let { file -> saveResultContract.launch(file.name) } },
            onDismissRequest = { showLogcatDialog = false },
        )
    }

    /* Wine Debug Log export setup */
    var showWineLogDialog by rememberSaveable { mutableStateOf(false) }
    var latestWineLogFile: File? by rememberSaveable { mutableStateOf(null) }
    LaunchedEffect(Unit) {
        val wineLogDir = File(context.getExternalFilesDir(null), "wine_logs")
        wineLogDir.mkdirs()
        val wineLogFile = File(wineLogDir, "wine_debug.log")
        latestWineLogFile = if (wineLogFile.exists()) wineLogFile else null
    }
    val saveWineLogContract = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { resultUri ->
        try {
            resultUri?.let { uri ->
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    latestWineLogFile?.inputStream()?.use { input -> input.copyTo(output) }
                }
            }
        } catch (e: Exception) {
            SnackbarManager.show("Failed to save Wine log to destination")
        }
    }

    if (showWineLogDialog && latestWineLogFile != null) {
        val wineText by produceState("Loading...", latestWineLogFile) {
            value = withContext(Dispatchers.IO) { readTail(latestWineLogFile) }
        }
        CrashLogDialog(
            visible = true,
            fileName = latestWineLogFile?.name ?: "wine_debug.log",
            fileText = wineText,
            onSave = { latestWineLogFile?.let { file -> saveWineLogContract.launch(file.name) } },
            onDismissRequest = { showWineLogDialog = false },
        )
    }

    val diagnosticScope = rememberCoroutineScope()
    var memoryResult by remember { mutableStateOf<String?>(null) }
    if (memoryResult != null) {
        AlertDialog(
            onDismissRequest = { memoryResult = null },
            title = { Text(stringResource(R.string.memory_probe_title)) },
            text = { Text(memoryResult.orEmpty()) },
            confirmButton = { TextButton(onClick = { memoryResult = null }) { Text(stringResource(R.string.compression_trial_close)) } },
        )
    }
    var compressionBusy by remember { mutableStateOf(false) }
    var compressionResult by remember { mutableStateOf<String?>(null) }
    if (compressionResult != null) {
        AlertDialog(
            onDismissRequest = { compressionResult = null },
            title = { Text(stringResource(R.string.compression_trial_title)) },
            text = { Text(compressionResult.orEmpty()) },
            confirmButton = { TextButton(onClick = { compressionResult = null }) { Text(stringResource(R.string.compression_trial_close)) } },
        )
    }
    var confirmDeleteDiagnostics by remember { mutableStateOf(false) }
    if (confirmDeleteDiagnostics) {
        AlertDialog(
            onDismissRequest = { confirmDeleteDiagnostics = false },
            title = { Text(stringResource(R.string.diagnostic_delete_title)) },
            text = { Text(stringResource(R.string.diagnostic_delete_confirm)) },
            confirmButton = { TextButton(onClick = {
                confirmDeleteDiagnostics = false
                diagnosticScope.launch {
                    val deleted = withContext(Dispatchers.IO) { runCatching { DiagnosticLog.deleteRecords(context) }.getOrDefault(false) }
                    SnackbarManager.show(context.getString(if (deleted) R.string.diagnostic_delete_ok else R.string.diagnostic_delete_failed))
                }
            }) { Text(stringResource(R.string.diagnostic_delete_action)) } },
            dismissButton = { TextButton(onClick = { confirmDeleteDiagnostics = false }) { Text(stringResource(R.string.diagnostic_cancel)) } },
        )
    }
    var diagnosticEnabled by remember { mutableStateOf(!isPreview && DiagnosticLog.enabled(context)) }
    val diagnosticExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) diagnosticScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { DiagnosticLog.exportLatest(context, it) } ?: false
                }.getOrDefault(false)
            }
            SnackbarManager.show(context.getString(if (result) R.string.diagnostic_export_ok else R.string.diagnostic_export_failed))
        }
    }

    SettingsGroup() {
        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(stringResource(R.string.memory_probe_title)) },
            subtitle = { Text(stringResource(R.string.memory_probe_subtitle)) },
            onClick = {
                if (!isPreview) diagnosticScope.launch {
                    memoryResult = withContext(Dispatchers.IO) { MemoryStatus.read(context) }
                }
            },
        )
        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(stringResource(R.string.compression_trial_title)) },
            subtitle = { Text(stringResource(if (compressionBusy) R.string.compression_trial_busy else R.string.compression_trial_subtitle)) },
            onClick = {
                if (!compressionBusy && !isPreview) {
                    compressionBusy = true
                    diagnosticScope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { CompressionSelfTest.run(context) }.getOrNull() }
                        compressionResult = result ?: context.getString(R.string.compression_trial_failed)
                        compressionBusy = false
                    }
                }
            },
        )
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            state = diagnosticEnabled,
            title = { Text(stringResource(R.string.diagnostic_logging_title)) },
            subtitle = { Text(stringResource(R.string.diagnostic_logging_subtitle)) },
            onCheckedChange = {
                diagnosticEnabled = it
                if (!isPreview) DiagnosticLog.setEnabled(context, it)
            },
        )
        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(stringResource(R.string.diagnostic_export_title)) },
            subtitle = { Text(stringResource(R.string.diagnostic_export_subtitle)) },
            onClick = { diagnosticExport.launch("gamenative-diagnostics.zip") },
        )
        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(stringResource(R.string.diagnostic_delete_title)) },
            subtitle = { Text(stringResource(R.string.diagnostic_delete_subtitle)) },
            onClick = { confirmDeleteDiagnostics = true },
        )
        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.settings_save_logcat_title)) },
            subtitle = { Text(text = stringResource(R.string.settings_save_logcat_subtitle)) },
            onClick = { saveLogCat.launch("app_logs_${CrashHandler.timestamp}.txt") },
        )
        // Link to open channel selector
        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.settings_debug_wine_channels_title)) },
            subtitle = {
                Text(
                    text = if (selectedWineChannels.isNotEmpty() && selectedWineChannels.any { it.isNotBlank() })
                        selectedWineChannels.filter { it.isNotBlank() }.joinToString(",")
                    else
                        stringResource(R.string.settings_debug_no_channels_selected)
                )
            },
            onClick = { showChannelsDialog = true },
        )
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            state = enableWineDebugPref,
            title = { Text(text = stringResource(R.string.settings_debug_wine_logs_title)) },
            subtitle = { Text(text = stringResource(R.string.settings_debug_wine_logs_subtitle)) },
            onCheckedChange = {
                enableWineDebugPref = it
                if (!isPreview) {
                    PrefManager.enableWineDebug = it
                }
            },
        )
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            state = enableBox86Logs,
            title = { Text(text = stringResource(R.string.settings_debug_box_logs_title)) },
            subtitle = { Text(text = stringResource(R.string.settings_debug_box_logs_subtitle)) },
            onCheckedChange = {
                enableBox86Logs = it
                if (!isPreview) {
                    WinlatorPrefManager.putBoolean("enable_box86_64_logs", it)
                }
            },
        )
        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.settings_debug_view_crash_title)) },
            subtitle = {
                Text(
                    text = if (latestCrashFile != null)
                        stringResource(R.string.settings_debug_view_crash_subtitle)
                    else
                        stringResource(R.string.settings_debug_no_crash_logs)
                )
            },
            enabled = latestCrashFile != null,
            onClick = { showLogcatDialog = true },
        )

        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.settings_debug_view_log_title)) },
            subtitle = {
                Text(
                    text = if (latestWineLogFile != null)
                        stringResource(R.string.settings_debug_view_log_subtitle)
                    else
                        stringResource(R.string.settings_debug_no_wine_logs)
                )
            },
            enabled = latestWineLogFile != null,
            onClick = { showWineLogDialog = true },
        )

        SettingsMenuLink(
            modifier = Modifier.combinedClickable(
                onLongClick = {
                    SteamService.logOut()
                    (context as ComponentActivity).finishAffinity()
                },
                onClick = {
                    SnackbarManager.show("Long click to activate")
                },
            ),
            colors = settingsTileColorsDebug(),
            title = { Text(text = stringResource(R.string.settings_debug_clear_prefs_title)) },
            subtitle = { Text(stringResource(R.string.settings_debug_clear_prefs_subtitle)) },
            onClick = {},
        )

        SettingsMenuLink(
            modifier = Modifier.combinedClickable(
                onLongClick = {
                    SteamService.stop()
                    SteamService.clearDatabase()
                    (context as ComponentActivity).finishAffinity()
                },
                onClick = {
                    SnackbarManager.show("Long click to activate")
                },
            ),
            colors = settingsTileColorsDebug(),
            title = { Text(text = stringResource(R.string.settings_debug_clear_db_title)) },
            subtitle = { Text(stringResource(R.string.settings_debug_clear_db_subtitle)) },
            onClick = {},
        )

        SettingsMenuLink(
            modifier = Modifier.combinedClickable(
                onLongClick = {
                    context.imageLoader.diskCache?.clear()
                    context.imageLoader.memoryCache?.clear()
                },
                onClick = {
                    SnackbarManager.show("Long click to activate")
                },
            ),
            colors = settingsTileColorsDebug(),
            title = { Text(text = stringResource(R.string.settings_debug_clear_cache_title)) },
            subtitle = { Text(text = stringResource(R.string.settings_debug_clear_cache_subtitle)) },
            onClick = {},
        )
    }
}

// readTail allocates ByteArray of this size, so must fit in Int
private const val MAX_LOG_DISPLAY_BYTES = 256 * 1024L // 256 KB

private fun readTail(file: File?): String {
    check(MAX_LOG_DISPLAY_BYTES <= Int.MAX_VALUE) { "MAX_LOG_DISPLAY_BYTES exceeds Int.MAX_VALUE" }
    if (file == null || !file.exists()) return "File not found: ${file?.name ?: "null"}"
    return try {
        val len = file.length()
        if (len <= MAX_LOG_DISPLAY_BYTES) {
            file.readText(Charsets.UTF_8)
        } else {
            val start = maxOf(0L, len - MAX_LOG_DISPLAY_BYTES)
            java.io.RandomAccessFile(file, "r").use { raf ->
                raf.seek(start)
                val bytes = ByteArray((len - start).toInt())
                raf.readFully(bytes)
                val text = bytes.toString(Charsets.UTF_8)
                // drop partial first line
                val idx = text.indexOf('\n')
                val trimmed = if (idx >= 0) text.substring(idx + 1) else text
                "... (${(len + 1023) / 1024}KB, showing last ${MAX_LOG_DISPLAY_BYTES / 1024}KB) ...\n$trimmed"
            }
        }
    } catch (e: Exception) {
        "Failed to read file: ${e.message ?: e.javaClass.simpleName}"
    }
}
