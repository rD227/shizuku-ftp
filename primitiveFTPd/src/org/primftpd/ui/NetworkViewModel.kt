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

    // 🔧 修复3：使用AtomicLong保证线程安全（EventBus回调在BACKGROUND线程，updateChart在Main线程）
    private val bytesInLastSecond = AtomicLong(0L)

    private val speedHistory = mutableListOf<Long>()
    private val maxHistoryPoints = 20
    //最大长度

    // 🔧 修复4：记录上一次event.bytes，计算增量delta
    //   原代码的 lastTotalBytes 在每秒清零 bytesInLastSecond 时没有重置，
    //   导致delta越来越大（transferredSize是累计值，不是每秒的增量）
    private var lastEventBytes = 0L

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
        // 🔧 修复5：正确计算增量
        //   event.bytes 是累计传输字节数（transferredSize），不是本次增量
        //   delta = 本次累计 - 上次累计 = 这段时间新增的字节
        val delta = if (currentTotal > lastEventBytes) {
            currentTotal - lastEventBytes
        } else {
            // 新传输开始（transferredSize被重置为0），直接用currentTotal
            currentTotal
        }
        lastEventBytes = currentTotal

        bytesInLastSecond.addAndGet(delta)

        Log.d("NetworkViewModel", ">>>收到事件: delta=${delta}B, bytesInLastSecond=${bytesInLastSecond.get()}B, total=${currentTotal}")
    }

    private suspend fun updateChart() {
        // 🔧 修复6：getAndSet(0) 原子地读取并清零，避免竞态
        val bytesThisSecond = bytesInLastSecond.getAndSet(0L)
        val speedKB = bytesThisSecond / 1024

        Log.d("NetworkViewModel", ">>>每秒更新: speed=${speedKB}KB/s, bytesThisSecond=${bytesThisSecond}B")

        speedHistory.add(speedKB)
        if (speedHistory.size > maxHistoryPoints) {
            speedHistory.removeAt(0)
        }

        modelProducer.runTransaction {
            lineSeries { series(speedHistory) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        EventBus.getDefault().unregister(this)
    }
}
