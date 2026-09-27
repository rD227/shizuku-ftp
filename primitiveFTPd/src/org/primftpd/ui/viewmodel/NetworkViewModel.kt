package org.primftpd.ui.viewmodel

import android.app.Application
import android.os.SystemClock
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.primftpd.events.DataTransferredEvent
import org.primftpd.ui.TrafficChartClearEvent
import org.primftpd.ui.LineChartSlide
import org.primftpd.ui.TrafficChartStore
import org.primftpd.ui.data.ChartPeak
import org.primftpd.ui.data.ChartTriStateEnum
import org.primftpd.ui.data.TrafficChartSample
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong

/**
 * Owns the traffic chart data shown on the main screen.
 *
 * Data is accumulated in per-second buckets and kept for [org.primftpd.ui.TrafficChartStore.Companion.MAX_AGE_SECONDS]
 * (about three days). The x-axis follows the selected measuring rule (MINUTE/HOUR/DAY); if the stored
 * history is shorter than the selected span, it is pinned to the left edge and the unmeasured
 * right-hand side is drawn as y = 0.
 * The renderer downsamples to roughly one point per screen dp only for drawing; the persisted
 * history keeps the original per-second resolution.
 */
class NetworkViewModel(application: Application) : AndroidViewModel(application) {

    val modelProducer = CartesianChartModelProducer()

    private val _chartPeaks = MutableStateFlow<List<ChartPeak>>(emptyList())

    /** 当前可见窗口内 FTP / SFTP 的峰值点，供图表画标记点。 */
    val chartPeaks: StateFlow<List<ChartPeak>> = _chartPeaks.asStateFlow()

    private val trafficChartStore = TrafficChartStore.Companion.getInstance(application)

    /** More samples than horizontal display units add no visible detail but greatly increase Vico's
     * path, area-fill, and diff-animation work. */
    private val screenRenderPointLimit =
        application.resources.configuration.screenWidthDp.coerceIn(
            MIN_SCREEN_RENDER_POINTS,
            MAX_SCREEN_RENDER_POINTS,
        )
    private val animationRenderPointLimit =
        (screenRenderPointLimit / 2).coerceAtLeast(MIN_ANIMATION_RENDER_POINTS)

    private val ftpBytesInLastSecond = AtomicLong(0L)
    private val sftpBytesInLastSecond = AtomicLong(0L)

    private val samples = mutableListOf<TrafficChartSample>()

    private var chartMeasuringRule = ChartTriStateEnum.HOUR

    private val _suppressVicoChartAnimation = MutableStateFlow(false)
    val suppressVicoChartAnimation: StateFlow<Boolean> =
        _suppressVicoChartAnimation.asStateFlow()

    private var chartRangeAnimationJob: Job? = null

    private val lineChartSlide = LineChartSlide()



    private var lastFtpEventBytes = 0L
    private var lastSftpEventBytes = 0L
    private var lastMemoryPruneTimestampSeconds = 0L
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
        lineChartSlide.reset()
        historyVersion++
        viewModelScope.launch {
            publishChart()
        }
    }

    fun setChartMeasuringRule(rule: ChartTriStateEnum, animate: Boolean) {
        if (chartMeasuringRule == rule) return

        val nowSeconds = currentTimestampSeconds()
        val (fromStart, fromEnd) = targetDomain(chartMeasuringRule, nowSeconds)

        lineChartSlide.reset()
        chartMeasuringRule = rule
        val (toStart, toEnd) = latestTargetDomain(rule, nowSeconds)

        logger.debug(">>> Chart measuring rule changed to {}", rule)

        chartRangeAnimationJob?.cancel()
        chartRangeAnimationJob = viewModelScope.launch {
            // The range animation publishes its own frames. Temporarily disable Vico's model-diff
            // animation so those frames are rendered directly instead of being queued again.
            _suppressVicoChartAnimation.value = true
            try {
                delay(16)
                if (animate) {
                    animateChartWindow(fromStart, fromEnd, toStart, toEnd)
                } else {
                    publishChart()
                }
                // Give Compose one frame to consume the final direct model before restoring the
                // regular per-second Vico animation.
                delay(16)
            } finally {
                _suppressVicoChartAnimation.value = false
            }
        }
    }

    fun panChartWindow(deltaSeconds: Double) {
        val rule = chartMeasuringRule
        if (rule != ChartTriStateEnum.MINUTE) return
        chartRangeAnimationJob?.cancel()
        lineChartSlide.pan(
            scope = viewModelScope,
            deltaSeconds = deltaSeconds,
            measuringRule = rule,
            boundsProvider = { chartPanBounds(rule) },
            onWindowChanged = { publishChart() },
        )
    }

    private fun chartPanBounds(rule: ChartTriStateEnum): LineChartSlide.PanBounds? {
        val span = rule.windowSeconds
        val nowSeconds = currentTimestampSeconds()
        val (_, latestEnd) = latestTargetDomain(rule, nowSeconds)
        val earliest = samples.firstOrNull()?.timestampSeconds ?: return null
        val newest = samples.lastOrNull()?.timestampSeconds?.coerceAtLeast(nowSeconds) ?: return null

        val minEnd = earliest + span
        val maxEnd = maxOf(newest, minEnd)
        return LineChartSlide.PanBounds(
            minEnd = minEnd,
            maxEnd = maxEnd,
            latestEnd = latestEnd,
        )
    }

    private fun targetDomain(
        rule: ChartTriStateEnum,
        nowSeconds: Long,
    ): Pair<Long, Long> {
        val (latestStart, latestEnd) = latestTargetDomain(rule, nowSeconds)
        val manualEnd = lineChartSlide.windowEndOverride ?: return latestStart to latestEnd
        return (manualEnd - rule.windowSeconds) to manualEnd
    }

    private fun latestTargetDomain(
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
        val durationMs = 360L
        val frameMs = 16L
        val startedAt = SystemClock.uptimeMillis()
        var elapsedMs: Long

        do {
            val frameStartedAt = SystemClock.uptimeMillis()
            elapsedMs = (frameStartedAt - startedAt).coerceAtMost(durationMs)
            val progress = elapsedMs.toFloat() / durationMs.toFloat()
            val eased = 1f - (1f - progress) * (1f - progress) * (1f - progress)

            publishChart(
                startOverride = fromStart + ((toStart - fromStart) * eased).toLong(),
                endOverride = fromEnd + ((toEnd - fromEnd) * eased).toLong(),
                maxRenderPoints = animationRenderPointLimit,
            )
            if (elapsedMs < durationMs) {
                val frameWorkMs = SystemClock.uptimeMillis() - frameStartedAt
                delay((frameMs - frameWorkMs).coerceAtLeast(1L))
            }
        } while (elapsedMs < durationMs)

        publishChart()
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
        // The ruler animation already publishes current snapshots. A competing once-per-second
        // transaction here can finish between its frames and make the whole chart jump.
        if (chartRangeAnimationJob?.isActive != true) {
            publishChart()
        }

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
        if (
            nowSeconds - lastMemoryPruneTimestampSeconds < MEMORY_PRUNE_INTERVAL_SECONDS
        ) {
            return
        }
        lastMemoryPruneTimestampSeconds = nowSeconds

        val cutoff = nowSeconds - TrafficChartStore.Companion.MAX_AGE_SECONDS
        val cutoffSearch = samples.binarySearchBy(cutoff) { it.timestampSeconds }
        val firstKeptIndex = if (cutoffSearch < 0) -cutoffSearch - 1 else cutoffSearch
        if (firstKeptIndex > 0) {
            // ArrayList.removeAt(0) shifts the whole history once for every expired sample. Remove
            // the expired prefix in one operation instead, and only do that work about once a
            // minute. This matters once the in-memory history has grown to several days.
            samples.subList(0, firstKeptIndex).clear()
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
        maxRenderPoints: Int = screenRenderPointLimit,
    ) {
        val fallbackTimestampSeconds = currentTimestampSeconds()
        val windowSamples = samplesForChartWindow(
            fallbackTimestampSeconds,
            startOverride = startOverride,
            endOverride = endOverride,
        )
        val snapshot = windowSamples.samples.toList()
        val series = withContext(Dispatchers.Default) {
            buildRenderSeriesPair(snapshot, fallbackTimestampSeconds, maxRenderPoints)
        }
        val ftpSeries = series.ftp
        val sftpSeries = series.sftp

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

        updateChartPeaks(ftpX, ftpY, sftpX, sftpY)

        modelProducer.runTransaction {
            lineSeries {
                series(ftpX, ftpY)
                series(sftpX, sftpY)
            }
        }
    }

    private fun updateChartPeaks(
        ftpX: List<Long>,
        ftpY: List<Long>,
        sftpX: List<Long>,
        sftpY: List<Long>,
    ) {
        val peaks = buildList {
            peakOf(ftpX, ftpY)?.let { (x, y) ->
                add(ChartPeak(x = x.toDouble(), valueKbPerSecond = y, isFtp = true))
            }
            peakOf(sftpX, sftpY)?.let { (x, y) ->
                add(ChartPeak(x = x.toDouble(), valueKbPerSecond = y, isFtp = false))
            }
        }
        if (_chartPeaks.value != peaks) {
            _chartPeaks.value = peaks
        }
    }

    private fun peakOf(xValues: List<Long>, yValues: List<Long>): Pair<Long, Long>? {
        if (xValues.isEmpty() || xValues.size != yValues.size) return null
        var peakIndex = -1
        var peakY = 0L
        for (index in yValues.indices) {
            val value = yValues[index]
            if (value > peakY) {
                peakY = value
                peakIndex = index
            }
        }
        return if (peakIndex >= 0) xValues[peakIndex] to peakY else null
    }

    /**
     * Builds the series sent to Vico. Raw samples are used until [maxRenderPoints] is reached.
     * After that, samples are bucketed and each bucket contributes its maximum. This preserves the
     * tall thin peaks while keeping the composable model small enough to redraw every second.
     */
    private fun buildRenderSeriesPair(
        samples: List<TrafficChartSample>,
        fallbackTimestampSeconds: Long,
        maxRenderPoints: Int,
    ): RenderSeriesPair {
        if (samples.isEmpty()) {
            val empty = RenderSeries(listOf(fallbackTimestampSeconds), listOf(0L))
            return RenderSeriesPair(empty, empty)
        }

        val pointLimit = maxRenderPoints.coerceAtLeast(1)
        val ftpX = ArrayList<Long>(minOf(samples.size, pointLimit) + 2)
        val ftpY = ArrayList<Long>(minOf(samples.size, pointLimit) + 2)
        val sftpX = ArrayList<Long>(minOf(samples.size, pointLimit) + 2)
        val sftpY = ArrayList<Long>(minOf(samples.size, pointLimit) + 2)

        if (samples.size <= pointLimit) {
            for (sample in samples) {
                // Use absolute epoch seconds so the bottom axis can format them as wall-clock time.
                ftpX.add(sample.timestampSeconds)
                ftpY.add(sample.ftpBytesPerSecond / 1024L)
                sftpX.add(sample.timestampSeconds)
                sftpY.add(sample.sftpBytesPerSecond / 1024L)
            }
            return RenderSeriesPair(RenderSeries(ftpX, ftpY), RenderSeries(sftpX, sftpY))
        }

        val samplesPerBucket = (samples.size + pointLimit - 1) / pointLimit
        var startIndex = 0
        while (startIndex < samples.size) {
            val endIndex = (startIndex + samplesPerBucket).coerceAtMost(samples.size)
            var ftpMaximum = Long.MIN_VALUE
            var sftpMaximum = Long.MIN_VALUE
            var ftpMaximumSample = samples[startIndex]
            var sftpMaximumSample = samples[startIndex]
            for (index in startIndex until endIndex) {
                val sample = samples[index]
                if (sample.ftpBytesPerSecond > ftpMaximum) {
                    ftpMaximum = sample.ftpBytesPerSecond
                    ftpMaximumSample = sample
                }
                if (sample.sftpBytesPerSecond > sftpMaximum) {
                    sftpMaximum = sample.sftpBytesPerSecond
                    sftpMaximumSample = sample
                }
            }
            ftpX.add(ftpMaximumSample.timestampSeconds)
            ftpY.add(ftpMaximum / 1024L)
            sftpX.add(sftpMaximumSample.timestampSeconds)
            sftpY.add(sftpMaximum / 1024L)
            startIndex = endIndex
        }

        // 降采样后也把首尾真实采样保留下来，避免 x 轴两端因为“只取桶内最大值”
        // 而丢掉边界点，造成视觉上左右有空隙。
        val first = samples.first()
        val last = samples.last()
        if (ftpX.first() != first.timestampSeconds) {
            ftpX.add(0, first.timestampSeconds)
            ftpY.add(0, first.ftpBytesPerSecond / 1024L)
        }
        if (sftpX.first() != first.timestampSeconds) {
            sftpX.add(0, first.timestampSeconds)
            sftpY.add(0, first.sftpBytesPerSecond / 1024L)
        }
        if (ftpX.last() != last.timestampSeconds) {
            ftpX.add(last.timestampSeconds)
            ftpY.add(last.ftpBytesPerSecond / 1024L)
        }
        if (sftpX.last() != last.timestampSeconds) {
            sftpX.add(last.timestampSeconds)
            sftpY.add(last.sftpBytesPerSecond / 1024L)
        }

        return RenderSeriesPair(RenderSeries(ftpX, ftpY), RenderSeries(sftpX, sftpY))
    }

    override fun onCleared() {
        super.onCleared()
        EventBus.getDefault().unregister(this)
    }

    private data class RenderSeries(
        val xValues: List<Long>,
        val yValues: List<Long>,
    )

    private data class RenderSeriesPair(
        val ftp: RenderSeries,
        val sftp: RenderSeries,
    )

    private data class ChartWindowSamples(
        val samples: List<TrafficChartSample>,
        val domainStart: Long,
        val domainEnd: Long,
        val zeroTailStart: Long? = null,
        val zeroTailEnd: Long? = null,
    )


    companion object {


        private const val MIN_SCREEN_RENDER_POINTS = 240
        private const val MAX_SCREEN_RENDER_POINTS = 720
        private const val MIN_ANIMATION_RENDER_POINTS = 120

        /** Prune the in-memory prefix in a batch instead of shifting a large list every second. */
        private const val MEMORY_PRUNE_INTERVAL_SECONDS = 60L

        /** Prune the SQLite table about once an hour. */
        private const val STORE_PRUNE_INTERVAL_SECONDS = 60L * 60L
    }
}

private fun currentTimestampSeconds(): Long = System.currentTimeMillis() / 1000L
