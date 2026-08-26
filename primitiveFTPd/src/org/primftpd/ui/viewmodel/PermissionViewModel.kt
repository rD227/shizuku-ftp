package org.primftpd.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.primftpd.ui.data.PermissionState

class PermissionViewModel(application: Application) : AndroidViewModel(application) {

    private val _permState = MutableStateFlow(PermissionState())
    val permState = _permState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val ctx = getApplication<Application>()
        _permState.update {
            PermissionState(
                fullStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    Environment.isExternalStorageManager() else true,
                mediaLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    ctx.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED else true,
                notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED else true
            )
        }
    }
}