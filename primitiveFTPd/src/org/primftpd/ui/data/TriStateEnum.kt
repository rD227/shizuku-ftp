package org.primftpd.ui.data

enum class ChartTriStateEnum {
    MINUTE,

    HOUR,
    DAY,
    WEEK;

    fun next(): ChartTriStateEnum = when (this) {
        MINUTE -> HOUR
        HOUR -> DAY
        DAY -> WEEK
        WEEK -> MINUTE
    }

    val windowSeconds: Long
        get() = when (this) {
            MINUTE -> 60L
            HOUR -> 60L * 60L
            DAY -> 24L * 60L * 60L
            WEEK -> 7L * 24L * 60L * 60L
        }
}