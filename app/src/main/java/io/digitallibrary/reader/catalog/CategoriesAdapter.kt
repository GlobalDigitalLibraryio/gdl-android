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
import io.digitallibrary.reader.R
import io.digitallibrary.reader.utilities.LanguageUtil
import kotlinx.android.synthetic.main.catalog_category.view.*
import kotlinx.android.synthetic.main.catalog_language_bar.view.*


class CategoriesAdapter(val fragment: Fragment, val callback: Callback) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_CATEGORY = 1
    }

    private var categories: List<Category> = emptyList()

    interface Callback {
        fun onCategoryClicked(category: Category) {}
        fun onBookClicked(book: Book) {}
        fun onChangeLanguageClicked() {}
    }

    fun updateCategories(newCategoriesList: List<Category>) {
        categories = newCategoriesList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        when (viewType) {
            TYPE_HEADER -> {
                val languageBar = LayoutInflater.from(parent.context).inflate(R.layout.catalog_language_bar, parent, false)
                return HeaderViewHolder(languageBar)
            }
            TYPE_CATEGORY -> {
                val categoryView = LayoutInflater.from(parent.context).inflate(R.layout.catalog_category, parent, false)
                return CategoryViewHolder(categoryView)
            }
        }
        throw RuntimeException("No type matches $viewType")
    }

    override fun getItemViewType(position: Int): Int {
        if (position == 0) {
            return TYPE_HEADER
        }
        return TYPE_CATEGORY
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder?, position: Int) {
        when (getItemViewType(position)) {
            TYPE_HEADER -> {
                val headerHolder = holder as HeaderViewHolder
                headerHolder.bindValues()
            }
            TYPE_CATEGORY -> {
                val categoryHolder = holder as CategoryViewHolder
                categoryHolder.bindValues(categories[position - 1])
            }
        }
    }

    override fun getItemCount(): Int {
        return categories.size + 1
    }

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bindValues() {
            itemView.catalog_change_language.setOnClickListener { callback.onChangeLanguageClicked() }
            val language = LanguageUtil.getCurrentLanguageText()
            val text = Gdl.appContext.getString(R.string.catalog_current_language, language)
            val spanText = SpannableString(text)
            val langStartIndex = text.indexOf(language)
            spanText.setSpan(StyleSpan(BOLD), langStartIndex, langStartIndex + language.length, 0)
            itemView.catalog_current_language.text = spanText
        }
    }

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bindValues(category: Category) {
            itemView.feed_title.text = category.title

            val recyclerView: RecyclerView = itemView.catalog_category_recyclerview
            val adapter = BooksAdapter(fragment.context!!, object : BooksAdapter.Callback {
                override fun onBookClicked(book: Book) {
                    callback.onBookClicked(book)
                }
            })
            recyclerView.adapter = adapter

            ViewModelProviders.of(fragment).get(CatalogViewModel::class.java).getBooks(category.id).observe(fragment, Observer {
                it?.let { adapter.updateBooks(it) }
            })

            itemView.feed_more.setOnClickListener { callback.onCategoryClicked(category) }
        }
    }
}