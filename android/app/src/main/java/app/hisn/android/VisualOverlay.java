package app.hisn.android;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A temporary, touch-blocking cover over a selected window when a face matches the setting.
 *
 * <p>It is an accessibility overlay, not an app window: no SYSTEM_ALERT_WINDOW permission is
 * needed and the app underneath cannot receive taps while the cover is up. The cover uses a
 * heavily downscaled snapshot of the frame (enough to look like blur, not enough to read) on a
 * dark scrim. It is removed only by a later clean match, a window change, screen off, disabling
 * the visual layer, or leaving the app. Nothing is written to disk.</p>
 */
final class VisualOverlay implements AutoCloseable {
    static volatile long shows;
    private final AccessibilityService service;
    private final WindowManager windows;
    private FrameLayout root;
    private ImageView snapshotView;
    private Bitmap snapshot;
    private boolean shown;

    VisualOverlay(AccessibilityService service) {
        this.service = service;
        this.windows = service.getSystemService(WindowManager.class);
    }

    boolean shown() { return shown; }

    /** Shows or refreshes the cover. A null frame leaves the existing snapshot in place. */
    void show(Bitmap frame) {
        if (windows == null) return;
        if (root == null) build();
        if (frame != null) {
            Bitmap smaller = blur(frame);
            if (smaller != null) {
                Bitmap previous = snapshot;
                snapshot = smaller;
                snapshotView.setImageBitmap(smaller);
                snapshotView.setVisibility(View.VISIBLE);
                if (previous != null && previous != smaller) previous.recycle();
            }
        }
        if (!shown) {
            try {
                windows.addView(root, params());
                shown = true;
                shows++;
            } catch (RuntimeException unavailable) {
                shown = false;
            }
        }
    }

    void hide() {
        if (!shown) {
            recycle();
            return;
        }
        shown = false;
        if (root != null) {
            try { windows.removeView(root); } catch (RuntimeException alreadyGone) { /* Nothing to remove. */ }
        }
        recycle();
    }

    private void recycle() {
        if (snapshotView != null) snapshotView.setImageDrawable(null);
        if (snapshot != null) { snapshot.recycle(); snapshot = null; }
    }

    /** A 10-pixel long-edge copy is colors only at full screen: no face or clothing survives. */
    private static Bitmap blur(Bitmap frame) {
        try {
            int width = frame.getWidth(), height = frame.getHeight();
            if (width < 1 || height < 1) return null;
            double scale = 10.0 / Math.max(width, height);
            int w = Math.max(1, (int) Math.round(width * scale));
            int h = Math.max(1, (int) Math.round(height * scale));
            return Bitmap.createScaledBitmap(frame, w, h, false);
        } catch (RuntimeException | OutOfMemoryError unavailable) {
            return null;
        }
    }

    private void build() {
        root = new FrameLayout(service);
        root.setBackgroundColor(Color.rgb(18, 18, 18));
        root.setClickable(true);
        root.setFocusable(false);
        root.setOnClickListener(view -> { /* Consume the tap: the content beneath stays covered. */ });
        root.setContentDescription(service.getString(R.string.person_overlay_title));

        snapshotView = new ImageView(service);
        snapshotView.setScaleType(ImageView.ScaleType.FIT_XY);
        snapshotView.setAlpha(1f);
        snapshotView.setVisibility(View.INVISIBLE);
        root.addView(snapshotView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        View scrim = new View(service);
        scrim.setBackgroundColor(0xE6000000);
        root.addView(scrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout copy = new LinearLayout(service);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_HORIZONTAL);
        copy.setPadding(dp(28), dp(28), dp(28), dp(28));
        int side = dp(28);
        FrameLayout.LayoutParams copyParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        copyParams.setMargins(side, side, side, side);
        root.addView(copy, copyParams);

        TextView title = new TextView(service);
        title.setText(R.string.person_overlay_title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 23);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setGravity(Gravity.CENTER);
        copy.addView(title);

        TextView body = new TextView(service);
        body.setText(R.string.person_overlay_body);
        body.setTextColor(0xFFE0E0E0);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(dp(3), 1.15f);
        copy.addView(body, margins(0, 12));

        TextView hint = new TextView(service);
        hint.setText(R.string.person_overlay_hint);
        hint.setTextColor(0xFFBDBDBD);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hint.setGravity(Gravity.CENTER);
        copy.addView(hint, margins(0, 12));
    }

    private WindowManager.LayoutParams params() {
        WindowManager.LayoutParams value = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        value.gravity = Gravity.TOP | Gravity.START;
        return value;
    }

    private LinearLayout.LayoutParams margins(int top, int bottom) {
        LinearLayout.LayoutParams value = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        value.topMargin = dp(top);
        value.bottomMargin = dp(bottom);
        return value;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                service.getResources().getDisplayMetrics()));
    }

    @Override public void close() {
        hide();
        root = null;
        snapshotView = null;
    }
}
