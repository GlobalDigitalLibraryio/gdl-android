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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import io.digitallibrary.reader.catalog.Language
import io.digitallibrary.reader.utilities.LanguageUtil
import kotlinx.android.synthetic.main.activity_select_language.*
import kotlinx.android.synthetic.main.toolbar.*

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
        super.onCreate(state)
        setContentView(R.layout.activity_select_language)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.languages_selection_title)

        var currentLanguageText = ""
        val languages: MutableList<Language> = ArrayList()

        val langItemsAdapter = object : ArrayAdapter<String>(this@SelectLanguageActivity, R.layout.item_language_row, R.id.language_name) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                if (getItem(position) == currentLanguageText) {
                    v.isSelected = true
                    v.findViewById<View>(R.id.checked).visibility = View.VISIBLE
                    v.findViewById<TextView>(R.id.language_name).setTextColor(ContextCompat.getColor(context, R.color.gdl_green))
                } else {
                    v.findViewById<View>(R.id.checked).visibility = View.INVISIBLE
                    v.findViewById<TextView>(R.id.language_name).setTextColor(ContextCompat.getColor(context, R.color.dark_text))
                }
                return v
            }
        }

        language_list.adapter = langItemsAdapter

        ViewModelProviders.of(this).get(LanguagesViewModel::class.java).currentLanguageText.observe(this, Observer {
            it?.let { currentLanguageText = it }
        })

        val shortDuration = resources.getInteger(android.R.integer.config_shortAnimTime).toLong()
        ViewModelProviders.of(this).get(LanguagesViewModel::class.java).languages.observe(this, Observer {
            it?.let {
                if (it.isEmpty()) {
                    spinner.visibility = View.VISIBLE
                    spinner.alpha = 0f
                    spinner.animate().alpha(1f).setDuration(shortDuration).setListener(null)
                    language_list.animate().alpha(0f).setDuration(shortDuration).setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator?) {
                            super.onAnimationEnd(animation)
                            language_list.visibility = View.GONE
                        }
                    })
                } else {
                    language_list.visibility = View.VISIBLE
                    language_list.alpha = 0f
                    language_list.animate().alpha(1f).setDuration(shortDuration).setListener(null)
                    spinner.animate().alpha(0f).setDuration(shortDuration).setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator?) {
                            super.onAnimationEnd(animation)
                            spinner.visibility = View.GONE
                        }
                    })
                }

                languages.clear()
                languages.addAll(it)
                langItemsAdapter.clear()
                langItemsAdapter.addAll(it.map { it.languageName })
                langItemsAdapter.notifyDataSetChanged()
            }
        })

        language_list.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            LanguageUtil.setLanguage(languages[position].link)
            Gdl.fetchOpdsFeed()
            val intent = Intent(LANGUAGE_SELECTED)
            LocalBroadcastManager.getInstance(Gdl.appContext).sendBroadcast(intent)
            finish()
        }
    }
}