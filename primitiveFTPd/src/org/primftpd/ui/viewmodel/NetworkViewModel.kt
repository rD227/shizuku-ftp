package org.primftpd.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
        chartMeasuringRule = rule
        logger.debug(">>> Chart measuring rule changed to {}", rule)
        viewModelScope.launch {
            publishChart()
        }
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

    private fun samplesForChartWindow(nowSeconds: Long): ChartWindowSamples {
        if (samples.isEmpty()) {
            // 空数据时也给一个完整刻度，左右两点都是 0。
            val rulerStart = nowSeconds - chartMeasuringRule.windowSeconds
            return ChartWindowSamples(
                samples = listOf(
                    TrafficChartSample(rulerStart, 0L, 0L),
                    TrafficChartSample(rulerStart + chartMeasuringRule.windowSeconds, 0L, 0L),
                ),
            )
        }

        val rulerSpanSeconds = chartMeasuringRule.windowSeconds
        val earliestTimestamp = samples.first().timestampSeconds
        val newestTimestamp = samples.last().timestampSeconds.coerceAtLeast(nowSeconds)

        return if (newestTimestamp - earliestTimestamp < rulerSpanSeconds) {
            // 数据还不够铺满当前刻度：刻度从左边界开始，仍然保持完整长度；
            // 已经量到的最新时刻之后补 y=0，让右侧未度量区域落在 0 值上。
            val rulerEnd = earliestTimestamp + rulerSpanSeconds
            val zeroStart = newestTimestamp + 1

            ChartWindowSamples(
                samples = samples,
                zeroTailStart = zeroStart.takeIf { it <= rulerEnd },
                zeroTailEnd = rulerEnd.takeIf { it > zeroStart },
            )
        } else {
            // 数据已经超过当前刻度：显示最近的一个完整刻度，不额外补零。
            val lowerBound = newestTimestamp - rulerSpanSeconds
            val firstVisibleIndex = samples.indexOfFirst { it.timestampSeconds >= lowerBound }
                .takeIf { it >= 0 } ?: 0

            ChartWindowSamples(
                samples = samples.subList(firstVisibleIndex, samples.size),
            )
        }
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

    private suspend fun publishChart() {
        val fallbackTimestampSeconds = currentTimestampSeconds()
        val windowSamples = samplesForChartWindow(fallbackTimestampSeconds)
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