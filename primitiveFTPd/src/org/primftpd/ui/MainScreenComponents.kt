package org.primftpd.ui

import android.app.Activity
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.primftpd.R

// --- Shared UI components ---

// --- Helpers ---

@Composable
fun MenuButton(iconRes: Int, rotation: Float, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
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
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSecondaryContainer)
        )
    }
}

@Composable
fun ServerControlButton(isRunning: Boolean, onClick: () -> Unit) {
    // 先获取动画颜色
    val buttonColor = animateColorAsState(
        targetValue = if (isRunning) Color(0xFFE57373) else Color(0xFF81C784),
        label = "serverButtonColor",
        animationSpec = telegramSpringSpec()
    ).value

    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .size(width = 220.dp, height = 45.dp),  // 去掉 alpha
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
    val view = LocalView.current
    val window = (view.context as Activity).getWindow()
    WindowCompat.getInsetsController(window, view)
        .hide(WindowInsetsCompat.Type.statusBars())
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
        modifier = Modifier
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
internal fun GlassBackgroundToggleButton(enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(
            painter = painterResource(id = if (enabled) R.drawable.visiable else R.drawable.baseline_disabled_visible_24),
            contentDescription = if (enabled) "Disable glass background" else "Enable glass background",
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
internal fun ShowCardButton(onClick: () -> Unit) {
    OutlinedIconButton(
        onClick = onClick,
        modifier = Modifier.size(55.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.outline_data_alert_24),
            contentDescription = "Show card",
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.secondaryContainer
        )
    }
}


@Preview(showBackground = true)
@Composable
fun startButtonPreview(){
    ShizukuFtpTheme() {
        ServerControlButton(isRunning = false, onClick = {})
    }
}