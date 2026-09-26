package org.primftpd.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.primftpd.ui.data.ChartTriStateEnum
import kotlin.math.abs

/**
 * 流量图横向滑动时维护“手动窗口右端”的状态。
 *
 * 这个类不依赖网络数据本身：数据边界和重绘时机由调用方通过 [PanBounds] 与
 * [pan] 的回调提供。这样 ViewModel 只负责提供数据，不负责滑动节奏与偏移状态。
 */
class LineChartSlide {

    /** 手指拖动后手动锚定的窗口右端；为 null 时跟随最新数据。 */
    var windowEndOverride: Long? = null
        private set

    private var pendingPanSeconds = 0.0
    private var panJob: Job? = null

    fun reset() {
        pendingPanSeconds = 0.0
        panJob?.cancel()
        panJob = null
        windowEndOverride = null
    }

    fun pan(
        scope: CoroutineScope,
        deltaSeconds: Double,
        measuringRule: ChartTriStateEnum,
        boundsProvider: () -> PanBounds?,
        onWindowChanged: suspend () -> Unit,
    ) {
        // 目前只开放分钟滑动：D/W 窗口很大，逐帧重建数据不划算，也容易误操作。
        if (measuringRule != ChartTriStateEnum.MINUTE) return
        if (!deltaSeconds.isFinite() || deltaSeconds == 0.0) return

        pendingPanSeconds += deltaSeconds
        if (panJob?.isActive == true) return

        val maxStepSeconds = 15L
        val frameDelayMs = 16L

        panJob = scope.launch {
            while (true) {
                val pending = pendingPanSeconds
                if (pending >= 1.0 || pending <= -1.0) {
                    val bounded = pending.coerceIn(
                        -maxStepSeconds.toDouble(),
                        maxStepSeconds.toDouble(),
                    )
                    val step = bounded.toLong().let { value ->
                        if (value != 0L) value else if (pending > 0.0) 1L else -1L
                    }
                    pendingPanSeconds -= step

                    val bounds = boundsProvider()
                    if (bounds == null) {
                        pendingPanSeconds = 0.0
                        break
                    }

                    val currentEnd = windowEndOverride ?: bounds.latestEnd
                    val requestedEnd = currentEnd + step
                    val clampedEnd = requestedEnd.coerceIn(bounds.minEnd, bounds.maxEnd)
                    val nextOverride = if (clampedEnd >= bounds.latestEnd) null else clampedEnd
                    if (nextOverride != windowEndOverride) {
                        windowEndOverride = nextOverride
                        onWindowChanged()
                    }
                }

                if (abs(pendingPanSeconds) < 1.0) break
                delay(frameDelayMs)
            }
        }
    }

    /**
     * 可滑动的时间窗口边界。
     *
     * @param minEnd 窗口右端最早可以到达的位置（保证左端不早于最早样本）。
     * @param maxEnd 窗口右端最晚可以到达的位置（通常就是最新时间）。
     * @param latestEnd “跟随最新数据”时窗口右端应该所在的位置。
     */
    data class PanBounds(
        val minEnd: Long,
        val maxEnd: Long,
        val latestEnd: Long,
    )
}

/**
 * 给流量图加水平拖拽手势，并把像素偏移换算成秒。
 */
fun Modifier.lineChartSlide(
    measuringRule: ChartTriStateEnum,
    onSlideBySeconds: (Double) -> Unit,
): Modifier = pointerInput(measuringRule) {
    detectHorizontalDragGestures { change, dragAmount ->
        change.consume()
        val chartWidth = size.width.coerceAtLeast(1)
        val secondsPerPixel = measuringRule.windowSeconds.toDouble() / chartWidth.toDouble()
        onSlideBySeconds(-dragAmount.toDouble() * secondsPerPixel)
    }
}
