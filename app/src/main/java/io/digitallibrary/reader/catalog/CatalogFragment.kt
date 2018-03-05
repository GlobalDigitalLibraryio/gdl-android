package io.digitallibrary.reader.catalog

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.digitallibrary.reader.Gdl
import io.digitallibrary.reader.R
import io.digitallibrary.reader.SelectLanguageActivity
import kotlinx.android.synthetic.main.fragment_catalog.*


class CatalogFragment : Fragment() {
    companion object {
        private const val TAG = "CatalogFragment"
    }

    private var canStartAnotherActivity = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_catalog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = SelectionsAdapter(this, object : SelectionsAdapter.Callback {
            override fun onSelectionClicked(selection: Selection) {
                if (canStartAnotherActivity) {
                    val intent = Intent(activity, CatalogActivity::class.java)
                    intent.putExtra("selection_link", selection.rootLink)
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

        swipe_refresh_layout.setOnRefreshListener { Gdl.fetchOpdsFeed(object : OpdsParser.Callback {
            override fun onFinished() {
                swipe_refresh_layout?.isRefreshing = false
            }

            override fun onError(error: OpdsParser.Error?, message: String?) {
                swipe_refresh_layout?.isRefreshing = false
            }
        }) }
        swipe_refresh_layout.setColorSchemeResources(R.color.gdl_links,  R.color.gdl_green)

        ViewModelProviders.of(this).get(CatalogViewModel::class.java).getSelections().observe(this, Observer {
            it?.let { adapter.updateCategories(it) }
        })
    }

    override fun onResume() {
        super.onResume()
        canStartAnotherActivity = true
    }
}