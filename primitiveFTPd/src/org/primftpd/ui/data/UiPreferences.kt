package org.primftpd.ui.data

import android.content.SharedPreferences
import androidx.core.content.edit

//这里我准备直接用单例对象来写了
//封装似乎用不上，而且不用似乎也没用什么风险，看起来原项目都没怎么封装
//实在要考虑封装可以考虑直接用方法获取私有对象的属性？
object UiPreferences {
    const val PREF_KEY_TOP_COMPONENT_PRESSED_DOWN = "topComponentPressedDownPref"
    const val DEFAULT_TOP_COMPONENT_PRESSED_DOWN = true

    fun getTopComponentPressedDown(prefs: SharedPreferences) =
        prefs.getBoolean(PREF_KEY_TOP_COMPONENT_PRESSED_DOWN, DEFAULT_TOP_COMPONENT_PRESSED_DOWN)

    fun setTopComponentPressedDown(prefs: SharedPreferences, pressedDown: Boolean) {
        prefs.edit { putBoolean(PREF_KEY_TOP_COMPONENT_PRESSED_DOWN, pressedDown) }
    }

//----------
    const val PREF_KEY_BLUR_INTENSITY = "blurIntensityPref"
    const val DEFAULT_BLUR_INTENSITY = 20

    fun getBlurIntensity(prefs: SharedPreferences): Float =
        prefs.getFloat(PREF_KEY_BLUR_INTENSITY, DEFAULT_BLUR_INTENSITY.toFloat())

    fun setBlurIntensity(prefs: SharedPreferences, intensity: Float) {
        prefs.edit { putFloat(PREF_KEY_BLUR_INTENSITY, intensity) }
    }
//——————
    const val PREF_KEY_USR_M3_TO_PICK_COLORS = "usrM3ToPickColorsPref"
    const val DEFAULT_USR_M3_TO_PICK_COLORS = false

    fun getUsrM3ToPickColors(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(PREF_KEY_USR_M3_TO_PICK_COLORS, DEFAULT_USR_M3_TO_PICK_COLORS)

    fun setUsrM3ToPickColors(prefs: SharedPreferences, value: Boolean) {
        prefs.edit { putBoolean(PREF_KEY_USR_M3_TO_PICK_COLORS, value) }
    }


}
