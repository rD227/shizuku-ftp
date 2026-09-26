package org.primftpd.ui.data

/**
 * A highlighted maximum point in the traffic chart's current visible window.
 *
 * @param x absolute epoch seconds on the x axis.
 * @param valueKbPerSecond the peak value in the same unit used by the chart (KB/s).
 * @param isFtp true for FTP, false for SFTP.
 */
data class ChartPeak(
    val x: Double,
    val valueKbPerSecond: Long,
    val isFtp: Boolean,
)
