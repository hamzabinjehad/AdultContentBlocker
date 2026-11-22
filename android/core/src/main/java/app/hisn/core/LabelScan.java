package app.hisn.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Which on-screen labels one bounded inspection reads. Android-free so the rule is unit-tested.
 *
 * Breadth-first over at most 2048 visited nodes and 2048 scheduled children. Editable,
 * password and EditText nodes are never read and their subtrees are skipped; invisible
 * nodes are skipped, and an invisible node with children is not opened. A childless node gives its text, else its content description.
 * A parent's own text (or content description when it has no text), common on card and button
 * layouts whose children are images, is read only when its whole subtree was inspected, gave no text, and held
 * no editable or password node (even an invisible one) and no invisible node with children,
 * which may hide one (an input inside a hidden container). A cut-short subtree never yields
 * its parent's description, and a parent that is read covers every enclosing parent.
 * Those descriptions follow the leaf labels, one line each, in screen order. Nothing
 * beyond the nodes already visited is fetched.
 */
public final class LabelScan {
    /** A large real screen can carry more than 1,024 visible labels before the text in use
     * appears; 2,048 covers those trees, and the accessibility service walks them off its main
     * thread so the extra Binder reads do not stall the app being read. */
    public static final int NODE_BUDGET = 2048;

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

    /** A parent whose label is read at the end unless something in its subtree rules it out. */
    private static final class Parent {
        final CharSequence label;
        final Parent outer;
        boolean ruledOut;
        Parent(CharSequence label, Parent outer) { this.label = label; this.outer = outer; }
    }

    /** Retain the parent until its children are needed in breadth-first order. */
    private static final class Children<N> {
        final N parent;
        final Parent owner;
        final int count;
        int next;
        Children(N parent, Parent owner, int count) {
            this.parent = parent;
            this.owner = owner;
            this.count = count;
        }
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
        ArrayDeque<Children<N>> queue = new ArrayDeque<>();
        N node = root;
        Parent owner = null;
        VisibleTextSample text = new VisibleTextSample(true);
        List<Parent> parents = new ArrayList<>();
        int visited = 0, scheduled = 1, held = 0;
        try {
            while ((node != null || !queue.isEmpty()) && !text.isFull()) {
                if (node == null) {
                    Children<N> children = queue.getFirst();
                    owner = children.owner;
                    node = tree.child(children.parent, children.next++);
                    if (children.next == children.count) tree.release(queue.removeFirst().parent);
                    if (node == null) {
                        text.markPartial();
                        ruleOut(owner);
                        continue;
                    }
                }
                visited++;
                boolean queued = false;
                try {
                    // A private node also rules out every enclosing parent: its label may repeat the input.
                    if (tree.isPrivate(node)) { ruleOut(owner); continue; }
                    // An invisible container is not opened, so it may hide an input whose value an
                    // enclosing label repeats: it rules out every enclosing parent.
                    if (!tree.isVisible(node)) { if (tree.childCount(node) > 0) ruleOut(owner); continue; }
                    int count = tree.childCount(node);
                    if (count > 0) {
                        CharSequence label = tree.text(node);
                        if (!hasText(label)) label = tree.description(node);
                        if (hasText(label)) {
                            if (held + label.length() <= VisibleTextSample.LIMIT) {
                                held += label.length();
                                parents.add(owner = new Parent(label, owner));
                            } else { text.markPartial(); ruleOut(owner); } // Skipped, but still text inside.
                        }
                        int available = Math.min(count, budget - scheduled);
                        if (available < count) {
                            text.markPartial();
                            ruleOut(owner);
                        }
                        if (available > 0) {
                            queue.addLast(new Children<>(node, owner, available));
                            scheduled += available;
                            queued = true;
                        }
                    } else {
                        CharSequence value = tree.text(node);
                        if (value == null || value.length() == 0) value = tree.description(node);
                        if (hasText(value)) ruleOut(owner);
                        text.append(value);
                    }
                } finally {
                    N current = node;
                    node = null;
                    if (!queued) tree.release(current);
                }
                if (check != null && visited % CHECK_INTERVAL == 0 && !text.isEmpty()
                        && check.stop(text, visited)) break;
            }
        } finally {
            if (node != null) tree.release(node);
            if (!queue.isEmpty()) text.markPartial();
            while (!queue.isEmpty()) {
                Children<N> children = queue.removeFirst();
                ruleOut(children.owner);
                tree.release(children.parent);
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
        for (int i = 0; i < read.length; i++) if (read[i]) text.append(parents.get(i).label);
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
