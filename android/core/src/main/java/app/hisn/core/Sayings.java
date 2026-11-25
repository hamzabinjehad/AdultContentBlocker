package app.hisn.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The Islamic edition's library of sayings on purifying the soul (tazkiya): Ibn al-Qayyim,
 * Ibn al-Jawzi, Ibn Rajab, Ibn Qudama and others, each with its book and printed page.
 *
 * <p>The text is bundled as a small tab-separated file (assets/sayings/sayings.tsv in the hisn
 * flavor) so this class needs no JSON library and runs in the plain-JVM core tests. Lines:
 * <pre>
 *   #hisn-sayings  1
 *   T  key  English name  Arabic name                 one topic; its index is its bit
 *   B  title  edition  author (Arabic)  author (English)   one book; its index is referenced
 *   Q  book  page  volume  topics  score  likes  source  link  text
 * </pre>
 * Text keeps the book's wording exactly; only newlines are escaped as backslash-n.
 *
 * <p>Nothing here ever edits a saying: search, filters and the daily pick only choose among them.
 */
public final class Sayings {

    /** One topic tag (gaze, repentance, the heart...). */
    public static final class Topic {
        public final String key;
        public final String en;
        public final String ar;
        public final int bit;

        Topic(String key, String en, String ar, int index) {
            this.key = key;
            this.en = en;
            this.ar = ar;
            this.bit = 1 << index;
        }
    }

    /** One source book. */
    public static final class Book {
        public final String title;
        public final String edition;
        public final String authorAr;
        public final String authorEn;

        Book(String title, String edition, String authorAr, String authorEn) {
            this.title = title;
            this.edition = edition;
            this.authorAr = authorAr;
            this.authorEn = authorEn;
        }
    }

    /** One saying, exactly as printed. */
    public static final class Saying {
        public final int index;
        public final String text;
        public final Book book;
        public final int page;
        public final String volume;
        public final int topics;
        public final int score;
        public final int likes;
        /** "iq" (the owner's Ibn al-Qayyim list), "book" (taken from the book) or "reader". */
        public final String source;
        public final String link;
        final String folded;

        Saying(int index, String text, Book book, int page, String volume, int topics, int score,
               int likes, String source, String link) {
            this.index = index;
            this.text = text;
            this.book = book;
            this.page = page;
            this.volume = volume;
            this.topics = topics;
            this.score = score;
            this.likes = likes;
            this.source = source;
            this.link = link;
            this.folded = fold(text);
        }

        /** "3/ 120" for multi-volume books, "120" otherwise. */
        public String pageLabel() {
            return volume.isEmpty() ? String.valueOf(page) : volume + "/ " + page;
        }

        public boolean short_() {
            return text.length() <= 240;
        }
    }

    /** A moment the reader is in, and the sayings that fit it. */
    public enum Moment {
        TEMPTED("tempt", "النظر|البصر|الطرف|الهوي|الشهو|العشق|اللذه|غض"),
        AFTER_SLIP("slip", "توب|التائب|الاستغفار|استغفر|الندم|رحمه|يغفر|المغفره"),
        ALONE("alone", "(^|\\s)(الخلوه|الخلوات|خلا|السر|السريره|السرائر|البواطن)(\\s|$|[،.])|يراك|نظر الله|نظر الحق|مراقب"),
        WEAK("weak", "الصبر|صبر|الهمه|العزم|مجاهده|جاهد|الفتور|الكسل"),
        TIME("time", "الوقت|العمر|الاوقات|الايام|الساعه|طول الامل|الفراغ"),
        HEAVY("heavy", "الهم|الغم|الحزن|ضيق|انشراح|الفرح|السرور|الانس");

        public final String key;
        final Pattern pattern;

        Moment(String key, String regex) {
            this.key = key;
            this.pattern = Pattern.compile(regex);
        }
    }

    /**
     * Echoes of the weak hadith "the glance is a poisoned arrow". Such passages stay in the
     * library (they are the scholars' words) but are never pushed: not in a moment, Help now or
     * the daily saying.
     */
    private static final Pattern NOT_PUSHED = Pattern.compile("سهم مسموم");

    public final List<Topic> topics;
    public final List<Book> books;
    public final List<Saying> all;

    private Sayings(List<Topic> topics, List<Book> books, List<Saying> all) {
        this.topics = Collections.unmodifiableList(topics);
        this.books = Collections.unmodifiableList(books);
        this.all = Collections.unmodifiableList(all);
    }

    public static Sayings parse(String tsv) {
        List<Topic> topics = new ArrayList<>();
        List<Book> books = new ArrayList<>();
        List<Saying> sayings = new ArrayList<>();
        String[] lines = tsv.split("\n");
        if (lines.length == 0 || !lines[0].startsWith("#hisn-sayings\t")) {
            throw new IllegalArgumentException("not a sayings file");
        }
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) continue;
            String[] f = line.split("\t", -1);
            switch (f[0]) {
                case "T":
                    topics.add(new Topic(f[1], f[2], f[3], topics.size()));
                    break;
                case "B":
                    books.add(new Book(f[1], f[2], f[3], f[4]));
                    break;
                case "Q":
                    if (f.length != 10) throw new IllegalArgumentException("bad saying at line " + (i + 1));
                    sayings.add(new Saying(sayings.size(), f[9].replace("\\n", "\n"),
                            books.get(Integer.parseInt(f[1])), Integer.parseInt(f[2]), f[3],
                            Integer.parseInt(f[4]), Integer.parseInt(f[5]), Integer.parseInt(f[6]),
                            f[7], f[8]));
                    break;
                default:
                    throw new IllegalArgumentException("unknown line kind at line " + (i + 1));
            }
        }
        return new Sayings(topics, books, sayings);
    }

    /**
     * Folds Arabic for matching: drops harakat, tatweel and the dagger alif, and treats the
     * alif forms, ta marbuta and alif maqsura as their plain letters, so «الهوى» finds «الهَوَى».
     */
    public static String fold(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'ً' && c <= 'ٟ') || c == 'ٰ' || c == 'ـ') continue;
            switch (c) {
                case 'أ': case 'إ': case 'آ': case 'ٱ': c = 'ا'; break;
                case 'ة': c = 'ه'; break;
                case 'ى': c = 'ي'; break;
                default: break;
            }
            b.append(c);
        }
        return b.toString().toLowerCase(Locale.ROOT);
    }

    /** True for sayings that may be pushed to the reader (moments, Help now, the daily pick). */
    public static boolean pushable(Saying s) {
        return !NOT_PUSHED.matcher(s.folded).find();
    }

    public Topic topic(String key) {
        for (Topic t : topics) if (t.key.equals(key)) return t;
        return null;
    }

    /** Sayings for a moment, best first; short ones lead because they are read in a hard minute. */
    public List<Saying> moment(Moment m) {
        List<Saying> out = new ArrayList<>();
        for (Saying s : all) if (pushable(s) && m.pattern.matcher(s.folded).find()) out.add(s);
        Collections.sort(out, (a, b) -> Integer.compare(weight(b), weight(a)));
        return out;
    }

    private static int weight(Saying s) {
        return s.score + (s.text.length() < 260 ? 6 : 0);
    }

    /**
     * The library view: every filter is optional (null or empty means "any"). Search matches the
     * folded text, the book title and the author's name.
     */
    public List<Saying> filter(String topicKey, String authorAr, Moment moment, String query) {
        Topic topic = topicKey == null || topicKey.isEmpty() ? null : topic(topicKey);
        String q = query == null ? "" : fold(query.trim());
        List<Saying> base = moment == null ? all : moment(moment);
        List<Saying> out = new ArrayList<>();
        for (Saying s : base) {
            if (topic != null && (s.topics & topic.bit) == 0) continue;
            if (authorAr != null && !authorAr.isEmpty() && !s.book.authorAr.equals(authorAr)) continue;
            if (!q.isEmpty() && !s.folded.contains(q) && !fold(s.book.title).contains(q)
                    && !fold(s.book.authorAr).contains(q) && !s.book.authorEn.toLowerCase(Locale.ROOT).contains(q)) continue;
            out.add(s);
        }
        return out;
    }

    /** The pool the daily saying comes from: short, strongly relevant, pushable. */
    public List<Saying> dailyPool() {
        List<Saying> out = new ArrayList<>();
        for (Saying s : all) if (s.short_() && s.score >= 18 && pushable(s)) out.add(s);
        Collections.sort(out, (a, b) -> Integer.compare(b.score, a.score));
        return out;
    }

    /** One saying per day, stable for the whole day; {@code skip} moves to the next one. */
    public Saying daily(long epochDay, int skip) {
        List<Saying> pool = dailyPool();
        if (pool.isEmpty()) return null;
        int i = (int) Math.floorMod(epochDay + skip, (long) pool.size());
        return pool.get(i);
    }

    /** Authors with at least {@code min} sayings, most first, as their Arabic names. */
    public List<String> authors(int min) {
        List<String> names = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (Saying s : all) {
            int i = names.indexOf(s.book.authorAr);
            if (i < 0) { names.add(s.book.authorAr); counts.add(1); }
            else counts.set(i, counts.get(i) + 1);
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) if (counts.get(i) >= min) order.add(i);
        Collections.sort(order, (a, b) -> Integer.compare(counts.get(b), counts.get(a)));
        List<String> out = new ArrayList<>();
        for (int i : order) out.add(names.get(i));
        return out;
    }

    public int count(String authorAr) {
        int n = 0;
        for (Saying s : all) if (s.book.authorAr.equals(authorAr)) n++;
        return n;
    }
}
