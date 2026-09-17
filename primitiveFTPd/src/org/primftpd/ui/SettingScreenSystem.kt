package org.primftpd.ui
import android.app.Activity
import android.content.Intent
import android.widget.Toast

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import org.primftpd.R
import org.primftpd.log.LogController
import org.primftpd.prefs.LoadPrefsUtil
import org.primftpd.prefs.Logging
import org.primftpd.ui.data.ColorBag
import org.primftpd.ui.data.SettingsBackup
import org.primftpd.util.Defaults


@Composable
@Suppress("WrongConstant")
internal fun SystemCategory(colorBag: ColorBag) {
    val context = LocalContext.current
    val prefs = rememberPrefs()

    var wakelock by remember { mutableStateOf(LoadPrefsUtil.wakelock(prefs)) }
    var announce by remember { mutableStateOf(LoadPrefsUtil.announce(prefs)) }
    var announceName by remember { mutableStateOf(LoadPrefsUtil.announceName(prefs)) }
    var startOnBoot by remember { mutableStateOf(LoadPrefsUtil.startOnBoot(prefs)) }
    var rootCopyFiles by remember { mutableStateOf(LoadPrefsUtil.rootCopyFiles(prefs)) }

    val loggingValues = LocalResources.current.getStringArray(R.array.prefLoggingValues).toList()
    val loggingNames = LocalResources.current.getStringArray(R.array.prefLoggingNames).toList()

    val loggingStr = remember {
        prefs.getString(LoadPrefsUtil.PREF_KEY_LOGGING, Logging.NONE.xmlValue()) ?: "0"
    }
    // this val's mutableStateOf is removed by AI
    //

    var loggingIndex by remember {
        mutableIntStateOf(loggingValues.indexOf(loggingStr).coerceAtLeast(0))
    }

    val hostKeyNames = LocalResources.current.getStringArray(R.array.prefHostkeyAlgosNames).toList()
    val hostKeyValues = LocalResources.current.getStringArray(R.array.prefHostkeyAlgosValues).toList()
    val hostKeyDefaults = setOf("ed25519")
    var savedHostKeys by remember {
        mutableStateOf(
            prefs.getStringSet(LoadPrefsUtil.PREF_KEY_HOSTKEY_ALGOS, hostKeyDefaults) ?: hostKeyDefaults
        )
    }

    var startDirPath by remember {
        mutableStateOf(LoadPrefsUtil.startDir(prefs).absolutePath)
    }

    val logPath = remember {
        val base = Defaults.homeDirScoped(context).absolutePath
        "$base/${LogController.LOGFILE_BASENAME}*".replace("//", "/")
    }
    val loggingSummary = stringResource(R.string.prefSummaryLoggingV2, logPath)

    var showAnnounceNameDialog by remember { mutableStateOf(false) }
    var showLoggingDialog by remember { mutableStateOf(false) }
    var showHostkeyDialog by remember { mutableStateOf(false) }

    val startDirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        startDirPath = LoadPrefsUtil.startDir(prefs).absolutePath
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                val flags = result.data?.flags ?: 0
                val takeFlags = flags and (
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                if (takeFlags != 0) {
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                    }
                }

                SettingsBackup.export(context, prefs, uri).fold(
                    onSuccess = {
                        Toast.makeText(context, "Export succeeded", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { error ->
                        Toast.makeText(
                            context,
                            "Export failed: ${error.message}",
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                )
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                val flags = result.data?.flags ?: 0
                val takeFlags = flags and (
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                if (takeFlags != 0) {
                    runCatching {
                        when (takeFlags) {
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION ->
                                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                        }
                    }
                }

                SettingsBackup.import(context, prefs, uri).fold(
                    onSuccess = {
                        Toast.makeText(context, "Import succeeded", Toast.LENGTH_SHORT).show()
                        if (context is Activity) {
                            context.recreate()
                        }
                    },
                    onFailure = { error ->
                        Toast.makeText(
                            context,
                            "Import failed: ${error.message}",
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                )
            }
        }
    }


    Text(
        text = stringResource(R.string.prefsCategoryTitleSystem),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = if ( colorBag.useM3Color ) MaterialTheme.colorScheme.primary
        else if (isSystemInDarkTheme()) colorBag.vibrant
        else colorBag.muted,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )

    ClickPrefRow(
        title = stringResource(R.string.prefTitleStartDir),
        description = startDirPath,
        onClick = {
            val intent = Defaults.createPrefDirPicker(
                context,
                LoadPrefsUtil.startDir(prefs),
                LoadPrefsUtil.PREF_KEY_START_DIR
            )
            startDirLauncher.launch(intent)
        }
    )

    SwitchPrefRow(
        title = stringResource(R.string.prefTitleWakelock),
        description = stringResource(R.string.prefSummaryWakelock),
        checked = wakelock,
        colorBag = colorBag,
        onCheckedChange = {
            wakelock = it
            prefs.edit { putBoolean(LoadPrefsUtil.PREF_KEY_WAKELOCK, it) }
        }
    )

    SwitchPrefRow(
        title = stringResource(R.string.prefTitleAnnounce),
        description = stringResource(R.string.prefSummaryAnnounce),
        checked = announce,
        colorBag = colorBag,
        onCheckedChange = {
            announce = it
            prefs.edit { putBoolean(LoadPrefsUtil.PREF_KEY_ANNOUNCE, it) }
        }
    )

    EditPrefRow(
        title = stringResource(R.string.prefTitleAnnounceName),
        description = stringResource(R.string.prefSummaryAnnounceName),
        currentValue = announceName,
        onClick = { showAnnounceNameDialog = true }
    )

    SwitchPrefRow(
        title = stringResource(R.string.prefTitleStartOnBoot),
        description = stringResource(R.string.prefSummaryStartOnBoot),
        checked = startOnBoot,
        colorBag = colorBag,
        onCheckedChange = {
            startOnBoot = it
            prefs.edit { putBoolean(LoadPrefsUtil.PREF_KEY_START_ON_BOOT, it) }
        }
    )

    ListPrefRow(
        title = stringResource(R.string.prefTitleLogging),
        description = loggingSummary,
        selectedLabel = loggingNames[loggingIndex],
        onClick = { showLoggingDialog = true }
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showHostkeyDialog = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.prefHostkeyAlgos),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.prefSummaryHostkeyAlgos),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = savedHostKeys.joinToString(", "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    SwitchPrefRow(
        title = stringResource(R.string.prefRootCopyFiles),
        description = stringResource(R.string.prefSummaryRootCopyFiles),
        checked = rootCopyFiles,
        colorBag = colorBag,
        onCheckedChange = {
            rootCopyFiles = it
            prefs.edit { putBoolean(LoadPrefsUtil.PREF_ROOT_COPY_FILES, it) }
        }
    )


    ClickPrefRow(
        title = "Export data",
        description = "Export the data to the selected directory",
        onClick = {
            val intent = Defaults.exportDirPicker(
                context,
                LoadPrefsUtil.startDir(prefs)
            )
            exportLauncher.launch(intent)
        }
    )
    ClickPrefRow(
        title = "Import data",
        description = "Import the data from the selected directory",
        onClick = {
            val intent = Defaults.exportDirPicker(
                context,
                LoadPrefsUtil.startDir(prefs)
            )
            importLauncher.launch(intent)
        }
    )



    // ── Dialogs ──

    if (showAnnounceNameDialog) {
        EditTextDialog(
            title = stringResource(R.string.prefTitleAnnounceName),
            currentValue = announceName,
            validate = { null },
            onDismiss = { showAnnounceNameDialog = false },
            onConfirm = {
                announceName = it
                prefs.edit { putString(LoadPrefsUtil.PREF_KEY_ANNOUNCE_NAME, it) }
            }
        )
    }

    if (showLoggingDialog) {
        ListSelectionDialog(
            title = stringResource(R.string.prefTitleLogging),
            entries = loggingNames,
            entryValues = loggingValues,
            selectedIndex = loggingIndex,
            onDismiss = { showLoggingDialog = false },
            onSelected = { idx, value ->
                loggingIndex = idx
                prefs.edit { putString(LoadPrefsUtil.PREF_KEY_LOGGING, value) }
            }
        )
    }

    if (showHostkeyDialog) {
        MultiSelectDialog(
            title = stringResource(R.string.prefHostkeyAlgos),
            entries = hostKeyNames,
            entryValues = hostKeyValues,
            initialSelected = savedHostKeys,
            onDismiss = { showHostkeyDialog = false },
            onConfirm = { selected ->
                savedHostKeys = selected
                prefs.edit { putStringSet(LoadPrefsUtil.PREF_KEY_HOSTKEY_ALGOS, selected) }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SystemPrefsPreview() {
    MaterialTheme {
        SettingsScreen(
            onBack = {},
            section = SettingsSection.SYSTEM,
            previewColorBag = ColorBag(
                vibrant = Color(0xFF6200EE),
                darkMuted = Color(0xFF3700B3),
                lightMuted = Color(0xFFBB86FC),
                muted = Color(0xFF03DAC5),
                useM3Color = false
            )
        )
    }
}

