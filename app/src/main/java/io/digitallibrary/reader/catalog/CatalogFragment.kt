package io.digitallibrary.reader.catalog

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.*
import io.digitallibrary.reader.Gdl
import io.digitallibrary.reader.R
import io.digitallibrary.reader.SelectLanguageActivity
import kotlinx.android.synthetic.main.fragment_catalog.*

class CatalogFragment : Fragment() {
    companion object {
        private const val TAG = "CatalogFragment"
    }

    private var canStartAnotherActivity = true
    private lateinit var viewModel: CatalogViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_catalog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProviders.of(activity!!).get(CatalogViewModel::class.java)

        val adapter = SelectionsAdapter(this, object : SelectionsAdapter.Callback {
            override fun onSelectionClicked(selection: Selection) {
                if (canStartAnotherActivity) {
                    val intent = Intent(activity, CatalogActivity::class.java)
                    intent.putExtra("selection_link", selection.link)
                    startActivity(intent)
                    canStartAnotherActivity = false
                }
            }

            override fun onBookClicked(book: Book) {
                if (canStartAnotherActivity) {
                    val intent = Intent(activity, BookDetailsActivity::class.java)
                    intent.putExtra("book_id", book.id)
                    startActivity(intent)
                    canStartAnotherActivity = false
                }
            }

            override fun onChangeLanguageClicked() {
                if (canStartAnotherActivity) {
                    val intent = Intent(activity, SelectLanguageActivity::class.java)
                    startActivity(intent)
                    canStartAnotherActivity = false
                }
            }
        })

        recycler_view.adapter = adapter

        val refreshCallback = object : OpdsParser.Callback {
            override fun onFinished() {
                swipe_refresh_layout?.isRefreshing = false
            }

            override fun onError(error: OpdsParser.Error?, message: String?) {
                swipe_refresh_layout?.isRefreshing = false
                when (error) {
                    OpdsParser.Error.HTTP_IO_ERROR -> showErrorMessage(
                        R.string.error_msg_connection_error_title,
                        R.string.error_msg_connection_error_message
                    )
                    OpdsParser.Error.HTTP_REQUEST_FAILED, OpdsParser.Error.XML_PARSING_ERROR -> showErrorMessage(
                        R.string.error_msg_server_error_title,
                        R.string.error_msg_server_error_message
                    )
                    OpdsParser.Error.POSSIBLE_OLD_CLIENT -> showErrorMessage(
                        R.string.error_msg_old_client_title,
                        R.string.error_msg_old_client_message
                    )
                }
            }
        }

        swipe_refresh_layout.setOnRefreshListener {
            removeErrorMessage()
            Gdl.fetchOpdsFeed(refreshCallback)
        }

        swipe_refresh_layout.setColorSchemeResources(R.color.gdl_links, R.color.gdl_green)

        val shortDuration = resources.getInteger(android.R.integer.config_shortAnimTime).toLong()

        viewModel.getCurrentCategorySelections()
            .observe(this, Observer {
                it?.let {
                    val oldHaveItems = adapter.itemCount > 0
                    val nowHaveItems = it.isNotEmpty()
                    adapter.updateSelections(it)
                    if (oldHaveItems != nowHaveItems) {
                        val fadeFromView = if (nowHaveItems) error_container else recycler_view
                        val fadeToView = if (nowHaveItems) recycler_view else error_container

                        fadeFromView.animate().alpha(0f).setDuration(shortDuration)
                            .setListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator?) {
                                    super.onAnimationEnd(animation)
                                    fadeFromView.visibility = View.GONE
                                }
                            })

                        fadeToView.visibility = View.VISIBLE
                        fadeToView.alpha = 0f
                        fadeToView.animate().setListener(null).alpha(1f).setDuration(shortDuration)
                    }

                    if (!nowHaveItems) {
                        removeErrorMessage()
                        swipe_refresh_layout?.isRefreshing = true
                        Gdl.fetchOpdsFeed(refreshCallback)
                    }
                }
            })

    }

    private fun showErrorMessage(title: Int, msg: Int) {
        val shortDuration = resources.getInteger(android.R.integer.config_shortAnimTime).toLong()
        error_title.text = getString(title)
        error_message.text = getString(msg)

        listOf(error_icon, error_title, error_message).forEach {
            it.visibility = View.VISIBLE
            it.alpha = 0f
            it.animate().setListener(null).alpha(1f).setDuration(shortDuration)
        }
    }

    private fun removeErrorMessage() {
        val shortDuration = resources.getInteger(android.R.integer.config_shortAnimTime).toLong()
        listOf(error_icon, error_title, error_message).forEach {
            it.animate().alpha(0f).setDuration(shortDuration)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator?) {
                        super.onAnimationEnd(animation)
                        it.visibility = View.GONE
                    }
                })
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater?.inflate(R.menu.catalog_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.gdl_activity_select_language -> {
                val i = Intent(context, SelectLanguageActivity::class.java)
                startActivity(i)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onResume() {
        super.onResume()
        canStartAnotherActivity = true
    }
}
