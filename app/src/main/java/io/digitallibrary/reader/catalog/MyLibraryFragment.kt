package io.digitallibrary.reader.catalog

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.AlertDialog
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.media.Image
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import android.widget.ImageView
import io.digitallibrary.reader.Gdl
import io.digitallibrary.reader.R
import kotlinx.android.synthetic.main.fragment_my_library.*


class MyLibraryFragment : Fragment() {

    companion object {
        private const val TAG = "MyLibraryFragment"
    }

    private lateinit var adapter: BooksAdapter
    private var canStartAnotherActivity = true
    private lateinit var layoutManager: GridLayoutManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_my_library, container, false)

        val viewModel = ViewModelProviders.of(activity!!).get(CatalogViewModel::class.java)

        val displayWidth = Gdl.readerAppServices.screenGetWidthPixels()
        val bookWidth = resources.getDimension(R.dimen.catalog_book_width)

        val recyclerView: RecyclerView = view.findViewById(R.id.recycler_view)
        layoutManager = GridLayoutManager(context, (displayWidth / bookWidth).toInt())
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

        var clickCounter = 0
        view.findViewById<ImageView>(R.id.empty_icon).setOnClickListener {
            if (++clickCounter == 6) {
                clickCounter = 0
                val dialog = AlertDialog.Builder(context)
                dialog.setTitle("Select backend environment")
                dialog.setItems(arrayOf("Test", "Staging", "Prod")) { _, which ->
                    when (which) {
                        0 -> Gdl.changeBackendEnvironment(OpdsParser.INITIAL_REQUEST_URL_TEST)
                        1 -> Gdl.changeBackendEnvironment(OpdsParser.INITIAL_REQUEST_URL_STAGING)
                        2 -> Gdl.changeBackendEnvironment(OpdsParser.INITIAL_REQUEST_URL_PROD)
                    }
                }
                dialog.create()
                dialog.show()
            }
        }

        var initialView = true
        val shortDuration = resources.getInteger(android.R.integer.config_shortAnimTime).toLong()

        viewModel.downloadedBooks.observe(this, Observer {
            val oldIsEmpty = adapter.itemCount == 0
            val newIsEmpty = it?.isEmpty() == true

            adapter.submitList(it)
            activity?.invalidateOptionsMenu()

            if (initialView) {
                // initial state is set without animations
                if (newIsEmpty) {
                    empty_message.visibility = View.VISIBLE
                } else {
                    recycler_view.visibility = View.VISIBLE
                }
                initialView = false
            } else {
                // Only animate when we have to
                if (oldIsEmpty != newIsEmpty) {
                    val fadeFromView = if (newIsEmpty) recycler_view else empty_message
                    val fadeToView = if (newIsEmpty) empty_message else recycler_view

                    fadeFromView.animate().alpha(0f).setDuration(shortDuration).setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator?) {
                            super.onAnimationEnd(animation)
                            fadeFromView.visibility = View.GONE
                        }
                    })

                    fadeToView.visibility = View.VISIBLE
                    fadeToView.alpha = 0f
                    fadeToView.animate().setListener(null).alpha(1f).setDuration(shortDuration)
                }
            }
        })

        return view
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        val displayWidth = Gdl.readerAppServices.screenGetWidthPixels()
        val bookWidth = resources.getDimension(R.dimen.catalog_book_width)
        layoutManager.spanCount = (displayWidth / bookWidth).toInt()
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
        confirmDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dialog_action_delete), { _, _ ->
            adapter.forEachSelected {
                deleteBook(it)
            }
            adapter.setSelectMode(false)
        })
        confirmDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.dialog_action_cancel), { _, _ -> })
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
            menu?.findItem(R.id.my_library_action_edit)?.isVisible = adapter.itemCount > 0
        }
        super.onPrepareOptionsMenu(menu)
    }
}
