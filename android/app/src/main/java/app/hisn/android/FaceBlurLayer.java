package app.hisn.android;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import app.hisn.core.PeopleRegions;
import app.hisn.core.UncheckedArea;
import java.util.ArrayList;
import java.util.List;

/** The face-only blur: a see-through accessibility overlay with one soft patch per matched
 * person, and a small label saying how many. It takes no touches, so the app underneath stays
 * usable.
 *
 * <p>It also veils what no screenshot has checked yet (see {@link UncheckedArea}): a new screen,
 * and each strip a scroll brings into view. New pictures so appear veiled and clear once checked,
 * instead of showing first and blurring a moment later. A veil has no pixels of what it hides;
 * it is a soft wash in the colours of the last checked frame.</p>
 *
 * <p>Each patch keeps at most 4 pixels across of the screen under it, blurred once when it is
 * made: its colours, never a face or clothing. Between screenshots the patches follow the page
 * by the scroll distances Android reports. Nothing is written to disk. Main thread only.</p>
 */
final class FaceBlurLayer {
    /** Long edge of the small frame copy the patches take their colours from. */
    static final int COLOR_EDGE = 64;
    /** Pixels across a patch keeps from the screen. */
    private static final int PATCH_PIXELS = 4;
    /** A patch's few pixels are enlarged this much and blurred, so it reads as soft, not blocky. */
    private static final int SMOOTH_SCALE = 8, SMOOTH_PASSES = 4;
    /** Growth while the screen moves by an amount Android did not report. */
    private static final float UNKNOWN_MOVE_SCALE = 1.6f;
    private static final int STRETCH_CAP_DP = 64;
    private static final int FALLBACK_COLOR = 0xFF9E9E9E;

    private final AccessibilityService service;
    private final WindowManager windows;
    private final List<Bitmap> patches = new ArrayList<>();
    private final List<View> patchViews = new ArrayList<>();
    private final List<View> veilViews = new ArrayList<>();
    private final UncheckedArea unchecked = new UncheckedArea();
    /** The veil's wash: the average colour of the last checked frame, nearly opaque. */
    private int veilColor = 0xF59E9E9E;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    /** Scroll stretch eases back this long after the last scroll step. */
    private static final long SETTLE_MILLIS = 250;
    private final Runnable settle = this::settle;
    private FrameLayout root;
    private TextView label;
    private boolean shown;

    FaceBlurLayer(AccessibilityService service, WindowManager windows) {
        this.service = service;
        this.windows = windows;
    }

    boolean shown() { return shown; }

    boolean hasPatches() { return !patchViews.isEmpty(); }

    /** Blurs each region (normalized x, y, w, h repeated, in the window's frame) where it is on
     * screen. colors is a {@link #reduce}d copy of the same frame, or null for plain grey patches.
     * Returns true when the layer was newly added to the screen. */
    boolean show(Rect window, float[] regions, Bitmap colors) {
        if (windows == null || window == null || window.isEmpty() || regions == null || regions.length < 4) return false;
        if (root == null) build();
        clearPatches();
        int count = 0;
        for (int i = 0; i + 3 < regions.length; i += 4) {
            float[] area = PeopleRegions.padded(regions[i], regions[i + 1], regions[i + 2], regions[i + 3]);
            if (addPatch(window, area, colors)) count++;
        }
        if (colors != null) veilColor = 0xF5000000 | (average(colors) & 0xFFFFFF);
        if (count > 0) label.setText(service.getResources().getQuantityString(R.plurals.person_faces_blurred, count, count));
        return sync();
    }

    /** No screenshot has checked this window yet: veil all of it. */
    void veilAll(Rect window) {
        if (windows == null || window == null || window.isEmpty()) return;
        if (root == null) build();
        unchecked.all(window.left, window.top, window.right, window.bottom);
        renderVeils();
        sync();
    }

    /** A screenshot of the window was just checked; the page has since moved by (dx, dy) on
     * screen. Only what came into view after the capture stays veiled. */
    void checked(Rect window, int dx, int dy) {
        if (windows == null || window == null || window.isEmpty()) return;
        if (root == null) build();
        unchecked.checked(window.left, window.top, window.right, window.bottom, dx, dy);
        renderVeils();
        sync();
    }

    /** The page moved by (dx, dy) on screen: patches follow it, and what scrolled into view is
     * veiled until the next check. */
    void scrolled(int dx, int dy) {
        shift(dx, dy);
        if (root == null) return;
        unchecked.moved(dx, dy);
        renderVeils();
        sync();
        handler.removeCallbacks(settle);
        handler.postDelayed(settle, SETTLE_MILLIS);
    }

    private void renderVeils() {
        for (View veil : veilViews) root.removeView(veil);
        veilViews.clear();
        for (int[] area : unchecked.areas()) {
            View veil = new View(service);
            veil.setBackgroundColor(veilColor);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(area[2] - area[0], area[3] - area[1]);
            params.leftMargin = area[0];
            params.topMargin = area[1];
            // Below the patches and the label.
            root.addView(veil, 0, params);
            veilViews.add(veil);
        }
    }

    /** Adds or removes the layer to match what it holds. True when it was newly added. */
    private boolean sync() {
        boolean content = !patchViews.isEmpty() || !veilViews.isEmpty();
        if (label != null) label.setVisibility(patchViews.isEmpty() ? View.GONE : View.VISIBLE);
        if (!content) {
            removeRoot();
            return false;
        }
        if (shown) return false;
        try {
            windows.addView(root, params());
            shown = true;
            if (!patchViews.isEmpty()) root.announceForAccessibility(label.getText());
            return true;
        } catch (RuntimeException unavailable) {
            shown = false;
            return false;
        }
    }

    private void removeRoot() {
        if (shown && root != null) {
            try { windows.removeView(root); } catch (RuntimeException alreadyGone) { /* Nothing to remove. */ }
        }
        shown = false;
    }

    private boolean addPatch(Rect window, float[] area, Bitmap colors) {
        int left = Math.round(window.left + area[0] * window.width());
        int top = Math.round(window.top + area[1] * window.height());
        int width = Math.round(area[2] * window.width());
        int height = Math.round(area[3] * window.height());
        if (width < 1 || height < 1) return false;
        ImageView patch = new ImageView(service);
        patch.setScaleType(ImageView.ScaleType.FIT_XY);
        Bitmap pixels = patchPixels(colors, area);
        if (pixels != null) {
            patches.add(pixels);
            patch.setImageBitmap(pixels);
            patch.setBackgroundColor(average(pixels));
        } else {
            patch.setBackgroundColor(FALLBACK_COLOR);
        }
        // Rounded close to an oval for a face; a softer rectangle for a whole person.
        final float radius = Math.min(width, height) * 0.45f;
        patch.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        patch.setClipToOutline(true);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.leftMargin = left;
        params.topMargin = top;
        root.addView(patch, root.getChildCount() - 1, params);
        patchViews.add(patch);
        return true;
    }

    /** The content moved by (dx, dy) pixels: every patch moves with it and stretches along the
     * movement for this step only. A late scroll report then still finds the face covered; the
     * next report or screenshot puts the patch back to its real size. */
    void shift(int dx, int dy) {
        if (!shown) return;
        float cap = dp(STRETCH_CAP_DP);
        float reachX = PeopleRegions.stretch(dx, cap), reachY = PeopleRegions.stretch(dy, cap);
        // Reports arrive late, so the patch leads by one more step in the direction of movement
        // until the page settles; the stretch covers either side of that guess.
        leadX = Math.max(-cap, Math.min(cap, dx));
        leadY = Math.max(-cap, Math.min(cap, dy));
        for (View patch : patchViews) {
            float[] base = base(patch);
            base[0] += dx;
            base[1] += dy;
            patch.setTranslationX(base[0] + leadX);
            patch.setTranslationY(base[1] + leadY);
            int width = Math.max(1, patch.getWidth()), height = Math.max(1, patch.getHeight());
            patch.setScaleX((width + 2 * reachX) / width);
            patch.setScaleY((height + 2 * reachY) / height);
        }
        handler.removeCallbacks(settle);
        handler.postDelayed(settle, SETTLE_MILLIS);
    }

    private float leadX, leadY;

    /** Where the page has moved a patch to, without the lead. */
    private static float[] base(View patch) {
        Object tag = patch.getTag();
        if (tag instanceof float[]) return (float[]) tag;
        float[] base = {patch.getTranslationX(), patch.getTranslationY()};
        patch.setTag(base);
        return base;
    }

    /** Scrolling stopped: back to the real size, where the page now holds the person. */
    private void settle() {
        if (!shown) return;
        // The lead band goes; what scrolled in stays veiled until the next check.
        unchecked.settled();
        renderVeils();
        sync();
        if (!shown) return;
        leadX = 0;
        leadY = 0;
        for (View patch : patchViews) {
            float[] base = base(patch);
            patch.animate().translationX(base[0]).translationY(base[1]).scaleX(1f).scaleY(1f).setDuration(120).start();
        }
    }

    /** The screen moves by an unknown amount: grow every patch until the next screenshot. */
    void moving() {
        if (!shown) return;
        for (View patch : patchViews) {
            patch.setScaleX(UNKNOWN_MOVE_SCALE);
            patch.setScaleY(UNKNOWN_MOVE_SCALE);
        }
    }

    void hide() {
        handler.removeCallbacks(settle);
        removeRoot();
        clearPatches();
        unchecked.clear();
        if (root != null) renderVeils();
    }

    private void clearPatches() {
        if (root != null) for (View patch : patchViews) root.removeView(patch);
        patchViews.clear();
        for (Bitmap patch : patches) patch.recycle();
        patches.clear();
    }

    private void build() {
        root = new FrameLayout(service);
        root.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        label = new TextView(service);
        label.setTextColor(0xFFE1F5EE);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        label.setPadding(dp(14), dp(7), dp(14), dp(7));
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0xE604342C);
        pill.setCornerRadius(dp(20));
        label.setBackground(pill);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.bottomMargin = dp(88);
        root.addView(label, params);
    }

    private static WindowManager.LayoutParams params() {
        WindowManager.LayoutParams value = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        value.gravity = Gravity.TOP | Gravity.START;
        // Screen coordinates: the layer starts at the very top-left, under any cutout.
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            value.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }
        return value;
    }

    /** A filtered copy with a {@link #COLOR_EDGE}-pixel long edge, kept only until the patches read
     * their colours. Null when it cannot be made. Safe off the main thread. */
    static Bitmap reduce(Bitmap frame) {
        try {
            int width = frame.getWidth(), height = frame.getHeight();
            if (width < 1 || height < 1) return null;
            double scale = Math.min(1.0, (double) COLOR_EDGE / Math.max(width, height));
            Bitmap small = Bitmap.createScaledBitmap(frame, Math.max(1, (int) Math.round(width * scale)),
                    Math.max(1, (int) Math.round(height * scale)), true);
            return small == frame ? frame.copy(Bitmap.Config.ARGB_8888, false) : small;
        } catch (RuntimeException | OutOfMemoryError unavailable) {
            return null;
        }
    }

    /** At most {@link #PATCH_PIXELS} across of the area, enlarged and blurred once here rather than
     * by a live effect on every frame, so moving the patch costs nothing to draw. */
    private static Bitmap patchPixels(Bitmap colors, float[] area) {
        if (colors == null) return null;
        Bitmap crop = null, small = null, smooth = null;
        try {
            int cw = colors.getWidth(), ch = colors.getHeight();
            int left = Math.max(0, Math.min(cw - 1, (int) Math.floor(area[0] * cw)));
            int top = Math.max(0, Math.min(ch - 1, (int) Math.floor(area[1] * ch)));
            int right = Math.max(left + 1, Math.min(cw, (int) Math.ceil((area[0] + area[2]) * cw)));
            int bottom = Math.max(top + 1, Math.min(ch, (int) Math.ceil((area[1] + area[3]) * ch)));
            crop = Bitmap.createBitmap(colors, left, top, right - left, bottom - top);
            double scale = Math.min(1.0, (double) PATCH_PIXELS / Math.max(crop.getWidth(), crop.getHeight()));
            small = Bitmap.createScaledBitmap(crop, Math.max(1, (int) Math.round(crop.getWidth() * scale)),
                    Math.max(1, (int) Math.round(crop.getHeight() * scale)), true);
            int sw = small.getWidth() * SMOOTH_SCALE, sh = small.getHeight() * SMOOTH_SCALE;
            smooth = Bitmap.createScaledBitmap(small, sw, sh, true);
            int[] pixels = new int[sw * sh];
            smooth.getPixels(pixels, 0, sw, 0, 0, sw, sh);
            for (int pass = 0; pass < SMOOTH_PASSES; pass++) VisualOverlay.boxBlur(pixels, sw, sh);
            return Bitmap.createBitmap(pixels, sw, sh, Bitmap.Config.ARGB_8888);
        } catch (RuntimeException | OutOfMemoryError unavailable) {
            return null;
        } finally {
            // createBitmap and createScaledBitmap may hand back their input; recycle each once.
            if (smooth != null && smooth != colors) smooth.recycle();
            if (small != null && small != colors && small != smooth) small.recycle();
            if (crop != null && crop != colors && crop != small && crop != smooth) crop.recycle();
        }
    }

    private static int average(Bitmap bitmap) {
        long r = 0, g = 0, b = 0;
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int c = bitmap.getPixel(x, y);
            r += (c >> 16) & 0xFF; g += (c >> 8) & 0xFF; b += c & 0xFF;
        }
        int n = Math.max(1, w * h);
        return 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                service.getResources().getDisplayMetrics()));
    }
}
