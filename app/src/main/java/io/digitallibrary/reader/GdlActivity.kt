package io.digitallibrary.reader

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.support.design.widget.TabLayout
import android.support.v4.app.FragmentManager
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import io.digitallibrary.reader.catalog.*
import io.digitallibrary.reader.utilities.SelectionsUtil
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.toolbar.*
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import kotlin.properties.Delegates


/**
 * The type of non-reader activities in the app.
 *
 * This is the where navigation drawer configuration takes place.
 */
class GdlActivity : AppCompatActivity(), FragmentManager.OnBackStackChangedListener {

    companion object {
        private const val TAG = "GdlActivity"

        enum class NavChoices {
            LANGUAGE,
            MY_LIBRARY,
            CATALOG,
            SELECTIONS;

            companion object {
                fun default(): NavChoices {
                    return CATALOG
                }
            }
        }
    }

    private lateinit var mDrawerToggle: ActionBarDrawerToggle
    private lateinit var broadcastReceiver: BroadcastReceiver
    private lateinit var currentCategorySelections: List<Selection>
    private lateinit var categories: List<Category>
    private var themeId: Int by Delegates.notNull()

    override fun onBackStackChanged() {
        Log.i(TAG, "onBackStackChanged c: " + supportFragmentManager.backStackEntryCount)
        mDrawerToggle.isDrawerIndicatorEnabled = supportFragmentManager.backStackEntryCount == 0
        mDrawerToggle.syncState()

        supportFragmentManager.fragments
                .filter { it.isVisible }
                .forEach { it.onResume() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                } else {
                    drawer_layout.openDrawer(Gravity.START)
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        //This method is called when the up button is pressed. Just the pop back stack.
        supportFragmentManager.popBackStack()
        return true
    }

    private fun getNavText(navChoice: NavChoices): String {
        return when (navChoice) {
            NavChoices.LANGUAGE -> getString(R.string.navigation_choice_select_language)
            NavChoices.MY_LIBRARY -> getString(R.string.navigation_choice_my_library)
            NavChoices.CATALOG -> getString(R.string.navigation_choice_selections)
            else -> throw IllegalArgumentException("NavChoice $navChoice does not have a menu text")
        }
    }

    private fun getThemeColor(attr: Int, forTheme: Int): Int {
        val themeAttr = theme.obtainStyledAttributes(forTheme, intArrayOf(attr))
        val color = themeAttr.getColor(0, 0)
        themeAttr.recycle()
        return color
    }

    private fun checkIfNeedToUpdateTheme() {
        val newThemeId = Gdl.getThemeId()
        if (themeId != newThemeId) {
            val primary = getThemeColor(R.attr.colorPrimary,newThemeId)
            val primaryDark = getThemeColor(R.attr.colorPrimaryDark, newThemeId)
            val accent = getThemeColor(R.attr.colorAccent, newThemeId)

            toolbar.setBackgroundColor(primary)
            tabs.setBackgroundColor(primary)
            tabs.setSelectedTabIndicatorColor(accent)
            window.statusBarColor = primaryDark

            themeId = newThemeId
            setTheme(themeId)
        }
    }

    override fun onCreate(state: Bundle?) {
        themeId = Gdl.getThemeId()
        setTheme(themeId)
        super.onCreate(state)
        setContentView(R.layout.activity_main)

        setSupportActionBar(toolbar)
        supportFragmentManager.addOnBackStackChangedListener(this)

        mDrawerToggle = ActionBarDrawerToggle(this, drawer_layout, R.string.navigation_drawer_open, R.string.navigation_drawer_close)

        // Set the drawer toggle as the DrawerListener
        drawer_layout.addDrawerListener(mDrawerToggle)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        mDrawerToggle.syncState()

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                launch(UI) {
                    drawer_layout.closeDrawers()
                    navigation.menu.getItem(NavChoices.LANGUAGE.ordinal).title = getNavText(NavChoices.LANGUAGE)
                    setCurrentFragment(NavChoices.default())
                    setCheckedItem(navigation.menu.getItem(NavChoices.default().ordinal))
                }
            }
        }

        try {
            LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, IntentFilter(SelectLanguageActivity.LANGUAGE_SELECTED))
        } catch (e: NullPointerException) {
            Log.e(TAG, "Error setting up local broadcast receiver")
            e.printStackTrace()
        }

        ViewModelProviders.of(this).get(CatalogViewModel::class.java).getCurrentCategorySelections().observe(this, Observer {
            it?.let {
                currentCategorySelections = it
                updateMenuCategories(it)
                checkIfNeedToUpdateTheme()
            }
        })

        ViewModelProviders.of(this).get(CatalogViewModel::class.java).getCategories().observe(this, Observer {
            it?.let {
                categories = it
                updateTabCategories(it)
                checkIfNeedToUpdateTheme()
            }
        })

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val cat = tab?.tag as Category
                SelectionsUtil.setCategory(cat.link, cat.title)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                // do nothing
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                // do nothing
            }
        })

        launch(UI) {
            val menu: Menu = navigation.menu
            menu.add(NavChoices.LANGUAGE.ordinal, NavChoices.LANGUAGE.ordinal, NavChoices.LANGUAGE.ordinal, getNavText(NavChoices.LANGUAGE))
            val myLibrary = menu.add(NavChoices.CATALOG.ordinal, NavChoices.MY_LIBRARY.ordinal, NavChoices.MY_LIBRARY.ordinal, getNavText(NavChoices.MY_LIBRARY))
            myLibrary.isCheckable = true
            val catalog = menu.add(NavChoices.CATALOG.ordinal, NavChoices.CATALOG.ordinal, NavChoices.CATALOG.ordinal, getNavText(NavChoices.CATALOG))
            catalog.isCheckable = true
            catalog.isChecked = true

            navigation.setNavigationItemSelectedListener {
                when (it.groupId) {
                    NavChoices.LANGUAGE.ordinal -> {
                        val intent = Intent(applicationContext, SelectLanguageActivity::class.java)
                        startActivity(intent)
                    }
                    NavChoices.CATALOG.ordinal -> {
                        when (it.itemId) {
                            NavChoices.MY_LIBRARY.ordinal -> {
                                setCurrentFragment(NavChoices.MY_LIBRARY)
                                setCheckedItem(it)
                            }
                            NavChoices.CATALOG.ordinal -> {
                                setCurrentFragment(NavChoices.CATALOG)
                                setCheckedItem(it)
                            }
                        }
                    }
                    NavChoices.SELECTIONS.ordinal -> {
                        val selection = currentCategorySelections[it.itemId - 10]
                        val intent = Intent(applicationContext, CatalogActivity::class.java)
                        intent.putExtra("selection_link", selection.link)
                        startActivity(intent)
                    }
                }
                drawer_layout.closeDrawers()
                true
            }
        }

        setCurrentFragment(NavChoices.default())

        val id = intent.getLongExtra("download_id", -1)
        if (id >= 0) {
            launch(CommonPool) {
                Gdl.database.bookDownloadDao().getBookDownload(id)?.let {
                    Gdl.database.bookDao().getBook(it.bookId)?.let {
                        val intent = Intent(applicationContext, BookDetailsActivity::class.java)
                        intent.putExtra("book_id", it.id)
                        startActivity(intent)
                    }
                }
            }
        }
    }

    private fun updateMenuCategories(selections: List<Selection>) {
        val menu: Menu = navigation.menu
        menu.removeGroup(NavChoices.SELECTIONS.ordinal)
        selections.forEachIndexed { index, selection ->
            menu.add(NavChoices.SELECTIONS.ordinal, index + 10, NavChoices.SELECTIONS.ordinal, selection.title)
        }
    }

    private fun updateTabCategories(categories: List<Category>) {
        if (categories.size > 1) {
            tabs.visibility = View.VISIBLE
            tabs.removeAllTabs()
            val activeCategoryLink = SelectionsUtil.getCurrentCategoryLink()
            categories.forEach {
                val tab = tabs.newTab()
                tab.text = it.title
                tab.tag = it
                tabs.addTab(tab)
                if (it.link == activeCategoryLink) {
                    tab.select()
                }
            }
        } else {
            tabs.visibility = View.GONE
        }
        checkIfNeedToUpdateTheme()
    }

    private fun setCurrentFragment(navChoice: NavChoices) {
        when (navChoice) {
            GdlActivity.Companion.NavChoices.MY_LIBRARY -> {
                val f = MyLibraryFragment()
                // Need to call commitAllowingStateLoss instead of just commit to avoid crash on old Android versions
                supportFragmentManager.beginTransaction().replace(R.id.content_frame, f).commitAllowingStateLoss()
                tabs.visibility = View.GONE
                setTitle(R.string.my_library_title)
            }
            GdlActivity.Companion.NavChoices.CATALOG -> {
                val f = CatalogFragment()
                // Need to call commitAllowingStateLoss instead of just commit to avoid crash on old Android versions
                supportFragmentManager.beginTransaction().replace(R.id.content_frame, f).commitAllowingStateLoss()
                setTitle(R.string.app_name)
                launch(UI) {
                    async {
                        val link = SelectionsUtil.getCurrentLanguageLink()
                        if (link != null) {
                            Gdl.database.categoryDao().getCategories(link)
                        } else {
                            null
                        }
                    }.await()?.let {
                        updateTabCategories(it)
                    }
                }
            }
            else -> {
                throw IllegalArgumentException("NavChoice $navChoice does not have a fragment")
            }
        }
    }

    private fun setCheckedItem(menuItem: MenuItem) {
        (0 until navigation.menu.size())
                .map { navigation.menu.getItem(it) }
                .forEach { it.isChecked = it == menuItem }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        mDrawerToggle.syncState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mDrawerToggle.onConfigurationChanged(newConfig)
    }

    override fun setTitle(title: CharSequence) {
        supportActionBar?.title = title
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.v(TAG, "onDestroy")

        mDrawerToggle.let {
            drawer_layout?.removeDrawerListener(it)
        }

        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        } catch (e: NullPointerException) {
            Log.e(TAG, "Error removing local broadcast receiver")
            e.printStackTrace()
        }
    }
}
