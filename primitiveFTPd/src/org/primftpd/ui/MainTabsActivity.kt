package org.primftpd.ui

//import androidx.compose.material.icons.filled.ArrowBack
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.primftpd.R
import org.primftpd.events.ServerStateChangedEvent
import org.primftpd.prefs.LoadPrefsUtil
import org.primftpd.util.ServicesStartStopUtil

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
    private lateinit var pftpdFragment: PftpdFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pftpdFragment = createPftpdFragment()
        enableEdgeToEdge()
        isServerRunning = ServicesStartStopUtil.checkServicesRunning(this).atLeastOneRunning()

        setContent {
            ShizukuFtpTheme {
                val navController = rememberNavController()
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
                    /*
                    composable("fingerprints") {
                        FingerprintsScreen(//can be del
                            onBack = {
                                navController.popBackStack()
                            }
                        )
                    }
                     */
                    composable("qr") {
                        /*
                        QrScreen(
                            onBack = {
                                navController.popBackStack()
                            }
                        )*/
                        FragmentContainerScreen(
                            "扫码了",
                            {org.primftpd.ui.QrFragment()},
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
                    composable("fingerPrint"){
                        FragmentContainerScreen(
                            "fingerPrint",
                            {org.primftpd.ui.PubKeyAuthKeysFragment(true)},
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
        // demo 😄
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
    initialRightVisible: Boolean = false
) {
    var rightMenuVisible by remember {
        mutableStateOf(initialRightVisible)
    }
    var leftMenuVisible by remember {
        mutableStateOf(initialLeftVisible)
    }

    val gearRotation by animateFloatAsState(
        targetValue = if (leftMenuVisible || rightMenuVisible) 180f else 0f,
        label = "GearRotation"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // 主内容
        Column(
            modifier = Modifier.fillMaxSize(),
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
                        onClick = {
                            rightMenuVisible = false
                            onNavigate("netWorkStatus")
                        }
                    )//&&
                    RowClick(
                        icon = ImageVector.vectorResource(id = R.drawable.outline_barcode_scanner_24),
                        text = "Scan code",
                        onClick = {
                            rightMenuVisible = false
                            onNavigate("qr")
                        }
                    )
                    RowClick(
                        icon = ImageVector.vectorResource(id = R.drawable.cleaner),
                        text = "Clean cache",
                        onClick = {
                            rightMenuVisible = false
                            onNavigate("clean")
                        }
                    )
                    RowClick(
                        icon = ImageVector.vectorResource(id = R.drawable.outline_dialogs_24),
                        text = "Client logs",
                        onClick = {
                            rightMenuVisible = false
                            onNavigate("clientStatus")
                        }
                    )
                    RowClick(
                        icon = ImageVector.vectorResource(id = R.drawable.outline_fingerprint_24),
                        text = "Finger print",
                        onClick = {
                            rightMenuVisible = false
                            //onNavigate("fingerprints")
                            onNavigate("fingerPrint")
                        }
                    )
                    RowClick(
                        icon = ImageVector.vectorResource(id = R.drawable.thinkey),
                        text = "Verification Key",
                        onClick = {
                        }
                    )
                    RowClick(
                        icon = ImageVector.vectorResource(id = R.drawable.outline_info_24),
                        text = "About",
                        onClick = {
                            rightMenuVisible = false
                            onNavigate("about")
                        }
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
                        onClick = {
                            leftMenuVisible = false
                            onNavigate("settings")
                        }
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

        DisposableEffect(containerId) {
            val fragment = fragmentFactory() // 在这里调用传入的构造器
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



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("关于")
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
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Primitive FTPd",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "2026.04.04",
                style = MaterialTheme.typography.bodyMedium
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp)
            )
            Text(text = "这是全屏关于页内容。")
            /*
            // show other links
            (view.findViewById<View?>(R.id.githubLabel) as TextView).setText("GitHub")
            (view.findViewById<View?>(R.id.githubTextView) as TextView).setText(AboutFragment.URL_GITHUB)

            (view.findViewById<View?>(R.id.fdroidLabel) as TextView).setText("F-Droid")
            (view.findViewById<View?>(R.id.fdroidTextView) as TextView).setText(AboutFragment.URL_FDROID)

            (view.findViewById<View?>(R.id.minaTextView) as TextView).setText(AboutFragment.URL_MINA)
            (view.findViewById<View?>(R.id.bouncyCastleTextView) as TextView).setText(AboutFragment.URL_BC)
            (view.findViewById<View?>(R.id.slf4jTextView) as TextView).setText(AboutFragment.URL_SLF4J)
            (view.findViewById<View?>(R.id.filepickerTextView) as TextView).setText(AboutFragment.URL_FILEPICKER)
            (view.findViewById<View?>(R.id.libsuperuserTextView) as TextView).setText(AboutFragment.URL_LIBSUPERUSER)
            (view.findViewById<View?>(R.id.eventbusTextView) as TextView).setText(AboutFragment.URL_EVENTBUS)

             */
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
    darkTheme: Boolean = isSystemInDarkTheme(),
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
    //uiMode = Configuration.UI_MODE_NIGHT_YES
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
            }
        )
    }
}

@Preview(showBackground = true, name = "Left Menu Open")
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
            initialLeftVisible = true
        )
    }
}

@Preview(showBackground = true, name = "About Screen")
@Composable
fun AboutScreenPreview() {
    ShizukuFtpTheme {
        AboutScreen(
            onBack = {
            }
        )
    }
}
