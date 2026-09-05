package org.primftpd.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.primftpd.events.DataTransferredEvent
import org.primftpd.ui.TrafficChartClearEvent
import org.primftpd.ui.TrafficChartStore
import org.primftpd.ui.data.ChartTriStateEnum
import org.primftpd.ui.data.TrafficChartSample
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong

/**
 * Owns the traffic chart data shown on the main screen.
 *
 * Data is accumulated in per-second buckets and kept for [org.primftpd.ui.TrafficChartStore.Companion.MAX_AGE_SECONDS]
 * (about three days). The x-axis follows the selected measuring rule (MINUTE/HOUR/DAY/WEEK); if the stored
 * history is shorter than the selected span, it is pinned to the left edge and the unmeasured
 * right-hand side is drawn as y = 0.
 * The renderer downsamples to [MAX_RENDER_POINTS] points only for drawing; the persisted history
 * keeps the original per-second resolution.
 */
class NetworkViewModel(application: Application) : AndroidViewModel(application) {

    val modelProducer = CartesianChartModelProducer()

    private val trafficChartStore = TrafficChartStore.Companion.getInstance(application)

    private val ftpBytesInLastSecond = AtomicLong(0L)
    private val sftpBytesInLastSecond = AtomicLong(0L)

    private val samples = mutableListOf<TrafficChartSample>()

    private var chartMeasuringRule = ChartTriStateEnum.HOUR

    private var chartAnimationJob: Job? = null
    private var chartAnimationInProgress = false



    private var lastFtpEventBytes = 0L
    private var lastSftpEventBytes = 0L
    private var lastStorePruneTimestampSeconds = 0L

    /**
     * Incremented whenever the chart history is cleared. Update ticks compare this value before
     * and after publishing to avoid showing a pre-clear model after the cleaner button is used.
     */
    private var historyVersion = 0L

    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        logger.debug(">>> NetworkViewModel created, registering EventBus")
        EventBus.getDefault().register(this)
        viewModelScope.launch {
            val historyVersionAtLoadStart = historyVersion
            val stored = withContext(Dispatchers.IO) {
                trafficChartStore.load(TrafficChartStore.Companion.MAX_AGE_SECONDS)
            }
            if (historyVersion == historyVersionAtLoadStart) {
                samples.addAll(stored)
                val nowSeconds = currentTimestampSeconds()
                lastStorePruneTimestampSeconds = nowSeconds
                if (stored.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        trafficChartStore.prune(nowSeconds - TrafficChartStore.Companion.MAX_AGE_SECONDS)
                    }
                }
            }
            publishChart()

            while (isActive) {
                delay(1000)
                updateChart()
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onDataTransferred(event: DataTransferredEvent) {
        val currentTotal = event.bytes

        if (event.isSftp) {
            val delta = if (currentTotal > lastSftpEventBytes) {
                currentTotal - lastSftpEventBytes
            } else {
                currentTotal
            }
            lastSftpEventBytes = currentTotal
            sftpBytesInLastSecond.addAndGet(delta)
            logger.debug(">>> SFTP event: delta={}B, sftpBytesInLastSecond={}B", delta, sftpBytesInLastSecond.get())
        } else {
            val delta = if (currentTotal > lastFtpEventBytes) {
                currentTotal - lastFtpEventBytes
            } else {
                currentTotal
            }
            lastFtpEventBytes = currentTotal
            ftpBytesInLastSecond.addAndGet(delta)
            logger.debug(">>> FTP event: delta={}B, ftpBytesInLastSecond={}B", delta, ftpBytesInLastSecond.get())
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onTrafficChartClear(event: TrafficChartClearEvent) {
        logger.debug(">>> Traffic-chart history cleared")
        samples.clear()
        historyVersion++
        viewModelScope.launch {
            publishChart()
        }
    }

    fun setChartMeasuringRule(rule: ChartTriStateEnum) {
        if (chartMeasuringRule == rule) return

        val nowSeconds = currentTimestampSeconds()
        val (fromStart, fromEnd) = targetDomain(chartMeasuringRule, nowSeconds)
        chartMeasuringRule = rule
        val (toStart, toEnd) = targetDomain(rule, nowSeconds)

        logger.debug(">>> Chart measuring rule changed to {}", rule)

        chartAnimationJob?.cancel()
        chartAnimationInProgress = false
        chartAnimationJob = viewModelScope.launch {
            animateChartWindow(fromStart, fromEnd, toStart, toEnd)
        }
    }

    private fun targetDomain(
        rule: ChartTriStateEnum,
        nowSeconds: Long,
    ): Pair<Long, Long> {
        val span = rule.windowSeconds
        if (samples.isEmpty()) {
            val end = nowSeconds
            return (end - span) to end
        }

        val earliest = samples.first().timestampSeconds
        val newest = samples.last().timestampSeconds.coerceAtLeast(nowSeconds)

        return if (newest - earliest < span) {
            earliest to (earliest + span)
        } else {
            (newest - span) to newest
        }
    }

    private suspend fun animateChartWindow(
        fromStart: Long,
        fromEnd: Long,
        toStart: Long,
        toEnd: Long,
    ) {
        chartAnimationInProgress = true
        val durationMs = 420L

        // 跨度很大的切换（例如 HOUR -> DAY）如果每 16ms 重绘一次，会因每帧数据量
        // 太大而显得卡顿。这里对大跨度适当拉大帧间隔，减少中间重绘次数。
        val startDelta = if (toStart > fromStart) toStart - fromStart else fromStart - toStart
        val endDelta = if (toEnd > fromEnd) toEnd - fromEnd else fromEnd - toEnd
        val maxDelta = maxOf(startDelta, endDelta)
        val stepMs = if (maxDelta > 2L * 60L * 60L) 48L else 16L

        var elapsedMs = 0L

        while (elapsedMs < durationMs) {
            elapsedMs = (elapsedMs + stepMs).coerceAtMost(durationMs)
            val progress = elapsedMs.toFloat() / durationMs.toFloat()
            // easeOutCubic：开始快、结束慢，压缩/展开会更自然。
            val eased = 1f - (1f - progress) * (1f - progress) * (1f - progress)

            val animatedStart = fromStart + ((toStart - fromStart) * eased).toLong()
            val animatedEnd = fromEnd + ((toEnd - fromEnd) * eased).toLong()

            publishChart(startOverride = animatedStart, endOverride = animatedEnd)
            delay(stepMs)
        }

        publishChart()
        chartAnimationInProgress = false
    }



    private suspend fun updateChart() {
        val versionAtStart = historyVersion
        val nowSeconds = currentTimestampSeconds()

        val ftpBytesThisSecond = ftpBytesInLastSecond.getAndSet(0L)
        val sftpBytesThisSecond = sftpBytesInLastSecond.getAndSet(0L)
        val sample = TrafficChartSample(
            timestampSeconds = nowSeconds,
            ftpBytesPerSecond = ftpBytesThisSecond,
            sftpBytesPerSecond = sftpBytesThisSecond,
        )

        logger.debug(
            ">>> Traffic update: ftp={}KB/s, sftp={}KB/s",
            sample.ftpBytesPerSecond / 1024L,
            sample.sftpBytesPerSecond / 1024L,
        )

        upsertSample(sample)
        pruneOldSamples(nowSeconds)
        publishChart()

        if (historyVersion == versionAtStart) {
            persistSample(sample)
        } else {
            // The cleaner button was pressed while the chart update was in flight. Drop the
            // pre-clear second and repaint the now-empty (or freshly restarted) history.
            publishChart()
        }
    }

    private fun upsertSample(sample: TrafficChartSample) {
        val index = samples.binarySearchBy(sample.timestampSeconds) { it.timestampSeconds }
        if (index >= 0) {
            samples[index] = sample
        } else {
            samples.add(-index - 1, sample)
        }
    }

    private fun pruneOldSamples(nowSeconds: Long) {
        val cutoff = nowSeconds - TrafficChartStore.Companion.MAX_AGE_SECONDS
        while (samples.isNotEmpty() && samples.first().timestampSeconds < cutoff) {
            samples.removeAt(0)
        }
    }

    private fun samplesForChartWindow(
        nowSeconds: Long,
        startOverride: Long? = null,
        endOverride: Long? = null,
    ): ChartWindowSamples {
        if (samples.isEmpty()) {
            // 空数据时也给一个完整刻度，左右两点都是 0。
            val span = chartMeasuringRule.windowSeconds
            val rulerStart = if (startOverride != null) startOverride else nowSeconds - span
            val rulerEnd = if (endOverride != null) endOverride else rulerStart + span
            return ChartWindowSamples(
                samples = listOf(
                    TrafficChartSample(rulerStart, 0L, 0L),
                    TrafficChartSample(rulerEnd, 0L, 0L),
                ),
                domainStart = rulerStart,
                domainEnd = rulerEnd,
            )
        }

        val (targetStart, targetEnd) = targetDomain(chartMeasuringRule, nowSeconds)
        val domainStart = startOverride ?: targetStart
        val domainEnd = endOverride ?: targetEnd

        // 用二分找到 [domainStart, domainEnd] 内的采样区间。
        val startSearch = samples.binarySearchBy(domainStart) { it.timestampSeconds }
        val startIndex = if (startSearch < 0) -startSearch - 1 else startSearch

        val endSearch = samples.binarySearchBy(domainEnd) { it.timestampSeconds }
        val endIndex = if (endSearch < 0) -endSearch - 1 else endSearch + 1

        val visibleSamples = if (startIndex < endIndex) {
            samples.subList(startIndex, endIndex.coerceAtMost(samples.size))
        } else {
            emptyList()
        }

        if (visibleSamples.isEmpty()) {
            // 当前动画窗口内没有真实数据，给一个纯零的完整刻度。
            return ChartWindowSamples(
                samples = listOf(
                    TrafficChartSample(domainStart, 0L, 0L),
                    TrafficChartSample(domainEnd, 0L, 0L),
                ),
                domainStart = domainStart,
                domainEnd = domainEnd,
            )
        }

        val zeroStart = visibleSamples.last().timestampSeconds + 1
        return ChartWindowSamples(
            samples = visibleSamples,
            domainStart = domainStart,
            domainEnd = domainEnd,
            zeroTailStart = zeroStart.takeIf { it <= domainEnd },
            zeroTailEnd = domainEnd.takeIf { it > zeroStart },
        )
    }


    private suspend fun persistSample(sample: TrafficChartSample) {
        val cutoff = sample.timestampSeconds - TrafficChartStore.Companion.MAX_AGE_SECONDS
        val shouldPruneStore =
            sample.timestampSeconds - lastStorePruneTimestampSeconds >= STORE_PRUNE_INTERVAL_SECONDS
        withContext(Dispatchers.IO) {
            trafficChartStore.append(sample)
            if (shouldPruneStore) {
                trafficChartStore.prune(cutoff)
            }
        }
        if (shouldPruneStore) {
            lastStorePruneTimestampSeconds = sample.timestampSeconds
        }
    }

    private suspend fun publishChart(
        startOverride: Long? = null,
        endOverride: Long? = null,
    ) {
        val fallbackTimestampSeconds = currentTimestampSeconds()
        val windowSamples = samplesForChartWindow(
            fallbackTimestampSeconds,
            startOverride = startOverride,
            endOverride = endOverride,
        )
        val snapshot = windowSamples.samples.toList()
        val (ftpSeries, sftpSeries) = withContext(Dispatchers.Default) {
            val ftpSeries = buildRenderSeries(snapshot, fallbackTimestampSeconds) {
            it.ftpBytesPerSecond / 1024L
        }
            val sftpSeries = buildRenderSeries(snapshot, fallbackTimestampSeconds) {
            it.sftpBytesPerSecond / 1024L
        }
            ftpSeries to sftpSeries
        }

        val ftpX = ftpSeries.xValues.toMutableList()
        val ftpY = ftpSeries.yValues.toMutableList()
        val sftpX = sftpSeries.xValues.toMutableList()
        val sftpY = sftpSeries.yValues.toMutableList()

        // 无论降采样与否，都保证序列的第一个点贴着当前刻度的左边界。
        if (ftpX.first() > windowSamples.domainStart) {
            ftpX.add(0, windowSamples.domainStart)
            ftpY.add(0, 0L)
        }
        if (sftpX.first() > windowSamples.domainStart) {
            sftpX.add(0, windowSamples.domainStart)
            sftpY.add(0, 0L)
        }


        // 数据还没铺满当前刻度时，把右侧未度量区域画成 y=0。
        windowSamples.zeroTailStart?.let { zeroStart ->
            val zeroEnd = windowSamples.zeroTailEnd ?: zeroStart
            if (ftpX.isEmpty() || ftpX.last() < zeroStart) {
                ftpX.add(zeroStart)
                ftpY.add(0L)
            }
            if (sftpX.isEmpty() || sftpX.last() < zeroStart) {
                sftpX.add(zeroStart)
                sftpY.add(0L)
            }
            if (zeroEnd > zeroStart) {
                ftpX.add(zeroEnd)
                ftpY.add(0L)
                sftpX.add(zeroEnd)
                sftpY.add(0L)
            }
        }

        // 确保最后一个点也贴着当前刻度的右边界。
        if (ftpX.last() < windowSamples.domainEnd) {
            ftpX.add(windowSamples.domainEnd)
            ftpY.add(0L)
        }
        if (sftpX.last() < windowSamples.domainEnd) {
            sftpX.add(windowSamples.domainEnd)
            sftpY.add(0L)
        }



        modelProducer.runTransaction {
            lineSeries {
                series(ftpX, ftpY)
                series(sftpX, sftpY)
            }
        }
    }

    /**
     * Builds the series sent to Vico. Raw samples are used until [MAX_RENDER_POINTS] is reached.
     * After that, samples are bucketed and each bucket contributes its maximum. This preserves the
     * tall thin peaks while keeping the composable model small enough to redraw every second.
     */
    private fun buildRenderSeries(
        samples: List<TrafficChartSample>,
        fallbackTimestampSeconds: Long,
        value: (TrafficChartSample) -> Long,
    ): RenderSeries {
        if (samples.isEmpty()) {
            return RenderSeries(listOf(fallbackTimestampSeconds), listOf(0L))
        }

        if (samples.size <= MAX_RENDER_POINTS) {
            val xValues = ArrayList<Long>(samples.size)
            val yValues = ArrayList<Long>(samples.size)
            for (sample in samples) {
                // Use absolute epoch seconds so the bottom axis can format them as wall-clock time.
                xValues.add(sample.timestampSeconds)
                yValues.add(value(sample))
            }
            return RenderSeries(xValues, yValues)
        }

        val samplesPerBucket = (samples.size + MAX_RENDER_POINTS - 1) / MAX_RENDER_POINTS
        val xValues = ArrayList<Long>(MAX_RENDER_POINTS)
        val yValues = ArrayList<Long>(MAX_RENDER_POINTS)

        var startIndex = 0
        while (startIndex < samples.size) {
            val endIndex = (startIndex + samplesPerBucket).coerceAtMost(samples.size)
            var bucketMaximum = Long.MIN_VALUE
            var bucketSample = samples[startIndex]
            for (index in startIndex until endIndex) {
                val sampleValue = value(samples[index])
                if (sampleValue > bucketMaximum) {
                    bucketMaximum = sampleValue
                    bucketSample = samples[index]
                }
            }
            xValues.add(bucketSample.timestampSeconds)
            yValues.add(bucketMaximum)
            startIndex = endIndex
        }

        // 降采样后也把首尾真实采样保留下来，避免 x 轴两端因为“只取桶内最大值”
        // 而丢掉边界点，造成视觉上左右有空隙。
        if (xValues.first() != samples.first().timestampSeconds) {
            xValues.add(0, samples.first().timestampSeconds)
            yValues.add(0, value(samples.first()))
        }
        if (xValues.last() != samples.last().timestampSeconds) {
            xValues.add(samples.last().timestampSeconds)
            yValues.add(value(samples.last()))
        }

        return RenderSeries(xValues, yValues)
    }

    override fun onCleared() {
        super.onCleared()
        EventBus.getDefault().unregister(this)
    }

    private data class RenderSeries(
        val xValues: List<Long>,
        val yValues: List<Long>,
    )

    private data class ChartWindowSamples(
        val samples: List<TrafficChartSample>,
        val domainStart: Long,
        val domainEnd: Long,
        val zeroTailStart: Long? = null,
        val zeroTailEnd: Long? = null,
    )


    companion object {


        /**
         * Maximum number of points passed to Vico for one series. A screen is at most a few
         * thousand pixels wide, so drawing more points would not add visible detail.
         */
        private const val MAX_RENDER_POINTS = 4_000

        /** Prune the SQLite table about once an hour. */
        private const val STORE_PRUNE_INTERVAL_SECONDS = 60L * 60L
    }
}

private fun currentTimestampSeconds(): Long = System.currentTimeMillis() / 1000L