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
import android.widget.Button;
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
 * the visual layer, or leaving the app. Its one button goes Home: it can take a person away from
 * the covered screen, never reveal it. Nothing is written to disk.</p>
 */
final class VisualOverlay implements AutoCloseable {
    static volatile long shows;
    private final AccessibilityService service;
    private final WindowManager windows;
    private FrameLayout root;
    private ImageView snapshotView;
    private TextView titleView, bodyView, hintView;
    private Button homeButton;
    private Bitmap snapshot;
    private boolean shown;

    VisualOverlay(AccessibilityService service) {
        this.service = service;
        this.windows = service.getSystemService(WindowManager.class);
    }

    boolean shown() { return shown; }

    /** Shows or refreshes a face cover. */
    void show(Bitmap frame) {
        show(frame, R.string.person_overlay_title, R.string.person_overlay_body,
                R.string.person_overlay_hint);
    }

    /** A native fast pre-check found a face; the full result will refine this cover. */
    void showChecking() {
        show(null, R.string.person_checking_title, R.string.person_checking_body,
                R.string.person_checking_hint);
    }

    /** One frame looked explicit; the screen stays hidden while a second capture confirms it. */
    void showConfirming() {
        show(null, R.string.visual_confirm_title, R.string.visual_confirm_body,
                R.string.visual_confirm_hint);
    }

    /** A protected/private window cannot be inspected; cover it rather than expose pixels. */
    void showProtected() {
        if (snapshot != null && snapshotView != null) {
            snapshotView.setImageDrawable(null);
            snapshot.recycle();
            snapshot = null;
        }
        show(null, R.string.protected_overlay_title, R.string.protected_overlay_body,
                R.string.protected_overlay_hint);
    }

    private void show(Bitmap frame, int title, int body, int hint) {
        if (windows == null) return;
        if (root == null) build();
        titleView.setText(title);
        bodyView.setText(body);
        hintView.setText(hint);
        // Screen readers hear what this cover is for, not a fixed face-cover label.
        root.setContentDescription(service.getString(title) + ". " + service.getString(body));
        if (frame != null) {
            Bitmap smaller = blur(frame);
            if (smaller != null) {
                Bitmap previous = snapshot;
                snapshot = smaller;
                snapshotView.setImageBitmap(smaller);
                snapshotView.setVisibility(View.VISIBLE);
                if (previous != null && previous != smaller) previous.recycle();
            }
        } else {
            snapshotView.setImageDrawable(null);
            snapshotView.setVisibility(View.INVISIBLE);
        }
        if (!shown) {
            try {
                windows.addView(root, params());
                shown = true;
                shows++;
                root.announceForAccessibility(service.getString(title));
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

        titleView = new TextView(service);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 23);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleView.setGravity(Gravity.CENTER);
        copy.addView(titleView);

        bodyView = new TextView(service);
        bodyView.setTextColor(0xFFE0E0E0);
        bodyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        bodyView.setGravity(Gravity.CENTER);
        bodyView.setLineSpacing(dp(3), 1.15f);
        copy.addView(bodyView, margins(0, 12));

        hintView = new TextView(service);
        hintView.setTextColor(0xFFBDBDBD);
        hintView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hintView.setGravity(Gravity.CENTER);
        copy.addView(hintView, margins(0, 12));

        // A way out that needs no gesture knowledge. Home leaves the covered app; it never
        // removes the cover over it.
        homeButton = new Button(service);
        homeButton.setText(R.string.restriction_home);
        homeButton.setAllCaps(false);
        homeButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        homeButton.setMinHeight(dp(48));
        homeButton.setOnClickListener(view -> {
            try { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME); }
            catch (RuntimeException unavailable) { /* The cover stays; Home still works. */ }
        });
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonParams.gravity = Gravity.CENTER_HORIZONTAL;
        buttonParams.topMargin = dp(8);
        copy.addView(homeButton, buttonParams);
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
        homeButton = null;
    }
}
