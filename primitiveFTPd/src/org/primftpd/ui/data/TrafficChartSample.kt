package org.primftpd.ui.data

data class TrafficChartSample(
    val timestampSeconds: Long,
    val ftpBytesPerSecond: Long,
    val sftpBytesPerSecond: Long,
)