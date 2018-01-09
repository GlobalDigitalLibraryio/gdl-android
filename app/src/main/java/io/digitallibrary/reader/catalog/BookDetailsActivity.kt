package io.digitallibrary.reader.catalog

import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import io.digitallibrary.reader.R
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import kotlinx.android.synthetic.main.book_details.*


class BookDetailsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bookId = intent.getStringExtra("book_id")
        val viewModel = ViewModelProviders.of(this).get(CatalogViewModel::class.java)

        setContentView(R.layout.book_details)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.catalog_book_detail)

        launch(UI) {
            val book = async { viewModel.getBook(bookId) }.await()
            Glide.with(applicationContext)
                    .load(book.cover)
                    .apply(RequestOptions().centerCrop().placeholder(R.drawable.book_image_placeholder))
                    .into(book_cover)
            book_title.text = book.title
            book_publisher.text = book.publisher
            book_description.text = book.description
            book_level.text = getString(R.string.book_level, book.readingLevel)
            book_authors.text = book.author
            book_license.text = book.license
        }
    }
}