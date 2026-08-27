package org.primftpd.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import org.primftpd.R

@Composable
internal fun LinkSideMenu(
    rightMenuVisible: Boolean,
    onMenuClick: (String) -> Unit,
    hazeState: HazeState,
    onClick: () -> Unit,
    blurIntensity: Int?,
    ){

    val iconNetwork = ImageVector.vectorResource(id = R.drawable.connectsetting)
    val iconQr = ImageVector.vectorResource(id = R.drawable.outline_barcode_scanner_24)
    val iconClean = ImageVector.vectorResource(id = R.drawable.cleaner)
    val iconLogs = ImageVector.vectorResource(id = R.drawable.outline_dialogs_24)
    val iconFingerprint = ImageVector.vectorResource(id = R.drawable.outline_fingerprint_24)
    val iconKey = ImageVector.vectorResource(id = R.drawable.thinkey)
    val iconAbout = ImageVector.vectorResource(id = R.drawable.outline_info_24)

    Box {
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
                blurIntensity = blurIntensity,
                modifier = Modifier.width(270.dp),
                content = {

                    Text(
                        text = "Function and tools",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Button(
                        onClick = onClick,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    ) {
                        Text("Close")
                    }
                    RowClick(
                        icon = iconNetwork,
                        text = "Devices access",
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
    }
}
@Composable
internal fun GearSideMenu(
        leftMenuVisible : Boolean,
        onMenuClick: (String) -> Unit,
        hazeState: HazeState,
        onClick: () -> Unit,
        blurIntensity: Int?,
){

    val iconAuth = ImageVector.vectorResource(id = R.drawable.authentication)
    val iconPort = ImageVector.vectorResource(id = R.drawable.port)
    val iconUi = ImageVector.vectorResource(id = R.drawable.uisetting_coarse)
    val iconSystem = ImageVector.vectorResource(id = R.drawable.system)

    Box {
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
                blurIntensity = blurIntensity,
                content = {

                    Text(
                        text = "Setting and System",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Button(
                        onClick = onClick,
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
    }
}