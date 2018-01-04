package io.digitallibrary.reader.reader;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.TextView;

import com.io7m.jnull.NullCheck;

import io.digitallibrary.reader.Gdl;
import io.digitallibrary.reader.R;
import io.digitallibrary.reader.reader.widget.RectangleOutline;


public class ReaderSettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.reader_settings);

        // Configure settings
        final TextView in_view_black_on_white =
                NullCheck.notNull((TextView) this.findViewById(R.id.reader_settings_black_on_white));
        final TextView in_view_white_on_black =
                NullCheck.notNull((TextView) this.findViewById(R.id.reader_settings_white_on_black));
        final TextView in_view_black_on_beige =
                NullCheck.notNull((TextView) this.findViewById(R.id.reader_settings_black_on_beige));

        final TextView in_view_text_smaller =
                NullCheck.notNull((TextView) this.findViewById(R.id.reader_settings_text_smaller));
        final TextView in_view_text_larger =
                NullCheck.notNull((TextView) this.findViewById(R.id.reader_settings_text_larger));

        final SeekBar in_view_brightness =
                NullCheck.notNull((SeekBar) this.findViewById(R.id.reader_settings_brightness));

        // Check if we're running on Android 5.0 or higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // add triangle outline
            final View in_rectangle =
                    NullCheck.notNull((View) this.findViewById(R.id.rectangle));
            in_rectangle.setOutlineProvider(new RectangleOutline());
        }

        final Gdl.ReaderAppServices rs = Gdl.getReaderAppServices();
        final ReaderSettingsType settings = rs.getSettings();

        /*
         * Configure the settings buttons.
         */
        in_view_black_on_white.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final @com.io7m.jnull.Nullable View v) {
                settings.setColorScheme(ReaderColorScheme.SCHEME_BLACK_ON_WHITE);
            }
        });

        in_view_white_on_black.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final @com.io7m.jnull.Nullable View v) {
                settings.setColorScheme(ReaderColorScheme.SCHEME_WHITE_ON_BLACK);
            }
        });

        in_view_black_on_beige.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final @com.io7m.jnull.Nullable View v) {
                settings.setColorScheme(ReaderColorScheme.SCHEME_BLACK_ON_BEIGE);
            }
        });

        in_view_text_larger.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final @com.io7m.jnull.Nullable View v) {
                if (settings.getFontScale() < 250) {
                    settings.setFontScale(settings.getFontScale() + 25.0f);
                }
            }
        });

        in_view_text_smaller.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final @com.io7m.jnull.Nullable View v) {
                if (settings.getFontScale() > 75) {
                    settings.setFontScale(settings.getFontScale() - 25.0f);
                }
            }
        });

        /*
         * Configure brightness controller.
         */
        final int brightness = getPreferences(Context.MODE_PRIVATE).getInt("reader_brightness", 50);
        in_view_brightness.setProgress(brightness);
        in_view_brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(final @com.io7m.jnull.Nullable SeekBar bar, final int progress,
                                          final boolean from_user) {
                final float back_light_value = (float) progress / 100;

                final WindowManager.LayoutParams layout_params = getWindow().getAttributes();
                layout_params.screenBrightness = back_light_value;
                getWindow().setAttributes(layout_params);
                getPreferences(Context.MODE_PRIVATE).edit().putInt("reader_brightness", progress).apply();

            }

            @Override
            public void onStartTrackingTouch(final @com.io7m.jnull.Nullable SeekBar bar) {
                // Nothing
            }

            @Override
            public void onStopTrackingTouch(final @com.io7m.jnull.Nullable SeekBar bar) {
                // Nothing
            }
        });

        // set reader brightness.
        final float back_light_value = (float) brightness / 100;
        final WindowManager.LayoutParams layout_params = getWindow().getAttributes();
        layout_params.screenBrightness = back_light_value;
        getWindow().setAttributes(layout_params);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    public void onBackgroundClicked(View view) {
        finish();
    }
}