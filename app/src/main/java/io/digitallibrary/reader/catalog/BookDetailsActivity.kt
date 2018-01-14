package io.digitallibrary.reader.catalog

import android.app.DownloadManager
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.MenuItem
import android.view.View.GONE
import android.view.View.VISIBLE
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import io.digitallibrary.reader.Gdl
import io.digitallibrary.reader.R
import io.digitallibrary.reader.reader.ReaderActivity
import kotlinx.android.synthetic.main.book_details.*
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.format.FormatStyle
import java.io.File
import java.net.URI


class BookDetailsActivity : AppCompatActivity() {
    private val TAG = "BookDetailsActivity"

    private val STATUS_DOWNLOADED = -1
    private val STATUS_NOT_DOWNLOADED = -2
    private val STATUS_DOWNLOADING = -3
    private val STATUS_FAILED = -4

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private var book: Book? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bookId = intent.getStringExtra("book_id")
        val viewModel = ViewModelProviders.of(this).get(CatalogViewModel::class.java)

        setContentView(R.layout.book_details)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.catalog_book_detail)

        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)

        var initialized = false

        viewModel.getBook(bookId).observe(this, Observer {
            it?.let {
                book = it
                if (!initialized) {
                    launch(UI) {
                        book?.let {
                            Glide.with(applicationContext)
                                    .load(it.image)
                                    .apply(RequestOptions().centerCrop().placeholder(R.drawable.book_image_placeholder))
                                    .into(book_cover)
                            book_title.text = it.title
                            book_publisher.text = it.publisher
                            book_description.text = it.description
                            book_level.text = getString(R.string.book_level, it.readingLevel)
                            book_authors.text = it.author
                            book_license.text = it.license
                            book_published.text = it.published?.format(formatter)
                        }

                        book_download_button.setOnClickListener { downloadBook() }
                        book_download_retry.setOnClickListener { downloadBook() }
                        book_download_dismiss.setOnClickListener { dismissFailedDownload() }

                        book_download_cancel.setOnClickListener { cancelDownloadBook() }
                        book_delete_book.setOnClickListener { deleteBook() }
                        book_read_book.setOnClickListener { readBook() }
                        initialized = true
                        updateDownloadingState()
                    }
                } else {
                    updateDownloadingState()
                }
            }
        })


    }

    private fun updateActionButtons() {
        launch(UI) {
            when (getDownloadStatus()) {
                STATUS_NOT_DOWNLOADED -> {
                    book_download_failed_container.visibility = GONE
                    book_downloading_container.visibility = GONE
                    book_read_and_delete_container.visibility = GONE
                    book_download_button_container.visibility = VISIBLE
                }
                STATUS_DOWNLOADING -> {
                    book_download_failed_container.visibility = GONE
                    book_download_button_container.visibility = GONE
                    book_read_and_delete_container.visibility = GONE
                    book_downloading_container.visibility = VISIBLE
                }
                STATUS_FAILED -> {
                    book_downloading_container.visibility = GONE
                    book_download_button_container.visibility = GONE
                    book_read_and_delete_container.visibility = GONE
                    book_download_failed_container.visibility = VISIBLE
                }
                STATUS_DOWNLOADED, DownloadManager.STATUS_SUCCESSFUL -> {
                    book_download_failed_container.visibility = GONE
                    book_downloading_container.visibility = GONE
                    book_download_button_container.visibility = GONE
                    book_read_and_delete_container.visibility = VISIBLE
                }
            }
        }
    }

    private fun isDownloaded(): Boolean {
        book?.downloaded?.let {
            return File(URI(it)).isFile
        }
        return false
    }

    private suspend fun getDownloadStatus(): Int {
        if (isDownloaded()) {
            return STATUS_DOWNLOADED
        }
        book?.let {
            val bookDownload = async { Gdl.getDatabase().bookDownloadDao().getBookDownload(it.id) }.await()
            if (bookDownload != null) {
                val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val newStatus = async {
                    val cursor = downloadManager.query(bookDownload.downloadId?.let { DownloadManager.Query().setFilterById(it) })
                    val status = if (cursor.moveToFirst()) {
                        cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                    } else {
                        null
                    }
                    cursor.close()
                    status
                }.await()

                when (newStatus) {
                    DownloadManager.STATUS_PAUSED, DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING -> return STATUS_DOWNLOADING
                    DownloadManager.STATUS_FAILED -> return STATUS_FAILED
                    DownloadManager.STATUS_SUCCESSFUL -> return STATUS_DOWNLOADED
                }
            }
        }
        return STATUS_NOT_DOWNLOADED
    }

    private suspend fun isDownloading(): Boolean {
        return getDownloadStatus() == STATUS_DOWNLOADING
    }

    private fun updateDownloadingState() {
        launch(CommonPool) {
            updateActionButtons()
            var isDownloading = isDownloading()
            while (isDownloading) {
                Log.i(TAG, "looping")
                isDownloading = isDownloading()
            }
            updateActionButtons()
        }
    }

    private fun downloadBook() {
        launch(CommonPool) {
            book?.let {
                val bookDownload = Gdl.getDatabase().bookDownloadDao().getBookDownload(it.id)
                if (bookDownload == null) {
                    val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val request = DownloadManager.Request(Uri.parse(it.ePubLink)).setDestinationInExternalFilesDir(applicationContext, null, "books/" + it.id + ".epub")
                    request.setTitle(it.title)
                    request.setDescription(applicationContext.getString(R.string.app_name))
                    val reqId = downloadManager.enqueue(request)
                    Gdl.getDatabase().bookDownloadDao().insert(BookDownload(bookId = it.id, downloadId = reqId))
                    updateDownloadingState()
                }
            }
        }
    }

    private fun cancelDownloadBook() {
        launch(CommonPool) {
            book?.let {
                val bookDownload = Gdl.getDatabase().bookDownloadDao().getBookDownload(it.id)
                if (bookDownload != null) {
                    val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    bookDownload.downloadId?.let { downloadManager.remove(it) }
                    Gdl.getDatabase().bookDownloadDao().delete(bookDownload)
                }
                updateActionButtons()
            }
        }
    }

    private fun dismissFailedDownload() {
        deleteBook()
    }

    private fun readBook() {
        launch(UI) {
            book?.let {
                if (isDownloaded()) {
                    it.downloaded?.let { uri ->
                        ReaderActivity.startActivity(this@BookDetailsActivity, it.id, File(URI(uri)))
                    }
                }
            }
        }
    }

    private fun deleteBook() {
        launch(CommonPool) {
            book?.let {
                val bookDownload = Gdl.getDatabase().bookDownloadDao().getBookDownload(it.id)
                if (bookDownload != null) {
                    val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    bookDownload.downloadId?.let { downloadManager.remove(it) }
                    Gdl.getDatabase().bookDownloadDao().delete(bookDownload)
                }

                it.downloaded?.let { uri ->
                    val bookFile = File(URI(uri))
                    if (bookFile.isFile) {
                        bookFile.delete()
                    }
                }
                it.downloaded = null
                Gdl.getDatabase().bookDao().update(it)
                Gdl.getDatabase().bookDownloadDao().delete(it.id)
                updateActionButtons()
            }
        }
    }
}