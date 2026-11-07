package app.hisn.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Which on-screen labels one bounded inspection reads. Android-free so the rule is unit-tested.
 *
 * Breadth-first over at most 1024 visited nodes and 1024 scheduled children. Editable,
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
    /** A large real screen can carry more than 512 visible labels before the text in use appears;
     * 1,024 covers those trees, and the accessibility service walks them off its main thread. */
    public static final int NODE_BUDGET = 1024;

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

    /** Called during a scan with the labels read so far; true stops the scan for an immediate
     * decision. Only labels already appended are visible; parent descriptions come at the end. */
    public interface Check {
        boolean stop(VisibleTextSample sample, int visited);
    }

    /** A partial match is checked every this many visited nodes, not for every leaf: the adult
     * policy is scanned once per checkpoint rather than once per label. */
    public static final int CHECK_INTERVAL = 64;

    /** Scans from root, which the scan takes ownership of and releases. */
    public static <N> VisibleTextSample sample(Tree<N> tree, N root) { return sample(tree, root, NODE_BUDGET); }

    /** The same scan within a smaller share of the budget (several windows share one), at least 1. */
    public static <N> VisibleTextSample sample(Tree<N> tree, N root, int budget) {
        return sample(tree, root, budget, null);
    }

    /** The same scan, with a checkpoint that may stop it early when the labels read so far match. */
    public static <N> VisibleTextSample sample(Tree<N> tree, N root, int budget, Check check) {
        budget = Math.max(1, Math.min(NODE_BUDGET, budget));
        ArrayDeque<Queued<N>> queue = new ArrayDeque<>();
        queue.add(new Queued<>(root, null));
        VisibleTextSample text = new VisibleTextSample(true);
        List<Parent> parents = new ArrayList<>();
        int visited = 0, scheduled = 1, held = 0;
        try {
            while (!queue.isEmpty() && visited++ < budget && !text.isFull()) {
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
                            if (scheduled >= budget) { text.markPartial(); ruleOut(owner); break; }
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
                if (check != null && visited % CHECK_INTERVAL == 0 && !text.isEmpty()
                        && check.stop(text, visited)) break;
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
