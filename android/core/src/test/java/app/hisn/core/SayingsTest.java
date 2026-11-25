package app.hisn.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public final class SayingsTest {
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError("Sayings: " + label);
    }

    private static final String SAMPLE = "#hisn-sayings\t1\n"
            + "T\tgaze\tGaze & desire\tغض البصر والشهوة\n"
            + "T\ttawba\tRepentance\tالتوبة والرجوع\n"
            + "B\tذم الهوى\t\tابن الجوزي\tIbn al-Jawzi\n"
            + "B\tمختصر منهاج القاصدين\t\tابن قدامة المقدسي\tIbn Qudama al-Maqdisi\n"
            + "Q\t0\t82\t\t1\t30\t0\tbook\thttps://app.turath.io/book/6870?page=82\tوَلَمَّا كَانَ إِطْلاقُ الْبَصَرِ سَبَبًا لِوُقُوعِ الْهَوَى فِي الْقَلْبِ…\n"
            + "Q\t1\t256\t2\t2\t25\t3\treader\thttps://app.turath.io/book/98087?page=256\t…لأن التائب من الذنب كمن لا ذنب له،\\nوالثوب المغسول كالذي لم يتسخ أصلًا.\n"
            + "Q\t1\t275\t\t1\t40\t0\tbook\thttps://app.turath.io/book/98087?page=275\tفإن النظر سهم مسموم من سهام إبليس\n"
            + "Q\t0\t10\t\t0\t5\t0\tbook\thttps://app.turath.io/book/6870?page=10\tفَاللهَ اللهَ! الخلوات الخلوات! فإن عليكن من الله عينًا ناظرةً\n";

    public static void main(String[] args) throws Exception {
        Sayings s = Sayings.parse(SAMPLE);
        check(s.topics.size() == 2 && s.books.size() == 2 && s.all.size() == 4, "parses topics, books and sayings");
        check(s.topic("tawba").bit == 2, "a topic's bit is its position");
        Sayings.Saying slip = s.all.get(1);
        check(slip.text.contains("\n") && slip.text.endsWith("أصلًا."), "newlines come back and the text is untouched");
        check(slip.pageLabel().equals("2/ 256"), "multi-volume page label");
        check(s.all.get(0).pageLabel().equals("82"), "single-volume page label");
        check(slip.source.equals("reader") && slip.likes == 3, "source and likes");

        check(Sayings.fold("الهَوَى").equals("الهوي"), "folding drops harakat and alif maqsura");
        check(Sayings.fold("إِطْلاقُ").equals("اطلاق"), "folding plain-alifs hamza forms");
        check(s.filter(null, null, null, "إطلاق البصر").size() == 1, "search ignores harakat");
        check(s.filter(null, null, null, "الهوى").size() == 2, "search also matches the book title");
        check(s.filter(null, null, null, "ابن قدامة").size() == 2, "search finds the author");
        check(s.filter(null, null, null, "qudama").size() == 2, "search finds the English author name");
        check(s.filter("gaze", null, null, "").size() == 2, "topic filter");
        check(s.filter(null, "ابن الجوزي", null, null).size() == 2, "author filter");

        List<Sayings.Saying> tempted = s.moment(Sayings.Moment.TEMPTED);
        check(tempted.size() == 1 && tempted.get(0) == s.all.get(0), "the poisoned-arrow echo is never pushed");
        check(s.filter("gaze", null, null, null).contains(s.all.get(2)), "but it stays in the library");
        check(s.moment(Sayings.Moment.AFTER_SLIP).contains(slip), "repentance fits after a slip");
        check(s.moment(Sayings.Moment.ALONE).contains(s.all.get(3)), "the private hours fit when alone");
        check(!s.moment(Sayings.Moment.ALONE).contains(s.all.get(2)), "'alone' matches whole words only");

        check(s.dailyPool().size() == 2, "daily pool: short, strong, pushable");
        check(s.daily(100, 0) != s.daily(100, 1), "skip moves to another saying");
        check(s.daily(100, 0) == s.daily(100, 0), "stable for the day");
        check(s.daily(-3, 0) != null, "a negative day still picks one");
        check(s.authors(1).get(0).equals("ابن الجوزي") || s.authors(1).get(0).equals("ابن قدامة المقدسي"), "authors listed");
        check(s.count("ابن الجوزي") == 2, "count per author");

        boolean threw = false;
        try { Sayings.parse("nope"); } catch (IllegalArgumentException e) { threw = true; }
        check(threw, "rejects a file without the header");

        // The bundled library itself: every line parses and the counts hold.
        Path bundled = Paths.get("app/src/hisn/assets/sayings/sayings.tsv");
        if (Files.exists(bundled)) {
            Sayings lib = Sayings.parse(new String(Files.readAllBytes(bundled), StandardCharsets.UTF_8));
            check(lib.all.size() >= 555, "the bundled library has every saying");
            for (Sayings.Moment m : Sayings.Moment.values()) check(!lib.moment(m).isEmpty(), "moment has sayings: " + m.key);
            for (Sayings.Saying x : lib.dailyPool()) check(Sayings.pushable(x), "daily pool is pushable");
            for (Sayings.Saying x : lib.all) check(x.link.startsWith("https://"), "every saying links to its page");
            check(lib.authors(4).size() >= 5, "several scholars");
        }
        System.out.println("Sayings: ok");
    }
}
