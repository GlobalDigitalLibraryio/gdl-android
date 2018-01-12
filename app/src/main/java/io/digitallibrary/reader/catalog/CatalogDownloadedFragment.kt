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
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import io.digitallibrary.reader.R

class CatalogDownloadedFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.catalog_without_categories_without_toolbar, container, false)

        val viewModel = ViewModelProviders.of(this).get(CatalogViewModel::class.java)


        val recyclerView: RecyclerView = view.findViewById(R.id.catalog_recyclerview)
        val layoutManager = FlexboxLayoutManager(context)
        layoutManager.justifyContent = JustifyContent.CENTER
        recyclerView.layoutManager = layoutManager
        val adapter = BooksAdapter(context!!, object: CategoriesAdapter.Callback {
            override fun onBookClicked(book: Book) {
                val intent = Intent(context, BookDetailsActivity::class.java)
                intent.putExtra("book_id", book.id)
                startActivity(intent)
            }
        }
        )
        recyclerView.adapter = adapter

        viewModel.getDownloadedBooks().observe(this, Observer {
            it?.let { adapter.updateBooks(it) }
        })

        return view
    }
}