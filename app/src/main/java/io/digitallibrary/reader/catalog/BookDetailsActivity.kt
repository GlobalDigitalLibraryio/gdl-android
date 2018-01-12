package io.digitallibrary.reader.catalog

import android.app.DownloadManager
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
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import kotlinx.android.synthetic.main.book_details.*
import kotlinx.coroutines.experimental.CommonPool
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.format.FormatStyle
import kotlin.coroutines.experimental.suspendCoroutine


class BookDetailsActivity : AppCompatActivity() {
    private val STATUS_NOT_SET = -1
    private val STATUS_DOWNLOADED = -2
    private val STATUS_NOT_DOWNLOADED = -3
    private val STATUS_DOWNLOADING = -4

    private var status: Int = STATUS_NOT_SET
    private var reqId: Long = -1

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

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

        launch(UI) {
            val book = async { viewModel.getBook(bookId) }.await()
            Glide.with(applicationContext)
                    .load(book.image)
                    .apply(RequestOptions().centerCrop().placeholder(R.drawable.book_image_placeholder))
                    .into(book_cover)
            book_title.text = book.title
            book_publisher.text = book.publisher
            book_description.text = book.description
            book_level.text = getString(R.string.book_level, book.readingLevel)
            book_authors.text = book.author
            book_license.text = book.license
            book_published.text = book.published?.format(formatter)
            book_download_button.setOnClickListener { downloadBook(book) }
            book_download_retry.setOnClickListener { downloadBook(book) }
            book_download_dismiss.setOnClickListener { dismissFailedDownload(book) }

            book_download_cancel.setOnClickListener { cancelDownloadBook(book) }
            book_delete_book.setOnClickListener { deleteBook(book) }
            book_read_book.setOnClickListener { readBook(book) }
            updateDownloadingState(book)
        }
    }

    private fun updateActionButtons() {
        launch(UI) {
            when (status) {
                STATUS_NOT_DOWNLOADED, DownloadManager.STATUS_PAUSED -> {
                    book_download_failed_container.visibility = GONE
                    book_downloading_container.visibility = GONE
                    book_read_and_delete_container.visibility = GONE
                    book_download_button_container.visibility = VISIBLE
                }
                STATUS_DOWNLOADING, DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING -> {
                    book_download_failed_container.visibility = GONE
                    book_download_button_container.visibility = GONE
                    book_read_and_delete_container.visibility = GONE
                    book_downloading_container.visibility = VISIBLE
                }
                DownloadManager.STATUS_FAILED -> {
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

    private fun updateDownloadingState(book: Book) {
        launch(UI) {
            if (!bookIsDownloaded(book)) {
                val bookDownload = async { Gdl.getDatabase().bookDownloadDao().getBookDownload(book.id) }.await()
                var downloading = bookDownload != null
                val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

                while (downloading) {
                    Log.i(TAG, "Looping")
                    val newStatus = async {
                        val cursor = downloadManager.query(bookDownload!!.downloadId?.let { DownloadManager.Query().setFilterById(it) })
                        cursor.moveToFirst()
//                        val bytesDownloaded = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
//                        val bytesTotal = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                    }.await()

                    when (newStatus) {
                        DownloadManager.STATUS_PAUSED -> Log.i(TAG, "Download paused")
                        DownloadManager.STATUS_PENDING -> Log.i(TAG, "Download pending")
                        DownloadManager.STATUS_RUNNING -> Log.i(TAG, "Download running")
                        DownloadManager.STATUS_FAILED -> {
                            Log.i(TAG, "Download failed")
                            async { Gdl.getDatabase().bookDownloadDao().delete(bookDownload!!) }.await()
                            downloading = false
                        }
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            Log.i(TAG, "Download successful")
                            async {
                                book.downloaded = true
                                Gdl.getDatabase().bookDao().update(book)
                            }.await()
                            status = STATUS_DOWNLOADED
                            downloading = false
                        }
                        else -> {
                            downloading = false
                            Log.wtf(TAG, "This shouldn't happen")
                        }
                    }
                    status = newStatus
                    updateActionButtons()
                }
            }
            updateActionButtons()
        }
    }

    private suspend fun bookIsDownloaded(book: Book): Boolean {
        return suspendCoroutine { cont ->
            launch {
                val bookFile = book.getBookFile(applicationContext)
                val fileExists = bookFile.isFile
                val dbRegisteredAsDownloaded = book.downloaded

                if (fileExists && dbRegisteredAsDownloaded) {
                    status = STATUS_DOWNLOADED
                    cont.resume(true)
                    return@launch
                }

                val bookDownload = Gdl.getDatabase().bookDownloadDao().getBookDownload(book.id)
                val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                var isDownloading = false
                if (bookDownload != null) {
                    val cursor = downloadManager.query(bookDownload.downloadId?.let { DownloadManager.Query().setFilterById(it) })
                    cursor.moveToFirst()
                    val dmStatus = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                    when (dmStatus) {
                        DownloadManager.STATUS_PAUSED, DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING -> isDownloading = true
                    }
                }

                if (fileExists && !dbRegisteredAsDownloaded) {
                    if (isDownloading) {
                        status = STATUS_DOWNLOADING
                    } else {
                        Gdl.getDatabase().bookDownloadDao().delete(book.id)
                        bookFile.delete()
                        status = STATUS_NOT_DOWNLOADED
                    }
                    cont.resume(false)
                    return@launch
                }

                if (!fileExists && dbRegisteredAsDownloaded) {
                    book.downloaded = false
                    Gdl.getDatabase().bookDao().update(book)
                    Gdl.getDatabase().bookDownloadDao().delete(book.id)
                    status = STATUS_NOT_DOWNLOADED
                    cont.resume(false)
                    return@launch
                }

                status = STATUS_NOT_DOWNLOADED
                cont.resume(false)
            }
        }
    }

    private fun downloadBook(book: Book) {
        launch {
            val bookDownload = Gdl.getDatabase().bookDownloadDao().getBookDownload(book.id)
            if (bookDownload == null) {
                val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val request = DownloadManager.Request(Uri.parse(book.ePubLink)).setDestinationInExternalFilesDir(applicationContext, null, book.getBookFilePath())
                request.setTitle(book.title)
                request.setDescription("Downloading " + book.title + " from the Global Digital Library")
                reqId = downloadManager.enqueue(request)
                Gdl.getDatabase().bookDownloadDao().insert(BookDownload(bookId = book.id, downloadId = reqId))
                updateDownloadingState(book)
            }
        }

    }

    private fun cancelDownloadBook(book: Book) {
        launch {
            val bookDownload = Gdl.getDatabase().bookDownloadDao().getBookDownload(book.id)
            if (bookDownload != null) {
                val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                bookDownload.downloadId?.let { downloadManager.remove(it) }
                Gdl.getDatabase().bookDownloadDao().delete(bookDownload)
                status = STATUS_NOT_DOWNLOADED
            }
            updateActionButtons()
        }
    }

    private fun dismissFailedDownload(book: Book) {
        status = STATUS_NOT_DOWNLOADED
        updateActionButtons()
    }

    private fun readBook(book: Book) {
        launch {
            if (bookIsDownloaded(book)) {
                val file = book.getBookFile(applicationContext)
                ReaderActivity.startActivity(this@BookDetailsActivity, book.id, file)
            } else {
                // Show error message
                val downloading = Gdl.getDatabase().bookDownloadDao().getBookDownload(book.id)
                if (downloading != null) {
                    Gdl.getDatabase().bookDownloadDao().delete(downloading)
                    status = -1
                    launch(UI) { updateActionButtons() }
                }
            }
        }
    }

    private fun deleteBook(book: Book) {
        launch(CommonPool) {
            val bookFile = book.getBookFile(applicationContext)
            if (bookFile.isFile) {
                bookFile.delete()
            }
            book.downloaded = false
            Gdl.getDatabase().bookDao().update(book)
            Gdl.getDatabase().bookDownloadDao().delete(book.id)
            status = STATUS_NOT_DOWNLOADED
            updateActionButtons()
        }
    }
}