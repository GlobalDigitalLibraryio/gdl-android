package io.digitallibrary.reader.catalog

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.os.Bundle
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.MenuItem
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import io.digitallibrary.reader.Gdl
import io.digitallibrary.reader.R
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch

class CatalogCategoryActivity : AppCompatActivity() {

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val categoryId = intent.getStringExtra("category_id")
        val viewModel = ViewModelProviders.of(this).get(CatalogViewModel::class.java)

        setContentView(R.layout.catalog_without_categories)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        launch(UI) {
            val category = async { viewModel.getCategory(categoryId) }.await()
            toolbar.title = category.title
        }

        val recyclerView: RecyclerView = findViewById(R.id.catalog_recyclerview)
        val layoutManager = FlexboxLayoutManager(this)
        layoutManager.justifyContent = JustifyContent.CENTER
        recyclerView.layoutManager = layoutManager
        val adapter = BooksAdapter(this, object: CategoriesAdapter.Callback {
                    override fun onBookClicked(book: Book) {
                        val intent = Intent(applicationContext, BookDetailsActivity::class.java)
                        intent.putExtra("book_id", book.id)
                        startActivity(intent)
                    }
                }
        )
        recyclerView.adapter = adapter

        viewModel.getBooks(categoryId).observe(this, Observer {
            it?.let { adapter.updateBooks(it) }
        })
    }
}