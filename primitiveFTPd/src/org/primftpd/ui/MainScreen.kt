package org.primftpd.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.RoundedCorner
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.primftpd.R
import org.primftpd.ui.data.BatteryState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- Main Screen ---

@SuppressLint("SuspiciousIndentation")
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
                LocalContext.current.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED
            } else true
            ),
    notificationPermission: Boolean = if (LocalInspectionMode.current) false else (
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                LocalContext.current.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
            } else true
            ),
    viewModel: NetworkViewModel? = if (LocalInspectionMode.current) null else viewModel(),
    onRailVisibleChange: ((Boolean) -> Unit)? = null,
) {
    var rightMenuVisible by remember { mutableStateOf(initialRightVisible) }
    var leftMenuVisible by remember { mutableStateOf(initialLeftVisible) }
    var showPermissionsDialog by remember { mutableStateOf(false) }
    //
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    //var wallpaperBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    //val wallpaperPicker = wallpaperBitmap?.let { getWallPaperPicker(imageBit = it) }
    var wallpaperBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val wallpaperPicker = rememberWallpaperPicker { wallpaperBitmap = it }

    val context = LocalContext.current

    val prefs = context.getSharedPreferences("ui_state", Context.MODE_PRIVATE)
    var resolvedRailVisible by remember { mutableStateOf(prefs.getBoolean("rail_visible", true)) }
    val resolvedOnRailVisibleChange = onRailVisibleChange ?: {
        resolvedRailVisible = it
        prefs.edit { putBoolean("rail_visible", it) }
    }

    val scope = rememberCoroutineScope()
    val hazeState = rememberHazeState()
    val batteryState = rememberBatteryState(context)
    //val chargeState = rememberBatteryCharging(context)
    LaunchedEffect(batteryState) {
        if (batteryState == null) {
            Toast.makeText(context, "Unable to read battery level", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(30_000)
        }
    }

    LaunchedEffect(Unit) {
        wallpaperBitmap = loadWallpaperBitmap(context)
    }


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
            onNavigate(route)
        }
    }

    val gearRotation by animateFloatAsState(
        targetValue = if (leftMenuVisible || rightMenuVisible) 180f else 0f,
        label = "GearRotation"
    )

    val railPadding by animateDpAsState(
        targetValue = if (resolvedRailVisible) 64.dp else 0.dp,
        label = "RailPadding"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // 打底背景：整屏模糊壁纸（hazeSource 供全屏 blur 输出），圆角外露的就是它
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
        ) {
            WallpaperBase(wallpaperBitmap = wallpaperBitmap)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeEffect(state = hazeState) {
                    inputScale = HazeInputScale.Auto
                    blurEffect {
                        blurRadius = 4.dp
                        fallbackTint = HazeColorEffect.tint(Color.Black.copy(alpha = 0.6f))
                    }
                }
        )

        // YumeBox 风格左侧窄边栏
        AnimatedVisibility(
            visible = resolvedRailVisible,
            enter = slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300)),
            exit = slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300)),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            // 透明侧栏：不传图片，直接透出根部打底模糊背景
            NarrowWallpaperRail(
                currentTime = currentTime,
                batteryState = batteryState,
                gearRotation = gearRotation,
                onGearClick = { leftMenuVisible = true },
                onLinkClick = { rightMenuVisible = true },
                onShowCardClick = { showPermissionsDialog = true },
                modifier = Modifier.fillMaxHeight()
            )
        }

        //radius of phone
        val cornerRadius = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val windowInsets = LocalView.current.rootWindowInsets
        val roundedCorner = windowInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
            roundedCorner?.radius?.let { with(LocalDensity.current) { it.toDp() } } ?: 32.dp
        } else {
            32.dp
        }


        // 主内容
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = railPadding)
                .clip(RoundedCornerShape(topStart = cornerRadius, bottomStart = cornerRadius))
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {


            Spacer(modifier = Modifier.height(16.dp))
            // 原 Spacer(weight = 0.7f) 替换为自定义图片，并做上下渐隐；长按更换图片
            MainHeroImage(
                wallpaperBitmap = wallpaperBitmap,
                modifier = Modifier
                    .weight(0.7f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = { wallpaperPicker?.launch("image/*") }
                        )
                    }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassBackgroundToggleButton(
                    enabled = resolvedRailVisible,
                    onClick = { resolvedOnRailVisibleChange(!resolvedRailVisible) }
                )
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
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 流量图表
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

            Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // 背景遮罩 (Scrim)
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

        // 右侧滑菜单
        AnimatedVisibility(
            visible = rightMenuVisible,
            enter = slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) + fadeIn(animationSpec = tween(300)),
            exit = slideOutHorizontally(
                targetOffsetX = { -it }
            ) + fadeOut(animationSpec = tween(300)),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            GlassSidebarBox(
                hazeState = hazeState,
                modifier = Modifier.width(270.dp),
                {

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
                        text = "Memory access method",
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
            )
        }

        // 左侧滑菜单
        AnimatedVisibility(
            visible = leftMenuVisible,
            enter = slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) + fadeIn(animationSpec = tween(300)),
            exit = slideOutHorizontally(
                targetOffsetX = { -it }
            ) + fadeOut(animationSpec = tween(300)),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            GlassSidebarBox(
                hazeState = hazeState,
                modifier = Modifier.width(280.dp),
                {

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
                        onClick = { onMenuClick("settings/auth") }
                    )
                    RowClick(
                        icon = iconPort,
                        text = "How to connect",
                        onClick = { onMenuClick("settings/connecting") }
                    )
                    RowClick(
                        icon = iconUi,
                        text = "UI setting",
                        onClick = { onMenuClick("settings/ui") }
                    )
                    RowClick(
                        icon = iconSystem,
                        text = "System",
                        onClick = { onMenuClick("settings/system") }
                    )
                }
            )
        }

        // 权限卡片弹窗：黑色遮罩 + 居中卡片
        PermissionsDialog(
            visible = showPermissionsDialog,
            onDismiss = { showPermissionsDialog = false },
            fullStorageAccess = fullStorageAccess,
            mediaLocationAccess = mediaLocationAccess,
            notificationPermission = notificationPermission
        )
    }
}

@Composable
private fun WallpaperBase(
    wallpaperBitmap: ImageBitmap?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        when {
            wallpaperBitmap != null -> Image(
                bitmap = wallpaperBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            !LocalInspectionMode.current -> Image(
                painter = painterResource(id = R.drawable.my_background),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            else -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF1A1A2E),
                                Color(0xFF16213E),
                                Color(0xFF0F3460)
                            )
                        )
                    )
            )
        }
        // 轻微压暗，保证前景内容可读
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.12f))
        )
    }
}

@Composable
private fun MainHeroImage(
    wallpaperBitmap: ImageBitmap?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier
        .fillMaxHeight()
        .padding(horizontal = 8.dp)) {
        if (wallpaperBitmap != null) {
            val cornerRadius = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val windowInsets = LocalView.current.rootWindowInsets
            val roundedCorner = windowInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
            roundedCorner?.radius?.let { with(LocalDensity.current) { it.toDp() } } ?: 32.dp
            } else {
                32.dp
            }
            Image(
                bitmap = wallpaperBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius))
            )
        } else if (!LocalInspectionMode.current) {
            Image(
                painter = painterResource(id = R.drawable.my_background),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    //.fillMaxHeight()
                    //.padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF1A1A2E),
                                Color(0xFF16213E),
                                Color(0xFF0F3460)
                            )
                        )
                    )
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.8f to Color.Transparent,
                        1f to MaterialTheme.colorScheme.background.copy(alpha = 1f)
                    )
                )
        )
    }
}

@Composable
private fun NarrowWallpaperRail(
    currentTime: Long,
    batteryState: BatteryState,
    gearRotation: Float,
    onGearClick: () -> Unit,
    onLinkClick: () -> Unit,
    onShowCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeText = remember(currentTime) {
        SimpleDateFormat("HH:mm", Locale.getDefault())
            .format(Date(currentTime))
    }
    // 透明侧栏
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(64.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            VerticalTimeText(timeText)
            Spacer(modifier = Modifier.height(32.dp))

            BatteryIcon(
                batteryState.percent?.toFloat()?.div(100) ?: 0f,
                isCharging = batteryState.isCharging
            )
            Text(
                text = batteryState.percent?.let { "$it%" } ?: "--%",
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodySmall,
                softWrap = false
            )

            Spacer(modifier = Modifier.weight(1f))
            MenuButton(iconRes = R.drawable.gear, rotation = gearRotation, onClick = onGearClick)
            Spacer(modifier = Modifier.height(8.dp))
            MenuButton(iconRes = R.drawable.link, rotation = gearRotation, onClick = onLinkClick)
            Spacer(modifier = Modifier.height(8.dp))
            ShowCardButton(onClick = onShowCardClick)
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}


@Composable
private fun PermissionsDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    fullStorageAccess: Boolean,
    mediaLocationAccess: Boolean,
    notificationPermission: Boolean,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 黑色遮罩，点击关闭
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() }
            )
            // 居中卡片
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 50.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                PermissionsCard(
                    fullStorageAccess = fullStorageAccess,
                    mediaLocationAccess = mediaLocationAccess,
                    notificationPermission = notificationPermission,
                    isExpanded = true,
                    onExpandedChange = { onDismiss() }
                )
            }
        }
    }
}

@Composable
private fun rememberBatteryState(context: Context): BatteryState {
    if (LocalInspectionMode.current) return BatteryState(percent = 39, isCharging = false)
    var state by remember {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        mutableStateOf(
            BatteryState(
                percent = runCatching {
                    bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it >= 0 }
                }.getOrNull(),
                isCharging = runCatching { bm?.isCharging }.getOrNull() ?: false
            )
        )
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                state = BatteryState(
                    percent = runCatching {
                        bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it >= 0 }
                    }.getOrNull(),
                    isCharging = runCatching { bm?.isCharging }.getOrNull() ?: false
                )
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    return state
}
@Composable
private fun GlassSidebarBox(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .hazeEffect(state = hazeState) {
                inputScale = HazeInputScale.Auto
                blurEffect {
                    blurRadius = 4.dp
                    fallbackTint = HazeColorEffect.tint(Color.Black.copy(alpha = 0.1f))
                }
            }
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .graphicsLayer { clip = true }
    ) {
        Column(content = content)
    }
}

private suspend fun loadWallpaperBitmap(context: Context): ImageBitmap? =
    withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("main_wallpaper", Context.MODE_PRIVATE)
        val path = prefs.getString("wallpaper_path", null)
        if (path.isNullOrBlank()) return@withContext null
        val file = File(path)
        if (!file.exists()) return@withContext null
        runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull()
    }


@Composable
fun PermissionsCard(
    fullStorageAccess: Boolean,
    mediaLocationAccess: Boolean,
    notificationPermission: Boolean,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val inspectionMode = LocalInspectionMode.current

    // 将传入的初始值状态化，以便监听更新
    var storageGranted by remember(fullStorageAccess) { mutableStateOf(fullStorageAccess) }
    var mediaGranted by remember(mediaLocationAccess) { mutableStateOf(mediaLocationAccess) }
    var notificationGranted by remember(notificationPermission) { mutableStateOf(notificationPermission) }

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
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !inspectionMode) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    storageGranted = Environment.isExternalStorageManager()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    mediaGranted = context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) ==
                            PackageManager.PERMISSION_GRANTED
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationGranted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                            PackageManager.PERMISSION_GRANTED
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
            .padding(
                start = 16.dp,
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
                shape = RoundedCornerShape(32.dp),
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
                                onExpandedChange(false)
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
                        modifier = Modifier.padding(bottom = 0.dp)
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
                                mediaLocationLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
                            }
                        )
                    }

                    // Android 13+ 通知权限
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        PermissionItem(
                            title = "Notification Permission",
                            hasPermission = notificationGranted,
                            onClick = {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        )
                    }
                }
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
    val image = AnimatedImageVector.animatedVectorResource(R.drawable.avd_anim)
    // 2. 使用 painter 驱动动画，hasPermission 改变时动画会自动触发
    val painter = rememberAnimatedVectorPainter(
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
        Box(
            modifier = Modifier.clickable(onClick = onClick,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
            ) {
                // 3. 替换原来的 Icon
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    //我应该把颜色改成不那么荧光的
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,                        // 只占一行
                        overflow = TextOverflow.Ellipsis
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
        }
        /**
        AnimatedVisibility(
            visible = !hasPermission,
            enter = scaleIn(animationSpec = telegramSpringSpec()) + fadeIn(),
            exit = scaleOut(animationSpec = spring(stiffness = Spring.StiffnessHigh)) + fadeOut(),
            modifier = Modifier.size(width = 60.dp, height = 38.dp)
            //modifier = Modifier.weight(1f)
        ) {
            TextButton(
                onClick = onClick,//contentPadding = PaddingValues(0.dp)
                //contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "Grant",
                    fontWeight = FontWeight.W800,
                    textDecoration = TextDecoration.Underline
                )
            }
        }
        **/
    }
}


/**
 * 这个玩意其实是可以用 `AndroidFragment<org.primftpd.prefs.FtpPrefsFragment>()` 代替的。摆了不想改了
 */
@OptIn(ExperimentalMaterial3Api::class)

// --- Preview ---

@Preview(
    showBackground = true,
    name = "Main Screen",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun MainScreenPreview() {
    ShizukuFtpTheme {
        MainScreen(
            isServerRunning = false,
            onStartServer = {},
            onStopServer = {},
            onNavigate = {},
            fullStorageAccess = true,
            mediaLocationAccess = true,
            notificationPermission = false,
        )
    }
}
/**@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NarrowWallpaperRailPreview() {
    ShizukuFtpTheme {
        NarrowWallpaperRail(
            currentTime = System.currentTimeMillis(),
            batteryState = BatteryState(percent = 20, isCharging = true),   // 改这里测不同电量
            gearRotation = 0f,
            onGearClick = {},
            onLinkClick = {},
            onShowCardClick = {},
        )
    }
}

**/