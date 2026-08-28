package org.primftpd.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.primftpd.ui.data.UiPreferences

class UiPreferencesViewModel(application: Application) : AndroidViewModel(application) {

    private val barDownPrefs = application.getSharedPreferences("ui_state", Context.MODE_PRIVATE)

    private val _topComponentPressedDown = MutableStateFlow(
        UiPreferences.getTopComponentPressedDown(barDownPrefs)
    )
    val topComponentPressedDown = _topComponentPressedDown.asStateFlow()

    fun setTopComponentPressedDown(value: Boolean) {
        UiPreferences.setTopComponentPressedDown(barDownPrefs, value)
        _topComponentPressedDown.value = value
    }

    fun getTopComponentPressedDown(): Boolean {
        return UiPreferences.getTopComponentPressedDown(barDownPrefs)
    }

    private val blurIntensityPrefs = application.getSharedPreferences("blur_intensity", Context.MODE_PRIVATE)
    private val _blurIntensity = MutableStateFlow(
        UiPreferences.getBlurIntensity(blurIntensityPrefs)
    )
    val blurIntensity = _blurIntensity.asStateFlow()
    fun setBlurIntensity(value: Float) {
        UiPreferences.setBlurIntensity(blurIntensityPrefs, value)
        _blurIntensity.value = value
    }
    fun getBlurIntensity(): Float {
        return UiPreferences.getBlurIntensity(blurIntensityPrefs)
    }
}