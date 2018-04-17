package io.digitallibrary.reader

import android.app.Application
import android.arch.persistence.room.Room
import android.content.Context
import android.content.res.Resources
import android.net.ConnectivityManager
import android.os.Process
import android.util.Log
import io.digitallibrary.reader.catalog.CatalogDatabase
import io.digitallibrary.reader.catalog.OpdsParser
import io.digitallibrary.reader.catalog.deleteBook
import io.digitallibrary.reader.reader.*
import io.digitallibrary.reader.utilities.SelectionsUtil
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.launch
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/**
 * Global application state.
 */
class Gdl : Application() {
    companion object {
        private const val TAG = "Gdl"

        @Volatile
        private lateinit var INSTANCE: Gdl

        lateinit var database: CatalogDatabase
        lateinit var httpClient: OkHttpClient
        lateinit var opdsParser: OpdsParser

        val appContext: Context by lazy {
            INSTANCE.applicationContext
        }

        fun fetchOpdsFeed(callback: OpdsParser.Callback? = null) {
            opdsParser.start(callback)
        }

        val sharedPrefs: Prefs by lazy {
            Prefs(INSTANCE.applicationContext)
        }

        val readerAppServices: ReaderAppServices by lazy {
            ReaderAppServices(appContext, appContext.resources)
        }

        fun isNetworkAvailable(): Boolean {
            val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            return activeNetworkInfo != null && activeNetworkInfo.isConnected
        }

        private fun namedThreadPool(count: Int, base: String, priority: Int): ExecutorService {
            val tf = Executors.defaultThreadFactory()
            val named = object : ThreadFactory {
                private var id: Int = 0

                override fun newThread(r: Runnable): Thread {
                    /*
                     * Apparently, it's necessary to use {@rootLink android.os.Process} to set
                     * the thread priority, rather than the standard Java thread
                     * functions.
                     */
                    val t = tf.newThread {
                        android.os.Process.setThreadPriority(priority)
                        r.run()
                    }
                    t.name = String.format("gdl-%s-tasks-%d", base, this.id)
                    ++this.id
                    return t
                }
            }

            return Executors.newFixedThreadPool(count, named)
        }

        fun getThemeId(): Int {
            return if ((SelectionsUtil.getCurrentCategoryLink() ?: "").contains("category/classroom_books")) {
                R.style.GdlThemeClassroom
            } else {
                R.style.GdlTheme
            }
        }

        fun getSettingsThemeId(): Int {
            return if ((SelectionsUtil.getCurrentCategoryLink() ?: "").contains("category/classroom_books")) {
                R.style.SettingsClassroom
            } else {
                R.style.Settings
            }
        }

        fun getTranslucentThemeId(): Int {
            return if ((SelectionsUtil.getCurrentCategoryLink() ?: "").contains("category/classroom_books")) {
                R.style.GdlTranslucentClassroom
            } else {
                R.style.GdlTranslucent
            }
        }

        fun changeBackendEnvironment(link: String) {
            launch(CommonPool) {
                SelectionsUtil.setLanguage(null, null)
                SelectionsUtil.setCategory(null, null)
                database.bookDao().getDownloadedBooks().forEach {
                    deleteBook(it)
                }
                database.commonDao().deleteAll()
                SelectionsUtil.setBackendEnvironment(link)
                fetchOpdsFeed()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "starting app: pid " + Process.myPid())
        Gdl.INSTANCE = this
        database = Room.databaseBuilder<CatalogDatabase>(this, CatalogDatabase::class.java, "catalog_db").build()
        httpClient = OkHttpClient()
        httpClient.dispatcher().maxRequestsPerHost = 20
        opdsParser = OpdsParser()
    }

    class ReaderAppServices(context: Context, private val rr: Resources) {
        val bookmarks: ReaderBookmarks
        private val epubExec: ExecutorService
        val epubLoader: ReaderReadiumEPUBLoaderType
        val httpServer: ReaderHTTPServerType
        private val mime: ReaderHTTPMimeMapType = ReaderHTTPMimeMap.newMap("application/octet-stream")
        val settings: ReaderSettingsType

        init {
            // Fallback port
            var port = 8080
            try {
                val s = ServerSocket(0)
                port = s.localPort
                s.close()
            } catch (e: IOException) {
                // Ignore
            }

            httpServer = ReaderHTTPServerAAsync.newServer(context.assets, this.mime, port)
            epubExec = Gdl.namedThreadPool(1, "epub", 19)
            epubLoader = ReaderReadiumEPUBLoader.newLoader(context, this.epubExec)
            settings = ReaderSettings.openSettings(context)
            bookmarks = ReaderBookmarks.openBookmarks(context)
        }

        fun screenDPToPixels(dp: Int): Double {
            return (dp * rr.displayMetrics.density).toDouble() + 0.5
        }

        fun screenGetHeightPixels(): Int {
            return rr.displayMetrics.heightPixels
        }

        fun screenGetWidthPixels(): Int {
            return rr.displayMetrics.widthPixels
        }
    }
}
