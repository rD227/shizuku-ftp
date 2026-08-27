package org.primftpd.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.primftpd.ui.data.UiPreferences

class UiPreferencesViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("ui_state", Context.MODE_PRIVATE)

    private val _topComponentPressedDown = MutableStateFlow(
        UiPreferences.getTopComponentPressedDown(prefs)
    )
    val topComponentPressedDown = _topComponentPressedDown.asStateFlow()

    fun setTopComponentPressedDown(value: Boolean) {
        UiPreferences.setTopComponentPressedDown(prefs, value)
        _topComponentPressedDown.value = value
    }

    fun getTopComponentPressedDown(): Boolean {
        return UiPreferences.getTopComponentPressedDown(prefs)
    }
}