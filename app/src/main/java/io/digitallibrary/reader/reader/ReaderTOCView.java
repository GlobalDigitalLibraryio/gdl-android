package io.digitallibrary.reader.reader;

import android.content.Context;
import android.database.DataSetObserver;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;

import java.util.List;

import io.digitallibrary.reader.Gdl;
import io.digitallibrary.reader.R;
import io.digitallibrary.reader.reader.ReaderTOC.TOCElement;

/**
 * A re-usable view of a table of contents.
 */
public final class ReaderTOCView implements ListAdapter {
    private static final String TAG = "ReaderTOCView";

    private static final int ELEMENT_START_MARGIN = 72;
    private static final int ELEMENT_INDENT_MARGIN = 24;

    private final ArrayAdapter<TOCElement> adapter;
    private final Context context;
    private final LayoutInflater inflater;
    private final ReaderTOCViewSelectionListenerType listener;
    private final ViewGroup view_layout;

    /**
     * Construct a TOC view.
     *
     * @param in_inflater A layout inflater
     * @param in_context  A context
     * @param in_toc      The table of contents
     * @param in_listener A selection listener
     */

    public ReaderTOCView(final LayoutInflater in_inflater, final Context in_context,
                         final ReaderTOC in_toc, final ReaderTOCViewSelectionListenerType in_listener) {
        NullCheck.notNull(in_inflater);
        NullCheck.notNull(in_context);
        NullCheck.notNull(in_toc);
        NullCheck.notNull(in_listener);

        final ViewGroup in_layout = NullCheck.notNull((ViewGroup) in_inflater.inflate(R.layout.reader_toc, null));
        final ListView in_list_view = NullCheck.notNull((ListView) in_layout.findViewById(R.id.reader_toc_list));

        final List<TOCElement> es = in_toc.getElements();
        this.adapter = new ArrayAdapter<>(in_context, 0, es);

        in_list_view.setAdapter(this);

        context = in_context;
        view_layout = in_layout;
        inflater = in_inflater;
        listener = in_listener;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return NullCheck.notNull(this.adapter).areAllItemsEnabled();
    }

    @Override
    public int getCount() {
        return NullCheck.notNull(this.adapter).getCount();
    }

    @Override
    public TOCElement getItem(final int position) {
        return NullCheck.notNull(NullCheck.notNull(this.adapter).getItem(position));
    }

    @Override
    public long getItemId(final int position) {
        return NullCheck.notNull(this.adapter).getItemId(position);
    }

    @Override
    public int getItemViewType(final int position) {
        return NullCheck.notNull(this.adapter).getItemViewType(position);
    }

    /**
     * @return The view group containing the main layout
     */
    ViewGroup getLayoutView() {
        return this.view_layout;
    }

    @Override
    public View getView(final int position, final @Nullable View reuse, final @Nullable ViewGroup parent) {
        final ViewGroup item_view;
        if (reuse != null) {
            item_view = (ViewGroup) reuse;
        } else {
            item_view = (ViewGroup) this.inflater.inflate(R.layout.reader_toc_element, parent, false);
        }

        /*
         * Populate the text view and set the left margin based on the desired
         * indentation level.
         */

        final TextView text_view = NullCheck.notNull((TextView) item_view.findViewById(R.id.reader_toc_element_text));
        final TOCElement e = NullCheck.notNull(this.adapter).getItem(position);
        text_view.setText(e.getTitle());

        final Gdl.ReaderAppServices rs = Gdl.Companion.getReaderAppServices();

        final float scale = context.getResources().getDisplayMetrics().density;

        final RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(38 * scale));
        p.setMargins((int) rs.screenDPToPixels((e.getIndent() * ELEMENT_INDENT_MARGIN) + ELEMENT_START_MARGIN), 0, 0, 0);
        text_view.setLayoutParams(p);

        item_view.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(
                    final @Nullable View v) {
                ReaderTOCView.this.listener.onTOCItemSelected(e);
            }
        });

        return item_view;
    }

    public void animateIn() {
        view_layout.setY(-Gdl.Companion.getReaderAppServices().screenGetHeightPixels());
        view_layout.animate().y(0f);
    }

    public void animateOut(Runnable callback) {
        view_layout.animate().y(-Gdl.Companion.getReaderAppServices().screenGetHeightPixels()).withEndAction(callback);
    }

    @Override
    public int getViewTypeCount() {
        return NullCheck.notNull(this.adapter).getViewTypeCount();
    }

    @Override
    public boolean hasStableIds() {
        return NullCheck.notNull(this.adapter).hasStableIds();
    }

    /**
     * Hide the back button!
     */
    @Override
    public boolean isEmpty() {
        return NullCheck.notNull(this.adapter).isEmpty();
    }

    @Override
    public boolean isEnabled(final int position) {
        return NullCheck.notNull(this.adapter).isEnabled(position);
    }

    /**
     * Called when a table of contents is destroyed.
     */
    void onTOCViewDestroy() {
        Log.d(TAG, "onTOCViewDestroy");
    }

    @Override
    public void registerDataSetObserver(final @Nullable DataSetObserver observer) {
        NullCheck.notNull(this.adapter).registerDataSetObserver(observer);
    }

    @Override
    public void unregisterDataSetObserver(final @Nullable DataSetObserver observer) {
        NullCheck.notNull(this.adapter).unregisterDataSetObserver(observer);
    }
}
