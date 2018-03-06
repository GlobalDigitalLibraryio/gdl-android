package io.digitallibrary.reader.catalog

import android.arch.paging.PagedListAdapter
import android.content.Context
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.util.SparseBooleanArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import io.digitallibrary.reader.R
import kotlinx.android.synthetic.main.item_catalog_book.view.*

/**
 * Adapter for showing books in recycler views. Have support for select mode.
 * This adapter will use paging, to avoid loading to many books into memory at the same time.
 */
class BooksAdapter(val providerContext: Context, val callback: Callback, val selectModeAllowed: Boolean = false) : PagedListAdapter<Book, BooksAdapter.BookViewHolder>(BooksAdapter.diffCallback) {
    companion object {
        private const val TAG = "BooksAdapter"

        private val diffCallback = object : DiffUtil.ItemCallback<Book>() {
            override fun areItemsTheSame(oldBook: Book, newBook: Book): Boolean {
                return oldBook.id == newBook.id
            }
            override fun areContentsTheSame(oldBook: Book, newBook: Book): Boolean {
                return oldBook == newBook
            }
        }
    }

    /**
     * Callbacks for different events from the adapter
     */
    interface Callback {
        /**
         * Called when a book is clicked
         *
         * @param book The selected book
         */
        fun onBookClicked(book: Book) {}

        /**
         * Called every time the list of selected books changes
         */
        fun onBookSelectionChanged() {}
    }

    private var selectedBooks: SparseBooleanArray = SparseBooleanArray()

    var selectModeActive = false

    fun setSelectMode(active: Boolean, initialSelected: Int = -1) {
        if (selectModeAllowed) {
            val notify = selectModeActive != active
            selectModeActive = active
            if (notify) {
                selectedBooks = SparseBooleanArray(itemCount)
                if (initialSelected >= 0) {
                    selectedBooks.put(initialSelected, true)
                }
                notifyDataSetChanged()
            }
            callback.onBookSelectionChanged()
        }
    }

    fun isAnySelected(): Boolean {
        return (0 until itemCount).any { selectedBooks.get(it) }
    }

    fun forEachSelected(callback: (Book) -> Unit) {
        (0 until itemCount).forEach {
            if (selectedBooks[it]) {
                getItem(it)?.let(callback)
            }
        }
    }

    fun selectAll() {
        (0 until itemCount).forEach { selectedBooks.put(it, true) }
        notifyDataSetChanged()
        callback.onBookSelectionChanged()
    }

    private fun toggleItem(book: Book) {
        val position = getPosition(book)
        if (position != -1) {
            selectedBooks.put(position, !selectedBooks[position])
            notifyItemChanged(position)
            callback.onBookSelectionChanged()
        }

    }

    private fun getPosition(book: Book): Int {
        (0 until itemCount).forEach { i ->
            getItem(i)?.let{
                if (it.id == book.id) {
                    return i
                }
            }
        }
        return -1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        return BookViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_catalog_book, parent, false))
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        getItem(position)?.let {
            holder.bindValues(it, position)
        }
    }

    inner class BookViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bindValues(book: Book, position: Int) {
            itemView.cell_book_title.text = book.title
            itemView.setOnClickListener {
                if (selectModeActive) {
                    toggleItem(book)
                } else {
                    callback.onBookClicked(book)
                }
            }

            if (selectModeAllowed) {
                itemView.setOnLongClickListener {
                    if (!selectModeActive) {
                        setSelectMode(true)
                        toggleItem(book)
                        true
                    } else {
                        false
                    }
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