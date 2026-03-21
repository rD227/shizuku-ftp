package org.primftpd.ui

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.primftpd.R
import org.primftpd.events.ServerStateChangedEvent
import org.primftpd.log.LogController
import org.primftpd.prefs.LoadPrefsUtil
import org.primftpd.util.NotificationUtil
import org.primftpd.util.ServicesStartStopUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory

// 1. 加上 open 关键字，允许 Java 的 LeanbackActivity 继承
open class MainTabsActivity : ComponentActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        const val INDEX_MAIN = 0
        const val INDEX_FINGERPRINTS = 1
        const val INDEX_PREFS = 2
        const val INDEX_ABOUT = 3
        const val INDEX_LOG = 4
    }

    private var logger: Logger = LoggerFactory.getLogger(javaClass)
    private var isServerRunning by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        isServerRunning = ServicesStartStopUtil.checkServicesRunning(this).atLeastOneRunning()

        setContent {
            ShizukuFtpTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        isServerRunning = isServerRunning,
                        onStartServer = { handleStart() },
                        onStopServer = { handleStop() }
                    )
                }
            }
        }

        EventBus.getDefault().register(this)
        LoadPrefsUtil.getPrefs(this).registerOnSharedPreferenceChangeListener(this)
    }

    // 2. 补全 LeanbackActivity 依赖的旧方法，并加上 open 允许重写
    protected open fun createPftpdFragment(): PftpdFragment? = null
    protected open fun isLeanback(): Boolean = false

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        return super.onCreateOptionsMenu(menu)
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
        LoadPrefsUtil.getPrefs(this).unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun handleStart() {
        ServicesStartStopUtil.startServers(this)
    }

    private fun handleStop() {
        ServicesStartStopUtil.stopServers(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onEvent(event: ServerStateChangedEvent) {
        isServerRunning = ServicesStartStopUtil.checkServicesRunning(this).atLeastOneRunning()
        if (!isServerRunning) {
            NotificationUtil.removeStatusbarNotification(this)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (isServerRunning) {
            Toast.makeText(this, R.string.restartServer, Toast.LENGTH_LONG).show()
        }
        if (LoadPrefsUtil.PREF_KEY_LOGGING == key) {
            val logging = LogController.readPrefs(this)
            LogController.setActiveConfig(this, logging)
            this.logger = LoggerFactory.getLogger(javaClass)
        }
    }
}

@Composable
fun MainScreen(
    isServerRunning: Boolean,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    initialLeftVisible: Boolean = false,
    initialRightVisible: Boolean = false
) {
    var rightMenuVisible by remember { mutableStateOf(initialRightVisible) }
    var leftMenuVisible by remember { mutableStateOf(initialLeftVisible) }

    val gearRotation by animateFloatAsState(
        targetValue = if (leftMenuVisible || rightMenuVisible) 180f else 0f,
        label = "GearRotation"
    )

    Box(modifier = Modifier.fillMaxSize()) {
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
                    //New files add in \res\drawable\...
                    rotation = gearRotation,
                    onClick = { leftMenuVisible = !leftMenuVisible }
                )

                Text(
                    text = if (isServerRunning) "服务器运行中" else "服务器已停止",
                    style = MaterialTheme.typography.titleMedium
                )

                MenuButton(
                    iconRes = R.drawable.link,
                    rotation = gearRotation,
                    onClick = { rightMenuVisible = !rightMenuVisible }
                )
            }

            Spacer(modifier = Modifier.height(64.dp))

            ServerControlButton(
                isRunning = isServerRunning,
                onClick = { if (isServerRunning) onStopServer() else onStartServer() }
            )
            
            Text(
                text = "点击切换服务器状态",
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(
            visible = rightMenuVisible || leftMenuVisible,
            enter = fadeIn(),
            exit = fadeOut()
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

        AnimatedVisibility(
            visible = rightMenuVisible,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            SideMenu(
                title = "功能与工具",
                width = 280.dp,
                onClose = { rightMenuVisible = false }
            ) {//这是右边的侧滑菜单
                RowClick(icon = ImageVector.vectorResource(id = R.drawable.connectsetting),"Network status",onClick = {})
                RowClick(icon = ImageVector.vectorResource(id = R.drawable.outline_barcode_scanner_24),"Scan code",onClick = {})
                RowClick(icon = ImageVector.vectorResource(id = R.drawable.cleaner),"Clean cache",onClick = {})
                RowClick(icon = ImageVector.vectorResource(id = R.drawable.outline_dialogs_24),"Client logs",onClick = {})
                RowClick(icon = ImageVector.vectorResource(id = R.drawable.outline_fingerprint_24),"Finger print",onClick = {})
                RowClick(icon = ImageVector.vectorResource(id = R.drawable.thinkey),"Verification Key",onClick = {})
                RowClick(icon = ImageVector.vectorResource(id = R.drawable.outline_info_24),"About",onClick = {})
            }
        }

        AnimatedVisibility(
            visible = leftMenuVisible,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            SideMenu(
                title = "设置与系统",
                width = 280.dp,
                onClose = { leftMenuVisible = false }
            ) {//这是左边的侧滑菜单
                RowClick(
                    icon = ImageVector.vectorResource(id = R.drawable.authentication),
                    "Authentication",
                    onClick = {})
                RowClick(
                    icon = ImageVector.vectorResource(id = R.drawable.port),
                    "How to connect",
                    onClick = {})
                RowClick(
                    icon = ImageVector.vectorResource(id = R.drawable.uisetting_coarse),
                    "UI setting",
                    onClick = {})
                RowClick(
                    icon = ImageVector.vectorResource(id = R.drawable.system),
                    "System",
                    onClick = {})
            }
        }
    }
}

@Composable
fun MenuButton(iconRes: Int, rotation: Float, onClick: () -> Unit) {
    Box (
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ){
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
        onClick = onClick,
        modifier = Modifier.size(160.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isRunning) Color(0xFFE57373) else Color(0xFF81C784)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
    ) {
        Text(
            text = if (isRunning) "停止" else "启动",
            style = MaterialTheme.typography.headlineSmall
        )
    }
}

@Composable
fun SideMenu(
    title: String,
    width: Dp,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(width),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )
            HorizontalDivider()
            Column(modifier = Modifier.weight(1f)) {
                content()
            }
            HorizontalDivider()
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("关闭菜单")
            }
        }
    }
}

@Composable
fun RowClick(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp)
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
/*
*
*
* Preview Function
*
* */
@Preview(showBackground = true, name = "Left Menu Open")
@Composable
fun LeftMenuOpenPreview() {
    ShizukuFtpTheme {
        MainScreen(isServerRunning = false, onStartServer = {}, onStopServer = {}, initialLeftVisible = true)
    }
}



/*不要删掉这段注释
@Preview(showBackground = true, name = "Server Stopped")
@Composable
fun MainScreenStoppedPreview() {
    MaterialThem {
        MainScreen(isServerRunning = false, onStartServer = {}, onStopServer = {})
    }
}

@Preview(showBackground = true, name = "Server Running")
@Composable
fun MainScreenRunningPreview() {
    MaterialTheme {
        MainScreen(isServerRunning = true, onStartServer = {}, onStopServer = {})
    }
}
*/


/*
*
*
*
*
* */
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
