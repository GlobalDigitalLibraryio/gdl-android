package io.digitallibrary.reader.catalog

import android.animation.LayoutTransition
import android.app.AlertDialog
import android.app.DownloadManager
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.content.FileProvider
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import io.digitallibrary.reader.Gdl
import io.digitallibrary.reader.R
import io.digitallibrary.reader.reader.ReaderActivity
import kotlinx.android.synthetic.main.activity_book_details.*
import kotlinx.android.synthetic.main.toolbar.*
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.format.FormatStyle
import java.io.File
import java.net.URI


class BookDetailsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BookDetailsActivity"
        private const val STATUS_DOWNLOADED = -1
        private const val STATUS_NOT_DOWNLOADED = -2
        private const val STATUS_DOWNLOADING = -3
        private const val STATUS_FAILED = -4
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private var paused = false
    private var canStartAnotherActivity = true

    override fun onPause() {
        super.onPause()
        // Need to check for this to kill long running coroutines when needed
        paused = true
    }

    override fun onResume() {
        super.onResume()
        paused = false
        canStartAnotherActivity = true
    }

    private var book: Book? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Gdl.getThemeId())
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_details)

        val bookId = intent.getStringExtra("book_id")
        val viewModel = ViewModelProviders.of(this).get(CatalogViewModel::class.java)


        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.selections_book_details)

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
                            book_publisher.text =
                                    getString(R.string.book_details_publisher, it.publisher)
                            book_description.text = it.description
                            if (it.readingLevel != null) {
                                book_level_container.visibility = VISIBLE
                                book_level.text = it.readingLevel
                            } else {
                                book_level_container.visibility = GONE
                            }
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
                        updateDownloadingState(true)
                    }
                } else {
                    updateDownloadingState()
                }
            }
        })

        viewModel.getBookContributors(bookId).observe(this, Observer {
            if (it != null) {
                val contributors = it.groupBy { it.type?.toLowerCase() }

                updateContributor(
                    contributors["author"] ?: emptyList(),
                    book_authors_header,
                    book_authors,
                    R.string.book_details_authors,
                    R.string.book_details_author
                )

                updateContributor(
                    contributors["illustrator"] ?: emptyList(),
                    book_illustrator_header,
                    book_illustrator,
                    R.string.book_details_illustrators,
                    R.string.book_details_illustrator
                )

                updateContributor(
                    contributors["translator"] ?: emptyList(),
                    book_translator_header,
                    book_translator,
                    R.string.book_details_translators,
                    R.string.book_details_translator
                )

                updateContributor(
                    contributors["photographer"] ?: emptyList(),
                    book_photographer_header,
                    book_photographer,
                    R.string.book_details_photographers,
                    R.string.book_details_photographer
                )

                updateContributor(
                    contributors["contributor"] ?: emptyList(),
                    book_contributor_header,
                    book_contributor,
                    R.string.book_details_contributors,
                    R.string.book_details_contributor
                )
            } else {
                listOf(
                    book_authors_header,
                    book_authors,
                    book_illustrator_header,
                    book_illustrator,
                    book_translator_header,
                    book_translator,
                    book_photographer_header,
                    book_photographer,
                    book_contributor_header,
                    book_contributor
                ).forEach { it.visibility = View.GONE }

            }
        })
    }

    private fun updateContributor(
        contributors: List<Contributor>,
        title: TextView,
        names: TextView,
        plural: Int,
        singular: Int
    ) {
        if (contributors.isNotEmpty()) {
            title.visibility = View.VISIBLE
            names.visibility = View.VISIBLE
            names.text = contributors.joinToString(", ") { it.name }
            if (contributors.size == 1) {
                title.text = getString(singular)
            } else {
                title.text = getString(plural)
            }
        } else {
            title.visibility = View.GONE
            names.visibility = View.GONE
        }
    }

    private fun updateActionButtons(activateTransitions: Boolean = false) {
        launch(UI) {
            when (getDownloadStatus()) {
                STATUS_NOT_DOWNLOADED -> {
                    book_download_retry.visibility = GONE
                    book_download_dismiss.visibility = GONE
                    book_read_book.visibility = GONE
                    book_delete_book.visibility = GONE
                    book_downloading.visibility = GONE
                    book_download_cancel.visibility = GONE
                    book_download_button.visibility = VISIBLE
                }
                STATUS_DOWNLOADING -> {
                    book_download_retry.visibility = GONE
                    book_download_dismiss.visibility = GONE
                    book_read_book.visibility = GONE
                    book_delete_book.visibility = GONE
                    book_downloading.visibility = VISIBLE
                    book_download_cancel.visibility = VISIBLE
                    book_download_button.visibility = GONE
                }
                STATUS_FAILED -> {
                    book_download_retry.visibility = VISIBLE
                    book_download_dismiss.visibility = VISIBLE
                    book_read_book.visibility = GONE
                    book_delete_book.visibility = GONE
                    book_downloading.visibility = GONE
                    book_download_cancel.visibility = GONE
                    book_download_button.visibility = GONE
                }
                STATUS_DOWNLOADED, DownloadManager.STATUS_SUCCESSFUL -> {
                    book_download_retry.visibility = GONE
                    book_download_dismiss.visibility = GONE
                    book_read_book.visibility = VISIBLE
                    book_delete_book.visibility = VISIBLE
                    book_downloading.visibility = GONE
                    book_download_cancel.visibility = GONE
                    book_download_button.visibility = GONE
                }
            }
            if (activateTransitions) {
                button_container.layoutTransition = LayoutTransition()
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
            val bookDownload =
                async { Gdl.database.bookDownloadDao().getBookDownload(it.id) }.await()
            if (bookDownload != null) {
                val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val newStatus = async {
                    val cursor = downloadManager.query(bookDownload.downloadId?.let {
                        DownloadManager.Query().setFilterById(it)
                    })
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

    private fun updateDownloadingState(activateTransitions: Boolean = false) {
        launch(CommonPool) {
            updateActionButtons(activateTransitions)
            var isDownloading = isDownloading()
            while (isDownloading && !paused) {
                isDownloading = isDownloading()
            }
            updateActionButtons()
        }
    }

    private fun downloadBook() {
        launch(CommonPool) {
            book?.let {
                val bookDownload = Gdl.database.bookDownloadDao().getBookDownload(it.id)
                if (bookDownload == null) {
                    val downloadManager =
                        getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val link = it.ePubLink ?: it.pdfLink
                    if (link != null) {
                        // We don't want any ':' in the file name
                        val bookFileName =
                            it.id.split(":").last() + if (it.ePubLink != null) ".epub" else ".pdf"
                        val request = DownloadManager.Request(Uri.parse(link))
                            .setDestinationInExternalFilesDir(
                                applicationContext,
                                null,
                                bookFileName
                            )
                        request.setTitle(it.title)
                        request.setDescription(applicationContext.getString(R.string.app_name))
                        request.setMimeType("application/x-gdl-book")
                        val reqId = downloadManager.enqueue(request)
                        Gdl.database.bookDownloadDao()
                            .insert(BookDownload(bookId = it.id, downloadId = reqId))
                        updateDownloadingState()
                    }
                }
            }
        }
    }

    private fun cancelDownloadBook() {
        launch(CommonPool) {
            book?.let {
                val bookDownload = Gdl.database.bookDownloadDao().getBookDownload(it.id)
                if (bookDownload != null) {
                    val downloadManager =
                        getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    bookDownload.downloadId?.let { downloadManager.remove(it) }
                    Gdl.database.bookDownloadDao().delete(bookDownload)
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
                        if (canStartAnotherActivity) {
                            val bookFile = File(URI(uri))
                            if (bookFile.name.endsWith(".epub")) {
                                ReaderActivity.startActivity(
                                    this@BookDetailsActivity,
                                    it.id,
                                    bookFile
                                )
                                canStartAnotherActivity = false
                            } else if (bookFile.name.endsWith(".pdf")) {
                                val pdfUri = FileProvider.getUriForFile(
                                    applicationContext,
                                    applicationContext.packageName + ".provider",
                                    bookFile
                                )
                                val intent = Intent(Intent.ACTION_VIEW)
                                intent.setDataAndType(pdfUri, "application/pdf")
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                startActivity(intent)
                                canStartAnotherActivity = false
                            }
                        }
                    }
                }
            }
        }
    }

    private fun deleteBook() {
        book?.let {

            val confirmDialog = AlertDialog.Builder(this).create()
            confirmDialog.setMessage(getString(R.string.my_library_confirm_delete_books))
            confirmDialog.setButton(
                AlertDialog.BUTTON_POSITIVE,
                getString(R.string.dialog_action_delete),
                { _, _ ->
                    deleteBook(it)
                })
            confirmDialog.setButton(
                AlertDialog.BUTTON_NEGATIVE,
                getString(R.string.dialog_action_cancel),
                { _, _ -> })
            confirmDialog.show()
        }
    }
}
