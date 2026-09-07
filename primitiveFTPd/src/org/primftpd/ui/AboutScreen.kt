package org.primftpd.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import org.primftpd.R
import org.primftpd.ui.ShizukuFtpTheme
import org.primftpd.ui.data.ColorBag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    colorBag: ColorBag
) {
    val context = LocalContext.current
    var hasNavigatedBack by remember { mutableStateOf(false) }

    val colorBag = colorBag
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("About", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!hasNavigatedBack) {
                            hasNavigatedBack = true
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            // App name and version
            Text(
                text = "Primitive FTPd",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Row {
                Text(
                    text = getVersionInfo(context),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Text("check updates",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable {
                            if( getVersionInfo(context) != "v1.0.0" )
                                openUrl(context, "https://github.com/rD227/shizuku-ftp/releases")
                        }
                )
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            
            // License
            SectionTitle(stringResource(R.string.licence))
            ClickableLink(
                label = "General Public License v3.0",
                url = "https://www.gnu.org/licenses/gpl-3.0.html",
                context = context,
                colorBag = colorBag
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Project links
            SectionTitle("   \uD83D\uDCC2")

            LinkItem("GitHub", "https://github.com/rD227/shizuku-ftp", context, colorBag)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Dependencies
            SectionTitle("Libraries")
            LinkItem("Compose-Material3", "https://developer.android.com/jetpack/androidx/releases/compose-material3", context,colorBag)
            LinkItem("Upstream GitHub Project", "https://github.com/wolpi/prim-ftpd", context,colorBag)
            LinkItem("AndroidX", "https://developer.android.com/jetpack", context,colorBag)
            LinkItem("Apache MINA", "https://mina.apache.org", context,colorBag)
            LinkItem("Bouncy Castle", "https://bouncycastle.org/", context,colorBag)
            LinkItem("SLF4J", "https://www.slf4j.org/", context,colorBag)
            LinkItem("NoNonsense-FilePicker", "https://github.com/spacecowboy/NoNonsense-FilePicker", context,colorBag)
            LinkItem("libsuperuser", "https://su.chainfire.eu/", context,colorBag)
            LinkItem("EventBus", "https://github.com/greenrobot/EventBus", context,colorBag)
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun LinkItem(
        label: String,
        url: String,
        context: Context,
        colorBag: ColorBag
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        ClickableLink(
            label = url,
            url = url,
            context = context,
            colorBag = colorBag
        )
    }
}

@Composable
private fun ClickableLink(
    label: String,
    url: String,
    context: Context,
    colorBag: ColorBag
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = if (colorBag.useM3Color) MaterialTheme.colorScheme.primary else if (isSystemInDarkTheme()) colorBag.lightMuted else colorBag.darkMuted,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier
            .clickable {
                openUrl(context, url)
            }
            .padding(vertical = 2.dp)
    )
}

private fun openUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun getVersionInfo(context: Context): String {
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        "${packageInfo.versionName} (code: $versionCode)"
    } catch (e: PackageManager.NameNotFoundException) {
        "unknown"
    }
}

@Preview(showBackground = true, name = "About Screen", locale = "zh", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun AboutScreenPreview() {
    ShizukuFtpTheme {
        AboutScreen(
            onBack = {},
            colorBag = ColorBag(
                vibrant = Color(0xFF6200EE),
                darkMuted = Color(0xFF3700B3),
                lightMuted = Color(0xFFBB86FC),
                muted = Color(0xFF03DAC5),
                useM3Color = false
            )
        )
    }
}
