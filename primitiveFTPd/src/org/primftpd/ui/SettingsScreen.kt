package org.primftpd.ui

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import org.primftpd.ui.viewmodel.UiPreferencesViewModel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.apache.ftpserver.impl.PassivePorts
import org.primftpd.R
import org.primftpd.log.LogController
import org.primftpd.prefs.LoadPrefsUtil
import org.primftpd.prefs.Logging
import org.primftpd.prefs.ServerToStart
import org.primftpd.util.Defaults
import org.primftpd.util.EncryptionUtil
import androidx.core.content.edit
import kotlinx.coroutines.flow.first
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.tooling.preview.Preview
import org.primftpd.ui.data.ColorBag
import org.primftpd.ui.util.WallpaperPalette
import org.primftpd.ui.util.rememberWallpaperAccentColor
import org.primftpd.ui.viewmodel.WallpaperViewModel

enum class SettingsSection(val route: String) {
    AUTH("auth"),
    CONNECTIVITY("connecting"),
    UI("ui"),
    SYSTEM("system")
}

// ─── Entry Point ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    section: SettingsSection = SettingsSection.AUTH,
    onBack: () -> Unit,
    previewColorBag: ColorBag? = null,
    wallpaperViewModel: WallpaperViewModel? = if(LocalInspectionMode.current) null else viewModel(),
    uiPreferencesViewModel: UiPreferencesViewModel? = if (LocalInspectionMode.current) null else viewModel()
) {
    var hasNavigatedBack by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val sectionOffsets = remember { mutableStateMapOf<SettingsSection, Int>() }

    LaunchedEffect(section) {
        snapshotFlow { sectionOffsets[section] }
            .first { it != null }
            ?.let { scrollState.animateScrollTo(it) }
    }

    val wallpaperBitmap: ImageBitmap? = wallpaperViewModel?.wallpaper?.collectAsState()?.value
    val colorBag = if (LocalInspectionMode.current && previewColorBag != null) {
        previewColorBag
    } else {
        ColorBag(
            vibrant = rememberWallpaperAccentColor(WallpaperPalette(bitmap = wallpaperBitmap)),
            darkMuted = rememberWallpaperAccentColor(
                WallpaperPalette(bitmap = wallpaperBitmap),
                type = "dark_muted"
            ),
            vibrantLight = rememberWallpaperAccentColor(
                WallpaperPalette(bitmap = wallpaperBitmap),
                type = "light_vibrant"
            ),
            useM3Color = (uiPreferencesViewModel?.usrM3ToPickColors?.collectAsState()?.value ?: false)
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.prefs)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!hasNavigatedBack) {
                            hasNavigatedBack = true
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(scrollState)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        sectionOffsets[SettingsSection.AUTH] = it.positionInParent().y.toInt()
                    }
            ) {
                AuthCategory()
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        sectionOffsets[SettingsSection.CONNECTIVITY] = it.positionInParent().y.toInt()
                    }
            ) {
                ConnectivityCategory()
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        sectionOffsets[SettingsSection.UI] = it.positionInParent().y.toInt()
                    }
            ) {
                UiCategory(uiPreferencesViewModel = uiPreferencesViewModel, colorBag = colorBag)
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        sectionOffsets[SettingsSection.SYSTEM] = it.positionInParent().y.toInt()
                    }
            ) {
                SystemCategory()
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
// ─── Helper: get SharedPreferences once ─────────────────────────

@Composable
internal fun rememberPrefs() = LoadPrefsUtil.getPrefs(LocalContext.current)


// ─── Category: Auth ──────────────────────────────────────────────

@Composable
private fun AuthCategory() {
    val context = LocalContext.current
    val prefs = rememberPrefs()

    var anonymousLogin by remember { mutableStateOf(LoadPrefsUtil.anonymousLogin(prefs)) }
    var userName by remember { mutableStateOf(LoadPrefsUtil.userName(prefs)) }
    val passwordExists = remember { mutableStateOf(LoadPrefsUtil.password(prefs) != null) }
    var pubKeyAuth by remember { mutableStateOf(LoadPrefsUtil.pubKeyAuth(prefs)) }

    var showUserNameDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }

    val pubKeySummary =
        stringResource(R.string.prefSummaryPubKeyAuth_v2, Defaults.pubKeyAuthKeyPath(context))

    Text(
        text = stringResource(R.string.prefsCategoryTitleAuth),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )

    SwitchPrefRow(
        title = stringResource(R.string.prefTitleAnonymousLogin),
        description = stringResource(R.string.prefSummaryAnonymousLogin),
        checked = anonymousLogin,
        onCheckedChange = {
            anonymousLogin = it
            prefs.edit { putBoolean(LoadPrefsUtil.PREF_ANONYMOUS_LOGIN, it) }
        }
    )

    EditPrefRow(
        title = stringResource(R.string.prefTitleUser),
        description = stringResource(R.string.prefSummaryUser),
        currentValue = userName,
        onClick = { showUserNameDialog = true }
    )

    EditPrefRow(
        title = stringResource(R.string.prefTitlePassword),
        description = stringResource(R.string.prefSummaryPassword),
        currentValue = if (passwordExists.value) "****" else "",
        onClick = { showPasswordDialog = true }
    )

    SwitchPrefRow(
        title = stringResource(R.string.prefTitlePubKeyAuth),
        description = pubKeySummary,
        checked = pubKeyAuth,
        onCheckedChange = {
            pubKeyAuth = it
            prefs.edit { putBoolean(LoadPrefsUtil.PREF_KEY_PUB_KEY_AUTH, it) }
        }
    )

    if (showUserNameDialog) {
        EditTextDialog(
            title = stringResource(R.string.prefTitleUser),
            currentValue = userName,
            validate = { null },
            onDismiss = { showUserNameDialog = false },
            onConfirm = {
                userName = it
                prefs.edit { putString(LoadPrefsUtil.PREF_KEY_USER, it) }
            }
        )
    }

    if (showPasswordDialog) {
        PasswordEditDialog(
            title = stringResource(R.string.prefTitlePassword),
            onDismiss = { showPasswordDialog = false },
            onConfirm = { plainText ->
                if (plainText == null) {
                    // Clear password
                    passwordExists.value = false
                    prefs.edit { remove(LoadPrefsUtil.PREF_KEY_PASSWORD) }
                } else {
                    val encrypted = EncryptionUtil.encrypt(plainText)
                    passwordExists.value = true
                    prefs.edit { putString(LoadPrefsUtil.PREF_KEY_PASSWORD, encrypted) }
                }
            }
        )
    }
}

// ─── Category: Connectivity ──────────────────────────────────────

@SuppressLint("LocalContextResourcesRead")
@Composable
private fun ConnectivityCategory() {
    val context = LocalContext.current
    val prefs = rememberPrefs()

    val serverToStartNames =
        LocalResources.current.getStringArray(R.array.prefWhichServerToStartNames).toList()
    val serverToStartValues =
        LocalResources.current.getStringArray(R.array.prefWhichServerToStartValues).toList()
    
    val whichServerStr = remember {
        prefs.getString(
            LoadPrefsUtil.PREF_KEY_WHICH_SERVER,
            ServerToStart.ALL.xmlValue(),
        ) ?: "0"
    }
    var whichServerIndex by remember {
        mutableStateOf(
            serverToStartValues.indexOf(whichServerStr).coerceAtLeast(0)
        )
    }

    var port by remember {
        mutableStateOf(
            prefs.getString(LoadPrefsUtil.PREF_KEY_PORT, "12345")
            ?: "12345"
        )
    }
    var securePort by remember {
        mutableStateOf(prefs.getString(LoadPrefsUtil.PREF_KEY_SECURE_PORT, "1234") ?: "1234")
    }
    var ftpPassivePorts by remember {
        mutableStateOf(prefs.getString(LoadPrefsUtil.PREF_KEY_FTP_PASSIVE_PORTS, "5678") ?: "5678")
    }
    var idleTimeout by remember {
        mutableStateOf(prefs.getString(LoadPrefsUtil.PREF_KEY_IDLE_TIMEOUT, "0") ?: "0")
    }
    var idleTimeoutServerStop by remember {
        mutableStateOf(prefs.getString(LoadPrefsUtil.PREF_KEY_IDLE_TIMEOUT_SERVER_STOP, "30") ?: "30")
    }
    var allowedIps by remember {
        mutableStateOf(prefs.getString(LoadPrefsUtil.PREF_KEY_ALLOWED_IPS_PATTERN, "") ?: "")
    }
    var bindIp by remember {
        mutableStateOf(prefs.getString(LoadPrefsUtil.PREF_KEY_BIND_IP, "") ?: "")
    }
    var chooseBindIp by remember {
        mutableStateOf(prefs.getBoolean(LoadPrefsUtil.PREF_KEY_CHOOSE_BIND_IP, false))
    }

    var showWhichServerDialog by remember { mutableStateOf(false) }
    var showPortDialog by remember { mutableStateOf(false) }
    var showSecurePortDialog by remember { mutableStateOf(false) }
    var showPassivePortsDialog by remember { mutableStateOf(false) }
    var showIdleTimeoutDialog by remember { mutableStateOf(false) }
    var showIdleTimeoutStopDialog by remember { mutableStateOf(false) }
    var showAllowedIpsDialog by remember { mutableStateOf(false) }
    var showBindIpDialog by remember { mutableStateOf(false) }

    @SuppressLint("LocalContextGetResourceValueCall")
    fun validatePort(input: String, otherVal: String): String? {
        val p = input.toIntOrNull()
        return when {
            p == null -> context.getString(R.string.portInvalid_v2)
            !LoadPrefsUtil.validatePort(p) -> context.getString(R.string.portInvalid_v2)
            input == otherVal -> context.getString(R.string.portsEqual_v2)
            else -> null
        }
    }//这里原来的otherKey删掉了，直接穿的两个参数

    @SuppressLint("LocalContextGetResourceValueCall")
    fun validatePassivePorts(input: String): String? {
        return try {
            PassivePorts(input, false)
            null
        } catch (_: Exception) {
            context.getString(R.string.ftpPassivePortsInvalid)
        }
    }

    Text(
        text = stringResource(R.string.prefsCategoryTitleConnectivity),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )

    ListPrefRow(
        title = stringResource(R.string.prefTitleWhichServerToStart),
        description = stringResource(R.string.prefSummaryWhichServerToStart),
        selectedLabel = serverToStartNames[whichServerIndex],
        onClick = { showWhichServerDialog = true }
    )

    EditPrefRow(
        title = stringResource(R.string.prefTitlePort),
        description = stringResource(R.string.prefSummaryPort),
        currentValue = port,
        onClick = { showPortDialog = true }
    )

    EditPrefRow(
        title = stringResource(R.string.prefTitlePortSecure),
        description = stringResource(R.string.prefSummaryPortSecure),
        currentValue = securePort,
        onClick = { showSecurePortDialog = true }
    )

    EditPrefRow(
        title = stringResource(R.string.prefTitleFtpPassivePorts),
        description = stringResource(R.string.prefSummaryFtpPassivePorts),
        currentValue = ftpPassivePorts,
        onClick = { showPassivePortsDialog = true }
    )

    EditPrefRow(
        title = stringResource(R.string.prefTitleIdleTimeout),
        description = stringResource(R.string.prefSummaryIdleTimeoutV2),
        currentValue = idleTimeout,
        onClick = { showIdleTimeoutDialog = true }
    )

    EditPrefRow(
        title = stringResource(R.string.prefTitleIdleTimeoutServerStop),
        description = stringResource(R.string.prefSummaryIdleTimeoutServerStop),
        currentValue = idleTimeoutServerStop,
        onClick = { showIdleTimeoutStopDialog = true }
    )

    EditPrefRow(
        title = stringResource(R.string.prefAllowedIpsPattern),
        description = stringResource(R.string.prefSummaryAllowedIpsPattern),
        currentValue = allowedIps,
        onClick = { showAllowedIpsDialog = true }
    )

    EditPrefRow(
        title = stringResource(R.string.prefBindIp),
        description = stringResource(R.string.prefSummaryBindIp),
        currentValue = bindIp,
        onClick = { showBindIpDialog = true }
    )

    SwitchPrefRow(
        title = stringResource(R.string.prefChooseBindIp),
        description = stringResource(R.string.prefSummaryChooseBindIp),
        checked = chooseBindIp,
        onCheckedChange = {
            chooseBindIp = it
            prefs.edit { putBoolean(LoadPrefsUtil.PREF_KEY_CHOOSE_BIND_IP, it) }
        }
    )

    // ── Dialogs ──

    if (showWhichServerDialog) {
        ListSelectionDialog(
            title = stringResource(R.string.prefTitleWhichServerToStart),
            entries = serverToStartNames,
            entryValues = serverToStartValues,
            selectedIndex = whichServerIndex,
            onDismiss = { showWhichServerDialog = false },
            onSelected = { idx, value ->
                whichServerIndex = idx
                prefs.edit { putString(LoadPrefsUtil.PREF_KEY_WHICH_SERVER, value) }
            }
        )
    }

    if (showPortDialog) {
        EditTextDialog(
            title = stringResource(R.string.prefTitlePort),
            currentValue = port,
            keyboardType = KeyboardType.Number,
            validate = { validatePort(it, securePort) },
            onDismiss = { showPortDialog = false },
            onConfirm = {
                port = it
                prefs.edit { putString(LoadPrefsUtil.PREF_KEY_PORT, it) }
            }
        )
    }

    if (showSecurePortDialog) {
        EditTextDialog(
            title = stringResource(R.string.prefTitlePortSecure),
            currentValue = securePort,
            keyboardType = KeyboardType.Number,
            validate = { validatePort(it, port) },
            onDismiss = { showSecurePortDialog = false },
            onConfirm = {
                securePort = it
                prefs.edit { putString(LoadPrefsUtil.PREF_KEY_SECURE_PORT, it) }
            }
        )
    }

    if (showPassivePortsDialog) {
        EditTextDialog(
            title = stringResource(R.string.prefTitleFtpPassivePorts),
            currentValue = ftpPassivePorts,
            validate = { validatePassivePorts(it) },
            onDismiss = { showPassivePortsDialog = false },
            onConfirm = {
                ftpPassivePorts = it
                prefs.edit { putString(LoadPrefsUtil.PREF_KEY_FTP_PASSIVE_PORTS, it) }
            }
        )
    }

    if (showIdleTimeoutDialog) {
        EditTextDialog(
            title = stringResource(R.string.prefTitleIdleTimeout),
            currentValue = idleTimeout,
            keyboardType = KeyboardType.Number,
            validate = { null },
            onDismiss = { showIdleTimeoutDialog = false },
            onConfirm = {
                idleTimeout = it
                prefs.edit { putString(LoadPrefsUtil.PREF_KEY_IDLE_TIMEOUT, it) }
            }
        )
    }

    if (showIdleTimeoutStopDialog) {
        EditTextDialog(
            title = stringResource(R.string.prefTitleIdleTimeoutServerStop),
            currentValue = idleTimeoutServerStop,
            keyboardType = KeyboardType.Number,
            validate = { null },
            onDismiss = { showIdleTimeoutStopDialog = false },
            onConfirm = {
                idleTimeoutServerStop = it
                prefs.edit { putString(LoadPrefsUtil.PREF_KEY_IDLE_TIMEOUT_SERVER_STOP, it) }
            }
        )
    }

    if (showAllowedIpsDialog) {
        EditTextDialog(
            title = stringResource(R.string.prefAllowedIpsPattern),
            currentValue = allowedIps,
            validate = { null },
            onDismiss = { showAllowedIpsDialog = false },
            onConfirm = {
                allowedIps = it
                prefs.edit { putString(LoadPrefsUtil.PREF_KEY_ALLOWED_IPS_PATTERN, it) }
            }
        )
    }

    if (showBindIpDialog) {
        EditTextDialog(
            title = stringResource(R.string.prefBindIp),
            currentValue = bindIp,
            validate = { null },
            onDismiss = { showBindIpDialog = false },
            onConfirm = {
                bindIp = it
                prefs.edit { putString(LoadPrefsUtil.PREF_KEY_BIND_IP, it) }
            }
        )
    }
}


// ─── Category: System ────────────────────────────────────────────

@Composable
private fun SystemCategory() {
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
        mutableStateOf(loggingValues.indexOf(loggingStr).coerceAtLeast(0))
    }

    val hostkeyNames = LocalResources.current.getStringArray(R.array.prefHostkeyAlgosNames).toList()
    val hostkeyValues = LocalResources.current.getStringArray(R.array.prefHostkeyAlgosValues).toList()
    val hostkeyDefaults = setOf("ed25519")
    var savedHostkeys by remember {
        mutableStateOf(
            prefs.getStringSet(LoadPrefsUtil.PREF_KEY_HOSTKEY_ALGOS, hostkeyDefaults) ?: hostkeyDefaults
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

    Text(
        text = stringResource(R.string.prefsCategoryTitleSystem),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
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
        onCheckedChange = {
            wakelock = it
            prefs.edit { putBoolean(LoadPrefsUtil.PREF_KEY_WAKELOCK, it) }
        }
    )

    SwitchPrefRow(
        title = stringResource(R.string.prefTitleAnnounce),
        description = stringResource(R.string.prefSummaryAnnounce),
        checked = announce,
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
            text = savedHostkeys.joinToString(", "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    SwitchPrefRow(
        title = stringResource(R.string.prefRootCopyFiles),
        description = stringResource(R.string.prefSummaryRootCopyFiles),
        checked = rootCopyFiles,
        onCheckedChange = {
            rootCopyFiles = it
            prefs.edit { putBoolean(LoadPrefsUtil.PREF_ROOT_COPY_FILES, it) }
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
            entries = hostkeyNames,
            entryValues = hostkeyValues,
            initialSelected = savedHostkeys,
            onDismiss = { showHostkeyDialog = false },
            onConfirm = { selected ->
                savedHostkeys = selected
                prefs.edit { putStringSet(LoadPrefsUtil.PREF_KEY_HOSTKEY_ALGOS, selected) }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PrefsPreview() {
    MaterialTheme {
        SettingsScreen(
            onBack = {},
            section = SettingsSection.UI
        )
    }
}

