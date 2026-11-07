package app.hisn.core;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reuses the last verdict for an unchanged selected-window frame.
 *
 * <p>The visual guard still takes one capture to know the frame did not change, but it does not
 * need to run the model again for the same coarse signature. Only a signature and an allow/block
 * word are retained; no pixels, URL or text. Entries expire after {@link #TTL_MILLIS}, are bounded
 * per selected window, and are cleared on a window change. A cached block is still checked against
 * the live window before it acts, like any other frame decision.</p>
 */
public final class VisualVerdictCache {
    /** Long enough to skip a still screen's repeat inference, short enough to re-check a screen. */
    public static final long TTL_MILLIS = 5000;
    public static final int MAX_WINDOWS = GuardWindows.MAX_WINDOWS;

    /** A single signature and verdict. */
    public static final class Entry {
        public final long signature;
        public final String verdict;
        final long at;
        Entry(long signature, String verdict, long at) {
            this.signature = signature;
            this.verdict = verdict;
            this.at = at;
        }
    }

    private final Map<Integer, Entry> entries = new LinkedHashMap<>();

    /** The cached verdict for this window and signature, or null when absent or expired. */
    public synchronized String get(int window, long signature, long now) {
        Entry entry = entries.get(window);
        if (entry == null) return null;
        if (now < entry.at || now - entry.at > TTL_MILLIS) {
            entries.remove(window);
            return null;
        }
        return entry.signature == signature ? entry.verdict : null;
    }

    /** Stores an allow or block verdict; other values are ignored, as an unknown is not reusable. */
    public synchronized void put(int window, long signature, String verdict, long now) {
        if (!"allow".equals(verdict) && !"block".equals(verdict)) return;
        if (window < 0 || now < 0) return;
        if (!entries.containsKey(window) && entries.size() >= MAX_WINDOWS) {
            Iterator<Integer> oldest = entries.keySet().iterator();
            if (oldest.hasNext()) { oldest.next(); oldest.remove(); }
        }
        entries.put(window, new Entry(signature, verdict, now));
    }

    /** Forgets windows no longer in the rotation. */
    public synchronized void retain(Collection<Integer> windows) {
        entries.keySet().retainAll(windows);
    }

    public synchronized void clear() { entries.clear(); }

    public synchronized int size() { return entries.size(); }
}
