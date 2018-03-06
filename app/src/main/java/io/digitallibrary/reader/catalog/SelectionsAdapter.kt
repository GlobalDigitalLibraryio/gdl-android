package io.digitallibrary.reader.catalog

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.graphics.Typeface.BOLD
import android.support.v4.app.Fragment
import android.support.v7.widget.RecyclerView
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.digitallibrary.reader.Gdl
import io.digitallibrary.reader.LanguagesViewModel
import io.digitallibrary.reader.R
import kotlinx.android.synthetic.main.item_catalog_language_bar.view.*
import kotlinx.android.synthetic.main.item_catalog_selection.view.*


class SelectionsAdapter(val fragment: Fragment, val callback: Callback) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_SELECTION = 1
    }

    private var selections: List<Selection> = emptyList()
    private var currentLanguage: String = ""

    interface Callback {
        fun onSelectionClicked(selection: Selection) {}
        fun onBookClicked(book: Book) {}
        fun onChangeLanguageClicked() {}
    }

    fun updateCategories(newCategoriesList: List<Selection>) {
        selections = newCategoriesList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        when (viewType) {
            TYPE_HEADER -> {
                val languageBar = LayoutInflater.from(parent.context).inflate(R.layout.item_catalog_language_bar, parent, false)
                return HeaderViewHolder(languageBar)
            }
            TYPE_SELECTION -> {
                val categoryView = LayoutInflater.from(parent.context).inflate(R.layout.item_catalog_selection, parent, false)
                return SelectionViewHolder(categoryView)
            }
        }
        throw RuntimeException("No type matches $viewType")
    }

    override fun getItemViewType(position: Int): Int {
        if (position == 0) {
            return TYPE_HEADER
        }
        return TYPE_SELECTION
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (getItemViewType(position)) {
            TYPE_HEADER -> {
                val headerHolder = holder as HeaderViewHolder
                headerHolder.bindValues()
            }
            TYPE_SELECTION -> {
                val selectionHolder = holder as SelectionViewHolder
                selectionHolder.bindValues(selections[position - 1])
            }
        }
    }

    override fun getItemCount(): Int {
        return selections.size + 1
    }

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bindValues() {
            itemView.catalog_change_language.setOnClickListener { callback.onChangeLanguageClicked() }

            ViewModelProviders.of(fragment).get(LanguagesViewModel::class.java).currentLanguageText.observe(fragment, Observer {
                it?.let {
                    val text = Gdl.appContext.getString(R.string.selections_current_language, it)
                    val spanText = SpannableString(text)
                    val langStartIndex = text.indexOf(it)
                    spanText.setSpan(StyleSpan(BOLD), langStartIndex, langStartIndex + it.length, 0)
                    itemView.catalog_current_language.text = spanText
                }
            })
        }
    }

    inner class SelectionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bindValues(selection: Selection) {
            itemView.feed_title.text = selection.title

            val recyclerView: RecyclerView = itemView.catalog_selection_recycler_view
            val adapter = BooksAdapter(fragment.context!!, object : BooksAdapter.Callback {
                override fun onBookClicked(book: Book) {
                    callback.onBookClicked(book)
                }
            })
            recyclerView.adapter = adapter

            ViewModelProviders.of(fragment).get(CatalogViewModel::class.java).getBooks(selection.rootLink).observe(fragment, Observer {
                it?.let { adapter.updateBooks(it) }
            })

            itemView.feed_more.setOnClickListener { callback.onSelectionClicked(selection) }
        }
    }
}