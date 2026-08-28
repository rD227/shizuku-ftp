package org.primftpd.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import kotlinx.coroutines.flow.flowOf
import org.primftpd.R
import org.primftpd.prefs.LoadPrefsUtil
import org.primftpd.ui.data.ColorBag
import org.primftpd.ui.data.UiPreferences
import org.primftpd.ui.viewmodel.UiPreferencesViewModel
import org.primftpd.util.NotificationUtil


@Composable
internal fun UiCategory(
    uiPreferencesViewModel: UiPreferencesViewModel? = null,
    colorBag: ColorBag
) {
    val context = LocalContext.current
    val prefs = rememberPrefs()

    var showTabNames by remember {
        mutableStateOf(prefs.getBoolean(LoadPrefsUtil.PREF_KEY_SHOW_TAB_NAMES, false))
    }
    var startOnOpen by remember {
        mutableStateOf(prefs.getBoolean(LoadPrefsUtil.PREF_KEY_START_ON_OPEN, false))
    }
    var showConnInfo by remember {
        mutableStateOf(LoadPrefsUtil.showConnectionInfoInNotification(prefs))
    }
    var showIpv4 by remember { mutableStateOf(LoadPrefsUtil.showIpv4InNotification(prefs)) }
    var showIpv6 by remember { mutableStateOf(LoadPrefsUtil.showIpv6InNotification(prefs)) }
    var showStartStop by remember { mutableStateOf(LoadPrefsUtil.showStartStopNotification(prefs)) }
    var quickSettingsUnlock by remember {
        mutableStateOf(LoadPrefsUtil.quickSettingsRequiresUnlock(prefs))
    }
    var stateBarPressDown by remember { mutableStateOf(uiPreferencesViewModel?.getTopComponentPressedDown()
        ?: UiPreferences.getTopComponentPressedDown(prefs)) }
    var usrM3ToPickColors by remember { mutableStateOf(uiPreferencesViewModel?.getUsrM3ToPickColors()
        ?: UiPreferences.getUsrM3ToPickColors(prefs)) }

    val changeInTimeStateBatPressDown by (uiPreferencesViewModel?.topComponentPressedDown ?: flowOf(
        UiPreferences.getTopComponentPressedDown(prefs)
    )).collectAsState(UiPreferences.getTopComponentPressedDown(prefs))

    var blurIntensity by remember { mutableFloatStateOf(uiPreferencesViewModel?.getBlurIntensity() ?: UiPreferences.getBlurIntensity(prefs)) }

    Text(
        text = stringResource(R.string.prefsCategoryTitleUi),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )

    SwitchPrefRow(
        title = "Weather Top Component Pressed Down",
        description = "Look bigger?",
        checked = stateBarPressDown,
        onCheckedChange = {
            stateBarPressDown = it
            uiPreferencesViewModel?.setTopComponentPressedDown(it)
        }
    )
    SwitchPrefRow(
        title = "Use M3 to Pick Colors?",
        description = "Pick colors from M3 or wallpaper",
        checked = usrM3ToPickColors,
        onCheckedChange = {
            usrM3ToPickColors = it
            uiPreferencesViewModel?.setUsrM3ToPickColors(it)
        }
    )

    SliderRow(
        sliderStep = 7,
        maxSlideValue = 40f,
        sliderTitle = "Blur Intensity",
        sliderDescription = "Adjust the blur intensity of the background.",
        //rememberedSliderPosition = uiPreferencesViewModel?.blurIntensity?.collectAsState()?.value ?: blurIntensity,
        rememberedSliderPosition  = blurIntensity,
        onSliderValueChange = { newValue ->
            blurIntensity = newValue
            uiPreferencesViewModel?.setBlurIntensity(newValue)
        },
        colorBag = colorBag
    )

    SwitchPrefRow(
        title = stringResource(R.string.prefShowTabNames),
        description = stringResource(R.string.prefSummaryShowTabNames),
        checked = showTabNames,
        onCheckedChange = {
            showTabNames = it
            prefs.edit { putBoolean(LoadPrefsUtil.PREF_KEY_SHOW_TAB_NAMES, it) }
        }
    )

    SwitchPrefRow(
        title = stringResource(R.string.prefTitleStartOnOpen),
        description = stringResource(R.string.prefSummaryStartOnOpen),
        checked = startOnOpen,
        onCheckedChange = {
            startOnOpen = it
            prefs.edit { putBoolean(LoadPrefsUtil.PREF_KEY_START_ON_OPEN, it) }
        }
    )

    SwitchPrefRow(
        title = stringResource(R.string.prefTitleShowConnectionInfoInNotification),
        description = stringResource(R.string.prefSummaryShowConnectionInfoInNotification),
        checked = showConnInfo,
        onCheckedChange = {
            showConnInfo = it
            prefs.edit { putBoolean(LoadPrefsUtil.PREF_KEY_SHOW_CONN_INFO, it) }
        }
    )

    SwitchPrefRow(
        title = stringResource(R.string.prefTitleSshowIpv4InNotification),
        description = stringResource(R.string.prefSummaryShowIpv4InNotification),
        checked = showIpv4,
        onCheckedChange = {
            showIpv4 = it
            prefs.edit { putBoolean(LoadPrefsUtil.PREF_KEY_SHOW_IPV4, it) }
        }
    )

    SwitchPrefRow(
        title = stringResource(R.string.prefTitleSshowIpv6InNotification),
        description = stringResource(R.string.prefSummaryShowIpv6InNotification),
        checked = showIpv6,
        onCheckedChange = {
            showIpv6 = it
            prefs.edit { putBoolean(LoadPrefsUtil.PREF_KEY_SHOW_IPV6, it) }
        }
    )

    SwitchPrefRow(
        title = stringResource(R.string.prefTitleShowStartStopNotification),
        description = stringResource(R.string.prefSummaryShowStartStopNotification),
        checked = showStartStop,
        onCheckedChange = {
            showStartStop = it
            prefs.edit { putBoolean(LoadPrefsUtil.PREF_KEY_SHOW_START_STOP_NOTIFICATION, it) }
            if (it) {
                NotificationUtil.createStartStopNotification(context)
            } else {
                NotificationUtil.removeStartStopNotification(context)
            }
        }
    )

    SwitchPrefRow(
        title = stringResource(R.string.prefTitleQuickSettingsRequiresUnlock),
        description = stringResource(R.string.prefSummaryQuickSettingsRequiresUnlock),
        checked = quickSettingsUnlock,
        onCheckedChange = {
            quickSettingsUnlock = it
            prefs.edit { putBoolean(LoadPrefsUtil.PREF_QUICK_SETTINGS_REQUIRES_UNLOCK, it) }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun UiPrefsPreview() {
    MaterialTheme {
        SettingsScreen(
            onBack = {},
            section = SettingsSection.UI
        )
    }
}