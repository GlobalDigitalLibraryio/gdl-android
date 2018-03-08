package io.digitallibrary.reader.catalog

import android.app.DownloadManager
import android.content.Context
import io.digitallibrary.reader.Gdl
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.launch
import java.io.File
import java.net.URI

fun deleteBooks(books: List<Book>) {
    for (book in books) {
        deleteBook(book)
    }
}

fun deleteBook(book: Book) {
    launch(CommonPool) {
        val bookDownload = Gdl.database.bookDownloadDao().getBookDownload(book.id)
        if (bookDownload != null) {
            val downloadManager = Gdl.appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            bookDownload.downloadId?.let { downloadManager.remove(it) }
            Gdl.database.bookDownloadDao().delete(bookDownload)
        }

        book.downloaded?.let { uri ->
            val bookFile = File(URI(uri))
            if (bookFile.isFile) {
                bookFile.delete()
            }
        }

        book.downloaded = null
        book.downloadedDateTime = null
        Gdl.database.bookDao().update(book)
        Gdl.database.bookDownloadDao().delete(book.id)
    }
}