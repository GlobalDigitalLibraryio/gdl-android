package io.digitallibrary.reader.reader.widget;

import android.annotation.TargetApi;
import android.graphics.Outline;
import android.graphics.Path;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;

/**
 * Only use this if Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
 */

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class RectangleOutline extends ViewOutlineProvider {
  @Override
  public void getOutline(View view, Outline outline) {
    Path p = new Path();
    p.moveTo(0, view.getHeight());
    p.lineTo(view.getWidth() / 2, 0);
    p.lineTo(view.getWidth(), view.getHeight());
    p.close();
    outline.setConvexPath(p);
  }
}