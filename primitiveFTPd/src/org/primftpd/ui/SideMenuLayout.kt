package org.primftpd.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.primftpd.ui.data.ColorBag
import org.primftpd.ui.data.SideMenuPref

/** 根据当前窗口宽度切换布局，平板分屏缩窄时也能保留正在查看的详情。 */
@Composable
internal fun SideMenuLayout(
    state: SideMenuState,
    railPadding: Dp,
    backdrop: GlassBackdrop,
    colorBag: ColorBag,
    sideMenuPref: SideMenuPref,
    onNavigate: (String) -> Unit,
    mainContent: @Composable (startPadding: Dp) -> Unit,
    detailContent: @Composable (route: String, expanded: Boolean, onBack: () -> Unit) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 600.dp
        val mainStartPadding by animateDpAsState(
            targetValue = if (expanded && state.isOpen) GlassSideMenuWidth else railPadding,
            animationSpec = if (state.isOpen && sideMenuPref.springAnimation) {
                spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
            } else tween(300),
            label = "sideMenuContentPadding",
        )
        mainContent(mainStartPadding)

        // 遮罩只压暗主屏幕，位于两栏下方，详情打开后不会覆盖其内容或拦截操作。
        AnimatedVisibility(
            visible = state.isOpen,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300)),
        ) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = state::close,
                    )
            )
        }

        Box(
            Modifier.fillMaxSize()
                .padding(start = if (expanded) GlassSideMenuWidth else 0.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
        ) {
            AnimatedContent(
                targetState = state.detailRoute,
                transitionSpec = {
                    val entering = slideInHorizontally(
                        animationSpec = if (sideMenuPref.springAnimation) {
                            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
                        } else tween(300),
                        initialOffsetX = { it },
                    ) + fadeIn(tween(300))
                    val exiting = slideOutHorizontally(
                        animationSpec = tween(300),
                        targetOffsetX = { if (targetState == null) it else -it / 4 },
                    ) + fadeOut(tween(300))
                    (entering togetherWith exiting).using(SizeTransform(clip = false))
                },
                modifier = Modifier.fillMaxSize(),
                label = "sideMenuDetail",
            ) { route ->
                if (route != null) {
                    Box(
                        Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            // 空白处的触摸也属于详情页，不能穿透到底下的关闭遮罩。
                            .pointerInput(Unit) { detectTapGestures {} }
                    ) {
                        detailContent(route, expanded, state::dismissDetail)
                    }
                }
            }
        }

        val onMenuClick: (String) -> Unit = { route ->
            if (expanded) {
                state.showDetail(route)
            } else {
                state.close()
                onNavigate(route)
            }
        }
        val showMenu = expanded || state.detailRoute == null
        LinkSideMenu(
            rightMenuVisible = state.menu == SideMenu.TOOLS && showMenu,
            onMenuClick = onMenuClick,
            backdrop = backdrop,
            onClick = state::close,
            colorBag = colorBag,
            sideMenuPref = sideMenuPref,
            selectedRoute = state.detailRoute,
        )
        GearSideMenu(
            leftMenuVisible = state.menu == SideMenu.SETTINGS && showMenu,
            onMenuClick = onMenuClick,
            backdrop = backdrop,
            onClick = state::close,
            colorBag = colorBag,
            sideMenuPref = sideMenuPref,
            selectedRoute = state.detailRoute,
        )
    }
    BackHandler(enabled = state.isOpen, onBack = state::back)
}
