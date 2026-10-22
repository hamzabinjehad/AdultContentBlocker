package app.hisn.core;

import java.util.Collections;

public final class VisibleTextSampleTest {
    public static void main(String[] args) {
        VisibleTextSample sample = new VisibleTextSample();
        sample.append("blocked " + "ordinary ".repeat(1000));
        if (new WordMatcher(Collections.singleton("blocked")).matches(sample.text()).isEmpty())
            throw new AssertionError("Long visible leaf was skipped");
        sample.append("safe ".repeat(10000));
        if (!sample.isPartial() || sample.text().length() > 32768)
            throw new AssertionError("Sample budget not reported");
        VisibleTextSample cut = new VisibleTextSample();
        cut.append(" ".repeat(32765) + "sexual");
        if (!new WordMatcher(Collections.singleton("sex")).matches(cut.text()).isEmpty())
            throw new AssertionError("Truncation manufactured a word boundary");
        VisibleTextSample expansion = new VisibleTextSample();
        expansion.append("\ufb03 ".repeat(20000));
        if (!expansion.isPartial() || expansion.text().length() > 32768)
            throw new AssertionError("Normalized expansion escaped budget");
        VisibleTextSample raw = new VisibleTextSample(true);
        raw.append("Watch p.o.r.n\nvideo");
        if (!raw.text().equals("Watch p.o.r.n video\n"))
            throw new AssertionError("Screen sample lost punctuation or leaf boundary");
        if (new WordMatcher(Collections.singleton("video")).matches(raw.text()).isEmpty())
            throw new AssertionError("Raw sample broke custom literal matching");
        raw.append("safe ".repeat(10000));
        if (!raw.isPartial() || raw.text().length() > 32768)
            throw new AssertionError("Raw sample escaped budget");
        System.out.println("VisibleTextSample tests passed");
    }
}
