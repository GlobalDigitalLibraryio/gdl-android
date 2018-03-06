package io.digitallibrary.reader.catalog

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.MenuItem
import io.digitallibrary.reader.Gdl
import io.digitallibrary.reader.R
import kotlinx.android.synthetic.main.toolbar.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch

class CatalogActivity : AppCompatActivity() {

    private var canStartAnotherActivity = true
    private lateinit var layoutManager: GridLayoutManager

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
        val selectionLink = intent.getStringExtra("selection_link")
        val viewModel = ViewModelProviders.of(this).get(CatalogViewModel::class.java)

        setContentView(R.layout.activity_catalog)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        launch(UI) {
            val selection = async { viewModel.getSelection(selectionLink) }.await()
            supportActionBar?.title = selection.title
        }

        val displayWidth = Gdl.readerAppServices.screenGetWidthPixels()
        val bookWidth = resources.getDimension(R.dimen.catalog_book_width)

        val recyclerView: RecyclerView = findViewById(R.id.recycler_view)
        layoutManager = GridLayoutManager(this, (displayWidth / bookWidth).toInt())
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

        viewModel.getBooks(selectionLink).observe(this, Observer {
            it?.let { adapter.updateBooks(it) }
        })
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        val displayWidth = Gdl.readerAppServices.screenGetWidthPixels()
        val bookWidth = resources.getDimension(R.dimen.catalog_book_width)
        layoutManager.spanCount = (displayWidth / bookWidth).toInt()
    }
}