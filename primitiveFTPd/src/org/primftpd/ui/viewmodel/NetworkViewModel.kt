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
import org.primftpd.ui.data.TrafficChartSample
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong

/**
 * Owns the traffic chart data shown on the main screen.
 *
 * Data is accumulated in per-second buckets and kept for [org.primftpd.ui.TrafficChartStore.Companion.MAX_AGE_SECONDS]
 * (about three days). The x-axis is not scrolled or animated: the whole history is always fitted
 * into the available chart width, so it becomes progressively more compressed as time passes.
 * The renderer downsamples to [MAX_RENDER_POINTS] points only for drawing; the persisted history
 * keeps the original per-second resolution.
 */
class NetworkViewModel(application: Application) : AndroidViewModel(application) {

    val modelProducer = CartesianChartModelProducer()

    private val trafficChartStore = TrafficChartStore.Companion.getInstance(application)

    private val ftpBytesInLastSecond = AtomicLong(0L)
    private val sftpBytesInLastSecond = AtomicLong(0L)

    private val samples = mutableListOf<TrafficChartSample>()

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
        val ftpSeries = buildRenderSeries(samples, fallbackTimestampSeconds) {
            it.ftpBytesPerSecond / 1024L
        }
        val sftpSeries = buildRenderSeries(samples, fallbackTimestampSeconds) {
            it.sftpBytesPerSecond / 1024L
        }

        val ftpX = ftpSeries.xValues
        val ftpY = ftpSeries.yValues
        val sftpX = sftpSeries.xValues
        val sftpY = sftpSeries.yValues

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