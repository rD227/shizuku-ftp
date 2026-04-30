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



class NetworkViewModel : ViewModel() {
    val modelProducer = CartesianChartModelProducer()

    private var bytesInLastSecond = 0L
    private val speedHistory = mutableListOf<Long>()
    private val maxHistoryPoints = 20

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    init {
        EventBus.getDefault().register(this)
        
        // 启动定时任务，每秒更新一次图表
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                updateChart()
            }
        }
    }

    private var lastTotalBytes = 0L

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onDataTransferred(event: DataTransferredEvent) {
        val currentTotal = event.bytes

        // 计算增量：如果当前总量比上次大，取差值；如果是新连接（总量变小），直接取当前值
        val delta = if (currentTotal > lastTotalBytes) {
            currentTotal - lastTotalBytes
        } else {
            currentTotal
        }

        bytesInLastSecond += delta
        lastTotalBytes = currentTotal
    }

    private suspend fun updateChart() {
        val speedKB = bytesInLastSecond / 1024
        bytesInLastSecond = 0 

        speedHistory.add(speedKB)
        if (speedHistory.size > maxHistoryPoints) {
            speedHistory.removeAt(0)
        }

        modelProducer.runTransaction {
            lineSeries {
                series(speedHistory)
            }
        }
        logger.debug(">>>updateChart() called, speedKB: $speedKB, bytesInLastSecond: $bytesInLastSecond")
    }

    override fun onCleared() {
        super.onCleared()
        EventBus.getDefault().unregister(this)
    }
}
