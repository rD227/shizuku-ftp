package org.primftpd.ui

import android.app.Application
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import org.primftpd.R

private const val GUIDE_DONE = "home_guide_v1_done"

internal class StartupViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("startup_experience", Context.MODE_PRIVATE)
    var guideVisible by mutableStateOf(!prefs.getBoolean(GUIDE_DONE, false))
        private set
    var page by mutableIntStateOf(0)

    fun finishGuide() {
        prefs.edit { putBoolean(GUIDE_DONE, true) }
        guideVisible = false
    }

}

@Composable
internal fun StartupExperience(state: StartupViewModel, onReady: () -> Unit = {}) {
    // The check starts only after the guide's exit transition has finished.
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = state.guideVisible
    LaunchedEffect(state.guideVisible, visibility.isIdle) {
        if (!state.guideVisible && visibility.isIdle) onReady()
    }
    if (visibility.currentState || visibility.targetState) {
        Dialog(onDismissRequest = state::finishGuide) {
            AnimatedVisibility(
                visibleState = visibility,
                enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.94f),
                exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.96f),
            ) {
                Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp) {
                    Column(Modifier.fillMaxWidth().padding(24.dp)) {
                        Text(stringResource(R.string.guide_welcome), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(16.dp))
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                            AnimatedContent(
                                targetState = state.page,
                                transitionSpec = {
                                    val direction = if (targetState > initialState) 1 else -1
                                    (slideInHorizontally(tween(320)) { direction * it / 3 } + fadeIn()) togetherWith
                                        (slideOutHorizontally(tween(320)) { -direction * it / 3 } + fadeOut())
                                }, label = "guidePage",
                            ) { page ->
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    GuideIllustration(page)
                                    Text(stringResource(guideTitles[page]), style = MaterialTheme.typography.titleMedium)
                                    Text(stringResource(guideDescriptions[page]), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
                            repeat(4) { index ->
                                Box(Modifier.padding(horizontal = 3.dp).size(if (index == state.page) 9.dp else 7.dp)
                                    .background(if (index == state.page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape))
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = state::finishGuide) { Text(stringResource(R.string.guide_skip)) }
                            if (state.page > 0) TextButton(onClick = { state.page-- }) { Text(stringResource(R.string.guide_back)) }
                            Button(onClick = { if (state.page == 3) state.finishGuide() else state.page++ }) {
                                Text(stringResource(if (state.page == 3) R.string.guide_done else R.string.guide_next))
                            }
                        }
                    }
                }
            }
            BackHandler { if (state.page > 0) state.page-- else state.finishGuide() }
        }
    }
}

private val guideTitles = listOf(R.string.guide_wallpaper_title, R.string.guide_settings_title, R.string.guide_connections_title, R.string.guide_permissions_title)
private val guideDescriptions = listOf(R.string.guide_wallpaper_body, R.string.guide_settings_body, R.string.guide_connections_body, R.string.guide_permissions_body)

/** Original miniature of this app's home screen, with the actual rail icons. */
@Composable
private fun GuideIllustration(page: Int) {
    val colors = MaterialTheme.colorScheme
    val pulse by rememberInfiniteTransition(label = "guidePulse").animateFloat(
        initialValue = 1f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "targetPulse",
    )
    Row(Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(22.dp)).background(colors.surfaceContainerHighest).padding(12.dp)) {
        Column(Modifier.width(48.dp).fillMaxHeight(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            listOf(R.drawable.gear, R.drawable.link, R.drawable.outline_data_alert_24).forEachIndexed { index, icon ->
                Box(Modifier.padding(vertical = 5.dp).size(40.dp).scale(if (page == index + 1) pulse else 1f)
                    .background(if (page == index + 1) colors.primaryContainer else colors.surface, CircleShape)
                    .border(if (page == index + 1) 2.dp else 0.dp, if (page == index + 1) colors.primary else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(icon), null, Modifier.size(24.dp), tint = colors.onSurface)
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(colors.primaryContainer, colors.tertiaryContainer)))) {
                if (page == 0) Box(Modifier.align(Alignment.TopEnd).padding(14.dp).size(40.dp).scale(pulse)
                    .border(2.dp, colors.primary, CircleShape).background(colors.primary.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                    Text("●", color = colors.primary)
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(32.dp).border(1.dp, colors.primary, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Text("shizukuFTP", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
