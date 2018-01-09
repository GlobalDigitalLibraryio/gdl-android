package io.digitallibrary.reader;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import static io.digitallibrary.reader.catalog.OpdsParserKt.fetchFeed;


public class SplashActivity extends AppCompatActivity {

    public SplashActivity() {}

    @Override
    protected void onCreate(final Bundle state) {
        super.onCreate(state);

//        fetchFeed();

        final Intent i = new Intent(this, GdlActivity.class);
        i.putExtra("reload", true);
        this.startActivity(i);
        this.overridePendingTransition(0, 0);
        this.finish();
    }
}
