package app.hisn.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The parts of a window that no screenshot has checked yet, in screen pixels.
 *
 * <p>A check covers the whole window as it was when captured. After that, every reported scroll
 * moves the page: whatever was unchecked moves with it, and the strip that scrolls into view at
 * the leading edge is new and unchecked. The face-only blur veils exactly these areas, so new
 * pictures appear veiled until the next check rather than being shown first. Android gives no
 * pixels before they are drawn; this is the closest a screen reader-based guard can get.</p>
 *
 * <p>Scroll reports come in batches about 100 ms apart, and later when the phone is busy, so
 * moving the veil only when a report arrives lags a fast scroll. While the page moves, a lead band
 * therefore stays at the edge new content comes in from, sized from the last step: new content
 * scrolls in under it. It goes when the page settles or a check lands.</p>
 *
 * <p>Areas are {left, top, right, bottom} rectangles clipped to the window. Strips on the same edge
 * merge, so a long scroll stays one area. Not thread-safe; pure Java.</p>
 */
public final class UncheckedArea {
    /** More separate areas than this and the whole window is treated as unchecked. */
    public static final int MAX_AREAS = 6;
    /** The lead band is this many scroll steps deep, and never more than this share of the window. */
    public static final int LEAD_STEPS = 3;
    public static final float LEAD_MAX_SHARE = 0.4f;
    private final List<int[]> areas = new ArrayList<>();
    private int[] window;
    private int[] lead;

    /** No screenshot has seen this window yet: all of it is unchecked. */
    public void all(int left, int top, int right, int bottom) {
        window = new int[]{left, top, right, bottom};
        areas.clear();
        lead = null;
        if (right > left && bottom > top) areas.add(window.clone());
    }

    /** A screenshot of the window was just checked, and the page has since moved by (dx, dy)
     * pixels: only what scrolled into view after the capture is unchecked. */
    public void checked(int left, int top, int right, int bottom, int dx, int dy) {
        window = new int[]{left, top, right, bottom};
        areas.clear();
        lead = null;
        reveal(dx, dy);
    }

    /** The page moved by (dx, dy) pixels on screen (negative dy: content went up). Unchecked areas
     * move with it and the strip that came into view is added. Without a known window nothing is
     * tracked. */
    public void moved(int dx, int dy) {
        if (window == null) return;
        for (int[] area : areas) { area[0] += dx; area[2] += dx; area[1] += dy; area[3] += dy; }
        clip();
        reveal(dx, dy);
        lead = band(dx, dy);
    }

    /** The page stopped moving: the lead band goes; what scrolled in stays unchecked. */
    public void settled() { lead = null; }

    /** Everything checked or the window is gone: nothing to veil. */
    public void clear() {
        areas.clear();
        window = null;
        lead = null;
    }

    public boolean isEmpty() { return areas.isEmpty() && lead == null; }

    /** Copies of the areas to veil, {left, top, right, bottom} each: the unchecked areas, then the
     * lead band while the page moves. */
    public List<int[]> areas() {
        List<int[]> out = new ArrayList<>();
        for (int[] area : areas) out.add(area.clone());
        if (lead != null) out.add(lead.clone());
        return Collections.unmodifiableList(out);
    }

    /** The band at the edge content is coming in from, LEAD_STEPS steps deep. */
    private int[] band(int dx, int dy) {
        int l = window[0], t = window[1], r = window[2], b = window[3];
        int maxY = Math.round((b - t) * LEAD_MAX_SHARE), maxX = Math.round((r - l) * LEAD_MAX_SHARE);
        int deepY = Math.min(maxY, Math.abs(dy) * LEAD_STEPS), deepX = Math.min(maxX, Math.abs(dx) * LEAD_STEPS);
        if (Math.abs(dy) >= Math.abs(dx)) {
            if (dy < 0) return new int[]{l, b - deepY, r, b};
            if (dy > 0) return new int[]{l, t, r, t + deepY};
            return null;
        }
        return dx < 0 ? new int[]{r - deepX, t, r, b} : new int[]{l, t, l + deepX, b};
    }

    private void reveal(int dx, int dy) {
        if (window == null) return;
        int l = window[0], t = window[1], r = window[2], b = window[3];
        // Content moving up uncovers the bottom edge; moving down, the top; likewise sideways.
        if (dy < 0) add(new int[]{l, Math.max(t, b + dy), r, b});
        else if (dy > 0) add(new int[]{l, t, r, Math.min(b, t + dy)});
        if (dx < 0) add(new int[]{Math.max(l, r + dx), t, r, b});
        else if (dx > 0) add(new int[]{l, t, Math.min(r, l + dx), b});
        if (areas.size() > MAX_AREAS) all(l, t, r, b);
    }

    private void add(int[] strip) {
        if (strip[2] <= strip[0] || strip[3] <= strip[1]) return;
        for (int[] area : areas) {
            // A strip touching or overlapping an area of the same width (or height) joins it.
            boolean sameColumns = area[0] == strip[0] && area[2] == strip[2];
            boolean sameRows = area[1] == strip[1] && area[3] == strip[3];
            if (sameColumns && strip[1] <= area[3] && strip[3] >= area[1]
                    || sameRows && strip[0] <= area[2] && strip[2] >= area[0]) {
                area[0] = Math.min(area[0], strip[0]); area[1] = Math.min(area[1], strip[1]);
                area[2] = Math.max(area[2], strip[2]); area[3] = Math.max(area[3], strip[3]);
                return;
            }
        }
        areas.add(strip);
    }

    private void clip() {
        for (int i = areas.size() - 1; i >= 0; i--) {
            int[] area = areas.get(i);
            area[0] = Math.max(area[0], window[0]); area[1] = Math.max(area[1], window[1]);
            area[2] = Math.min(area[2], window[2]); area[3] = Math.min(area[3], window[3]);
            if (area[2] <= area[0] || area[3] <= area[1]) areas.remove(i);
        }
    }
}
