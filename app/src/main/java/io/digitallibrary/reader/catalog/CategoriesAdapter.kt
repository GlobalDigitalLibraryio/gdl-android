package io.digitallibrary.reader.catalog

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.support.v4.app.Fragment
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.digitallibrary.reader.R
import kotlinx.android.synthetic.main.catalog_category.view.*

class CategoriesAdapter(val fragment: Fragment, val callback: Callback) : RecyclerView.Adapter<CategoriesAdapter.CategoryViewHolder>() {

    private var categories: List<Category>? = null

    interface Callback {
        fun onCategoryClicked(category: Category) { }
        fun onBookClicked(book: Book) { }
    }

    fun updateCategories(newCategoriesList: List<Category>) {
        categories = newCategoriesList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val categoryView = LayoutInflater.from(parent.context).inflate(R.layout.catalog_category, parent, false)
        return CategoryViewHolder(categoryView)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bindValues(categories!![position])
    }

    override fun getItemCount(): Int {
        return categories?.size ?: 0
    }

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bindValues(category: Category) {
            itemView.feed_title.text = category.title

            val recyclerView: RecyclerView = itemView.catalog_category_recyclerview
            val adapter = BooksAdapter(fragment.context!!, callback)
            recyclerView.adapter = adapter

            ViewModelProviders.of(fragment).get(CatalogViewModel::class.java).getBooks(category.id).observe(fragment, Observer {
                it?.let { adapter.updateBooks(it) }
            })

            itemView.feed_more.setOnClickListener { callback.onCategoryClicked(category) }
        }
    }
}