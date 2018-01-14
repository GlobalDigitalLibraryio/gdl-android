package io.digitallibrary.reader.catalog

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.LocalBroadcastManager
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.digitallibrary.reader.Gdl
import io.digitallibrary.reader.R
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.util.Log
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch


class CatalogFragment : Fragment() {
    private var TAG = "CatalogFragment"

    var broadcastReceiver: BroadcastReceiver? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.catalog_with_categories, container, false)

        val recyclerView: RecyclerView = view.findViewById(R.id.catalog_recyclerview)
        val adapter = CategoriesAdapter(this, object: CategoriesAdapter.Callback {
            override fun onCategoryClicked(category: Category) {
                val intent = Intent(activity, CatalogCategoryActivity::class.java)
                intent.putExtra("category_id", category.id)
                startActivity(intent)
            }
            override fun onBookClicked(book: Book) {
                val intent = Intent(activity, BookDetailsActivity::class.java)
                intent.putExtra("book_id", book.id)
                startActivity(intent)
            }
        })
        recyclerView.adapter = adapter

        val swipeRefreshLayout: SwipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout)
        swipeRefreshLayout.setOnRefreshListener {
            Gdl.fetch()
        }

        ViewModelProviders.of(this).get(CatalogViewModel::class.java).getCategories().observe(this, Observer {
            it?.let { adapter.updateCategories(it) }
        })

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                launch(UI) {
                    swipeRefreshLayout.isRefreshing = false
                }
            }
        }

        try {
            LocalBroadcastManager.getInstance(context!!).registerReceiver(broadcastReceiver!!, IntentFilter(OPDS_PARSE_DONE))
        } catch (e: NullPointerException) {
            Log.e(TAG, "Error setting up local broadcast receiver")
            e.printStackTrace()
        }

        return view
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            LocalBroadcastManager.getInstance(context!!).unregisterReceiver(broadcastReceiver!!)
        } catch (e: NullPointerException) {
            Log.e(TAG, "Error removing local broadcast receiver")
            e.printStackTrace()
        }
    }
}