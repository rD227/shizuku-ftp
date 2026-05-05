package org.primftpd.ui

import android.util.Log
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
        Log.d("NetworkViewModel", ">>> ViewModel 已创建，正在注册 EventBus")
        logger?.debug(">>>SLF4J try to look the ViewModel start, ready to register th event bus ")
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
            Log.d("NetworkViewModel", ">>>SFTP事件: delta=${delta}B, sftpBytesInLastSecond=${sftpBytesInLastSecond.get()}B")
        } else {
            val delta = if (currentTotal > lastFtpEventBytes) {
                currentTotal - lastFtpEventBytes
            } else {
                currentTotal
            }
            lastFtpEventBytes = currentTotal
            ftpBytesInLastSecond.addAndGet(delta)
            Log.d("NetworkViewModel", ">>>FTP事件: delta=${delta}B, ftpBytesInLastSecond=${ftpBytesInLastSecond.get()}B")
        }
    }

    private suspend fun updateChart() {
        val ftpBytesThisSecond = ftpBytesInLastSecond.getAndSet(0L)
        val sftpBytesThisSecond = sftpBytesInLastSecond.getAndSet(0L)
        val ftpSpeedKB = ftpBytesThisSecond / 1024
        val sftpSpeedKB = sftpBytesThisSecond / 1024

        Log.d("NetworkViewModel", ">>>每秒更新: ftp=${ftpSpeedKB}KB/s, sftp=${sftpSpeedKB}KB/s")

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
