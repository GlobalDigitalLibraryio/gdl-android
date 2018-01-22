package io.digitallibrary.reader

import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import io.digitallibrary.reader.utilities.LanguageUtil
import kotlinx.android.synthetic.main.select_language.*
import java.util.*

class SelectLanguageActivity : AppCompatActivity() {
    companion object {
        val TAG = "SelectLanguageActivity"
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
        setContentView(R.layout.select_language)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.languages_title)

        fetchLanguages()
    }

    private fun fetchLanguages() {
        LanguageUtil.getLanguageListFromServer(this, object : LanguageUtil.Callback {
            override fun onSuccess(languages: Map<String, String>) {
                val langArray = ArrayList(languages.keys)
                Collections.sort(langArray)
                var selected = -1
                for (i in langArray.indices) {
                    if (langArray[i] == LanguageUtil.getCurrentLanguageText()) {
                        selected = i
                        continue
                    }
                }
                val langItemsAdapter = object : ArrayAdapter<String>(this@SelectLanguageActivity, R.layout.language_row, R.id.language_name, langArray) {
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val v = super.getView(position, convertView, parent)
                        if (position == selected) {
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

                spinner.visibility = View.GONE
                language_list.visibility = View.VISIBLE
                language_list.adapter = langItemsAdapter
                language_list.setItemChecked(selected, true)
                language_list.onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
                    selected = position
                    val newLang = langArray[position]
                    val newLangCode = languages[newLang]
                    val oldLang = LanguageUtil.getCurrentLanguage()
                    LanguageUtil.setLanguage(newLangCode, newLang)
                    view.findViewById<View>(R.id.checked).visibility = View.VISIBLE
                    view.findViewById<TextView>(R.id.language_name).setTextColor(ContextCompat.getColor(baseContext, R.color.gdl_green))
                    langItemsAdapter.notifyDataSetChanged()
                    if (oldLang != newLang) {
                        Gdl.fetch()
                    }
                    finish()
                }
            }

            override fun onError() {
                spinner.visibility = View.GONE
                error_msg.visibility = View.VISIBLE
            }
        })
    }
}
