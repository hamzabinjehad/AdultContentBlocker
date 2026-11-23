package app.hisn.core;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Search-engine hostnames that Hisn answers with a SafeSearch CNAME.
 *
 * <p>A DNS server can force SafeSearch by returning a CNAME instead of the
 * engine's own address; the client follows the target and the target serves the
 * restricted experience. Hisn rewrites only exact hostnames that the engine
 * documents for this method and whose certificate the target serves: on
 * 23 November 2026 a TLS handshake that checked the hostname succeeded for every
 * name below against its target's address. That covers Google's 187 published search
 * domains (google.com/supported_domains), YouTube's documented Restricted Mode names
 * including the youtubei API the YouTube app uses, Yandex family search and
 * Pixabay's safe search. Names that failed that check (youtube-nocookie.com without
 * www, yandex.ua) are left alone: a wrong rewrite would break the site instead of
 * filtering it.
 */
public final class SafeSearch {
    public static final String GOOGLE = "forcesafesearch.google.com";
    public static final String BING = "strict.bing.com";
    public static final String DUCKDUCKGO = "safe.duckduckgo.com";
    public static final String YOUTUBE = "restrict.youtube.com";
    public static final String YANDEX = "familysearch.yandex.ru";
    public static final String PIXABAY = "safesearch.pixabay.com";

    /** The part after "google." of each domain in google.com/supported_domains. */
    private static final Set<String> GOOGLE_SUFFIXES = new HashSet<>(Arrays.asList((
            "ad ae al am as at az ba be bf bg bi bj bs bt by ca cat cd cf cg ch ci cl cm cn co.ao co.bw "
            + "co.ck co.cr co.id co.il co.in co.jp co.ke co.kr co.ls co.ma co.mz co.nz co.th co.tz co.ug "
            + "co.uk co.uz co.ve co.vi co.za co.zm co.zw com com.af com.ag com.ar com.au com.bd com.bh "
            + "com.bn com.bo com.br com.bz com.co com.cu com.cy com.do com.ec com.eg com.et com.fj com.gh "
            + "com.gi com.gt com.hk com.jm com.kh com.kw com.lb com.ly com.mm com.mt com.mx com.my com.na "
            + "com.ng com.ni com.np com.om com.pa com.pe com.pg com.ph com.pk com.pr com.py com.qa com.sa "
            + "com.sb com.sg com.sl com.sv com.tj com.tr com.tw com.ua com.uy com.vc com.vn cv cz de dj dk "
            + "dm dz ee es fi fm fr ga ge gg gl gm gr gy hn hr ht hu ie im iq is it je jo kg ki kz la li lk "
            + "lt lu lv md me mg mk ml mn mu mv mw ne nl no nr nu pl pn ps pt ro rs ru rw sc se sh si sk sm "
            + "sn so sr st td tg tl tm tn to tt vu ws").split(" ")));

    private static final Map<String, String> EXACT = new HashMap<>();
    static {
        for (String name : new String[] {"bing.com", "www.bing.com"}) EXACT.put(name, BING);
        for (String name : new String[] {"duckduckgo.com", "www.duckduckgo.com"}) EXACT.put(name, DUCKDUCKGO);
        // support.google.com/a/answer/6214622 lists these five for DNS Restricted Mode.
        for (String name : new String[] {"youtube.com", "www.youtube.com", "m.youtube.com",
                "youtubei.googleapis.com", "youtube.googleapis.com", "www.youtube-nocookie.com"}) {
            EXACT.put(name, YOUTUBE);
        }
        EXACT.put("images.google.com", GOOGLE);
        for (String name : new String[] {"yandex.ru", "yandex.com", "yandex.com.tr", "yandex.by",
                "yandex.kz", "yandex.uz", "ya.ru"}) {
            EXACT.put(name, YANDEX);
            EXACT.put("www." + name, YANDEX);
        }
        for (String name : new String[] {"pixabay.com", "www.pixabay.com"}) EXACT.put(name, PIXABAY);
    }

    private SafeSearch() { }

    /** The CNAME target for an exact search hostname, or null to forward normally. */
    public static String target(String hostname) {
        if (hostname == null) return null;
        String name = hostname.toLowerCase(Locale.ROOT);
        if (name.endsWith(".")) name = name.substring(0, name.length() - 1);
        String exact = EXACT.get(name);
        if (exact != null) return exact;
        String google = name.startsWith("www.google.") ? name.substring(11)
                : name.startsWith("google.") ? name.substring(7) : null;
        return google != null && GOOGLE_SUFFIXES.contains(google) ? GOOGLE : null;
    }
}
