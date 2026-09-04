package org.primftpd.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.primftpd.ui.data.ChartTriStateEnum
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

    //______
    private val _chartMeasuringRule = MutableStateFlow(
        UiPreferences.getChartMeasuringRule(barDownPrefs)
    )
    val chartMeasuringRule = _chartMeasuringRule.asStateFlow()

    fun setChartMeasuringRule(value: ChartTriStateEnum) {
        UiPreferences.setChartMeasuringRule(barDownPrefs, value)
        _chartMeasuringRule.value = value
    }

    fun getChartMeasuringRule(): ChartTriStateEnum {
        return UiPreferences.getChartMeasuringRule(barDownPrefs)
    }

    //————————
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
    //______
    private val useM3ToPickColors = application.getSharedPreferences("usr_m3_to_pick_colors", Context.MODE_PRIVATE)
    private val _usrM3ToPickColors = MutableStateFlow(
        UiPreferences.getUsrM3ToPickColors(useM3ToPickColors)
    )
    val usrM3ToPickColors = _usrM3ToPickColors.asStateFlow()
    fun setUsrM3ToPickColors(value: Boolean) {
        UiPreferences.setUsrM3ToPickColors(useM3ToPickColors, value)
        _usrM3ToPickColors.value = value
    }
    fun getUsrM3ToPickColors(): Boolean {
        return UiPreferences.getUsrM3ToPickColors(useM3ToPickColors)
    }
    //______
    private val glassSideMenuWallpaperPrefs = application.getSharedPreferences("glass_side_menu_wallpaper", Context.MODE_PRIVATE)
    private val _glassSideMenuWallpaper = MutableStateFlow(
        UiPreferences.getGlassSideMenuWallpaper(glassSideMenuWallpaperPrefs)
    )
    val glassSideMenuWallpaper = _glassSideMenuWallpaper.asStateFlow()
    fun setGlassSideMenuWallpaper(value: Boolean) {
        UiPreferences.setGlassSideMenuWallpaper(glassSideMenuWallpaperPrefs, value)
        _glassSideMenuWallpaper.value = value
    }
    fun getGlassSideMenuWallpaper(): Boolean {
        return UiPreferences.getGlassSideMenuWallpaper(glassSideMenuWallpaperPrefs)
    }
    //________
    private val sideMenuSpringAnimationPrefs = application.getSharedPreferences("side_menu_spring_animation", Context.MODE_PRIVATE)
    private val _sideMenuSpringAnimation = MutableStateFlow(
        UiPreferences.getSideMenuSpringAnimation(sideMenuSpringAnimationPrefs)
    )
    val sideMenuSpringAnimation = _sideMenuSpringAnimation.asStateFlow()
    fun setSideMenuSpringAnimation(value: Boolean) {
        UiPreferences.setSideMenuSpringAnimation(sideMenuSpringAnimationPrefs, value)
        _sideMenuSpringAnimation.value = value
    }
    fun getSideMenuSpringAnimation(): Boolean {
        return UiPreferences.getSideMenuSpringAnimation(sideMenuSpringAnimationPrefs)
    }
    ///_______
    /**
    private val colorBagPrefs = application.getSharedPreferences("color_bag", Context.MODE_PRIVATE)
    private val _colorBag = MutableStateFlow<org.primftpd.ui.data.ColorBag?>(null)
    val colorBag = _colorBag.asStateFlow()
    fun setColorBag(value: org.primftpd.ui.data.ColorBag?) {
        _colorBag.value = value
    }
    **/
}