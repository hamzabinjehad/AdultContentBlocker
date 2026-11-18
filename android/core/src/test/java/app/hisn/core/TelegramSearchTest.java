package app.hisn.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Run by test-core.sh with the same seed fixture AdultTextPolicyTest reads. */
public final class TelegramSearchTest {
    private static void check(boolean value, String label) { if (!value) throw new AssertionError("Telegram: " + label); }

    private static TelegramSearch.Sample query(String text) { return new TelegramSearch.Sample(text, "", ""); }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new AssertionError("Telegram: pass the seed TSV fixture");
        List<AdultTextPolicy.Rule> rules = new ArrayList<>();
        for (String row : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (!row.isEmpty()) rules.add(AdultTextPolicyTest.seedRow(row));
        }
        rules.addAll(AdultTextPolicy.reviewedContextRules());
        AdultTextPolicy adult = new AdultTextPolicy(rules);
        WordMatcher none = new WordMatcher(Collections.<String>emptyList());

        check(TelegramSearch.watches(true, "org.telegram.messenger")
                && TelegramSearch.watches(true, "org.telegram.messenger.web")
                && TelegramSearch.watches(true, "org.thunderdog.challegram"), "Telegram and Telegram X are watched");
        check(!TelegramSearch.watches(false, "org.telegram.messenger"), "only while the adult layer is on");
        check(!TelegramSearch.watches(true, "com.whatsapp") && !TelegramSearch.watches(true, null), "other apps are not");

        // Read from Telegram 13.2.2 on a Galaxy S21 FE (2340 px tall, 420 dpi = 2.625 px per dp).
        float phone = 2.625f;
        check(TelegramSearch.bandBottom(0, 2340, phone) == 273, "the band is the action bar's height");
        check(TelegramSearch.inTopBand(106, 192, 0, 2340, phone), "the chat title is header");
        check(TelegramSearch.inTopBand(180, 222, 0, 2340, phone), "the subtitle under it is header");
        check(!TelegramSearch.inTopBand(0, 246, 0, 2340, phone), "the bar's container touches the top edge");
        check(!TelegramSearch.inTopBand(0, 296, 0, 2340, phone), "a message scrolled under the bar is not header");
        check(!TelegramSearch.inTopBand(265, 314, 0, 2340, phone), "the first message row is not header");
        check(!TelegramSearch.inTopBand(1102, 1321, 0, 2340, phone), "the message box is never header");
        check(!TelegramSearch.inTopBand(100, 100, 0, 2340, phone), "an empty node is not header");
        check(TelegramSearch.inTopBand(1100, 1200, 1000, 2000, phone), "a split-screen window has its own band");
        check(TelegramSearch.bandBottom(0, 400, phone) == 200, "a short window's band is at most half of it");

        // Inline bot queries, as typed on the phone: "@SearcheeBot #adult sex porno".
        check("#adult sex porno".equals(TelegramSearch.inlineQuery("@SearcheeBot #adult sex porno ")),
                "an inline query's search part");
        check(TelegramSearch.inlineQuery("see you at 5") == null && TelegramSearch.inlineQuery("@Mom") == null
                && TelegramSearch.inlineQuery("email me @ home") == null && TelegramSearch.inlineQuery(null) == null,
                "an ordinary message is not a query");
        check(TelegramSearch.blocks(adult, none, query(TelegramSearch.inlineQuery("@SearcheeBot #adult sex porno"))),
                "the phone's inline bot search is blocked");
        check(TelegramSearch.botChat("Searchee Bot, Muted\n235,370 monthly users")
                && TelegramSearch.botChat("Helper\nbot") && TelegramSearch.botChat("Helper\n1 monthly user"),
                "a bot's header");
        check(!TelegramSearch.botChat("Family\nlast seen recently") && !TelegramSearch.botChat("Robotics club\n42 members")
                && !TelegramSearch.botChat(null), "a person's or a group's header");
        // A channel the bot listed, as its header read on the phone.
        check(TelegramSearch.blocks(adult, none, new TelegramSearch.Sample("", "PornDrive\n833.4K subscribers", "")),
                "a run-together adult channel name");
        for (String name : new String[] {"PornStars HD", "xxxVideos", "HentaiWorld"}) {
            check(TelegramSearch.blocks(adult, none, new TelegramSearch.Sample("", name, "")), "run together: " + name);
        }
        for (String name : new String[] {"Pornic beach", "Sexton family", "Saxony fans", "سكسونيا", "Xx Gaming"}) {
            check(!TelegramSearch.blocks(adult, none, new TelegramSearch.Sample("", name, "")), "not run together: " + name);
        }
        // The search bot's listing rows, as shown on the phone.
        check(TelegramSearch.blocks(adult, none, new TelegramSearch.Sample("", "Searchee Bot\n235,370 monthly users",
                "Adult\n INDONESIA  18+ PORNO VIDEO SEX , private [11813 participants]")),
                "an adult listing in a bot chat");
        check(!TelegramSearch.blocks(adult, none, new TelegramSearch.Sample("", "Searchee Bot\n235,370 monthly users",
                "Choose a category or just use the global search.\nTransport\nSales\nReligion\nFamily & Children")),
                "the bot's ordinary categories");

        // Searches people actually type, in English, Arabic and Franco-Arabic.
        for (String text : new String[] {"porn", "Porn", "p0rn", "xxx", "onlyfans", "onlyfans leaks", "sex videos",
                "sex chat", "milf", "xnxx", "pornhub", "سكس", "سكسي", "افلام سكس", "سكس عربي", "شرموطه", "sharmota"}) {
            check(TelegramSearch.blocks(adult, none, query(text)), "search blocked: " + text);
        }
        // Ordinary searches, recovery and the words that only look adult.
        for (String text : new String[] {"", "mom", "cooking", "football highlights", "quran", "اخبار", "مطبخ",
                "تحميل افلام", "kids cartoons", "movies", "girls", "sexual health", "sex education", "breast cancer",
                "porn addiction", "porn addiction help", "porn blocker", "Nick", "Nick Bourne", "Nick Jonas",
                "نيك", "couscous", "كسكس", "Essex", "Middlesex", "Sex and the City"}) {
            check(!TelegramSearch.blocks(adult, none, query(text)), "search allowed: " + text);
        }
        check(!adult.blocksQuery("one two three four five six seven porn"), "a long line is prose, not a query");

        check(TelegramSearch.blocks(adult, none, new TelegramSearch.Sample("", "افلام سكس\n12K subscribers", "")),
                "an adult channel's title");
        check(TelegramSearch.blocks(adult, none, new TelegramSearch.Sample("", "XXX Videos HD\n3,410 subscribers", "")),
                "an English adult channel's title");
        check(!TelegramSearch.blocks(adult, none, new TelegramSearch.Sample("", "Family\nlast seen recently", "")),
                "a family chat's title");
        check(!TelegramSearch.blocks(adult, none, new TelegramSearch.Sample("", "Porn Addiction Recovery\n240 members", "")),
                "a recovery group's title");
        check(TelegramSearch.blocks(adult, none, new TelegramSearch.Sample("hot", "", "News\nسكس عربي\nWeather")),
                "an adult channel among the results of an innocent-looking search");
        check(!TelegramSearch.blocks(adult, none, new TelegramSearch.Sample("doctor", "",
                "Sexual health clinic\nDr. Ahmad\nThe appointment is at five tomorrow")), "medical results stay allowed");

        WordMatcher own = new WordMatcher(Collections.singleton("forbidden"));
        check(TelegramSearch.blocks(null, own, query("forbidden channel")),
                "the owner's own words, even before the adult terms load");
        check(!TelegramSearch.blocks(null, none, query("porn")), "no verdict without a policy");
        StringBuilder huge = new StringBuilder();
        while (huge.length() <= TelegramSearch.MAX_CHARS) huge.append("porn ");
        check(!TelegramSearch.blocks(adult, none, new TelegramSearch.Sample("", "", huge.toString())),
                "an oversized sample is skipped, never thrown");
        check(!TelegramSearch.blocks(adult, none, null), "no sample, no verdict");
        try { TelegramSearch.PACKAGES.add("x.y"); throw new AssertionError("Telegram packages are fixed"); }
        catch (UnsupportedOperationException expected) { }
        System.out.println("TelegramSearchTest passed");
    }
}
