package io.digitallibrary.reader

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)

        Gdl.fetchOpdsFeed()

        val i = Intent(this, GdlActivity::class.java)
        i.putExtra("reload", true)
        this.startActivity(i)
        this.finish()
    }
}
