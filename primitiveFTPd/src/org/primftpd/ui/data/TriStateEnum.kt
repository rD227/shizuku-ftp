package org.primftpd.ui.data

enum class ChartTriStateEnum {
    MINUTE,

    HOUR,
    DAY;

    val windowSeconds: Long
        get() = when (this) {
            MINUTE -> 60L
            HOUR -> 60L * 60L
            DAY -> 24L * 60L * 60L
        }
}
