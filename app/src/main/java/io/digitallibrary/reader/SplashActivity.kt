package io.digitallibrary.reader

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager.CONNECTIVITY_ACTION
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.sync.Mutex

class SplashActivity : AppCompatActivity() {

    private lateinit var connectionReceiver: BroadcastReceiver
    private var noNetworkDialog: AlertDialog? = null
    private val lock = Mutex()
    private var haveCalledStart = false

    private fun start() {
        haveCalledStart = true
        Gdl.fetchOpdsFeed()
        val i = Intent(this@SplashActivity, GdlActivity::class.java)
        val id = intent.getLongExtra("download_id", -1)
        if (id >= 0) {
            i.putExtra("download_id", id)
        }
        noNetworkDialog?.dismiss()
        noNetworkDialog = null
        startActivity(i)
        finish()
    }

    private fun checkForNet() {
        launch(UI) {
            lock.lock()
            try {
                if (!haveCalledStart) {
                    val haveBooks = async { Gdl.database.bookDao().haveAnyBooks() }.await()
                    if (haveBooks) {
                        start()
                    } else {
                        if (Gdl.isNetworkAvailable()) {
                            start()
                        } else {
                            setContentView(R.layout.activity_no_network_splash)
                            noNetworkDialog = AlertDialog.Builder(this@SplashActivity).create()
                            noNetworkDialog?.setTitle(R.string.dialog_no_network_first_time_title)
                            noNetworkDialog?.setMessage(getString(R.string.dialog_no_network_first_time_message))
                            noNetworkDialog?.setButton(
                                AlertDialog.BUTTON_POSITIVE,
                                getString(R.string.dialog_action_network_settings),
                                { _, _ ->
                                    val intent =
                                        Intent(android.provider.Settings.ACTION_NETWORK_OPERATOR_SETTINGS)
                                    startActivity(intent)
                                    noNetworkDialog = null
                                })
                            noNetworkDialog?.setCancelable(false)
                            noNetworkDialog?.setCanceledOnTouchOutside(false)
                            noNetworkDialog?.show()
                        }
                    }
                }
            } finally {
                lock.unlock()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                checkForNet()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val i = IntentFilter(CONNECTIVITY_ACTION)
        registerReceiver(connectionReceiver, i)
        checkForNet()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(connectionReceiver)
    }
}
