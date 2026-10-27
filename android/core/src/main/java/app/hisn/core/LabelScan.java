package app.hisn.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Which on-screen labels one bounded inspection reads. Android-free so the rule is unit-tested.
 *
 * Breadth-first over at most 512 visited nodes and 512 scheduled children. Editable,
 * password and EditText nodes are never read and their subtrees are skipped; invisible
 * nodes are skipped, and an invisible node with children is not opened. A childless node gives its text, else its content description.
 * A parent's own content description, common on card and button layouts whose children
 * are images, is read only when its whole subtree was inspected, gave no text, and held
 * no editable or password node (even an invisible one) and no invisible node with children,
 * which may hide one (an input inside a hidden container). A cut-short subtree never yields
 * its parent's description, and a parent that is read covers every enclosing parent.
 * Those descriptions follow the leaf labels, one line each, in screen order. Nothing
 * beyond the nodes already visited is fetched.
 */
public final class LabelScan {
    public static final int NODE_BUDGET = 512;

    /** Node access. {@link #release} is called once for every node the scan holds, root included. */
    public interface Tree<N> {
        /** Password, editable or an EditText: never read. */
        boolean isPrivate(N node);
        boolean isVisible(N node);
        int childCount(N node);
        /** Null when Android cannot provide the child. */
        N child(N node, int index);
        CharSequence text(N node);
        CharSequence description(N node);
        void release(N node);
    }

    /** A parent whose description is read at the end unless something in its subtree rules it out. */
    private static final class Parent {
        final CharSequence description;
        final Parent outer;
        boolean ruledOut;
        Parent(CharSequence description, Parent outer) { this.description = description; this.outer = outer; }
    }

    private static final class Queued<N> {
        final N node;
        final Parent owner;
        Queued(N node, Parent owner) { this.node = node; this.owner = owner; }
    }

    /** Scans from root, which the scan takes ownership of and releases. */
    public static <N> VisibleTextSample sample(Tree<N> tree, N root) {
        ArrayDeque<Queued<N>> queue = new ArrayDeque<>();
        queue.add(new Queued<>(root, null));
        VisibleTextSample text = new VisibleTextSample(true);
        List<Parent> parents = new ArrayList<>();
        int visited = 0, scheduled = 1, held = 0;
        try {
            while (!queue.isEmpty() && visited++ < NODE_BUDGET && !text.isFull()) {
                Queued<N> item = queue.removeFirst();
                N node = item.node;
                try {
                    // A private node also rules out every enclosing parent: its label may repeat the input.
                    if (tree.isPrivate(node)) { ruleOut(item.owner); continue; }
                    // An invisible container is not opened, so it may hide an input whose value an
                    // enclosing label repeats: it rules out every enclosing parent.
                    if (!tree.isVisible(node)) { if (tree.childCount(node) > 0) ruleOut(item.owner); continue; }
                    int count = tree.childCount(node);
                    if (count > 0) {
                        Parent owner = item.owner;
                        CharSequence description = tree.description(node);
                        if (hasText(description)) {
                            if (held + description.length() <= VisibleTextSample.LIMIT) {
                                held += description.length();
                                parents.add(owner = new Parent(description, owner));
                            } else { text.markPartial(); ruleOut(owner); } // Skipped, but still text inside.
                        }
                        for (int i = 0; i < count; i++) {
                            if (scheduled >= NODE_BUDGET) { text.markPartial(); ruleOut(owner); break; }
                            scheduled++;
                            N child = tree.child(node, i);
                            if (child != null) queue.addLast(new Queued<>(child, owner));
                            else { text.markPartial(); ruleOut(owner); }
                        }
                    } else {
                        CharSequence value = tree.text(node);
                        if (value == null || value.length() == 0) value = tree.description(node);
                        if (hasText(value)) ruleOut(item.owner);
                        text.append(value);
                    }
                } finally { tree.release(node); }
            }
        } finally {
            if (!queue.isEmpty()) text.markPartial();
            while (!queue.isEmpty()) {
                Queued<N> item = queue.removeFirst();
                ruleOut(item.owner);
                tree.release(item.node);
            }
        }
        // Breadth-first order lists every parent before the ones inside it, so a backward pass
        // settles inner parents first: one that is read is text inside every enclosing parent.
        boolean[] read = new boolean[parents.size()];
        for (int i = parents.size() - 1; i >= 0; i--) {
            Parent parent = parents.get(i);
            if (parent.ruledOut) continue;
            read[i] = true;
            ruleOut(parent.outer);
        }
        // Appended in screen order, after the leaf labels.
        for (int i = 0; i < read.length; i++) if (read[i]) text.append(parents.get(i).description);
        return text;
    }

    /** Marks a parent and every enclosing one. An already ruled-out parent's are already marked. */
    private static void ruleOut(Parent parent) {
        for (; parent != null && !parent.ruledOut; parent = parent.outer) parent.ruledOut = true;
    }

    private static boolean hasText(CharSequence value) {
        if (value == null) return false;
        for (int i = 0; i < value.length(); i++) if (!Character.isWhitespace(value.charAt(i))) return true;
        return false;
    }

    private LabelScan() { }
}
