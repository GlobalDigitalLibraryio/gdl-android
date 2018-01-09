package io.digitallibrary.reader.catalog

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import io.digitallibrary.reader.R
import kotlinx.android.synthetic.main.catalog_book.view.*

class BooksAdapter(val providerContext : Context, val callback: CategoriesAdapter.Callback) : RecyclerView.Adapter<BooksAdapter.BookViewHolder>() {

    private var books: List<Book>? = null

    fun updateBooks(newBooksList: List<Book>) {
        Log.i(TAG, "updateBooks: " + newBooksList.size)
        books = newBooksList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val categoryView = LayoutInflater.from(parent.context).inflate(R.layout.catalog_book, parent, false)
        return BookViewHolder(categoryView)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        holder.bindValues(books!![position])
    }

    override fun getItemCount(): Int {
        return books?.size ?: 0
    }

    inner class BookViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bindValues(book: Book) {
            itemView.cell_book_title.text = book.title
            itemView.setOnClickListener { callback.onBookClicked(book) }
            Glide.with(providerContext)
                    .load(book.cover)
                    .apply(RequestOptions().centerCrop().placeholder(R.drawable.book_image_placeholder))
                    .into(itemView.cell_cover_image)
        }
    }
}