package org.primftpd.ui

//import androidx.compose.material.icons.filled.ArrowBack
//import android.R.string.yes
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Menu
//import android.view.View
//import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.primftpd.R
import org.primftpd.events.ServerStateChangedEvent
import org.primftpd.prefs.LoadPrefsUtil
import org.primftpd.util.EncryptionUtil
import org.primftpd.util.ServicesStartStopUtil
import androidx.core.content.edit
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

//import org.slf4j.Logger
//import org.slf4j.LoggerFactory



//PROMPT：in first stage, I can use EventBus to get the data and draw the UI or dashboard
//        in Second stage (OR NEEDN'T), I can use ViewModel?
//        https://github.com/copilot/c/ec33ed69-a613-468d-94ee-90b004f3aec9
//        in the end, I can delete all the EventBus


open class MainTabsActivity : FragmentActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        const val INDEX_MAIN = 0
        const val INDEX_FINGERPRINTS = 1
        const val INDEX_PREFS = 2
        const val INDEX_ABOUT = 3
        const val INDEX_LOG = 4
        const val DIALOG_TAG = "dialogs"
    }

    //private var logger: Logger = LoggerFactory.getLogger(javaClass)
    private var isServerRunning by mutableStateOf(false)
    private var showPasswordDialog by mutableStateOf(false)
    private lateinit var pftpdFragment: PftpdFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pftpdFragment = createPftpdFragment()
        enableEdgeToEdge()
        isServerRunning = ServicesStartStopUtil.checkServicesRunning(this).atLeastOneRunning()

        setContent {
            ShizukuFtpTheme {
                val navController = rememberNavController()
                
                // 密码输入弹窗
                if (showPasswordDialog) {
                    PasswordInputDialog(
                        onDismiss = { showPasswordDialog = false },
                        onSave = { password ->
                            // 加密并保存密码到 SharedPreferences
                            val prefs = LoadPrefsUtil.getPrefs(this)
                            val encryptedPassword = EncryptionUtil.encrypt(password)
                            prefs.edit {
                                putString(
                                    LoadPrefsUtil.PREF_KEY_PASSWORD,
                                    encryptedPassword
                                )
                            }
                            showPasswordDialog = false
                            // 保存后重新启动服务
                            ServicesStartStopUtil.startServers(this)
                        }
                    )
                }

                NavHost(
                    navController = navController,
                    startDestination = "main"
                ) {
                    composable("main") {
                        MainScreen(
                            isServerRunning = isServerRunning,
                            onStartServer = {
                                handleStart()
                            },
                            onStopServer = {
                                handleStop()
                                //logger.info("onStopServer() called")
                            },
                            onNavigate = { route ->
                                navController.navigate(route)
                            }
                        )
                    }
                    composable("about") {
                        AboutScreen(
                            onBack = {
                                navController.popBackStack()
                            }
                        )
                    }
                    composable("qr") {
                        /*
                        QrScreen(
                            onBack = {
                                navController.popBackStack()
                            }
                        )*/
                        FragmentContainerScreen(
                            "扫码了",
                            {org.primftpd.ui.QrFragment(pftpdFragment)},
                            {navController.popBackStack()}
                        )
                    }
                    composable("settings") {
                        FragmentContainerScreen(
                            title = "设置",
                            fragmentFactory = { org.primftpd.prefs.FtpPrefsFragment() },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    //Can be overwritten now
                    composable("netWorkStatus"){
                        FragmentContainerScreen(
                            "networkStatus",
                            {org.primftpd.ui.PftpdFragment()},
                            {navController.popBackStack()}
                            )
                    }
                    composable("clientStatus"){
                        FragmentContainerScreen(
                            "clientStatus",
                            {org.primftpd.ui.ClientActionFragment()},
                            {navController.popBackStack()}
                            )
                    }
                    composable("VerificationKey"){
                        FragmentContainerScreen(
                            "Verification Key",
                            {org.primftpd.ui.PubKeyAuthKeysFragment(true)/*what is true?*/},
                            {navController.popBackStack()}
                            )
                    }
                    composable("fingerPrint"){
                        FragmentContainerScreen(
                            "fingerPrint",
                            {org.primftpd.ui.KeysFingerprintsFragment()},
                            {navController.popBackStack()}
                            )
                    }
                    composable("clean"){
                        FragmentContainerScreen(
                            "cleaner",
                            {org.primftpd.ui.CleanSpaceFragment()},
                            {navController.popBackStack()}
                            )
                    }
                }
            }
        }

        EventBus.getDefault().register(this)
        LoadPrefsUtil.getPrefs(this).registerOnSharedPreferenceChangeListener(this)
    }

    protected open fun createPftpdFragment(): PftpdFragment {
        return PftpdFragment()
    }

    protected open fun isLeanback(): Boolean {
        return false
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        return super.onCreateOptionsMenu(menu)
    }

    private fun handleStart() {
        val context = this
        val prefs = LoadPrefsUtil.getPrefs(context)
        val prefsBean = LoadPrefsUtil.loadPrefs(org.slf4j.LoggerFactory.getLogger(javaClass), prefs)

        // 检查密码是否设置（如果需要密码认证）
        if (prefsBean.serverToStart.isPasswordMandatory(prefsBean) && 
            org.primftpd.util.StringUtils.isBlank(prefsBean.password)) {
            // 弹出密码输入框
            showPasswordDialog = true
            return
        }

        // 仅当需要启动 SFTP 时才检查密钥
        if (prefsBean.serverToStart.startSftp()) {
            val keyProvider = org.primftpd.util.KeyFingerprintProvider()

            if (!keyProvider.areFingerprintsGenerated()) {
                keyProvider.calcPubkeyFingerprints(context)
            }

            val keyPresent = keyProvider.isKeyPresent()
            if (!keyPresent) {
                val askDiag = org.primftpd.ui.GenKeysAskDialogFragment()
                val args = Bundle().apply {
                    putBoolean(org.primftpd.ui.GenKeysAskDialogFragment.KEY_START_SERVER, true)
                }
                askDiag.arguments = args
                askDiag.show(supportFragmentManager, DIALOG_TAG)
                return
            }
        }

        // 走 this，避免依赖游离 fragment 的生命周期
        ServicesStartStopUtil.startServers(this)
    }


    private fun handleStop() {
        ServicesStartStopUtil.stopServers(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onEvent(event: ServerStateChangedEvent) {
        isServerRunning = ServicesStartStopUtil.checkServicesRunning(this).atLeastOneRunning()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (isServerRunning) {
            Toast.makeText(this, R.string.restartServer, Toast.LENGTH_LONG).show()
        }
        if (LoadPrefsUtil.PREF_KEY_HOSTKEY_ALGOS == key) {
            val askDiag = org.primftpd.ui.GenKeysAskDialogFragment()
            askDiag.show(supportFragmentManager, DIALOG_TAG)
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }
}

// --- Screens ---

@Composable
fun MainScreen(
    isServerRunning: Boolean,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onNavigate: (String) -> Unit,
    initialLeftVisible: Boolean = false,
    initialRightVisible: Boolean = false,
    // 权限状态模拟参数，默认为系统真实值
    fullStorageAccess: Boolean = if (LocalInspectionMode.current) false else (
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
    ),
    mediaLocationAccess: Boolean = if (LocalInspectionMode.current) false else (
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            LocalContext.current.checkSelfPermission(android.Manifest.permission.ACCESS_MEDIA_LOCATION) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
    ),
    notificationPermission: Boolean = if (LocalInspectionMode.current) false else (
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LocalContext.current.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
    )
) {
    var rightMenuVisible by remember {
        mutableStateOf(initialRightVisible)
    }
    var leftMenuVisible by remember {
        mutableStateOf(initialLeftVisible)
    }

    val scope = rememberCoroutineScope()

    // 🎯 核心优化：定义一个通用的菜单导航函数
    // 封装了：1. 关闭菜单 2. 协程延时（等待侧滑动画完成） 3. 执行跳转
    val onMenuClick: (String) -> Unit = { route ->
        rightMenuVisible = false
        leftMenuVisible = false
        scope.launch {
            delay(0) // 略小于侧滑动画时间，让跳转在动画快结束时触发
            onNavigate(route)
        }
    }

    val gearRotation by animateFloatAsState(
        targetValue = if (leftMenuVisible || rightMenuVisible) 180f else 0f,
        label = "GearRotation"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // 主内容
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MenuButton(
                    iconRes = R.drawable.gear,
                    rotation = gearRotation,
                    onClick = {
                        leftMenuVisible = true
                    }
                )
                Text(
                    text = if (isServerRunning) "Server is running" else "Server has stopped",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MenuButton(
                    iconRes = R.drawable.link,
                    rotation = gearRotation,
                    onClick = {
                        rightMenuVisible = true
                    }
                )
            }
            Spacer(modifier = Modifier.height(64.dp))
            ServerControlButton(
                isRunning = isServerRunning,
                onClick = {
                    if (isServerRunning) {
                        onStopServer()
                    } else {
                        onStartServer()
                    }
                }
            )
            Text(
                text = "Click to switch server status",
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // 🎯 新增：权限状态卡片
            Spacer(modifier = Modifier.height(32.dp))
            PermissionsCard(
                fullStorageAccess = fullStorageAccess,
                mediaLocationAccess = mediaLocationAccess,
                notificationPermission = notificationPermission
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 背景遮罩 (Scrim)
        if (rightMenuVisible || leftMenuVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember {
                            MutableInteractionSource()
                        },
                        indication = null
                    ) {
                        rightMenuVisible = false
                        leftMenuVisible = false
                    }
            )
        }

        // 右侧滑菜单
        AnimatedVisibility(
            visible = rightMenuVisible,
            enter = slideInHorizontally(initialOffsetX = {
                it
            }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = {
                it
            }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(280.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column {
                    Text(
                        text = "功能与工具",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                    Button(
                        onClick = {
                            rightMenuVisible = false
                        },
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    ) {
                        Text("关闭")
                    }
                    RowClick(
                        icon = ImageVector.vectorResource(id = R.drawable.connectsetting),
                        text = "Network status",
                        onClick = { onMenuClick("netWorkStatus") }
                    )
                    RowClick(
                        icon = ImageVector.vectorResource(id = R.drawable.outline_barcode_scanner_24),
                        text = "Scan code",
                        onClick = { onMenuClick("qr") }
                    )
                    RowClick(
                        icon = ImageVector.vectorResource(id = R.drawable.cleaner),
                        text = "Clean cache",
                        onClick = { onMenuClick("clean") }
                    )
                    RowClick(
                        icon = ImageVector.vectorResource(id = R.drawable.outline_dialogs_24),
                        text = "Client logs",
                        onClick = { onMenuClick("clientStatus") }
                    )
                    RowClick(
                        icon = ImageVector.vectorResource(id = R.drawable.outline_fingerprint_24),
                        text = "Finger print",
                        onClick = { onMenuClick("fingerPrint") }
                    )
                    RowClick(
                        icon = ImageVector.vectorResource(id = R.drawable.thinkey),
                        text = "Verification Key",
                        onClick = { onMenuClick("VerificationKey") }
                    )
                    RowClick(
                        icon = ImageVector.vectorResource(id = R.drawable.outline_info_24),
                        text = "About",
                        onClick = { onMenuClick("about") }
                    )
                }
            }
        }

        // 左侧滑菜单
        AnimatedVisibility(
            visible = leftMenuVisible,
            enter = slideInHorizontally(initialOffsetX = {
                -it
            }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = {
                -it
            }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(280.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column {
                    Text(
                        text = "设置与系统",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                    Button(
                        onClick = {
                            leftMenuVisible = false
                        },
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    ) {
                        Text("关闭")
                    }
                    RowClick(
                        icon = ImageVector.vectorResource(id = R.drawable.authentication),
                        text = "Authentication",
                        onClick = { onMenuClick("settings") }
                    )
                    RowClick(
                        icon = ImageVector.vectorResource(id = R.drawable.port),
                        text = "How to connect",
                        onClick = {
                        }
                    )
                    RowClick(
                        icon = ImageVector.vectorResource(id = R.drawable.uisetting_coarse),
                        text = "UI setting",
                        onClick = {
                        }
                    )
                    RowClick(
                        icon = ImageVector.vectorResource(id = R.drawable.system),
                        text = "System",
                        onClick = {
                        }
                    )
                }
            }
        }
    }
}

// 🎯 新增：权限状态卡片
@Composable
fun PermissionsCard(
    fullStorageAccess: Boolean,
    mediaLocationAccess: Boolean,
    notificationPermission: Boolean
) {
    val context = LocalContext.current
    val inspectionMode = LocalInspectionMode.current

    // 将传入的初始值状态化，以便监听更新
    var storageGranted by remember(fullStorageAccess) { mutableStateOf(fullStorageAccess) }
    var mediaGranted by remember(mediaLocationAccess) { mutableStateOf(mediaLocationAccess) }
    var notificationGranted by remember(notificationPermission) { mutableStateOf(notificationPermission) }

    // 监听生命周期：当从系统设置页面返回应用时（onResume），重新检查权限
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !inspectionMode) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    storageGranted = Environment.isExternalStorageManager()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    mediaGranted = context.checkSelfPermission(android.Manifest.permission.ACCESS_MEDIA_LOCATION) == 
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationGranted = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == 
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val mediaLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        mediaGranted = isGranted // 弹窗结束后立即更新状态
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        notificationGranted = isGranted // 弹窗结束后立即更新状态
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Permissions Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // Android 11+ 完整存储访问权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                PermissionItem(
                    title = "Full Storage Access",
                    hasPermission = storageGranted,
                    onClick = {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                )
            }
            
            // Android 10+ 媒体位置访问权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                PermissionItem(
                    title = "Media Location Access",
                    hasPermission = mediaGranted,
                    onClick = {
                        mediaLocationLauncher.launch(android.Manifest.permission.ACCESS_MEDIA_LOCATION)
                    }
                )
            }
            
            // Android 13+ 通知权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionItem(
                    title = "Notification Permission",
                    hasPermission = notificationGranted,
                    onClick = {
                        notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                )
            }
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    hasPermission: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = if (hasPermission) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (hasPermission) Color(0xFF4CAF50) else Color(0xFFFF9800),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (hasPermission) "Granted" else "Not granted",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        if (!hasPermission) {
            TextButton(onClick = onClick) {
                Text(
                    text = "Grant",
                    textDecoration = TextDecoration.Underline
                )
            }
        }
    }
}


/**
 * 这个玩意其实是可以用 `AndroidFragment<org.primftpd.prefs.FtpPrefsFragment>()` 代替的。摆了不想改了
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FragmentContainerScreen(
    title: String,
    fragmentFactory: () -> androidx.fragment.app.Fragment, // 传入 Fragment 的构造方法
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val fragmentManager = (context as? FragmentActivity)?.supportFragmentManager
    var hasNavigatedBack by remember { mutableStateOf(false) }


    var canLoadFragment by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(50)
        canLoadFragment = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (!hasNavigatedBack) {
                                hasNavigatedBack = true
                                onBack()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        val containerId = remember { android.view.View.generateViewId() }
        val fragmentTag = remember(containerId) { "fragment_$containerId" }

        AndroidView<FragmentContainerView>(
            factory = { ctx ->
                FragmentContainerView(ctx).apply {
                    id = containerId
                }
            },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        )

        if (canLoadFragment) {
            DisposableEffect(containerId) {
                val fragment = fragmentFactory()
                fragmentManager?.beginTransaction()
                    ?.replace(containerId, fragment, fragmentTag)
                    ?.commit()

                onDispose {
                    fragmentManager?.findFragmentByTag(fragmentTag)?.let { existingFragment ->
                        fragmentManager.beginTransaction()
                            .remove(existingFragment)
                            .commitAllowingStateLoss()
                    }
                }
            }
        }
    }
}




@OptIn(ExperimentalMaterial3Api::class)
@Composable
//can be del
fun FingerprintsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("密钥指纹")
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            onBack()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
            ) {
            Text("密钥指纹信息内容...")
        }
    }
}

// --- 密码输入弹窗 ---
@Composable
fun PasswordInputDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.prefTitlePassword),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                Text(
                    text =  stringResource(R.string.generateKeysMessage),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.prefTitlePassword)) },
                    placeholder = { Text("Please input password") },//useless?
                    visualTransformation = if (passwordVisible) 
                        VisualTransformation.None 
                    else 
                        PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible)
                                    ImageVector.vectorResource(id = R.drawable.visiable)
                                else
                                    ImageVector.vectorResource(id = R.drawable.baseline_disabled_visible_24),
                                contentDescription = if (passwordVisible) "Hide password"
                                     else "Show password"
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (password.isNotBlank()) {
                        onSave(password)
                    }
                },
                enabled = password.isNotBlank()
            ) {
                //Text(stringResource(R.string.yes)
                Text("Save and Start")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// --- Helpers ---

@Composable
fun MenuButton(iconRes: Int, rotation: Float, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable {
                onClick()
            }
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier
                .size(28.dp)
                .rotate(rotation),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
fun ServerControlButton(isRunning: Boolean, onClick: () -> Unit) {
    Button(
        onClick = {
            onClick()
        },
        modifier = Modifier.size(160.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isRunning) Color(0xFFE57373) else Color(0xFF81C784)
        )
    ) {
        Text(
            text = if (isRunning) "stop" else "start",
            style = MaterialTheme.typography.headlineSmall
        )
    }
}

@Composable
fun RowClick(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(9.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ShizukuFtpTheme(
    darkTheme: Boolean = if (LocalInspectionMode.current) false else isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

// --- Previews ---

@Preview(showBackground = true,
    name = "Main Screen",
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun MainScreenPreview() {
    ShizukuFtpTheme {
        MainScreen(
            isServerRunning = false,
            onStartServer = {
            },
            onStopServer = {
            },
            onNavigate = {
            },
            fullStorageAccess = true,
            mediaLocationAccess = true,
            notificationPermission = false
        )
    }
}

@Preview(showBackground = true, name = "Right Menu Open")
@Composable
fun LeftMenuOpenPreview() {
    ShizukuFtpTheme {
        MainScreen(
            isServerRunning = false,
            onStartServer = {
            },
            onStopServer = {
            },
            onNavigate = {
            },
            initialRightVisible = true,
            fullStorageAccess = false
        )
    }
}

/*
@Preview(showBackground = true, name = "Password Dialog")
@Composable
fun PasswordDialogPreview() {
    ShizukuFtpTheme {
        PasswordInputDialog(
            onDismiss = {},
            onSave = {}
        )
    }
}
*/