package org.primftpd.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect
import org.primftpd.R
import org.primftpd.ui.data.ColorBag
import org.primftpd.ui.util.WallpaperPalette
import org.primftpd.ui.util.rememberWallpaperAccentColor

// --- Shared UI components ---

// --- Helpers ---


@Composable
fun MenuButton(
    iconRes: Int,
    rotation: Float,
    onClick: () -> Unit,
    colorBag: ColorBag,
    useM3Color: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(if (useM3Color) MaterialTheme.colorScheme.secondaryContainer else colorBag.vibrant )
            .clickable { onClick() }
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier
                .size(28.dp)
                .rotate(rotation),
            colorFilter = ColorFilter.tint(if (useM3Color) MaterialTheme.colorScheme.onSecondaryContainer else colorBag.darkMuted )
        )
    }
}

@Composable
fun ServerControlButton(isRunning: Boolean, onClick: () -> Unit) {
    val buttonColor = animateColorAsState(
        targetValue = if (isRunning) Color(0xFFE57373) else Color(0xFF81C784),
        label = "serverButtonColor",
        animationSpec = telegramSpringSpec()
    ).value

    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .size(width = 220.dp, height = 45.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, buttonColor),   // 边框使用动画颜色
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = buttonColor             // 文字颜色也使用动画颜色
        )
    ) {
        Text(
            text = if (isRunning) "stop" else "start",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.offset(y = (-2).dp),
            fontSize = 20.sp
        )
        //Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun RowClick(
        icon: ImageVector,
        text: String,
        onClick: () -> Unit,
        colorBag: ColorBag
    ) {
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
                .background(if (colorBag.useM3Color) MaterialTheme.colorScheme.primary else colorBag.vibrant),
            //TODO: 这里可以给util.ColorBag加一个其他颜色，然后加到data里面，专门用于按钮的背景色，避免和vibrant混用
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (colorBag.useM3Color) MaterialTheme.colorScheme.onPrimary else colorBag.darkMuted,
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
fun BatteryIcon(
    progress: Float,
    isCharging: Boolean, // 是否在充电
    modifier: Modifier = Modifier,
    batteryColor: Color = Color.White,
    chargingColor: Color = Color.Green
) {
    Canvas(modifier = modifier.size(width = 48.dp, height = 16.dp).offset(x = (4).dp)) {
        val strokeWidth = 2.dp.toPx()
        val cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())

        // 电池头部小凸起
        val headWidth = 5.dp.toPx()
        val headHeight = 6.dp.toPx()
        val headLeft = size.width - headWidth
        val headTop = (size.height - headHeight) / 2f
        drawRoundRect(
            color = batteryColor,
            topLeft = Offset(headLeft - 4, headTop),
            size = Size(headWidth, headHeight),
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
        )

        // 电池外框
        val bodyWidth = size.width - headWidth - 4.dp.toPx()
        val bodyHeight = size.height
        drawRoundRect(
            color = batteryColor,
            topLeft = Offset(0f, 0f),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = cornerRadius,
            style = Stroke(width = strokeWidth)
        )

        // 绘制内部电量填充
        val padding = 4.dp.toPx()
        val innerMaxWidth = bodyWidth - padding * 2
        val innerMaxHeight = bodyHeight - padding * 2
        val currentFillWidth = innerMaxWidth * progress.coerceIn(0f, 1f)

        val fillColor = if (isCharging) {
            chargingColor }
        else {
                if (progress > 0.2f) batteryColor else Color(0xFFFF9800)
        }
        if (currentFillWidth > 0f) {
            drawRoundRect(
                color = fillColor,
                topLeft = Offset(padding, padding),
                size = Size(currentFillWidth, innerMaxHeight),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }
    }
}


@Composable
fun ShizukuFtpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!LocalInspectionMode.current) {
        val window = (view.context as Activity).getWindow()
        WindowCompat.getInsetsController(window, view)
            .hide(WindowInsetsCompat.Type.statusBars())
    }
    val colorScheme = when {
        dynamicColor && !LocalInspectionMode.current && Build.VERSION.SDK_INT >= 31 -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(
        colorScheme = colorScheme,
    ) {
        Surface(content = content)
    }
}

@Composable
fun VerticalTimeText(timeText: String) {
    val parts = timeText.split(":")
    val hour = parts.getOrElse(0) { "00" }
    val minute = parts.getOrElse(1) { "00" }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.Companion
    ) {
        Text(
            text = hour,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(y = (6).dp)
        )

        Text(
            text = ":",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.W900,
            fontSize = 30.sp,
            modifier = Modifier.rotate(90f)
                .offset(y = (-2).dp)
        )

        Text(
            text = minute,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(y = (-8).dp)
        )
    }
}

@Composable
internal fun GlassBackgroundToggleButton(enabled: Boolean, onClick: () -> Unit,rotation: Float,useOriginColor : Boolean = false, colorBag: ColorBag) {
    FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (useOriginColor) MaterialTheme.colorScheme.secondaryContainer else colorBag.vibrant ,
                contentColor = if (useOriginColor) MaterialTheme.colorScheme.onSecondaryContainer else colorBag.darkMuted
            )
        ) {
        Image(
            painter = painterResource(id = if (enabled) R.drawable.boldincenter else R.drawable.blodcantsee),
            contentDescription = if (enabled) "Disable glass background" else "Enable glass background",
            modifier = Modifier.size(24.dp)
                .rotate(rotation),
            colorFilter =  ColorFilter.tint( if (useOriginColor) MaterialTheme.colorScheme.onSecondaryContainer else colorBag.darkMuted)
        )
    }
}

@Composable
internal fun ShowCardButton(
    onClick: () -> Unit,
    colorBag: ColorBag,
    useM3Color: Boolean = false
) {
    OutlinedIconButton(
        onClick = onClick,
        modifier = Modifier.size(55.dp),
        border = BorderStroke(1.dp, if (useM3Color) MaterialTheme.colorScheme.outline else colorBag.darkMuted ),
        /**colors = IconButtonDefaults.outlinedIconButtonColors(
            containerColor = if (useM3Color) MaterialTheme.colorScheme.secondaryContainer else colorBag.vibrant,
            contentColor = if (useM3Color) MaterialTheme.colorScheme.onSecondaryContainer else colorBag.darkMuted
        )**/
    ) {
        Icon(
            painter = painterResource(id = R.drawable.outline_data_alert_24),
            contentDescription = "Show card",
            modifier = Modifier.size(32.dp),
            tint = if (useM3Color) MaterialTheme.colorScheme.onSecondaryContainer else colorBag.darkMuted
        )
    }
}

/** 全屏模糊 Box 与 GlassSidebarBox 共用的玻璃模糊修饰符，避免重复配置 haze 参数 */
@Composable
internal fun Modifier.glassHaze(
    hazeState: HazeState,
    blurIntensity: Float?,
    darkTint: Color = Color.Black.copy(alpha = 0.22f),
    lightTint: Color = Color.White.copy(alpha = 0.22f),
    darkFallback: Color = Color.Black.copy(alpha = 0.62f),
    lightFallback: Color = Color.White.copy(alpha = 0.82f),
): Modifier {
    //if (blurIntensity == null || blurIntensity == 0f) return this
    val isDark = isSystemInDarkTheme()
    return this.hazeEffect(state = hazeState) {
        inputScale = HazeInputScale.Fixed(0.5f)
        blurEffect {
            blurIntensity?.let { blurRadius = it.dp }
            noiseFactor = 0.06f
            colorEffects = listOf(HazeColorEffect.tint(if (isDark) darkTint else lightTint))
            fallbackTint = HazeColorEffect.tint(if (isDark) darkFallback else lightFallback)
        }
    }
}

@Composable
internal fun GlassSidebarBox(
    hazeState: HazeState,
    //modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
    blurIntensity: Float?,
    showWallpaper: Boolean = true
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .then(
                if (showWallpaper) {
                    Modifier.glassHaze(hazeState, blurIntensity)
                } else {
                    Modifier.background(MaterialTheme.colorScheme.surface)
                }
            )
            .width(270.dp)
    ) {
        Column(content = content)
    }
}

/**
@SuppressLint("LocalContextResourcesRead")
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun MenuButtonPreview(){
    ShizukuFtpTheme() {
        val imageBitmap = ImageBitmap.imageResource(id = R.drawable.my_background)
        //val accentColor = rememberWallpaperAccentColor(WallpaperPalette(bitmap = imageBitmap))
        //replaced by ColorBag data class
        val colorBag = ColorBag(
            vibrant = Color(0xFF81C784),
            darkMuted = Color(0xFFE57373),
            vibrantLight = Color(0xFFF48FB1),
            useM3Color = false
        )
        MenuButton(
            iconRes = R.drawable.link,
            rotation = 0f,
            onClick = {},
            colorBag = colorBag,
            //containerColor = accentColor,
        )
    }
}
        **/