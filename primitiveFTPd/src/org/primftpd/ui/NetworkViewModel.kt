package org.primftpd.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.primftpd.events.DataTransferredEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong

class NetworkViewModel : ViewModel() {
    val modelProducer = CartesianChartModelProducer()

    private val ftpBytesInLastSecond = AtomicLong(0L)
    private val sftpBytesInLastSecond = AtomicLong(0L)

    private val ftpSpeedHistory = mutableListOf<Long>()
    private val sftpSpeedHistory = mutableListOf<Long>()
    private val maxHistoryPoints = 20

    private var lastFtpEventBytes = 0L
    private var lastSftpEventBytes = 0L

    private val logger: Logger? = LoggerFactory.getLogger(javaClass)

    init {
        logger?.debug(">>> ViewModel created, registering EventBus")
        EventBus.getDefault().register(this)
        viewModelScope.launch {
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
            logger?.debug(">>>SFTP event: delta={}B, sftpBytesInLastSecond={}B", delta, sftpBytesInLastSecond.get())
        } else {
            val delta = if (currentTotal > lastFtpEventBytes) {
                currentTotal - lastFtpEventBytes
            } else {
                currentTotal
            }
            lastFtpEventBytes = currentTotal
            ftpBytesInLastSecond.addAndGet(delta)
            logger?.debug(">>>FTP event: delta={}B, ftpBytesInLastSecond={}B", delta, ftpBytesInLastSecond.get())
        }
    }

    private suspend fun updateChart() {
        val ftpBytesThisSecond = ftpBytesInLastSecond.getAndSet(0L)
        val sftpBytesThisSecond = sftpBytesInLastSecond.getAndSet(0L)
        val ftpSpeedKB = ftpBytesThisSecond / 1024
        val sftpSpeedKB = sftpBytesThisSecond / 1024

        logger?.info(">>> every second update: ftp={}KB/s, sftp={}KB/s", ftpSpeedKB, sftpSpeedKB)

        ftpSpeedHistory.add(ftpSpeedKB)
        sftpSpeedHistory.add(sftpSpeedKB)
        if (ftpSpeedHistory.size > maxHistoryPoints) {
            ftpSpeedHistory.removeAt(0)
        }
        if (sftpSpeedHistory.size > maxHistoryPoints) {
            sftpSpeedHistory.removeAt(0)
        }

        modelProducer.runTransaction {
            lineSeries {
                series(ftpSpeedHistory)
                series(sftpSpeedHistory)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        EventBus.getDefault().unregister(this)
    }
}
