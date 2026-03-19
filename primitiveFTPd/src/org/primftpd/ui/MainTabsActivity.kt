package org.primftpd.ui

import android.content.SharedPreferences
import android.os.Bundle
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

class MainTabsActivity : ComponentActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var logger: Logger = LoggerFactory.getLogger(javaClass)

    // 状态：服务器是否正在运行（用于 UI 同步）
    private var isServerRunning by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        
        // 初始检查服务器状态
        isServerRunning = ServicesStartStopUtil.checkServicesRunning(this).atLeastOneRunning()

        setContent {
            // 这里建议使用你项目的主题，如果没有就先用 MaterialTheme
            MaterialTheme {
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

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
        LoadPrefsUtil.getPrefs(this).unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun handleStart() {
        // 注意：这里原本需要一个 Fragment 引用，后续可能需要重构 ServicesStartStopUtil
        // 暂时简单调用
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
            // 1. 主内容区域
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 顶部状态栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 左侧齿轮按钮 (设置)
                    MenuButton(
                        iconRes = R.drawable.gear,
                        rotation = gearRotation,
                        onClick = { leftMenuVisible = !leftMenuVisible }
                    )

                    Text(
                        text = if (isServerRunning) "服务器运行中" else "服务器已停止",
                        style = MaterialTheme.typography.titleMedium
                    )

                    // 右侧链接按钮 (工具)
                    MenuButton(
                        iconRes = R.drawable.link,
                        rotation = gearRotation,
                        onClick = { rightMenuVisible = !rightMenuVisible }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // 核心控制按钮 (代替了旧版的菜单栏 Start/Stop)
                ServerControlButton(
                    isRunning = isServerRunning,
                    onClick = { if (isServerRunning) onStopServer() else onStartServer() }
                )
                
                Text(
                    text = "点击切换服务器状态",
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // 2. 背景遮罩
            if (rightMenuVisible || leftMenuVisible) {
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

            // 3. 右侧滑菜单 (工具类)
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
                ) {
                    // 这里映射你之前的 Fragment 逻辑
                    RowClick(icon = ImageVector.vectorResource(id = R.drawable.refresh), "网络状态", onClick = {})
                    RowClick(icon = ImageVector.vectorResource(id = R.drawable.outline_cloud_download_24), "扫码连接", onClick = {})
                    RowClick(icon = ImageVector.vectorResource(id = R.drawable.refresh), "清理空间", onClick = {})
                }
            }

            // 4. 左侧滑菜单 (设置类)
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
                ) {
                    RowClick(icon = ImageVector.vectorResource(id = R.drawable.refresh), "身份验证", onClick = {})
                    RowClick(icon = ImageVector.vectorResource(id = R.drawable.refresh), "连接方式", onClick = {})
                    RowClick(icon = ImageVector.vectorResource(id = R.drawable.refresh), "系统设置", onClick = {})
                }
            }
        }
    }

    @Composable
    fun MenuButton(iconRes: Int, rotation: Float, onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .rotate(rotation),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer)
            )
        }
    }

    @Composable
    fun ServerControlButton(isRunning: Boolean, onClick: () -> Unit) {
        Button(
            onClick = onClick,
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) Color(0xFFE57373) else Color(0xFF81C784)
            )
        ) {
            Text(if (isRunning) "停止" else "启动")
        }
    }

    @Composable
    fun SideMenu(
        title: String,
        //？？？？
        width: Dp,
        onClose: () -> Unit,
        content: @Composable ColumnScope.() -> Unit
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(width)
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(16.dp)
                )
                Divider()
                content()
                Spacer(modifier = Modifier.weight(1f))
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
