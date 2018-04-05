package io.digitallibrary.reader

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import io.digitallibrary.reader.catalog.Language
import io.digitallibrary.reader.utilities.SelectionsUtil
import kotlinx.android.synthetic.main.activity_select_language.*
import kotlinx.android.synthetic.main.toolbar.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch

class SelectLanguageActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "SelectLanguageActivity"
        const val LANGUAGE_SELECTED = "LANGUAGE_SELECTED"
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreate(state: Bundle?) {
        setTheme(Gdl.getSettingsThemeId())
        super.onCreate(state)
        setContentView(R.layout.activity_select_language)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.languages_selection_title)

        // -1 means not set
        // Correct value will be set when languages is added to the adapter
        var selectedLangPosition = -1

        val langItemsAdapter = object : ArrayAdapter<Language>(this@SelectLanguageActivity, R.layout.item_language_row, R.id.language_name) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                if (position == selectedLangPosition) {
                    v.isSelected = true
                    v.findViewById<View>(R.id.checked).visibility = View.VISIBLE
                    v.findViewById<TextView>(R.id.language_name).setTextColor(ContextCompat.getColor(context, R.color.gdl_green))
                    v.findViewById<TextView>(R.id.language_name).text = getItem(position).languageName
                } else {
                    v.findViewById<View>(R.id.checked).visibility = View.INVISIBLE
                    v.findViewById<TextView>(R.id.language_name).setTextColor(ContextCompat.getColor(context, R.color.dark_text))
                    v.findViewById<TextView>(R.id.language_name).text = getItem(position).languageName
                }
                return v
            }
        }

        language_list.adapter = langItemsAdapter

        val shortDuration = resources.getInteger(android.R.integer.config_shortAnimTime).toLong()
        ViewModelProviders.of(this).get(LanguagesViewModel::class.java).languages.observe(this, Observer {
            it?.let {
                val fadeFromView = if (it.isEmpty()) language_list else spinner
                val fadeToView = if (it.isEmpty()) spinner else language_list

                fadeFromView.animate().alpha(0f).setDuration(shortDuration).setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator?) {
                        super.onAnimationEnd(animation)
                        fadeFromView.visibility = View.GONE
                    }
                })

                fadeToView.visibility = View.VISIBLE
                fadeToView.alpha = 0f
                fadeToView.animate().alpha(1f).setDuration(shortDuration).setListener(null)

                val currentLink = SelectionsUtil.getCurrentLanguageLink()
                selectedLangPosition = it.indexOfFirst { it.link == currentLink }

                langItemsAdapter.clear()
                langItemsAdapter.addAll(it)
                langItemsAdapter.notifyDataSetChanged()

                // Need to wait for the view to be drawn, before we know if we have to scroll
                val layoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        language_list.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        if (selectedLangPosition != -1) {
                            if (language_list.lastVisiblePosition < selectedLangPosition + 2) {
                                language_list.smoothScrollToPosition(selectedLangPosition + 2)
                            } else if (language_list.firstVisiblePosition > selectedLangPosition - 2) {
                                language_list.smoothScrollToPosition(selectedLangPosition - 2)
                            }
                        }
                    }
                }
                language_list.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
            }
        })

        language_list.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            SelectionsUtil.setLanguage(langItemsAdapter.getItem(position).link, langItemsAdapter.getItem(position).languageName)
            // Try to get default category, if we don't have any yet, the OPDS parser will set one
            launch(UI) {
                val categories = async { Gdl.database.categoryDao().getCategories(langItemsAdapter.getItem(position).link) }.await()
                if (categories.isNotEmpty()) {
                    SelectionsUtil.setCategory(categories[0].link, categories[0].title)
                }
            }
            Gdl.fetchOpdsFeed()
            val intent = Intent(LANGUAGE_SELECTED)
            LocalBroadcastManager.getInstance(Gdl.appContext).sendBroadcast(intent)
            finish()
        }
    }
}