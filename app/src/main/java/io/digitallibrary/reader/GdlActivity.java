package io.digitallibrary.reader;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import io.digitallibrary.reader.utilities.LanguageUtil;

/**
 * The type of non-reader activities in the app.
 * <p>
 * This is the where navigation drawer configuration takes place.
 */

public class GdlActivity extends AppCompatActivity implements FragmentManager.OnBackStackChangedListener {
    private static final String TAG = "GdlActivity";

    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    private TextView currentLang;

    private LinearLayout mCatalogRow;
    private LinearLayout mMyLibraryRow;

    public static final String MENU_CHOICE_PREF = "MENU_CHOICE_PREF";
    private static MenuChoices currentMenuChoice;
    private SharedPreferences.OnSharedPreferenceChangeListener langListener;

    @Override
    public void onBackStackChanged() {
        Log.i(TAG, "onBackStackChanged c: " + getSupportFragmentManager().getBackStackEntryCount());
        mDrawerToggle.setDrawerIndicatorEnabled(getSupportFragmentManager().getBackStackEntryCount() == 0);
        mDrawerToggle.syncState();

        List<Fragment> fragments = getSupportFragmentManager().getFragments();
        for (Fragment f : fragments) {
            if (f.isVisible()) {
                f.onResume();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    getSupportFragmentManager().popBackStack();
                } else {
                    mDrawerLayout.openDrawer(Gravity.START);
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        //This method is called when the up button is pressed. Just the pop back stack.
        getSupportFragmentManager().popBackStack();
        return true;
    }

    public enum MenuChoices {
        CATALOG,
        MY_BOOKS;

        public static MenuChoices getDefault() {
            return CATALOG;
        }
    }

    public GdlActivity() {}

    @Override
    protected void onCreate(final Bundle state) {
        super.onCreate(state);
        Log.v(TAG, "onCreate");
        setContentView(R.layout.main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportFragmentManager().addOnBackStackChangedListener(this);

        currentLang = findViewById(R.id.current_language);
        currentLang.setText(getString(R.string.nav_current_language, LanguageUtil.getCurrentLanguageText()));

        mDrawerLayout = findViewById(R.id.drawer_layout);
        mCatalogRow = findViewById(R.id.catalog_row);
        mMyLibraryRow = findViewById(R.id.my_library_row);

        mDrawerLayout = findViewById(R.id.drawer_layout);
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
                R.string.drawer_open, R.string.drawer_close);

        // Set the drawer toggle as the DrawerListener
        mDrawerLayout.addDrawerListener(mDrawerToggle);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        mDrawerToggle.syncState();
        currentMenuChoice = MenuChoices.getDefault();
        updateNavigationRows();
        updateFragment();


        langListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if (key == LanguageUtil.getLangPrefKey()) {
                    updateFragment();
                }
            }
        };
        Gdl.getSharedPrefs().registerListener(langListener);
    }

    private void updateNavigationRows() {
        if (currentMenuChoice == MenuChoices.CATALOG) {
            mCatalogRow.setBackgroundResource(R.drawable.nav_menu_background_pressed);
        } else {
            mCatalogRow.setBackgroundResource(R.drawable.navigation_drawer_item_background);
        }
        if (currentMenuChoice == MenuChoices.MY_BOOKS) {
            mMyLibraryRow.setBackgroundResource(R.drawable.nav_menu_background_pressed);
        } else {
            mMyLibraryRow.setBackgroundResource(R.drawable.navigation_drawer_item_background);
        }
    }

    private void updateFragment() {
//        Fragment f;
//        if (currentMenuChoice == MenuChoices.CATALOG) {
//            final GdlCatalogAppServicesType app = Gdl.getCatalogAppServices();
//            final BooksType books = app.getBooks();
//            final BooksControllerConfigurationType books_config = books.booksGetConfiguration();
//            CatalogFeedArgumentsRemote remote = new CatalogFeedArgumentsRemote(getString(R.string.catalog), books_config.getCurrentRootFeedURI());
//            f = new MainCatalogFragment();
//            Bundle b = new Bundle();
//            CatalogFeedFragment.setFragmentArguments(b, remote);
//            f.setArguments(b);
//        } else if (currentMenuChoice == MenuChoices.MY_BOOKS) {
//            CatalogFeedArgumentsLocalBooks local = new CatalogFeedArgumentsLocalBooks(
//                    getString(R.string.nav_my_library),
//                    BooksFeedSelection.BOOKS_FEED_LOANED);
//            f = new MainBooksFragment();
//            Bundle b = new Bundle();
//            CatalogFeedFragment.setFragmentArguments(b, local);
//            f.setArguments(b);
//        } else {
//            throw new UnreachableCodeException();
//        }
//        getSupportFragmentManager().beginTransaction().replace(R.id.content_frame, f).commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        currentLang.setText(getString(R.string.nav_current_language, LanguageUtil.getCurrentLanguageText()));
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return super.onPrepareOptionsMenu(menu);
    }


    public void onCloseDrawerClicked(View view) {
        mDrawerLayout.closeDrawers();
    }

    public void onLanguageClicked(View view) {
        Log.v(TAG, "onLanguageClicked");
        Intent i = new Intent(this, SelectLanguageActivity.class);
        startActivity(i);
    }

    public void onMyLibraryClicked(View view) {
        Log.v(TAG, "onMyLibraryClicked");
        if (currentMenuChoice == MenuChoices.MY_BOOKS) {
            return;
        }
        currentMenuChoice = MenuChoices.MY_BOOKS;
        updateNavigationRows();
        updateFragment();
        mDrawerLayout.closeDrawers();
    }

    public void onCatalogClicked(View view) {
        Log.v(TAG, "onCatalogClicked");
        if (currentMenuChoice == MenuChoices.CATALOG) {
            return;
        }
        currentMenuChoice = MenuChoices.CATALOG;
        updateNavigationRows();
        updateFragment();
        mDrawerLayout.closeDrawers();
    }

    @Override
    public void setTitle(CharSequence title) {
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            getSupportActionBar().setTitle(title);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy");
        mDrawerLayout.removeDrawerListener(mDrawerToggle);
        Gdl.getSharedPrefs().unregisterListener(langListener);
    }
}
