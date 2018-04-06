package io.digitallibrary.reader.reader

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.SeekBar
import io.digitallibrary.reader.Gdl
import io.digitallibrary.reader.R
import kotlinx.android.synthetic.main.activity_reader_settings.*

class ReaderSettingsActivity : AppCompatActivity() {

    private lateinit var settings: ReaderSettingsType

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Gdl.getTranslucentThemeId())

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_reader_settings)

        animateIn()

        settings = Gdl.readerAppServices.settings

        reader_settings_black_on_white.setOnClickListener {
            settings.colorScheme = ReaderColorScheme.SCHEME_BLACK_ON_WHITE
            updateCheck()
        }
        reader_settings_white_on_black.setOnClickListener {
            settings.colorScheme = ReaderColorScheme.SCHEME_WHITE_ON_BLACK
            updateCheck()
        }
        reader_settings_black_on_beige.setOnClickListener {
            settings.colorScheme = ReaderColorScheme.SCHEME_BLACK_ON_BEIGE
            updateCheck()
        }

        reader_settings_text_larger.setOnClickListener {
            if (settings.fontScale < 250) {
                settings.fontScale = settings.fontScale + 25.0f
            }
        }

        reader_settings_text_smaller.setOnClickListener {
            if (settings.fontScale > 75) {
                settings.fontScale = settings.fontScale - 25.0f
            }
        }

        reader_settings_brightness.progress = Gdl.sharedPrefs.getInt("reader_brightness", 50)
        reader_settings_brightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(@com.io7m.jnull.Nullable bar: SeekBar, progress: Int, from_user: Boolean) {
                val backLightValue = progress.toFloat() / 100
                val layoutParams = window.attributes
                layoutParams.screenBrightness = backLightValue
                window.attributes = layoutParams
                Gdl.sharedPrefs.putInt("reader_brightness", progress)
            }

            override fun onStartTrackingTouch(@com.io7m.jnull.Nullable bar: SeekBar) {
                // Nothing
            }

            override fun onStopTrackingTouch(@com.io7m.jnull.Nullable bar: SeekBar) {
                // Nothing
            }
        })

        updateCheck()
    }

    private fun updateCheck() {
        when (settings.colorScheme) {
            ReaderColorScheme.SCHEME_BLACK_ON_WHITE -> {
                reader_settings_black_on_white_check.visibility = View.VISIBLE
                reader_settings_black_on_beige_check.visibility = View.INVISIBLE
                reader_settings_white_on_black_check.visibility = View.INVISIBLE
            }
            ReaderColorScheme.SCHEME_BLACK_ON_BEIGE -> {
                reader_settings_black_on_white_check.visibility = View.INVISIBLE
                reader_settings_black_on_beige_check.visibility = View.VISIBLE
                reader_settings_white_on_black_check.visibility = View.INVISIBLE
            }
            ReaderColorScheme.SCHEME_WHITE_ON_BLACK -> {
                reader_settings_black_on_white_check.visibility = View.INVISIBLE
                reader_settings_black_on_beige_check.visibility = View.INVISIBLE
                reader_settings_white_on_black_check.visibility = View.VISIBLE
            }
            else -> throw IllegalArgumentException("Enum is broken")
        }
    }

    private fun animateIn() {
        val scale = application.resources.displayMetrics.density
        reader_settings_container.y = -200f * scale
        reader_settings_container.animate().y(0f)
    }

    private fun animateOut(callback: Runnable) {
        val scale = application.resources.displayMetrics.density
        reader_settings_container.animate().y(-200f * scale).withEndAction(callback)
    }

    override fun finish() {
        animateOut(Runnable {
            super.finish()
            overridePendingTransition(0, 0)
        })
    }

    fun onClose(view: View) {
        finish()
    }
}