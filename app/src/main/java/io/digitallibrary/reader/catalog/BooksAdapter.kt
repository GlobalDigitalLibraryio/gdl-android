package io.digitallibrary.reader.catalog

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.util.SparseBooleanArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import io.digitallibrary.reader.R
import kotlinx.android.synthetic.main.catalog_book.view.*

class BooksAdapter(val providerContext : Context, val callback: Callback, val selectModeAllowed: Boolean = false) : RecyclerView.Adapter<BooksAdapter.BookViewHolder>() {
    companion object {
        private const val TAG = "BooksAdapter"
    }

    interface Callback {
        fun onBookClicked(book: Book) {}
        fun onBookSelectionChanged() {}
    }

    private var books: List<Book> = emptyList()
    private var selectedBooks: SparseBooleanArray = SparseBooleanArray()

    var selectModeActive = false

    fun setSelectMode(active: Boolean, initialSelected: Int = -1) {
        val notify = selectModeActive != active
        selectModeActive = active
        if (notify) {
            selectedBooks = SparseBooleanArray(books.size)
            if (initialSelected >= 0) {
                selectedBooks.put(initialSelected, true)
            }
            notifyDataSetChanged()
        }
        callback.onBookSelectionChanged()
    }

    private fun SparseBooleanArray.containsAnyTrue(): Boolean {
        return (0 until books.size).any { get(it) }
    }

    private fun SparseBooleanArray.selectAll() {
        (0 until books.size).forEach { put(it, true) }
    }

    fun size(): Int {
        return books.size
    }

    fun isAnySelected(): Boolean {
        return selectedBooks.containsAnyTrue()
    }

    fun getSelectedBooks(): List<Book> {
        return books.filterIndexed { index, _ -> selectedBooks[index] }
    }

    fun selectAll() {
        selectedBooks.selectAll()
        notifyDataSetChanged()
        callback.onBookSelectionChanged()
    }

    fun toggleItem(position: Int) {
        selectedBooks.put(position, !selectedBooks[position])
        notifyItemChanged(position)
        callback.onBookSelectionChanged()
    }

    fun updateBooks(newBooksList: List<Book>) {
        books = newBooksList
        selectedBooks = SparseBooleanArray(books.size)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        return BookViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.catalog_book, parent, false))
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        holder.bindValues(books[position], position)
    }

    override fun getItemCount(): Int {
        return books.size
    }

    inner class BookViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bindValues(book: Book, position: Int) {
            itemView.cell_book_title.text = book.title
            itemView.setOnClickListener {
                if (selectModeActive) {
                    toggleItem(position)
                } else {
                    callback.onBookClicked(book)
                }
            }
            itemView.setOnLongClickListener {
                if (!selectModeActive && selectModeAllowed) {
                    setSelectMode(true)
                    toggleItem(position)
                    true
                } else {
                    false
                }
            }
            Glide.with(providerContext)
                    .load(book.thumb)
                    .apply(RequestOptions().centerCrop().placeholder(R.drawable.book_image_placeholder))
                    .into(itemView.cell_cover_image)


            if (selectModeActive) {
                if (selectedBooks[position]) {
                    // Selected
                    itemView.book_card_view.alpha = 1F
                    itemView.selected_frame.visibility = View.VISIBLE
                    itemView.selected_checked.visibility = View.VISIBLE
                } else {
                    // Not selected
                    itemView.book_card_view.alpha = 0.5F
                    itemView.selected_frame.visibility = View.GONE
                    itemView.selected_checked.visibility = View.GONE
                }
            } else {
                // Regular
                itemView.book_card_view.alpha = 1F
                itemView.selected_frame.visibility = View.GONE
                itemView.selected_checked.visibility = View.GONE
            }
        }
    }
}