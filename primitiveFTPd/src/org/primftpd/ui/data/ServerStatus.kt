package org.primftpd.ui.data

data class ServerState(
    val gearLeftVisible: Boolean = false,  // 替代 initialLeftVisible
    val linkRightVisible: Boolean = false  // 替代 initialRightVisible
)
data class TabState(
    val isServerRunning: Boolean = false,
)

data class PermissionState(
    val fullStorage: Boolean = true,
    val mediaLocation: Boolean = true,
    val notification: Boolean = true,
)