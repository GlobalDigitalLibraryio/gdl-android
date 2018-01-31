package io.digitallibrary.reader

import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.os.Bundle

class HandleLinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val i = Intent(applicationContext, SplashActivity::class.java)
        val id = ContentUris.parseId(intent.data)
        if (id >= 0) {
            i.putExtra("download_id", id)
        }
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(i)
    }
}
