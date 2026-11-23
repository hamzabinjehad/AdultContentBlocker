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
 * light scrim, with the words on their own card. It is removed only by a later clean match, a window change, screen off, disabling
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
    private Button homeButton, safeButton;
    /** Marks the covered private app safe; milliseconds left when a protection lock makes it wait. */
    interface Allow { long run(); }
    private Allow allow;
    private Bitmap snapshot;
    private boolean shown;
    /** Face-only blur, the see-through alternative to this cover. Only one of the two shows. */
    private final FaceBlurLayer faces;

    VisualOverlay(AccessibilityService service) {
        this.service = service;
        this.windows = service.getSystemService(WindowManager.class);
        this.faces = new FaceBlurLayer(service, windows);
    }

    boolean shown() { return shown; }

    boolean facesShown() { return faces.shown(); }

    /** Blurs only the given people; see {@link FaceBlurLayer#show}. Replaces a full cover. */
    void showFaces(android.graphics.Rect window, float[] regions, Bitmap colors) {
        if (windows == null) return;
        if (shown) hideCover();
        if (faces.show(window, regions, colors)) shows++;
    }

    void shift(int dx, int dy) { faces.shift(dx, dy); }

    /** Face-only blur: veil the whole window until a screenshot checks it. */
    void veilAll(android.graphics.Rect window) { if (!shown) faces.veilAll(window); }

    /** Face-only blur: the window was just checked; only what scrolled in since stays veiled. */
    void checked(android.graphics.Rect window, int dx, int dy) { faces.checked(window, dx, dy); }

    /** Face-only blur: the page moved; patches follow and new strips are veiled. */
    void scrolled(int dx, int dy) { faces.scrolled(dx, dy); }

    void moving() { faces.moving(); }



    /** Shows or refreshes a face cover using an already reduced snapshot. */
    void show(Bitmap colors) {
        show(colors, R.string.person_overlay_title, R.string.person_overlay_body,
                R.string.person_overlay_hint);
    }

    /** Cover mode is waiting for the first model to load. */
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
    void showProtected(Allow allow) {
        this.allow = allow;
        show(null, R.string.protected_overlay_title, R.string.protected_overlay_body,
                R.string.protected_overlay_hint);
        if (safeButton != null) safeButton.setVisibility(View.VISIBLE);
    }

    private void show(Bitmap colors, int title, int body, int hint) {
        if (windows == null) return;
        faces.hide();
        if (root == null) build();
        if (safeButton != null) safeButton.setVisibility(View.GONE);
        titleView.setText(title);
        bodyView.setText(body);
        hintView.setText(hint);
        // Screen readers hear what this cover is for, not a fixed face-cover label.
        root.setContentDescription(service.getString(title) + ". " + service.getString(body));
        recycle();
        if (colors != null) snapshot = colors.copy(Bitmap.Config.ARGB_8888, false);
        snapshotView.setImageBitmap(snapshot);
        snapshotView.setVisibility(snapshot == null ? View.INVISIBLE : View.VISIBLE);
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
        faces.hide();
        hideCover();
    }

    private void hideCover() {
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

    /** Long edge of the kept snapshot. At 32 pixels a face is a few pixels wide, and the box blur
     * below spreads each of those over its neighbours: shapes and colors stay, no face or clothing
     * detail survives. The overlay then smooths it at full screen so it reads as frosted glass. */
    static final int SNAPSHOT_EDGE = 32;

    static Bitmap blur(Bitmap frame) {
        Bitmap current = frame;
        try {
            int width = frame.getWidth(), height = frame.getHeight();
            if (width < 1 || height < 1) return null;
            // Halve with filtering until close, so each kept pixel averages its area instead of
            // sampling one point of it.
            while (Math.max(width, height) > SNAPSHOT_EDGE * 2) {
                width = Math.max(1, width / 2);
                height = Math.max(1, height / 2);
                Bitmap next = Bitmap.createScaledBitmap(current, width, height, true);
                if (current != frame && current != next) current.recycle();
                current = next;
            }
            double scale = Math.min(1.0, (double) SNAPSHOT_EDGE / Math.max(width, height));
            int w = Math.max(1, (int) Math.round(width * scale));
            int h = Math.max(1, (int) Math.round(height * scale));
            Bitmap small = Bitmap.createScaledBitmap(current, w, h, true);
            if (current != frame && current != small) current.recycle();
            current = null;
            int[] pixels = new int[w * h];
            small.getPixels(pixels, 0, w, 0, 0, w, h);
            if (small == frame) small = null; else small.recycle();
            boxBlur(pixels, w, h);
            boxBlur(pixels, w, h);
            Bitmap out = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
            java.util.Arrays.fill(pixels, 0);
            return out;
        } catch (RuntimeException | OutOfMemoryError unavailable) {
            if (current != null && current != frame) current.recycle();
            return null;
        }
    }

    /** One 3x3 average pass, edges clamped. */
    static void boxBlur(int[] pixels, int w, int h) {
        int[] source = pixels.clone();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = 0, g = 0, b = 0, n = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    int yy = Math.min(h - 1, Math.max(0, y + dy));
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = Math.min(w - 1, Math.max(0, x + dx));
                        int c = source[yy * w + xx];
                        r += (c >> 16) & 0xFF; g += (c >> 8) & 0xFF; b += c & 0xFF; n++;
                    }
                }
                pixels[y * w + x] = 0xFF000000 | (r / n) << 16 | (g / n) << 8 | (b / n);
            }
        }
        java.util.Arrays.fill(source, 0);
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
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            // Smooths the tiny snapshot's pixel steps into an even frosted blur. The privacy
            // comes from the 32-pixel source, not from this effect.
            float radius = dp(28);
            snapshotView.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(
                    radius, radius, android.graphics.Shader.TileMode.CLAMP));
        }
        root.addView(snapshotView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        View scrim = new View(service);
        // Light enough that the blurred screen shows through, dark enough to settle it.
        scrim.setBackgroundColor(0x59000000);
        root.addView(scrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout copy = new LinearLayout(service);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_HORIZONTAL);
        copy.setPadding(dp(24), dp(24), dp(24), dp(20));
        // The words sit on their own card so they stay readable over any blurred colors.
        android.graphics.drawable.GradientDrawable card = new android.graphics.drawable.GradientDrawable();
        card.setColor(0xD9141414);
        card.setCornerRadius(dp(28));
        copy.setBackground(card);
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

        // Only on the private-window cover: a bank or authenticator the owner trusts. It is
        // refused during a protection lock, so it is never a way around a commitment.
        safeButton = new Button(service);
        safeButton.setText(R.string.protected_overlay_safe);
        safeButton.setAllCaps(false);
        safeButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        safeButton.setMinHeight(dp(48));
        safeButton.setVisibility(View.GONE);
        safeButton.setOnClickListener(view -> {
            Allow current = allow;
            long left = current == null ? 1 : current.run();
            if (left > 0) {
                int minutes = (int) Math.max(1, (left + 59_999) / 60_000);
                try {
                    android.widget.Toast.makeText(service,
                            service.getResources().getQuantityString(R.plurals.protected_overlay_safe_wait, minutes, minutes),
                            android.widget.Toast.LENGTH_LONG).show();
                } catch (RuntimeException unavailable) { /* The cover stays. */ }
            }
        });
        LinearLayout.LayoutParams safeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        safeParams.gravity = Gravity.CENTER_HORIZONTAL;
        safeParams.topMargin = dp(4);
        copy.addView(safeButton, safeParams);
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
