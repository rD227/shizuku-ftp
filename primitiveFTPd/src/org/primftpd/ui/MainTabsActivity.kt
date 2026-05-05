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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.graphics.res.animatedVectorResource
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.coerceAtLeast
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
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
                            {QrFragment(pftpdFragment)},
                            {navController.popBackStack()}
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                    //Can be overwritten now
                    composable("netWorkStatus"){
                        FragmentContainerScreen(
                            "networkStatus",
                            {PftpdFragment()},
                            {navController.popBackStack()}
                            )
                    }
                    composable("clientStatus"){
                        FragmentContainerScreen(
                            "clientStatus",
                            {ClientActionFragment()},
                            {navController.popBackStack()}
                            )
                    }
                    composable("VerificationKey"){
                        FragmentContainerScreen(
                            "Verification Key",
                            {PubKeyAuthKeysFragment(true)/*what is true?*/},
                            {navController.popBackStack()}
                            )
                    }
                    composable("fingerPrint"){
                        FragmentContainerScreen(
                            "fingerPrint",
                            {KeysFingerprintsFragment()},
                            {navController.popBackStack()}
                            )
                    }
                    composable("clean"){
                        FragmentContainerScreen(
                            "cleaner",
                            {CleanSpaceFragment()},
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

            val keyPresent = keyProvider.isKeyPresent
            if (!keyPresent) {
                val askDiag = GenKeysAskDialogFragment()
                val args = Bundle().apply {
                    putBoolean(GenKeysAskDialogFragment.KEY_START_SERVER, true)
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
            val askDiag = GenKeysAskDialogFragment()
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
    ),
    viewModel: NetworkViewModel = viewModel()
) {
    var rightMenuVisible by remember {
        mutableStateOf(initialRightVisible)
    }
    var leftMenuVisible by remember {
        mutableStateOf(initialLeftVisible)
    }

    val scope = rememberCoroutineScope()

    // 预加载图标
    val iconNetwork = ImageVector.vectorResource(id = R.drawable.connectsetting)
    val iconQr = ImageVector.vectorResource(id = R.drawable.outline_barcode_scanner_24)
    val iconClean = ImageVector.vectorResource(id = R.drawable.cleaner)
    val iconLogs = ImageVector.vectorResource(id = R.drawable.outline_dialogs_24)
    val iconFingerprint = ImageVector.vectorResource(id = R.drawable.outline_fingerprint_24)
    val iconKey = ImageVector.vectorResource(id = R.drawable.thinkey)
    val iconAbout = ImageVector.vectorResource(id = R.drawable.outline_info_24)
    val iconAuth = ImageVector.vectorResource(id = R.drawable.authentication)
    val iconPort = ImageVector.vectorResource(id = R.drawable.port)
    val iconUi = ImageVector.vectorResource(id = R.drawable.uisetting_coarse)
    val iconSystem = ImageVector.vectorResource(id = R.drawable.system)


    val onMenuClick: (String) -> Unit = { route ->
        rightMenuVisible = false
        leftMenuVisible = false
        scope.launch {
            //delay(20)
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
            Spacer(modifier = Modifier.height(20.dp))
            Box(contentAlignment = Alignment.TopCenter) {
                // 1. 背景图表：先定义，使其在层级上处于底层 (Behind)
                val modelProducer = remember { CartesianChartModelProducer() }
                LaunchedEffect(Unit) {
                    modelProducer.runTransaction {
                        lineSeries {
                            series(2, 6, 4, 12, 8, 16, 10, 20)
                        }
                    }
                }

                Column {
                    Spacer(modifier = Modifier.height(28.dp))

                    NetworkTrafficChart(
                        modelProducer = viewModel.modelProducer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(bottom = 0.dp)
                            .padding(top = 12.dp)
                    )
                }

                PermissionsCard(
                    fullStorageAccess = fullStorageAccess,
                    mediaLocationAccess = mediaLocationAccess,
                    notificationPermission = notificationPermission
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 背景遮罩 (Scrim) - 添加淡入淡出动画
        AnimatedVisibility(
            visible = rightMenuVisible || leftMenuVisible,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        rightMenuVisible = false
                        leftMenuVisible = false
                    }
            )
        }

        // 右侧滑菜单 - 性能优化与动画微调
        AnimatedVisibility(
            visible = rightMenuVisible,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                //animationSpec = tween(300)
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) + fadeIn(animationSpec = tween(300)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                //animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300)),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(270.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .graphicsLayer { clip = true } // 提示系统开启硬件加速
            ) {
                Column {
                    Text(
                        text = "Function and tools",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                    Button(
                        onClick = {
                            rightMenuVisible = false
                        },
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    ) {
                        Text("Close")
                    }
                    RowClick(
                        icon = iconNetwork,
                        text = "Network status",
                        onClick = { onMenuClick("netWorkStatus") }
                    )
                    RowClick(
                        icon = iconQr,
                        text = "Scan code",
                        onClick = { onMenuClick("qr") }
                    )
                    RowClick(
                        icon = iconClean,
                        text = "Clean cache",
                        onClick = { onMenuClick("clean") }
                    )
                    RowClick(
                        icon = iconLogs,
                        text = "Client logs",
                        onClick = { onMenuClick("clientStatus") }
                    )
                    RowClick(
                        icon = iconFingerprint,
                        text = "Finger print",
                        onClick = { onMenuClick("fingerPrint") }
                    )
                    RowClick(
                        icon = iconKey,
                        text = "Verification Key",
                        onClick = { onMenuClick("VerificationKey") }
                    )
                    RowClick(
                        icon = iconAbout,
                        text = "About",
                        onClick = { onMenuClick("about") }
                    )
                }
            }
        }

        // 左侧滑菜单 - 性能优化与动画微调
        AnimatedVisibility(
            visible = leftMenuVisible,
            enter = slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) + fadeIn(animationSpec = tween(300),

                ),
            exit = slideOutHorizontally(
                targetOffsetX = { -it },
                //animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300)),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(280.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .graphicsLayer { clip = true }
            ) {
                Column {
                    Text(
                        text = "Setting and System",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                    Button(
                        onClick = {
                            leftMenuVisible = false
                        },
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    ) {
                        Text("Close")
                    }
                    RowClick(
                        icon = iconAuth,
                        text = "Authentication",
                        onClick = { onMenuClick("settings") }
                    )
                    RowClick(
                        icon = iconPort,
                        text = "How to connect",
                        onClick = {
                        }
                    )
                    RowClick(
                        icon = iconUi,
                        text = "UI setting",
                        onClick = {
                        }
                    )
                    RowClick(
                        icon = iconSystem,
                        text = "System",
                        onClick = {
                        }
                    )
                }
            }
        }
    }
}


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

    // 🎯 控制卡片展开/收起状态
    var isExpanded by remember { mutableStateOf(true) }
    // 🎯 控制是否贴边
    var shouldStickToEdge by remember { mutableStateOf(false) }


    LaunchedEffect(isExpanded) {
        if (!isExpanded) {
            delay(5000)
            shouldStickToEdge = true
        } else {
            shouldStickToEdge = false
        }
    }

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

    val animatedPadding by animateDpAsState(
        targetValue = if (!isExpanded && shouldStickToEdge) 0.dp else 16.dp,
        label = "padding",
        //animationSpec = telegramSpringSpec()
        //why add this will crash?
        //underZero number is not allowed
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp,
                    end = animatedPadding.coerceAtLeast(0.dp)
                )
    ) {
        // 主卡片 - 带动画的滑动
        AnimatedVisibility(
            visible = isExpanded,
            enter = slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = spring(
                    dampingRatio = 0.5f,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(),
            exit = slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = spring(
                    dampingRatio = 0.5f,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeOut()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                // 🎯 顶部置顶按钮
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClick = {
                                isExpanded = false
                            }
                        )
                        .padding(vertical = 0.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.outline_data_alert_24),
                        contentDescription = "Hide card",
                        modifier = Modifier.size(24.dp),
                        tint = Color(0xFF4CAF50)
                    )
                }

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp)
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

        // 🎯 右侧圆形浮动按钮 - 只在卡片隐藏时显示
        AnimatedVisibility(
            visible = !isExpanded,
            enter = scaleIn(animationSpec = spring(
                dampingRatio = 0.5f,
                stiffness = Spring.StiffnessMediumLow
            )) + fadeIn(),
            exit = scaleOut(animationSpec = spring(
                dampingRatio = 0.5f,
                stiffness = Spring.StiffnessMediumLow
            )) + fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            val buttonOffsetX by animateDpAsState(
                targetValue = if (shouldStickToEdge) 24.dp else 0.dp,
                label = "buttonOffset"
            )
            Box(
                modifier = Modifier
                    .offset(x = buttonOffsetX)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable {
                        if (shouldStickToEdge){
                            shouldStickToEdge = false
                        }else{
                            isExpanded = true
                        }
                    }
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.outline_data_alert_24),
                    contentDescription = "Show card",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        }


    }
}


//做一个流量监控的条形图，直接这样写是会在后台一直绘制，还是在进入时记录数据绘制呢？



fun <T> telegramSpringSpec() = spring<T>(
    dampingRatio = 0.5f,
    stiffness = Spring.StiffnessMediumLow //真正的 telegram动画应该是另外一个库的开源矢量图：Lottie 库
)//cpp的库，太疯狂了

@Composable
fun PermissionItem(
    title: String,
    hasPermission: Boolean,
    onClick: () -> Unit
) {
    // 1. 加载 AVD 资源
    val image = androidx.compose.animation.graphics.vector.AnimatedImageVector.animatedVectorResource(R.drawable.avd_anim)
    // 2. 使用 painter 驱动动画，hasPermission 改变时动画会自动触发
    val painter = androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter(
        animatedImageVector = image,
        atEnd = hasPermission
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // 3. 替换原来的 Icon
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                //我应该把颜色改成不那么荧光的
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )

                AnimatedContent(
                    targetState = hasPermission,
                    transitionSpec = {
                        (slideInVertically { it / 2 } + fadeIn())
                            .togetherWith(slideOutVertically { -it / 2 } + fadeOut())
                            .using(SizeTransform(clip = false))
                    },
                    label = "statusText"
                ) { targetPermission ->
                    Text(
                        text = if (targetPermission) "Granted" else "Not granted",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (targetPermission)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !hasPermission,
            enter = scaleIn(animationSpec = telegramSpringSpec()) + fadeIn(),
            exit = scaleOut(animationSpec = spring(stiffness = Spring.StiffnessHigh)) + fadeOut()
        ) {
            TextButton(onClick = onClick) {
                Text(
                    text = "Grant",
                    fontWeight = FontWeight.Bold,
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

        Box(modifier = Modifier
            .padding(padding)
            .fillMaxSize()) {
            AndroidView<FragmentContainerView>(
                factory = { ctx ->
                    FragmentContainerView(ctx).apply {
                        id = containerId
                    }
                },
                modifier = Modifier.fillMaxSize()
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
            } else {
                // 加载中占位，避免跳转瞬间的突兀感
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
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
            containerColor = animateColorAsState( targetValue = if (isRunning) Color(0xFFE57373) else Color(0xFF81C784)
                , label = "serverButtonColor",
                animationSpec = telegramSpringSpec()
            ).value
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

/*
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
*/
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
}*/
