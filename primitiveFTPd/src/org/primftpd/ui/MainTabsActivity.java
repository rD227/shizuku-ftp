package org.primftpd.ui

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.EdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.tabs.TabLayout
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.primftpd.R
import org.primftpd.events.ServerStateChangedEvent
import org.primftpd.log.LogController
import org.primftpd.prefs.FtpPrefsFragment
import org.primftpd.prefs.LoadPrefsUtil
import org.primftpd.prefs.Logging
import org.primftpd.util.NotificationUtil
import org.primftpd.util.ServicesStartStopUtil
import org.slf4j.LoggerFactory

open class MainTabsActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        @JvmStatic
        protected var INDEX_FINGERPRINTS = 0
        protected const val TAB_NAME_MAIN_UI = "pftpd"
        protected const val TAB_NAME_QR = "QR"
    }

    private var logger = LoggerFactory.getLogger(javaClass)

    protected var startIcon: MenuItem? = null
    protected var stopIcon: MenuItem? = null

    protected lateinit var pftpdFragment: PftpdFragment
    protected lateinit var qrFragment: QrFragment
    private lateinit var adapter: MainAdapter

    protected open fun createPftpdFragment(): PftpdFragment {
        return PftpdFragment()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        logger.trace("onCreate()")

        // EdgeToEdge on Android pre-15
        // There are some serious insets listener issues on API 28/29,
        // ViewPager2 also documents a serious bug when using API < 30.
        // I haven't checked ViewPager v1... but migration to ViewPager2 is a TODO
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            EdgeToEdge.enable(this)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tabs_activity)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.isNavigationBarContrastEnforced = false
        }

        val appBarLayout = findViewById<AppBarLayout>(R.id.app_bar)
        val tabLayout = findViewById<TabLayout>(R.id.tabs)
        val viewPager = findViewById<ViewPager>(R.id.view_pager)
        tabLayout.setupWithViewPager(viewPager)

        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { v, insetsCompat ->
            val insets = insetsCompat.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            insetsCompat
        }

        adapter = MainAdapter(supportFragmentManager)
        viewPager.adapter = adapter

        pftpdFragment = createPftpdFragment()
        qrFragment = QrFragment(pftpdFragment)
        adapter.addFragment(pftpdFragment)
        adapter.addFragment(qrFragment)
        adapter.addFragment(CleanSpaceFragment())
        adapter.addFragment(ClientActionFragment())
        adapter.addFragment(KeysFingerprintsFragment())
        INDEX_FINGERPRINTS = adapter.count - 1
        adapter.addFragment(PubKeyAuthKeysFragment(isLeanback()))
        adapter.addFragment(FtpPrefsFragment())
        adapter.addFragment(AboutFragment())
        updateTabNames()

        // listen for events
        EventBus.getDefault().register(this)

        val prefs = LoadPrefsUtil.getPrefs(this)
        prefs.registerOnSharedPreferenceChangeListener(this)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val tabCharSeq = tab.text
                if (tabCharSeq != null) {
                    val tabText = tabCharSeq.toString()
                    if (TAB_NAME_QR == tabText) {
                        qrFragment.drawIfChanged()
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    protected open fun isLeanback(): Boolean {
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)

        val prefs = LoadPrefsUtil.getPrefs(this)
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun updateTabNames() {
        val prefs = LoadPrefsUtil.getPrefs(this)
        val tabNames = prefs.getBoolean(LoadPrefsUtil.PREF_KEY_SHOW_TAB_NAMES, false)
        adapter.clearTitles()
        adapter.addTitle(TAB_NAME_MAIN_UI)
        adapter.addTitle(TAB_NAME_QR)

        val tabLayout = findViewById<TabLayout>(R.id.tabs)
        if (tabNames) {
            adapter.addTitle("\uD83D\uDDD1 " + getText(R.string.iconCleanSpace))
            adapter.addTitle("\uD83D\uDDD2 " + getText(R.string.clientActionsLabel))
            adapter.addTitle("\uD83D\uDD11 " + getText(R.string.iconKeysFingerprints))
            adapter.addTitle("\uD83D\uDD10 " + getText(R.string.pubkeyAuthKeysHeading))
            adapter.addTitle("⚙ " + getText(R.string.prefs))
            adapter.addTitle("\uD83D\uDE4F " + getText(R.string.iconAbout))

            tabLayout.tabMode = TabLayout.MODE_SCROLLABLE
        } else {
            adapter.addTitle("\uD83D\uDDD1")
            adapter.addTitle("\uD83D\uDDD2")
            adapter.addTitle("\uD83D\uDD11")
            adapter.addTitle("\uD83D\uDD10")
            adapter.addTitle("⚙")
            adapter.addTitle("\uD83D\uDE4F")

            tabLayout.tabMode = TabLayout.MODE_FIXED
        }
        adapter.notifyDataSetChanged()
    }

    private inner class MainAdapter(supportFragmentManager: FragmentManager) :
        FragmentPagerAdapter(supportFragmentManager) {

        private val fragments = ArrayList<Fragment>()
        private val titles = ArrayList<CharSequence>()

        fun addFragment(fragment: Fragment) {
            fragments.add(fragment)
        }

        fun clearTitles() {
            titles.clear()
        }

        fun addTitle(title: String) {
            titles.add(title)
        }

        override fun getItem(position: Int): Fragment {
            logger.trace("getItem({})", position)
            return fragments[position]
        }

        override fun getCount(): Int {
            // logger.trace("getCount()") // don't log this as it gets called too often
            return fragments.size
        }

        override fun getPageTitle(position: Int): CharSequence {
            logger.trace("getPageTitle({})", position)
            return titles[position]
        }
    }

    override fun onResume() {
        super.onResume()
        logger.debug("onResume()")
        updateButtonStates()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        logger.debug("onCreateOptionsMenu()")

        menuInflater.inflate(R.menu.pftpd, menu)

        startIcon = menu.findItem(R.id.menu_start)
        stopIcon = menu.findItem(R.id.menu_stop)

        // at least required on app start
        updateButtonStates()

        return true
    }

    protected open fun updateButtonStates() {
        logger.debug("updateButtonStates()")

        val atLeastOneRunning = ServicesStartStopUtil.checkServicesRunning(this).atLeastOneRunning()

        // remove status bar notification if server not running
        if (!atLeastOneRunning) {
            NotificationUtil.removeStatusbarNotification(this)
        }

        // action bar icons
        if (startIcon == null || stopIcon == null) {
            return
        }

        startIcon?.isVisible = !atLeastOneRunning
        stopIcon?.isVisible = atLeastOneRunning
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        logger.debug("onOptionsItemSelected()")
        val itemId = item.itemId
        when (itemId) {
            R.id.menu_start -> handleStart()
            R.id.menu_stop -> handleStop()
        }

        return super.onOptionsItemSelected(item)
    }

    fun handleStart() {
        logger.trace("handleStart()")
        ServicesStartStopUtil.startServers(pftpdFragment)
    }

    protected open fun handleStop() {
        logger.trace("handleStop()")
        ServicesStartStopUtil.stopServers(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onEvent(event: ServerStateChangedEvent) {
        logger.debug("got ServerStateChangedEvent")
        updateButtonStates()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        val atLeastOneRunning = ServicesStartStopUtil.checkServicesRunning(this).atLeastOneRunning()
        if (atLeastOneRunning) {
            Toast.makeText(this, R.string.restartServer, Toast.LENGTH_LONG).show()
        }
        if (LoadPrefsUtil.PREF_KEY_LOGGING == key) {
            handleLoggingPref()
        }
        if (LoadPrefsUtil.PREF_KEY_SHOW_TAB_NAMES == key) {
            updateTabNames()
        }
        if (LoadPrefsUtil.PREF_KEY_HOSTKEY_ALGOS == key) {
            val askDiag = GenKeysAskDialogFragment(pftpdFragment)
            askDiag.show(supportFragmentManager, PftpdFragment.DIALOG_TAG)
        }
    }

    protected open fun handleLoggingPref() {
        val logging = LogController.readPrefs(this)
        logger.debug("got 'logging': {}", logging)

        val activeLogging = LogController.getActiveConfig()

        val recreateLogger = activeLogging != logging

        if (recreateLogger) {
            // re-create own log and log of relevant fragments, don't care about other classes
            LogController.setActiveConfig(this, logging)
            logger = LoggerFactory.getLogger(javaClass)
            logger.debug("changed logging")

            val cnt = adapter.count
            for (i in 0 until cnt) {
                val fragment = adapter.getItem(i)
                if (fragment is RecreateLogger) {
                    fragment.recreateLogger()
                }
            }
        }
    }
}