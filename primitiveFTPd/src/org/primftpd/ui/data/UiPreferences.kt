package org.primftpd.ui.data

import android.content.SharedPreferences
import androidx.core.content.edit

//这里我准备直接用单例对象来写了
//封装似乎用不上，而且不用似乎也没用什么风险，看起来原项目都没怎么封装
//实在要考虑封装可以考虑直接用方法获取私有对象的属性？
object UiPreferences {
    const val PREF_KEY_BLUR_INTENSITY = "blurIntensityPref"
    const val DEFAULT_BLUR_INTENSITY = 4f

    fun getBlurIntensity(prefs: SharedPreferences) =
        prefs.getFloat(PREF_KEY_BLUR_INTENSITY, DEFAULT_BLUR_INTENSITY)

    fun setBlurIntensity(prefs: SharedPreferences, intensity: Float) {
        prefs.edit { putFloat(PREF_KEY_BLUR_INTENSITY, intensity) }
    }

    const val PREF_KEY_TOP_COMPONENT_PRESSED_DOWN = "topComponentPressedDownPref"
    const val DEFAULT_TOP_COMPONENT_PRESSED_DOWN = true

    fun getTopComponentPressedDown(prefs: SharedPreferences) =
        prefs.getBoolean(PREF_KEY_TOP_COMPONENT_PRESSED_DOWN, DEFAULT_TOP_COMPONENT_PRESSED_DOWN)

    fun setTopComponentPressedDown(prefs: SharedPreferences, pressedDown: Boolean) {
        prefs.edit { putBoolean(PREF_KEY_TOP_COMPONENT_PRESSED_DOWN, pressedDown) }
    }
}