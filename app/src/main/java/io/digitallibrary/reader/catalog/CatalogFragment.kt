package io.digitallibrary.reader.catalog

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.digitallibrary.reader.Gdl
import io.digitallibrary.reader.R
import io.digitallibrary.reader.SelectLanguageActivity
import kotlinx.android.synthetic.main.fragment_catalog.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch


class CatalogFragment : Fragment() {
    companion object {
        private const val TAG = "CatalogFragment"
    }

    private var broadcastReceiver: BroadcastReceiver? = null
    private var canStartAnotherActivity = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_catalog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = CategoriesAdapter(this, object : CategoriesAdapter.Callback {
            override fun onCategoryClicked(category: Category) {
                if (canStartAnotherActivity) {
                    val intent = Intent(activity, CatalogActivity::class.java)
                    intent.putExtra("category_id", category.id)
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

        swipe_refresh_layout.setOnRefreshListener { Gdl.fetchOpdsFeed() }
        swipe_refresh_layout.setColorSchemeResources(R.color.gdl_links,  R.color.gdl_green)

        ViewModelProviders.of(this).get(CatalogViewModel::class.java).getCategories().observe(this, Observer {
            it?.let { adapter.updateCategories(it) }
        })

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                launch(UI) {
                    swipe_refresh_layout.isRefreshing = false
                }
            }
        }

        try {
            LocalBroadcastManager.getInstance(context!!).registerReceiver(broadcastReceiver!!, IntentFilter(OPDS_PARSE_DONE))
        } catch (e: NullPointerException) {
            Log.e(TAG, "Error setting up local broadcast receiver")
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        canStartAnotherActivity = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            if (broadcastReceiver != null) {
                LocalBroadcastManager.getInstance(context!!).unregisterReceiver(broadcastReceiver!!)
                broadcastReceiver = null
            }
        } catch (e: NullPointerException) {
            Log.e(TAG, "Error removing local broadcast receiver")
            e.printStackTrace()
        }
    }
}