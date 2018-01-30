package io.digitallibrary.reader.catalog

import android.app.AlertDialog
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.RecyclerView
import android.view.*
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import io.digitallibrary.reader.R


class MyLibraryFragment : Fragment() {

    companion object {
        private const val TAG = "MyLibraryFragment"
    }

    private lateinit var adapter: BooksAdapter
    private var canStartAnotherActivity = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.catalog_without_categories_without_toolbar, container, false)

        val viewModel = ViewModelProviders.of(this).get(CatalogViewModel::class.java)

        val recyclerView: RecyclerView = view.findViewById(R.id.catalog_recyclerview)
        val layoutManager = FlexboxLayoutManager(context)
        layoutManager.justifyContent = JustifyContent.CENTER
        recyclerView.layoutManager = layoutManager
        adapter = BooksAdapter(context!!, object : BooksAdapter.Callback {
            override fun onBookClicked(book: Book) {
                if (canStartAnotherActivity) {
                    val intent = Intent(context, BookDetailsActivity::class.java)
                    intent.putExtra("book_id", book.id)
                    startActivity(intent)
                    canStartAnotherActivity = false
                }
            }

            override fun onBookSelectionChanged() {
                activity?.invalidateOptionsMenu()
            }
        }, true)
        recyclerView.adapter = adapter

        viewModel.getDownloadedBooks().observe(this, Observer {
            it?.let { adapter.updateBooks(it) }
            activity?.invalidateOptionsMenu()
        })

        return view
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onResume() {
        super.onResume()
        canStartAnotherActivity = true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.my_library_action_edit -> {
                adapter.setSelectMode(true)
                return true
            }
            R.id.my_library_action_edit_cancel -> {
                adapter.setSelectMode(false)
                return true
            }
            R.id.my_library_action_edit_select_all -> {
                adapter.selectAll()
                return true
            }
            R.id.my_library_action_edit_delete -> {
                deleteSelectedBooks()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun deleteSelectedBooks() {
        val confirmDialog = AlertDialog.Builder(activity).create()
        confirmDialog.setMessage(getString(R.string.my_library_confirm_delete_books))
        confirmDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dialog_delete), { _, _ ->
            deleteBooks(adapter.getSelectedBooks())
            adapter.setSelectMode(false)
        })
        confirmDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.dialog_cancel), { _, _ -> })
        confirmDialog.show()
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater?.inflate(R.menu.my_library_menu, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu?) {
        if (adapter.selectModeActive) {
            menu?.findItem(R.id.my_library_action_edit_cancel)?.isVisible = true
            menu?.findItem(R.id.my_library_action_edit_select_all)?.isVisible = true
            menu?.findItem(R.id.my_library_action_edit)?.isVisible = false
            menu?.findItem(R.id.my_library_action_edit_delete)?.isVisible = adapter.isAnySelected()
        } else {
            menu?.findItem(R.id.my_library_action_edit_cancel)?.isVisible = false
            menu?.findItem(R.id.my_library_action_edit_select_all)?.isVisible = false
            menu?.findItem(R.id.my_library_action_edit_delete)?.isVisible = false
            menu?.findItem(R.id.my_library_action_edit)?.isVisible = adapter.size() > 0
        }
        super.onPrepareOptionsMenu(menu)
    }
}