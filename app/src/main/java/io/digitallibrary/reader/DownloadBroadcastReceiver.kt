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
        when (intent?.action) {
            DownloadManager.ACTION_DOWNLOAD_COMPLETE -> {
                val id = intent.extras.getLong(DownloadManager.EXTRA_DOWNLOAD_ID)
                launch(CommonPool) {
                    Gdl.database.bookDownloadDao().getBookDownload(id)?.bookId?.let {
                        if (context != null) {
                            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                            val localUri = async {
                                val cursor = downloadManager.query(DownloadManager.Query().setFilterById(id))
                                val res = if (cursor.moveToFirst()) {
                                    cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                                } else {
                                    null
                                }
                                cursor.close()
                                res
                            }.await()
                            if (localUri != null) {
                                Gdl.database.bookDao().getBook(it)?.let {
                                    it.downloaded = localUri.toString()
                                    Gdl.database.bookDao().update(it)
                                }
                            }
                        }
                    }
                }
            }
            DownloadManager.ACTION_NOTIFICATION_CLICKED -> {
                val ids = intent.extras.getLongArray(DownloadManager.EXTRA_NOTIFICATION_CLICK_DOWNLOAD_IDS)
                if (ids.isNotEmpty()) {
                    val i = Intent(context, SplashActivity::class.java)
                    i.putExtra("download_id", ids[0])
                    i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context?.startActivity(i)
                }
            }
        }
    }
}