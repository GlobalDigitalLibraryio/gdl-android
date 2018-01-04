package io.digitallibrary.reader;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import java.util.Timer;
import java.util.TimerTask;

/**
 * A splash screen activity that either shows a license agreement, or simply
 * starts up another activity without displaying anything if the user has
 * already agreed to the license.
 */

public class MainSplashActivity extends Activity {

    /**
     * Construct an activity.
     */
    public MainSplashActivity() {
    }

    @Override
    protected void onCreate(final Bundle state) {
        super.onCreate(state);
        this.setContentView(R.layout.splash);

        final Timer timer = new Timer();
        timer.schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        finishSplash();
                    }
                }, 2000L);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        this.finishSplash();
    }

    private void finishSplash() {
        if (Gdl.getSharedPrefs().contains("welcome")) {
            this.startMain();
        } else {
            this.startMain();
        }
    }

    private void startMain() {
        final Intent i = new Intent(this, GdlActivity.class);
        i.putExtra("reload", true);
        this.startActivity(i);
        this.overridePendingTransition(0, 0);
        this.finish();
    }
}
