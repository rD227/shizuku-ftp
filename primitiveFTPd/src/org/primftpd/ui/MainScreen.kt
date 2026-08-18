package org.primftpd.ui

import android.content.res.Configuration
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Build
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.primftpd.R

// --- Main Screen ---

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
    viewModel: NetworkViewModel? = if (LocalInspectionMode.current) null else viewModel()
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

            // >>> 新增：权限状态卡片
            Spacer(modifier = Modifier.height(20.dp))
            Box(contentAlignment = Alignment.TopCenter) {
                // 背景图表放在权限卡片下层，数据由 NetworkViewModel 提供。
                if (viewModel != null) {
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
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onBackground
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
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onBackground
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

    // >>> 控制卡片展开/收起状态
    var isExpanded by remember { mutableStateOf(true) }
    // >>> 控制是否贴边
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
                // >>> 顶部置顶按钮
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

        // >>> 右侧圆形浮动按钮 - 只在卡片隐藏时显示
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


//做一个流量监控的条形图

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

// --- Preview ---

@Preview(showBackground = true,
    name = "Main Screen",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
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
            notificationPermission = false,
            //initialLeftVisible = true
        )
    }
}

