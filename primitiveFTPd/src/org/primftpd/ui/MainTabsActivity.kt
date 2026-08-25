package org.primftpd.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.primftpd.R
import org.primftpd.events.ServerStateChangedEvent
import org.primftpd.ui.ShizukuFtpTheme
import org.primftpd.prefs.LoadPrefsUtil
import org.primftpd.util.EncryptionUtil
import org.primftpd.util.ServicesStartStopUtil

open class MainTabsActivity : FragmentActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        const val INDEX_MAIN = 0
        const val INDEX_FINGERPRINTS = 1
        const val INDEX_PREFS = 2
        const val INDEX_ABOUT = 3
        const val INDEX_LOG = 4
        const val DIALOG_TAG = "dialogs"
    }

    private var isServerRunning by mutableStateOf(false)
    private var showPasswordDialog by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        isServerRunning = ServicesStartStopUtil.checkServicesRunning(this).atLeastOneRunning()

        setContent {
            ShizukuFtpTheme {
                val navController = rememberNavController()

                if (showPasswordDialog) {
                    PasswordInputDialog(
                        onDismiss = { showPasswordDialog = false },
                        onSave = { password ->
                            val prefs = LoadPrefsUtil.getPrefs(this)
                            val encryptedPassword = EncryptionUtil.encrypt(password)
                            prefs.edit {
                                putString(
                                    LoadPrefsUtil.PREF_KEY_PASSWORD,
                                    encryptedPassword
                                )
                            }
                            showPasswordDialog = false
                            handleStart()
                        }
                    )
                }

                NavHost(
                    navController = navController,
                    startDestination = "main"
                ) {
                    composable("main") {
                        MainScreen(
                            isServerRunning = isServerRunning,
                            onStartServer = { handleStart() },
                            onStopServer = { handleStop() },
                            onNavigate = { route -> navController.navigate(route) }
                        )
                    }
                    composable("about") {
                        AboutScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("qr") {
                        FragmentContainerScreen(
                            "扫码了",
                            { QrFragment(null) },
                            { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = "settings/{section}",
                        arguments = listOf(navArgument("section") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val sectionName = backStackEntry.arguments?.getString("section") ?: "auth"
                        val settingsSection = when (sectionName) {
                            "connecting" -> SettingsSection.CONNECTIVITY
                            "ui" -> SettingsSection.UI
                            "system" -> SettingsSection.SYSTEM
                            else -> SettingsSection.AUTH
                        }
                        SettingsScreen(
                            section = settingsSection,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("netWorkStatus") {
                        NetworkStatusScreen(
                            isServerRunning = isServerRunning,
                            onStartServer = { handleStart() },
                            onStopServer = { handleStop() },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("clientStatus") {
                        FragmentContainerScreen(
                            "clientStatus",
                            { ClientActionFragment() },
                            { navController.popBackStack() }
                        )
                    }
                    composable("VerificationKey") {
                        FragmentContainerScreen(
                            "Verification Key",
                            { PubKeyAuthKeysFragment(true) },
                            { navController.popBackStack() }
                        )
                    }
                    composable("fingerPrint") {
                        FragmentContainerScreen(
                            "fingerPrint",
                            { KeysFingerprintsFragment() },
                            { navController.popBackStack() }
                        )
                    }
                    composable("clean") {
                        FragmentContainerScreen(
                            "cleaner",
                            { CleanSpaceFragment() },
                            { navController.popBackStack() }
                        )
                    }
                }
            }
        }

        EventBus.getDefault().register(this)
        LoadPrefsUtil.getPrefs(this).registerOnSharedPreferenceChangeListener(this)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        return super.onCreateOptionsMenu(menu)
    }

    private fun handleStart() {
        val context = this
        val prefs = LoadPrefsUtil.getPrefs(context)
        val prefsBean = LoadPrefsUtil.loadPrefs(org.slf4j.LoggerFactory.getLogger(javaClass), prefs)

        if (prefsBean.serverToStart.isPasswordMandatory(prefsBean) &&
            org.primftpd.util.StringUtils.isBlank(prefsBean.password)
        ) {
            showPasswordDialog = true
            return
        }

        if (prefsBean.serverToStart.startSftp()) {
            val keyProvider = org.primftpd.util.KeyFingerprintProvider()

            if (!keyProvider.areFingerprintsGenerated()) {
                keyProvider.calcPubkeyFingerprints(context)
            }

            val keyPresent = keyProvider.isKeyPresent
            if (!keyPresent) {
                val askDiag = GenKeysAskDialogFragment()
                val args = Bundle().apply {
                    putBoolean(GenKeysAskDialogFragment.KEY_START_SERVER, true)
                }
                askDiag.arguments = args
                askDiag.show(supportFragmentManager, DIALOG_TAG)
                return
            }
        }

        ServicesStartStopUtil.startServers(this)
    }


    private fun handleStop() {
        ServicesStartStopUtil.stopServers(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onEvent(event: ServerStateChangedEvent) {
        isServerRunning = ServicesStartStopUtil.checkServicesRunning(this).atLeastOneRunning()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (isServerRunning) {
            Toast.makeText(this, R.string.restartServer, Toast.LENGTH_LONG).show()
        }
        if (LoadPrefsUtil.PREF_KEY_HOSTKEY_ALGOS == key) {
            val askDiag = GenKeysAskDialogFragment()
            askDiag.show(supportFragmentManager, DIALOG_TAG)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LoadPrefsUtil.getPrefs(this).unregisterOnSharedPreferenceChangeListener(this)
        EventBus.getDefault().unregister(this)
    }
}
