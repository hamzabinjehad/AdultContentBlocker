package app.hisn.core;

import java.nio.charset.StandardCharsets;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * An immutable set of canonical (lowercase ASCII) hostnames packed into one byte array.
 *
 * The published list holds about 236,000 names. As a {@code HashSet<String>} that is a
 * String, a char/byte array and a map node per name, roughly 25 MB of heap and a long
 * garbage-collection trace on every phone, and more than an Android 6 default heap
 * comfortably holds. Here the names share one byte array, one offset array and one
 * open-addressing index, about 7 MB, with no per-name objects.
 *
 * {@link #contains} uses the String's cached hash, so a DNS lookup costs one probe
 * sequence and one byte comparison, without allocating.
 */
final class CompactHostSet extends AbstractSet<String> {
    private final byte[] names;
    /** offsets[i]..offsets[i + 1] is name i. */
    private final int[] offsets;
    /** Name index + 1, or 0 for an empty slot. Power-of-two size, at most half full. */
    private final int[] slots;
    private final int size;

    private CompactHostSet(byte[] names, int[] offsets, int[] slots, int size) {
        this.names = names;
        this.offsets = offsets;
        this.slots = slots;
        this.size = size;
    }

    @Override public int size() { return size; }

    @Override public boolean contains(Object value) {
        if (!(value instanceof String)) return false;
        String name = (String) value;
        int mask = slots.length - 1;
        for (int slot = spread(name.hashCode()) & mask; ; slot = (slot + 1) & mask) {
            int entry = slots[slot];
            if (entry == 0) return false;
            if (equals(entry - 1, name)) return true;
        }
    }

    @Override public Iterator<String> iterator() {
        return new Iterator<String>() {
            private int next;
            @Override public boolean hasNext() { return next < size; }
            @Override public String next() {
                if (next >= size) throw new NoSuchElementException();
                int start = offsets[next], end = offsets[++next];
                return new String(names, start, end - start, StandardCharsets.US_ASCII);
            }
        };
    }

    private boolean equals(int index, String name) {
        int start = offsets[index], length = offsets[index + 1] - start;
        if (length != name.length()) return false;
        for (int i = 0; i < length; i++) if (names[start + i] != name.charAt(i)) return false;
        return true;
    }

    /** String.hashCode of the stored bytes, which are ASCII and so equal the String's chars. */
    private int hash(int index) {
        int hash = 0;
        for (int i = offsets[index]; i < offsets[index + 1]; i++) hash = 31 * hash + names[i];
        return hash;
    }

    /** Spreads the String hash's low bits, which are weak for names that share a suffix. */
    private static int spread(int hash) {
        hash ^= hash >>> 16;
        hash *= 0x85ebca6b;
        return hash ^ (hash >>> 13);
    }

    /** Collects canonical names; {@link #build} rejects duplicates. */
    static final class Builder {
        private byte[] names = new byte[1 << 16];
        private int[] offsets = new int[1 << 12];
        private int count, used;

        int count() { return count; }

        /** Adds a name {@link Hostname#normalize} returned, which is always lowercase ASCII. */
        void add(String canonical) {
            int length = canonical.length();
            if (used + length > names.length) names = Arrays.copyOf(names, Math.max(names.length * 2, used + length));
            if (count + 2 > offsets.length) offsets = Arrays.copyOf(offsets, offsets.length * 2);
            for (int i = 0; i < length; i++) {
                char c = canonical.charAt(i);
                if (c >= 0x80) throw new IllegalArgumentException("Hostname is not canonical ASCII");
                names[used++] = (byte) c;
            }
            offsets[++count] = used;
        }

        /** The set, or null when a name was added twice. */
        CompactHostSet build() {
            int capacity = Integer.highestOneBit(Math.max(4, count * 2 - 1)) << 1;
            CompactHostSet set = new CompactHostSet(Arrays.copyOf(names, used),
                    Arrays.copyOf(offsets, count + 1), new int[capacity], count);
            int mask = capacity - 1;
            for (int index = 0; index < count; index++) {
                int slot = spread(set.hash(index)) & mask;
                for (; set.slots[slot] != 0; slot = (slot + 1) & mask) {
                    if (set.same(set.slots[slot] - 1, index)) return null;
                }
                set.slots[slot] = index + 1;
            }
            names = null;
            offsets = null;
            return set;
        }
    }

    private boolean same(int a, int b) {
        int startA = offsets[a], startB = offsets[b], length = offsets[a + 1] - startA;
        if (length != offsets[b + 1] - startB) return false;
        for (int i = 0; i < length; i++) if (names[startA + i] != names[startB + i]) return false;
        return true;
    }
}
