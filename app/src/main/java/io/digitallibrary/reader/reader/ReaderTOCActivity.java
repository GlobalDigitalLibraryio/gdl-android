package io.digitallibrary.reader.reader;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;

import io.digitallibrary.reader.reader.ReaderTOC.TOCElement;

/**
 * Activity for displaying the table of contents on devices with small screens.
 */

public final class ReaderTOCActivity extends AppCompatActivity implements ReaderTOCViewSelectionListenerType {
    private static final String TAG = "ReaderTOCActivity";

    /**
     * The name of the argument containing the TOC.
     */
    public static final String TOC_ID = "io.digitallibrary.reader.reader.ReaderTOCActivity.toc";

    /**
     * The name of the argument containing the selected TOC item.
     */
    public static final String TOC_SELECTED_ID =
            "io.digitallibrary.reader.reader.ReaderTOCActivity.toc_selected";

    /**
     * The activity request code (for retrieving the result of executing the
     * activity).
     */
    public static final int TOC_SELECTION_REQUEST_CODE = 23;


    private @Nullable ReaderTOCView view;

    /**
     * Construct an activity.
     */
    public ReaderTOCActivity() {
    }

    /**
     * Start a TOC activity. The user will be prompted to select a TOC item, and
     * the results of that selection will be reported using the request code
     * {@link #TOC_SELECTION_REQUEST_CODE}.
     *
     * @param from The parent activity
     * @param toc  The table of contents
     */
    public static void startActivityForResult(final Activity from, final ReaderTOC toc) {
        NullCheck.notNull(from);
        NullCheck.notNull(toc);

        final Intent i = new Intent(Intent.ACTION_PICK);
        i.setClass(from, ReaderTOCActivity.class);
        i.putExtra(ReaderTOCActivity.TOC_ID, toc);

        from.startActivityForResult(i, ReaderTOCActivity.TOC_SELECTION_REQUEST_CODE);
    }

    @Override
    public void finish() {
        super.finish();
        this.overridePendingTransition(0, 0);
    }

    @Override
    protected void onCreate(final @Nullable Bundle state) {
        super.onCreate(state);

        Log.d(TAG, "onCreate");

        final Intent input = NullCheck.notNull(this.getIntent());
        final Bundle args = NullCheck.notNull(input.getExtras());

        final ReaderTOC in_toc =
                NullCheck.notNull((ReaderTOC) args.getSerializable(ReaderTOCActivity.TOC_ID));

        final LayoutInflater inflater = NullCheck.notNull(this.getLayoutInflater());
        this.view = new ReaderTOCView(inflater, this, in_toc, this);
        this.setContentView(this.view.getLayoutView());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        NullCheck.notNull(this.view).onTOCViewDestroy();
    }

    @Override
    public void onTOCBackSelected() {
        this.finish();
    }

    @Override
    public void onTOCItemSelected(final TOCElement e) {
        final Intent intent = new Intent();
        intent.putExtra(ReaderTOCActivity.TOC_SELECTED_ID, e);
        this.setResult(Activity.RESULT_OK, intent);
        this.finish();
    }

    public void onBackgroundClicked(View view) {
        finish();
    }
}
