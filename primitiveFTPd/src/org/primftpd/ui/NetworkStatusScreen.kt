package org.primftpd.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.primftpd.R
import org.primftpd.crypto.HostKeyAlgorithm
import org.primftpd.events.ClientActionEvent
import org.primftpd.events.ServerInfoResponseEvent
import org.primftpd.events.ServerStateChangedEvent
import org.primftpd.ui.ShizukuFtpTheme
import org.primftpd.prefs.LoadPrefsUtil
import org.primftpd.prefs.PrefsBean
import org.primftpd.prefs.StorageType
import org.primftpd.util.IpAddressBean
import org.primftpd.util.IpAddressProvider
import org.primftpd.util.KeyFingerprintBean
import org.primftpd.util.KeyFingerprintProvider
import org.primftpd.util.ServersRunningBean
import org.primftpd.util.ServicesStartStopUtil
import org.primftpd.util.StringUtils
import org.slf4j.LoggerFactory
import rikka.shizuku.Shizuku

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkStatusScreen(
    isServerRunning: Boolean,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logger = remember { LoggerFactory.getLogger("NetworkStatusScreen") }
    val configuration = LocalConfiguration.current
    val isLeftToRight = configuration.layoutDirection == android.util.LayoutDirection.LTR

    // ─── States ───
    var serversRunning by remember { mutableStateOf(ServersRunningBean()) }
    var prefsBean by remember { mutableStateOf<PrefsBean?>(null) }
    var ipAddressBeans by remember { mutableStateOf<List<IpAddressBean>>(emptyList()) }
    var isLoadingAddresses by remember { mutableStateOf(true) }

    var quickShareFileCount by remember { mutableIntStateOf(-1) }

    var safUrl by remember { mutableStateOf<String?>(null) }
    var clientAction1 by remember { mutableStateOf("") }
    var clientAction2 by remember { mutableStateOf("") }
    var clientAction3 by remember { mutableStateOf("") }

    var hasNormalStorageAccess by remember { mutableStateOf<Boolean?>(null) }
    var hasFullStorageAccess by remember { mutableStateOf<Boolean?>(null) }
    var hasMediaLocationAccess by remember { mutableStateOf<Boolean?>(null) }
    var hasNotificationPermission by remember { mutableStateOf<Boolean?>(null) }

    var selectedStorageType by remember { mutableStateOf<StorageType?>(null) }
    var keyFingerprintBean by remember { mutableStateOf<KeyFingerprintBean?>(null) }
    var chosenAlgo by remember { mutableStateOf<HostKeyAlgorithm?>(null) }
    var showSafWarning by remember { mutableStateOf(false) }

    var shizukuBinderReady by remember { mutableStateOf(false) }
    var pendingShizukuSelection by remember { mutableStateOf(false) }
    var shizukuRetryCount by remember { mutableIntStateOf(0) }

    var hasNavigatedBack by remember { mutableStateOf(false) }

    val ipAddressProvider = remember { IpAddressProvider() }
    val keyFingerprintProvider = remember { KeyFingerprintProvider() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var timestampOfLastEvent by remember { mutableLongStateOf(0L) }

    // ─── Helper Functions ───

    fun reloadPrefs(): PrefsBean? {
        val prefs = LoadPrefsUtil.getPrefs(context)
        return LoadPrefsUtil.loadPrefs(logger, prefs)
    }

    fun isEventInTime(): Boolean {
        val currentTime = System.currentTimeMillis()
        val offset = currentTime - timestampOfLastEvent
        if (offset > 20) {
            timestampOfLastEvent = currentTime
            return true
        }
        return false
    }

    fun refreshPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            hasNormalStorageAccess = ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            hasFullStorageAccess = Environment.isExternalStorageManager()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            hasMediaLocationAccess = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_MEDIA_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun refreshAddresses() {
        isLoadingAddresses = true
        scope.launch {
            withContext(Dispatchers.IO) {
                val beans = ipAddressProvider.ipAddressTexts(context, true, isLeftToRight)
                withContext(Dispatchers.Main) {
                    ipAddressBeans = beans
                    isLoadingAddresses = false
                }
            }
        }
    }

    fun refreshServerState() {
        serversRunning = ServicesStartStopUtil.checkServicesRunning(context)
    }

    fun checkSafAccess() {
        val bean = prefsBean ?: return
        if (bean.storageType == StorageType.SAF || bean.storageType == StorageType.RO_SAF) {
            val persistedUriPermissions = context.contentResolver.persistedUriPermissions
            if (persistedUriPermissions.isEmpty()) {
                showSafWarning = true
                return
            }
            var cursor: Cursor? = null
            try {
                cursor = context.contentResolver.query(
                    bean.safUrl.toUri(),
                    arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                    null, null, null
                )
                if (cursor == null) {
                    showSafWarning = true
                    return
                }
                cursor.moveToFirst()
            } catch (_: Exception) {
                showSafWarning = true
                return
            } finally {
                cursor?.close()
            }
        }
        showSafWarning = false
    }

    fun finalizeShizukuSelection() {
        pendingShizukuSelection = false
        shizukuRetryCount = 0
        val prefs = LoadPrefsUtil.getPrefs(context)
        LoadPrefsUtil.storeStorageType(prefs, StorageType.SHIZUKU)
        reloadPrefs()?.let {
            prefsBean = it
            selectedStorageType = StorageType.SHIZUKU
        }
        checkSafAccess()
    }

    fun revertStorageTypeToPlain() {
        pendingShizukuSelection = false
        shizukuRetryCount = 0
        val prefs = LoadPrefsUtil.getPrefs(context)
        LoadPrefsUtil.storeStorageType(prefs, StorageType.PLAIN)
        reloadPrefs()?.let {
            prefsBean = it
            selectedStorageType = StorageType.PLAIN
        }
    }

    fun tryFinalizeShizukuSelection() {
        if (!pendingShizukuSelection) return
        shizukuRetryCount++
        val pingBinder = shizukuBinderReady || Shizuku.pingBinder()
        if (!pingBinder) {
            if (shizukuRetryCount < 8) {
                mainHandler.postDelayed({ tryFinalizeShizukuSelection() }, 500L)
                return
            }
            Toast.makeText(context, "Shizuku service is not available", Toast.LENGTH_LONG).show()
            revertStorageTypeToPlain()
            return
        }
        shizukuBinderReady = true
        if (Shizuku.getVersion() < 11) {
            Toast.makeText(context, "Shizuku version is not supported", Toast.LENGTH_LONG).show()
            revertStorageTypeToPlain()
            return
        }
        val selfPermission = Shizuku.checkSelfPermission()
        if (selfPermission == PackageManager.PERMISSION_GRANTED) {
            finalizeShizukuSelection()
        } else {
            Shizuku.requestPermission(1235)
        }
    }

    fun onStorageTypeChanged(type: StorageType) {
        pendingShizukuSelection = false
        shizukuRetryCount = 0
        when (type) {
            StorageType.PLAIN, StorageType.ROOT -> {
                val prefs = LoadPrefsUtil.getPrefs(context)
                LoadPrefsUtil.storeStorageType(prefs, type)
                reloadPrefs()?.let {
                    prefsBean = it
                    selectedStorageType = type
                }
                checkSafAccess()
            }
            StorageType.SHIZUKU -> {
                if (shizukuBinderReady || Shizuku.pingBinder()) {
                    if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                        finalizeShizukuSelection()
                    } else {
                        pendingShizukuSelection = true
                        Shizuku.requestPermission(1235)
                    }
                } else {
                    pendingShizukuSelection = true
                    mainHandler.postDelayed({ tryFinalizeShizukuSelection() }, 500L)
                }
            }
            StorageType.SAF, StorageType.RO_SAF, StorageType.VIRTUAL -> {
                val prefs = LoadPrefsUtil.getPrefs(context)
                LoadPrefsUtil.storeStorageType(prefs, type)
                reloadPrefs()?.let {
                    prefsBean = it
                    selectedStorageType = type
                }
                checkSafAccess()
            }
        }
    }

    fun displayKeyFingerprints() {
        scope.launch {
            withContext(Dispatchers.IO) {
                keyFingerprintProvider.calcPubkeyFingerprints(context)
                val algo = keyFingerprintProvider.findPreferredHostKeyAlog(context)
                val fingerprint = keyFingerprintProvider.fingerprints[algo]
                withContext(Dispatchers.Main) {
                    chosenAlgo = algo
                    keyFingerprintBean = fingerprint
                }
            }
        }
    }

    // ─── Launchers ───

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val uriStr = uri.toString()
            val modeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            safUrl?.let { oldUrl ->
                try {
                    context.contentResolver.releasePersistableUriPermission(oldUrl.toUri(), modeFlags)
                } catch (_: SecurityException) { }
            }
            try {
                context.grantUriPermission(context.packageName, uri, modeFlags)
                context.contentResolver.takePersistableUriPermission(uri, modeFlags)
            } catch (_: SecurityException) { }
            LoadPrefsUtil.getPrefs(context).edit {
                putString(LoadPrefsUtil.PREF_KEY_SAF_URL, uriStr)
            }
            safUrl = uriStr
            reloadPrefs()?.let {
                prefsBean = it
                selectedStorageType = it.storageType
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshPermissions()
    }

    // ─── Effects ───

    DisposableEffect(context) {
        val subscriber = object : Any() {
            @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
            fun onServerStateChanged(event: ServerStateChangedEvent) {
                if (isEventInTime()) refreshServerState()
            }

            @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
            fun onServerInfoResponse(event: ServerInfoResponseEvent) {
                val numberOfFiles = event.quickShareNumberOfFiles
                if (isEventInTime() && numberOfFiles >= 0) {
                    quickShareFileCount = numberOfFiles
                }
            }

            @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
            fun onClientAction(event: ClientActionEvent) {
                val formatted = ClientActionFragment.format(event)
                clientAction3 = clientAction2
                clientAction2 = clientAction1
                clientAction1 = formatted
            }
        }
        EventBus.getDefault().register(subscriber)
        onDispose { EventBus.getDefault().unregister(subscriber) }
    }

    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { refreshAddresses() }
            override fun onLost(network: Network) { refreshAddresses() }
            override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) { refreshAddresses() }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cm.registerDefaultNetworkCallback(callback)
        }
        onDispose { cm.unregisterNetworkCallback(callback) }
    }

    DisposableEffect(context) {
        val binderReceivedListener = Shizuku.OnBinderReceivedListener {
            shizukuBinderReady = true
            if (pendingShizukuSelection) {
                mainHandler.removeCallbacksAndMessages(null)
                mainHandler.post { tryFinalizeShizukuSelection() }
            }
        }
        val binderDeadListener = Shizuku.OnBinderDeadListener { shizukuBinderReady = false }
        val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == 1235) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    finalizeShizukuSelection()
                } else {
                    revertStorageTypeToPlain()
                }
            }
        }
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        shizukuBinderReady = Shizuku.pingBinder()
        onDispose {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionListener)
        }
    }

    LaunchedEffect(Unit) {
        refreshPermissions()
        refreshAddresses()
        reloadPrefs()?.let {
            prefsBean = it
            selectedStorageType = it.storageType
            safUrl = it.safUrl
        }
        refreshServerState()
        displayKeyFingerprints()
        checkSafAccess()
    }

    // ─── UI ───
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Network Status") },
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (serversRunning.atLeastOneRunning()) onStopServer() else onStartServer()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (serversRunning.atLeastOneRunning())
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    if (serversRunning.atLeastOneRunning())
                        stringResource(R.string.stopService)
                    else
                        stringResource(R.string.startService)
                )
            }

            if (quickShareFileCount >= 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.quickShareInfoActivityV2, quickShareFileCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "${stringResource(R.string.ipAddrLabel)} (${stringResource(R.string.ifacesLabel)})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isLoadingAddresses) {
                Spacer(modifier = Modifier.height(8.dp))
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                ipAddressBeans.forEach { bean ->
                    SelectionContainer {
                        Text(
                            text = bean.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "${stringResource(R.string.protocolLabel)} / ${stringResource(R.string.portLabel)} / ${stringResource(R.string.state)}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            prefsBean?.let { bean ->
                Spacer(modifier = Modifier.height(8.dp))
                val ftpState = if (serversRunning.ftp) stringResource(R.string.serverStarted) else stringResource(R.string.serverStopped)
                val sftpState = if (serversRunning.ssh) stringResource(R.string.serverStarted) else stringResource(R.string.serverStopped)
                SelectionContainer {
                    Text(
                        text = "ftp / ${bean.portStr} / $ftpState",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                SelectionContainer {
                    Text(
                        text = "sftp / ${bean.securePortStr} / $sftpState",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }

            prefsBean?.let { bean ->
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.usernameLabel, bean.userName),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.pubKeyAuth, bean.isPubKeyAuth),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.isAnonymous, bean.isAnonymousLogin),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.passwordPresent, StringUtils.isNotEmpty(bean.password)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.storageType),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            val storageTypes = listOf(
                StorageType.PLAIN to stringResource(R.string.storageTypePlainV2),
                StorageType.ROOT to stringResource(R.string.storageTypeRoot),
                StorageType.SHIZUKU to stringResource(R.string.storageTypeShizuku),
                StorageType.SAF to stringResource(R.string.storageTypeSaf),
                StorageType.RO_SAF to stringResource(R.string.storageTypeRoSaf),
                StorageType.VIRTUAL to stringResource(R.string.storageTypeVirtual),
            )
            Column(modifier = Modifier.selectableGroup()) {
                storageTypes.forEach { (type, label) ->
                    StorageTypeRow(
                        type = type,
                        label = label,
                        selected = selectedStorageType == type,
                        onSelected = { onStorageTypeChanged(type) },
                        onSafRequired = { safLauncher.launch(null) }
                    )
                }
            }

            if (selectedStorageType == StorageType.SAF ||
                selectedStorageType == StorageType.RO_SAF ||
                selectedStorageType == StorageType.VIRTUAL) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.safExplainHeading),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Text(
                    text = stringResource(R.string.safExplainV2),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            val onSafUrlClick = remember {
                {
                    try { safLauncher.launch(null) }
                    catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, "SAF seems to be broken on your device :(", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            val currentSafUrl = safUrl
            if (!currentSafUrl.isNullOrBlank() &&
                (selectedStorageType == StorageType.SAF ||
                        selectedStorageType == StorageType.RO_SAF ||
                        selectedStorageType == StorageType.VIRTUAL)) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.selectedSafUri),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Text(
                    text = currentSafUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                        .clickable{
                            onSafUrlClick
                        }
                )
            }

            if (showSafWarning) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "SAF access not available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Permissions",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                PermissionRow(
                    textId = R.string.hasNormalAccessToStorage,
                    granted = hasNormalStorageAccess,
                    onRequest = { permissionLauncher.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)) }
                )
            } else {
                PermissionRow(
                    textId = R.string.hasFullAccessToStorage,
                    granted = hasFullStorageAccess,
                    onRequest = {
                        val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.fromParts("package", context.packageName, null)
                        context.startActivity(intent)
                    }
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                PermissionRow(
                    textId = R.string.hasAccessToMediaLocation,
                    granted = hasMediaLocationAccess,
                    onRequest = { permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_MEDIA_LOCATION)) }
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionRow(
                    textId = R.string.hasNotificationPermission,
                    granted = hasNotificationPermission,
                    highlightMissing = true,
                    onRequest = { permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.clientActionsLabel),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (clientAction1.isEmpty() && clientAction2.isEmpty() && clientAction3.isEmpty()) {
                Text(text = "—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            listOf(clientAction1, clientAction2, clientAction3).forEach { action ->
                if (action.isNotEmpty()) {
                    SelectionContainer {
                        Text(
                            text = action, style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.fingerprintsLabel),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { displayKeyFingerprints() }) {
                    Text("Refresh")
                }
            }

            val algo = chosenAlgo
            val kfb = keyFingerprintBean
            if (algo != null && kfb != null) {
                Spacer(modifier = Modifier.height(4.dp))
                listOf(
                    "MD5" to kfb.fingerprintMd5,
                    "SHA1" to kfb.fingerprintSha1,
                    "SHA256" to kfb.fingerprintSha256
                ).forEach { (label, value) ->
                    Text(
                        text = "$label (${algo.displayName})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                    SelectionContainer {
                        Text(
                            text = value, style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Calculating...", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = {
                Toast.makeText(context, "Please use the menu to access fingerprints", Toast.LENGTH_SHORT).show()
            }) {
                Text(stringResource(R.string.allKeysFingerprintsLabel), color = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StorageTypeRow(
    type: StorageType,
    label: String,
    selected: Boolean,
    onSelected: () -> Unit,
    onSafRequired: () -> Unit
) {
    val onClick = remember(type) {
        {
            onSelected()
            if (type == StorageType.SAF || type == StorageType.RO_SAF || type == StorageType.VIRTUAL) {
                onSafRequired()
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PermissionRow(
    textId: Int,
    granted: Boolean?,
    highlightMissing: Boolean = false,
    onRequest: () -> Unit
) {
    if (granted == true) {
        Text(
            text = stringResource(textId, true),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
        )
    } else if (granted == false) {
        val baseText = stringResource(textId, false)
        val annotated = buildAnnotatedString {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                append(baseText)
            }
            append(" ")
            withStyle(SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline
            )) {
                append(stringResource(R.string.Request))
            }
        }
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .then(
                    if (highlightMissing) Modifier.background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                    else Modifier
                )
                .clickable { onRequest() }
                .padding(4.dp),
            //onClick = onRequest
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NetworkStatusScreenPreview() {
    ShizukuFtpTheme {
        NetworkStatusScreen(
            onStartServer = {},
            onStopServer = {},
            onBack = {},
            isServerRunning = false
        )
    }
}