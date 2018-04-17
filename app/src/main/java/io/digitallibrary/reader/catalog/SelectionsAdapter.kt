package io.digitallibrary.reader.catalog

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.support.v4.app.Fragment
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.digitallibrary.reader.R
import kotlinx.android.synthetic.main.item_catalog_selection.view.*


class SelectionsAdapter(val fragment: Fragment, val callback: Callback) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var selections: List<Selection> = emptyList()
    private var viewModel = ViewModelProviders.of(fragment.activity!!)

    interface Callback {
        fun onSelectionClicked(selection: Selection) {}
        fun onBookClicked(book: Book) {}
        fun onChangeLanguageClicked() {}
    }

    fun updateSelections(newSelectionsList: List<Selection>) {
        selections = newSelectionsList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val categoryView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_catalog_selection, parent, false)
        return SelectionViewHolder(categoryView)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val selectionHolder = holder as SelectionViewHolder
        selectionHolder.bindValues(selections[position])
    }

    override fun getItemCount(): Int {
        return selections.size
    }

    inner class SelectionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bindValues(selection: Selection) {
            itemView.feed_title.text = selection.title

            val recyclerView: RecyclerView = itemView.catalog_selection_recycler_view
            val adapter = BooksAdapter(fragment.context!!, object : BooksAdapter.Callback {
                override fun onBookClicked(book: Book) {
                    callback.onBookClicked(book)
                }
            })
            recyclerView.adapter = adapter

            viewModel.get(CatalogViewModel::class.java)
                .getBooks(selection.link).observe(fragment, Observer {
                    it?.let { adapter.submitList(it) }
                })

            itemView.feed_more.setOnClickListener { callback.onSelectionClicked(selection) }
        }
    }
}
