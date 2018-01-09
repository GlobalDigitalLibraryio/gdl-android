package io.digitallibrary.reader.catalog

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.digitallibrary.reader.R

class CatalogFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.catalog_with_categories, container, false)

        val recyclerView: RecyclerView = view.findViewById(R.id.catalog_recyclerview)
        val adapter = CategoriesAdapter(this, object: CategoriesAdapter.Callback {
            override fun onCategoryClicked(category: Category) {
                val intent = Intent(activity, CatalogCategoryActivity::class.java)
                intent.putExtra("category_id", category.id)
                startActivity(intent)
            }
            override fun onBookClicked(book: Book) {
                val intent = Intent(activity, BookDetailsActivity::class.java)
                intent.putExtra("book_id", book.id)
                startActivity(intent)
            }
        })
        recyclerView.adapter = adapter

        ViewModelProviders.of(this).get(CatalogViewModel::class.java).getCategories().observe(this, Observer {
            it?.let { adapter.updateCategories(it) }
        })

        return view
    }
}