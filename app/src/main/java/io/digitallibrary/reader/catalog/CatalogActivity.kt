package io.digitallibrary.reader.catalog

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.RecyclerView
import android.view.MenuItem
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import io.digitallibrary.reader.R
import kotlinx.android.synthetic.main.toolbar.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch

class CatalogActivity : AppCompatActivity() {

    private var canStartAnotherActivity = true

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        canStartAnotherActivity = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val categoryId = intent.getStringExtra("category_id")
        val viewModel = ViewModelProviders.of(this).get(CatalogViewModel::class.java)

        setContentView(R.layout.activity_catalog)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        launch(UI) {
            val category = async { viewModel.getCategory(categoryId) }.await()
            supportActionBar?.title = category.title
        }

        val recyclerView: RecyclerView = findViewById(R.id.catalog_recyclerview)
        val layoutManager = FlexboxLayoutManager(this)
        layoutManager.justifyContent = JustifyContent.CENTER
        recyclerView.layoutManager = layoutManager
        val adapter = BooksAdapter(this, object : BooksAdapter.Callback {
            override fun onBookClicked(book: Book) {
                if (canStartAnotherActivity) {
                    val intent = Intent(applicationContext, BookDetailsActivity::class.java)
                    intent.putExtra("book_id", book.id)
                    startActivity(intent)
                    canStartAnotherActivity = false
                }
            }
        }
        )
        recyclerView.adapter = adapter

        viewModel.getBooks(categoryId).observe(this, Observer {
            it?.let { adapter.updateBooks(it) }
        })
    }
}