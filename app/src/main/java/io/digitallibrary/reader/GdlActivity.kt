package io.digitallibrary.reader

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.support.v4.app.FragmentManager
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import io.digitallibrary.reader.catalog.*
import io.digitallibrary.reader.utilities.LanguageUtil
import kotlinx.android.synthetic.main.main.*
import kotlinx.android.synthetic.main.toolbar.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch

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
            CATEGORIES;

            companion object {
                fun default(): NavChoices {
                    return CATALOG
                }
            }
        }
    }

    private lateinit var mDrawerToggle: ActionBarDrawerToggle
    private var broadcastReceiver: BroadcastReceiver? = null

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
            NavChoices.LANGUAGE -> getString(R.string.nav_current_language, LanguageUtil.getCurrentLanguageText())
            NavChoices.MY_LIBRARY -> getString(R.string.nav_my_library)
            NavChoices.CATALOG -> getString(R.string.nav_catalog)
            else -> throw IllegalArgumentException("NavChoice $navChoice does not have a menu text")
        }
    }

    private lateinit var categories: List<Category>

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        Log.v(TAG, "onCreate")

        setContentView(R.layout.main)

        setSupportActionBar(toolbar)
        supportFragmentManager.addOnBackStackChangedListener(this)


        mDrawerToggle = ActionBarDrawerToggle(this, drawer_layout, R.string.drawer_open, R.string.drawer_close)

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
            LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver!!, IntentFilter(SelectLanguageActivity.LANGUAGE_SELECTED))
        } catch (e: NullPointerException) {
            Log.e(TAG, "Error setting up local broadcast receiver")
            e.printStackTrace()
        }

        ViewModelProviders.of(this).get(CatalogViewModel::class.java).getCategories().observe(this, Observer {
            it?.let {
                categories = it
                updateMenuCategories(it)
            }
        })

        launch(UI) {
            val menu: Menu = navigation.menu
            NavChoices.values().forEach {
                if (it != NavChoices.CATEGORIES) {
                    val choice = menu.add(it.ordinal, it.ordinal, it.ordinal, getNavText(it))
                    if (it == NavChoices.default()) {
                        choice.isChecked = true
                    }
                }
            }
            navigation.setNavigationItemSelectedListener {
                when (it.groupId) {
                    NavChoices.LANGUAGE.ordinal -> {
                        val intent = Intent(applicationContext, SelectLanguageActivity::class.java)
                        startActivity(intent)
                    }
                    NavChoices.MY_LIBRARY.ordinal -> {
                        setCurrentFragment(NavChoices.MY_LIBRARY)
                        setCheckedItem(it)
                    }
                    NavChoices.CATALOG.ordinal -> {
                        setCurrentFragment(NavChoices.CATALOG)
                        setCheckedItem(it)
                    }
                    NavChoices.CATEGORIES.ordinal -> {
                        val category = categories[it.itemId - 10]
                        val intent = Intent(applicationContext, CatalogCategoryActivity::class.java)
                        intent.putExtra("category_id", category.id)
                        startActivity(intent)
                    }
                }
                drawer_layout.closeDrawers()
                true
            }
        }

        setCurrentFragment(NavChoices.default())
    }

    private fun updateMenuCategories(categories: List<Category>) {
        val menu: Menu = navigation.menu
        menu.removeGroup(NavChoices.CATEGORIES.ordinal)
        categories.forEachIndexed { index, category ->
            menu.add(NavChoices.CATEGORIES.ordinal, index + 10, NavChoices.CATEGORIES.ordinal, category.title)
        }
    }

    private fun setCurrentFragment(navChoice: NavChoices) {
        when (navChoice) {
            GdlActivity.Companion.NavChoices.MY_LIBRARY -> {
                val f = CatalogDownloadedFragment()
                // Need to call commitAllowingStateLoss instead of just commit to avoid crash on old Android versions
                supportFragmentManager.beginTransaction().replace(R.id.content_frame, f).commitAllowingStateLoss()
                setTitle(R.string.nav_my_library)
            }
            GdlActivity.Companion.NavChoices.CATALOG -> {
                val f = CatalogFragment()
                // Need to call commitAllowingStateLoss instead of just commit to avoid crash on old Android versions
                supportFragmentManager.beginTransaction().replace(R.id.content_frame, f).commitAllowingStateLoss()
                setTitle(R.string.catalog)
            }
            else -> {
                throw IllegalArgumentException("NavChoice $navChoice does not have a fragment")
            }
        }
    }

    private fun setCheckedItem(menuItem: MenuItem) {
        (0 until navigation.menu.size() - 1)
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
            if (broadcastReceiver != null) {
                LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver!!)
                broadcastReceiver = null
            }
        } catch (e: NullPointerException) {
            Log.e(TAG, "Error removing local broadcast receiver")
            e.printStackTrace()
        }
    }
}
