package org.primftpd.ui.data

enum class ChartTriStateEnum {
    HOUR,
    DAY,
    WEEK;

    fun next(): ChartTriStateEnum = when (this) {
        HOUR -> DAY
        DAY -> WEEK
        WEEK -> HOUR
    }

    val windowSeconds: Long
        get() = when (this) {
            HOUR -> 60L * 60L
            DAY -> 24L * 60L * 60L
            WEEK -> 7L * 24L * 60L * 60L
        }
}