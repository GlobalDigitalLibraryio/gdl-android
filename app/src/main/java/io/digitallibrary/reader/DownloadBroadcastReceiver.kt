package io.digitallibrary.reader

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch

class DownloadBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "android.intent.action.DOWNLOAD_COMPLETE") {
            val id = intent.extras.getLong(DownloadManager.EXTRA_DOWNLOAD_ID)
            launch(CommonPool) {
                Gdl.getDatabase().bookDownloadDao().getBookDownload(id)?.bookId?.let {
                    if (context != null) {
                    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        val localUri = async {
                            val cursor = downloadManager.query(DownloadManager.Query().setFilterById(id))
                            if (cursor.moveToFirst()) {
                                cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                            } else {
                                null
                            }
                        }.await()
                        if (localUri != null) {
                            val book = Gdl.getDatabase().bookDao().getBook(it)
                            book.downloaded = localUri.toString()
                            Gdl.getDatabase().bookDao().update(book)
                        }
                    }
                }
            }
        }
    }
}