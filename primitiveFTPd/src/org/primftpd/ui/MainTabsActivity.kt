package org.primftpd.ui

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.NonNull
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.ExperimentalFoundationApi//Import experimental library
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
//There is HorizontalPager and rememberPagerState
import androidx.core.graphics.Insets
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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.ArrayList
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.launch

open class MainTabsActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var logger: Logger = LoggerFactory.getLogger(javaClass)

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
        enableEdgeToEdge()

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
            val insets: Insets = insetsCompat.getInsets(
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

    private inner class MainAdapter(supportFragmentManager: FragmentManager) : FragmentPagerAdapter(supportFragmentManager) {
        var fragments = ArrayList<Fragment>()
        var titles = ArrayList<CharSequence>()

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
            return fragments.size
        }

        override fun getPageTitle(position: Int): CharSequence? {
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
        updateButtonStates()
        return true
    }

    protected open fun updateButtonStates() {
        logger.debug("updateButtonStates()")
        val atLeastOneRunning = ServicesStartStopUtil.checkServicesRunning(this).atLeastOneRunning()

        if (!atLeastOneRunning) {
            NotificationUtil.removeStatusbarNotification(this)
        }

        val start = startIcon ?: return
        val stop = stopIcon ?: return

        start.isVisible = !atLeastOneRunning
        stop.isVisible = atLeastOneRunning
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        logger.debug("onOptionsItemSelected()")
        val itemId = item.itemId
        if (itemId == R.id.menu_start) {
            handleStart()
        } else if (itemId == R.id.menu_stop) {
            handleStop()
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
            LogController.setActiveConfig(this, logging)
            this.logger = LoggerFactory.getLogger(javaClass)
            logger.debug("changed logging")

            val cnt = adapter.count
            for (i in 0 until cnt) {
                val fragment = adapter.getItem(i)
                if (fragment is RecreateLogger) {
                    (fragment as RecreateLogger).recreateLogger()
                }
            }
        }
    }

    companion object {
        @JvmField protected var INDEX_FINGERPRINTS = 0
        protected const val TAB_NAME_MAIN_UI = "pftpd"
        protected const val TAB_NAME_QR = "QR"
    }
}
/*
* debug function
*
*
* */

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Preview
fun MainTabsScreen() {
    // 1. 定义选项卡页面
    val tabs = listOf("服务器控制", "设置", "关于")
    // 2. 记住当前选中的页面状态，以及配置 Pager
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // 3. 顶部的选项卡栏 (替代了 TabLayout)
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        // 点击 Tab 时，让下方的内容平滑滚动过去
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = { Text(title) }
                )
            }
        }
/*
*
*或者这种种方法
        // 4. 下方可以滑动的内容区域 (替代了 ViewPager 和 Fragment)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            // 根据当前页码，显示不同的 Compose 界面
            when (page) {
                0 -> FtpServerControlScreen() //  FTP 启停、Shizuku 授权等核心界面
                1 -> SettingsScreen()         // 设置界面
                2 -> AboutScreen()            // 关于界面
            }
        }
        8？
 */
    }
}