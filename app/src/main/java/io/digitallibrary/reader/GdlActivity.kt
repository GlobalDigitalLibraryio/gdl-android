package io.digitallibrary.reader

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.support.v4.app.FragmentManager
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import io.digitallibrary.reader.catalog.CatalogDownloadedFragment
import io.digitallibrary.reader.catalog.CatalogFragment
import io.digitallibrary.reader.utilities.LanguageUtil
import kotlinx.android.synthetic.main.main.*
import kotlinx.android.synthetic.main.toolbar.*

/**
 * The type of non-reader activities in the app.
 *
 * This is the where navigation drawer configuration takes place.
 */
class GdlActivity : AppCompatActivity(), FragmentManager.OnBackStackChangedListener {

    companion object {
        private const val TAG = "GdlActivity"
    }

    private var currentMenuChoice: MenuChoices = MenuChoices.default()
    private lateinit var mDrawerToggle: ActionBarDrawerToggle
    private var langListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

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

    enum class MenuChoices {
        CATALOG,
        MY_BOOKS;

        companion object {
            fun default(): MenuChoices { return CATALOG }
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        Log.v(TAG, "onCreate")

        setContentView(R.layout.main)

        setSupportActionBar(toolbar)
        supportFragmentManager.addOnBackStackChangedListener(this)

        current_language.text = getString(R.string.nav_current_language, LanguageUtil.getCurrentLanguageText())

        mDrawerToggle = ActionBarDrawerToggle(this, drawer_layout, R.string.drawer_open, R.string.drawer_close)

        // Set the drawer toggle as the DrawerListener
        drawer_layout.addDrawerListener(mDrawerToggle)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        mDrawerToggle.syncState()
        updateFragment(MenuChoices.default())

        langListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == LanguageUtil.getLangPrefKey()) {
                updateFragment(MenuChoices.CATALOG)
                drawer_layout.closeDrawers()
            }
        }
        Gdl.sharedPrefs.registerListener(langListener)
    }

    private fun updateNavigationRows() {
        if (currentMenuChoice == MenuChoices.CATALOG) {
            catalog_row.setBackgroundResource(R.drawable.nav_menu_background_pressed)
        } else {
            catalog_row.setBackgroundResource(R.drawable.navigation_drawer_item_background)
        }
        if (currentMenuChoice == MenuChoices.MY_BOOKS) {
            my_library_row.setBackgroundResource(R.drawable.nav_menu_background_pressed)
        } else {
            my_library_row.setBackgroundResource(R.drawable.navigation_drawer_item_background)
        }
    }

    private fun updateFragment(newChoice: MenuChoices) {
        currentMenuChoice = newChoice
        when (currentMenuChoice) {
            MenuChoices.CATALOG -> {
                val f = CatalogFragment()
                // Need to call commitAllowingStateLoss instead of just commit to avoid crash on old Android versions
                supportFragmentManager.beginTransaction().replace(R.id.content_frame, f).commitAllowingStateLoss()
                setTitle(R.string.catalog)
            }
            MenuChoices.MY_BOOKS -> {
                val f = CatalogDownloadedFragment()
                supportFragmentManager.beginTransaction().replace(R.id.content_frame, f).commitAllowingStateLoss()
                setTitle(R.string.nav_my_library)
            }
        }
        updateNavigationRows()
    }

    override fun onResume() {
        super.onResume()
        current_language.text = getString(R.string.nav_current_language, LanguageUtil.getCurrentLanguageText())
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        mDrawerToggle.syncState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mDrawerToggle.onConfigurationChanged(newConfig)
    }

    fun onCloseDrawerClicked(view: View) {
        drawer_layout.closeDrawers()
    }

    fun onLanguageClicked(view: View) {
        Log.v(TAG, "onLanguageClicked")
        val i = Intent(this, SelectLanguageActivity::class.java)
        startActivity(i)
    }

    fun onMyLibraryClicked(view: View) {
        Log.v(TAG, "onMyLibraryClicked")
        if (currentMenuChoice == MenuChoices.MY_BOOKS) {
            return
        }
        updateFragment(MenuChoices.MY_BOOKS)
        drawer_layout.closeDrawers()
    }

    fun onCatalogClicked(view: View) {
        Log.v(TAG, "onCatalogClicked")
        if (currentMenuChoice == MenuChoices.CATALOG) {
            return
        }
        updateFragment(MenuChoices.CATALOG)
        drawer_layout.closeDrawers()
    }

    override fun setTitle(title: CharSequence) {
        val bar = supportActionBar
        if (bar != null) {
            supportActionBar?.title = title
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.v(TAG, "onDestroy")
        mDrawerToggle.let {
            drawer_layout?.removeDrawerListener(it)
        }
        Gdl.sharedPrefs.unregisterListener(langListener)
        langListener = null
    }
}
