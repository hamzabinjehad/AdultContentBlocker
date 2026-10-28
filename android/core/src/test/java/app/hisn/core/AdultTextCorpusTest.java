package app.hisn.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Reproducible synthetic fixture counts, never a general detection-accuracy claim. */
public final class AdultTextCorpusTest {
    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) throw new IllegalArgumentException("terms.tsv corpus.tsv [--report-only]");
        List<AdultTextPolicy.Rule> rules = new ArrayList<>();
        for (String row : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8)) rules.add(AdultTextPolicyTest.seedRow(row));
        rules.addAll(AdultTextPolicy.reviewedContextRules());
        AdultTextPolicy policy = new AdultTextPolicy(rules);
        int expectedBlocks = 0, trueBlocks = 0, expectedAllows = 0, falseBlocks = 0, requiredMisses = 0;
        int latinBlocks = 0, latinTrue = 0, latinAllows = 0, latinFalse = 0;
        for (String row : Files.readAllLines(Path.of(args[1]), StandardCharsets.UTF_8)) {
            if (row.isEmpty() || row.startsWith("#")) continue;
            String[] fields = row.split("\t", 3);
            if (fields.length != 3 || !(fields[0].equals("block") || fields[0].equals("allow")))
                throw new AssertionError("Invalid corpus fixture");
            boolean expected = fields[0].equals("block"), actual = policy.blocksAnyLine(fields[2].replace("\\n", "\n"));
            // "\\n" separates the visible labels of one screen, judged as TextGuardService does.
            // Franco-Arabic (Arabizi) families start with "arabizi"; also reported separately.
            // A family ending in "challenge" is a known miss: counted and printed, never required.
            boolean latin = fields[1].startsWith("arabizi");
            if (expected) {
                expectedBlocks++;
                if (latin) latinBlocks++;
                if (actual) { trueBlocks++; if (latin) latinTrue++; }
                else if (!fields[1].endsWith("challenge")) requiredMisses++;
            }
            else { expectedAllows++; if (latin) latinAllows++; if (actual) { falseBlocks++; if (latin) latinFalse++; } }
            if (actual != expected) System.out.println((expected ? "MISS " : "FALSE_BLOCK ") + fields[1] + ": " + fields[2]);
        }
        System.out.println("Synthetic adult-text corpus: " + trueBlocks + "/" + expectedBlocks
                + " expected blocks detected; " + falseBlocks + "/" + expectedAllows + " benign controls blocked"
                + " (Franco-Arabic rows: " + latinTrue + "/" + latinBlocks + " blocks, "
                + latinFalse + "/" + latinAllows + " benign blocked)");
        if (args.length == 3 && !"--report-only".equals(args[2])) throw new IllegalArgumentException("Unknown mode");
        // Challenge rows stay in reported denominators; known limitations must
        // remain visible rather than being relabelled as harmless controls.
        if (args.length == 2 && (requiredMisses != 0 || falseBlocks != 0))
            throw new AssertionError("Synthetic corpus regressions; counts are not population accuracy");
    }
}
