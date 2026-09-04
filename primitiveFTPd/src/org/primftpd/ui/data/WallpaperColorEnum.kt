package org.primftpd.ui.data

enum class WallpaperColorEnum( val value: String ) {
    VIBRANT("vibrant"),
    DARK_MUTED("dark_muted"),
    LIGHT_MUTED("light_muted"),
    MUTED("muted");

    companion object {
        fun fromString(type: String): WallpaperColorEnum? =
            entries.find { it.value == type }
    }
}