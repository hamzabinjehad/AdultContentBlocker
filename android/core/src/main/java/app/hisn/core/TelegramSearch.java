package app.hisn.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Telegram's own search, and the channel or chat that search opens, for the built-in adult layer.
 *
 * <p>Telegram's traffic is MTProto, so DNS cannot tell an adult channel from a family group, and
 * the ordinary text check never reads an input field. Someone could therefore type "سكس" or
 * "porn" into Telegram's global search, open a public channel from the results and watch it,
 * while Hisn saw nothing. This class is the rule for closing that path without reading private
 * conversations:</p>
 *
 * <ul>
 *   <li>the search field's text (the query being typed), judged as a request
 *       ({@link AdultTextPolicy#blocksQuery}), so "سكس عربي" or "xxx" counts;</li>
 *   <li>the screen's top band: the open chat or channel's title and the line under it;</li>
 *   <li>the search results, only while the search field is open;</li>
 *   <li>an inline bot query typed into the message box ("@SearcheeBot #adult sex") and the
 *       results above it: only text that starts with "@username " is read from the box;</li>
 *   <li>the whole screen of a chat with a bot, such as a search bot with an "Adult" category:
 *       a bot is a service, not a person.</li>
 * </ul>
 *
 * <p>In a chat with a person or group, message text below the title, an ordinary message being
 * typed and unopened chats are not read. A
 * channel with an innocent name is caught only when Telegram is also chosen under Apps to
 * inspect (whole-screen text and the image layer). Telegram draws its own views and gives them
 * no view IDs, so the search field is found by position: an input field in the top band. The
 * composer sits at the bottom and is never in that band.</p>
 */
public final class TelegramSearch {
    /** Telegram and the forks that use its interface: official, direct download, beta, Telegram X,
     *  Plus Messenger, Nekogram, NekoX, exteraGram and Cherrygram. */
    public static final Set<String> PACKAGES = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.messenger.beta",
            "org.thunderdog.challegram", "org.telegram.plus", "tw.nekomimi.nekogram", "nekox.messenger",
            "com.exteragram.messenger", "uz.unnarsx.cherrygram")));

    /** The top band, in dp: the status bar and Telegram's action bar. On a Galaxy S21 FE at 420
     *  dpi the title and its subtitle end at 94 dp; a message bubble cannot fit in the 10 dp left. */
    static final int TOP_BAND_DP = 104;
    /** Bounded samples; the adult policy refuses longer input rather than truncating it. */
    public static final int MAX_CHARS = 8192;
    /** Nodes read per check. Search results are a short list; a long one is read in part. */
    public static final int MAX_NODES = 400;

    private TelegramSearch() { }

    /** True when the built-in adult layer is on and this is a Telegram client. */
    public static boolean watches(boolean adultLayerOn, String packageName) {
        return adultLayerOn && packageName != null && PACKAGES.contains(packageName);
    }

    /** Bottom edge of the top band for a window spanning {@code top}..{@code bottom} on screen, at
     *  {@code density} pixels per dp; never more than half a short (landscape or split) window. */
    public static int bandBottom(int top, int bottom, float density) {
        int band = Math.round(TOP_BAND_DP * Math.max(density, 0.5f));
        return top + Math.min(band, Math.max(0, bottom - top) / 2);
    }

    /**
     * True when a node lies wholly inside the top band, so it belongs to the header. A node that
     * touches the window's top edge is a message scrolled under the action bar (its bounds are
     * clipped to the edge), or the bar's own container, whose title and subtitle are read instead.
     */
    public static boolean inTopBand(int nodeTop, int nodeBottom, int windowTop, int windowBottom, float density) {
        return nodeBottom > nodeTop && nodeTop > windowTop && nodeBottom <= bandBottom(windowTop, windowBottom, density);
    }

    /** A Telegram username: 3 to 32 letters, digits or underscores (bots end in "bot"). */
    private static final java.util.regex.Pattern INLINE = java.util.regex.Pattern.compile(
            "^@([A-Za-z0-9_]{3,32})\\s+(.+)$", java.util.regex.Pattern.DOTALL);

    /**
     * The search part of an inline bot query typed into the message box ("@SearcheeBot #adult
     * sex"), or null when the box holds an ordinary message. Inline queries go to the bot as a
     * search, in any chat, so only text that starts with "@username " is read from the box.
     */
    public static String inlineQuery(CharSequence composer) {
        if (composer == null) return null;
        java.util.regex.Matcher match = INLINE.matcher(composer.toString().trim());
        if (!match.matches()) return null;
        String rest = match.group(2).trim();
        return rest.isEmpty() ? null : rest;
    }

    /**
     * True when the header is a bot's: Telegram's subtitle for a bot is "bot" or "N monthly users"
     * (read from Telegram 13.2.2 in English on a Galaxy S21 FE, 18 November 2026). The Arabic
     * forms are Telegram's translations and are not yet read from a device. A bot is a service,
     * not a person, so its chat (its replies, buttons and listings) is read in full.
     */
    public static boolean botChat(String header) {
        if (header == null) return false;
        for (String line : header.split("\n")) {
            String text = line.trim().toLowerCase(java.util.Locale.ROOT);
            if (text.equals("bot") || text.equals("بوت") || text.matches("^[\\d.,\\s\u066c\u066b]+ monthly users?$")
                    || (text.contains("مستخدم") && text.contains("شهري"))) return true;
        }
        return false;
    }

    /** What one look at a Telegram window read. Any part may be empty. */
    public static final class Sample {
        public final String query;
        public final String header;
        public final String results;

        public Sample(String query, String header, String results) {
            this.query = query == null ? "" : query;
            this.header = header == null ? "" : header;
            this.results = results == null ? "" : results;
        }
    }

    /**
     * True when the query, the header or (while searching) a result line is adult: the owner's own
     * words, or the reviewed adult terms. The query and each short header or result line (a
     * channel or chat name) are judged as a search request; recovery and blocking context still
     * allows them ("Porn addiction recovery" is a support group). Longer lines are judged as
     * screen text, so "Sexual health clinic" or a message snippet needs real evidence.
     */
    public static boolean blocks(AdultTextPolicy adult, WordMatcher words, Sample sample) {
        if (sample == null) return false;
        for (String part : new String[] {sample.query, sample.header, sample.results}) {
            if (part.isEmpty() || part.length() > MAX_CHARS) continue;
            if (words != null && !words.matches(part).isEmpty()) return true;
        }
        if (adult == null) return false;
        if (!sample.query.isEmpty() && sample.query.length() <= MAX_CHARS
                && adult.blocksQuery(sample.query.replace('\n', ' '))) return true;
        return names(adult, sample.header) || names(adult, sample.results);
    }

    private static boolean names(AdultTextPolicy adult, String lines) {
        if (lines.isEmpty() || lines.length() > MAX_CHARS) return false;
        for (String line : lines.split("\n")) if (adult.blocksName(line)) return true;
        return adult.blocksAnyLine(lines);
    }
}
