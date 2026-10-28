package app.hisn.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class LabelScanTest {
    /** A fake accessibility node; null children stand for nodes Android could not provide. */
    static final class Node {
        final String text, description;
        final boolean secret, visible;
        final List<Node> children = new ArrayList<>();
        Node(String text, String description, boolean secret, boolean visible, Node... children) {
            this.text = text; this.description = description; this.secret = secret; this.visible = visible;
            this.children.addAll(Arrays.asList(children));
        }
    }

    static Node leaf(String text) { return new Node(text, null, false, true); }
    static Node image(String description) { return new Node(null, description, false, true); }
    static Node card(String description, Node... children) { return new Node(null, description, false, true, children); }
    static Node input(String text, String description, Node... children) { return new Node(text, description, true, true, children); }
    static Node hidden(Node node) {
        Node copy = new Node(node.text, node.description, node.secret, false);
        copy.children.addAll(node.children);
        return copy;
    }

    /** Counts holds and releases; every node the scan obtains must be released exactly once. */
    static final class Tree implements LabelScan.Tree<Node> {
        final Map<Node, Integer> held = new IdentityHashMap<>();
        int obtained, failAfter = Integer.MAX_VALUE;
        Node obtain(Node node) {
            if (node == null) return null;
            if (++obtained > failAfter) throw new IllegalStateException("node went stale");
            held.merge(node, 1, Integer::sum);
            return node;
        }
        @Override public boolean isPrivate(Node node) { return node.secret; }
        @Override public boolean isVisible(Node node) { return node.visible; }
        @Override public int childCount(Node node) { return node.children.size(); }
        @Override public Node child(Node node, int index) { return obtain(node.children.get(index)); }
        @Override public CharSequence text(Node node) { return node.text; }
        @Override public CharSequence description(Node node) { return node.description; }
        @Override public void release(Node node) {
            Integer count = held.get(node);
            check(count != null && count > 0, "released only nodes the scan holds");
            if (count == 1) held.remove(node); else held.put(node, count - 1);
        }
    }

    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    static VisibleTextSample scan(Node root) {
        Tree tree = new Tree();
        VisibleTextSample sample = LabelScan.sample(tree, tree.obtain(root));
        check(tree.held.isEmpty(), "every obtained node is released");
        return sample;
    }

    static List<String> labels(Node root) {
        String text = scan(root).text();
        return text.isEmpty() ? new ArrayList<>() : Arrays.asList(text.split("\n"));
    }

    public static void main(String[] args) {
        check(labels(card(null, leaf("Title"), image("Play button"))).equals(Arrays.asList("Title", "Play button")),
                "leaf text, else the leaf description");
        check(labels(card("Hot clip, 3 minutes", image(null), card(null, image(""))))
                .equals(Arrays.asList("Hot clip, 3 minutes")), "a card whose children carry no text gives its description");
        check(labels(card("Hot clip", image(null), leaf("Hot clip title"))).equals(Arrays.asList("Hot clip title")),
                "a card with a text child is read through the child only");
        check(labels(card("Card", card(null, card(null, leaf("Deep title"))))).equals(Arrays.asList("Deep title")),
                "text anywhere below rules out the parent description");
        check(labels(card("Card", image(null), leaf("   "))).equals(Arrays.asList("   ", "Card")),
                "a blank label is not text");
        check(labels(card("Search: secret words", image(null), input("secret words", null))).isEmpty(),
                "a parent holding an input field is never read, nor the input");
        check(labels(card("Card", image(null), hidden(input("typed", null)))).isEmpty(),
                "an invisible input field still rules out the parent description");
        check(labels(card("Reply: draft text", image(null), hidden(card(null, input("draft text", null))))).isEmpty(),
                "an input inside an invisible container rules out the parent description");
        check(labels(card("Outer", card("Inner", image(null), hidden(card(null, image(null)))))).isEmpty(),
                "an invisible container rules out every enclosing parent, not only its own");
        check(labels(card("Card", card(null, input(null, "pin")))).isEmpty(),
                "an input field deeper down rules out every enclosing parent");
        check(labels(input("password", "Password field", leaf("child"))).isEmpty(),
                "a private parent and its subtree are skipped");
        check(labels(card(null, hidden(card("Hidden card", image(null))), hidden(leaf("Hidden leaf")))).isEmpty(),
                "invisible nodes are not read");
        check(labels(card("Shown card", image(null), hidden(leaf("Hidden leaf")))).equals(Arrays.asList("Shown card")),
                "an invisible childless label below a card neither reads nor rules it out");
        check(labels(card("Outer", card("Inner", image(null)), image(null))).equals(Arrays.asList("Inner")),
                "the innermost card is read and covers its enclosing card");
        check(labels(card("Outer", card("Left", image(null)), card("Right", image(null))))
                .equals(Arrays.asList("Left", "Right")), "sibling cards are each read once, in screen order");
        check(labels(card("Root card", image(null))).equals(Arrays.asList("Root card")), "the root itself can be a card");

        Node missing = card("Card", image(null));
        missing.children.add(null);
        VisibleTextSample partial = scan(missing);
        check(partial.isPartial() && partial.text().isEmpty(), "a child Android cannot provide rules out the parent");

        // 600 empty images: the budget cuts the card's subtree short.
        Node[] many = new Node[600];
        for (int i = 0; i < many.length; i++) many[i] = image(null);
        VisibleTextSample cut = scan(card("Big card", many));
        check(cut.isPartial() && cut.text().isEmpty(), "a subtree cut by the node budget never yields its parent");
        // A card inspected in full survives a budget cut in a later, wide list.
        Node[] list = new Node[700];
        for (int i = 0; i < list.length; i++) list[i] = leaf("row " + i);
        VisibleTextSample wide = scan(card(null, card("First card", image(null)), card(null, list)));
        check(wide.isPartial() && wide.text().endsWith("\nFirst card\n"), "complete cards survive a cut elsewhere");
        // A card whose child could not be scheduled is not complete.
        Node[] rows = new Node[700];
        rows[0] = card("Early card", image(null));
        for (int i = 1; i < rows.length; i++) rows[i] = leaf("row " + i);
        check(!scan(card(null, rows)).text().contains("Early card"), "an unscheduled child rules out its card");
        int lines = wide.text().split("\n").length;
        check(lines < LabelScan.NODE_BUDGET, "visited nodes stay within the budget");

        // A shared budget (several windows): never more nodes obtained than the share, and a cut is partial.
        for (int share : new int[]{1, 2, 100, 256}) {
            Tree counted = new Tree();
            VisibleTextSample part = LabelScan.sample(counted, counted.obtain(card(null, list)), share);
            check(counted.obtained <= Math.max(1, share) && part.isPartial() && counted.held.isEmpty(),
                    "a share of " + share + " obtains at most that many nodes");
        }
        Tree whole = new Tree();
        check(!LabelScan.sample(whole, whole.obtain(card(null, leaf("a"), leaf("b"))), 3).isPartial()
                && whole.obtained == 3, "a tree within its share is complete");
        // A large active feed and a tiny picture-in-picture window: the small one is read first, so
        // the feed gets nearly the whole budget instead of half of it.
        Node[] feed = new Node[480];
        for (int i = 0; i < feed.length; i++) feed[i] = leaf("post " + i);
        Map<Integer, Node> trees = new java.util.HashMap<>();
        trees.put(1, card(null, feed));
        trees.put(2, card(null, leaf("Paused"), image("Play")));
        List<GuardWindows.Window> windows = Arrays.asList(new GuardWindows.Window(1, "feed", true, true, true, false),
                new GuardWindows.Window(2, "video", true, false, false, true));
        List<GuardWindows.Window> order = GuardWindows.scanOrder(windows);
        int left = LabelScan.NODE_BUDGET;
        boolean anyPartial = false;
        for (int i = 0; i < order.size(); i++) {
            Tree shared = new Tree();
            VisibleTextSample read = LabelScan.sample(shared, shared.obtain(trees.get(order.get(i).id)),
                    GuardWindows.share(left, order.size() - i));
            left -= shared.obtained;
            anyPartial |= read.isPartial();
        }
        check(order.get(1).id == 1 && !anyPartial && left >= 0, "a small window first leaves the feed complete");
        Tree halved = new Tree();
        check(LabelScan.sample(halved, halved.obtain(trees.get(1)), GuardWindows.share(LabelScan.NODE_BUDGET, 2))
                .isPartial(), "while the feed first, at half the budget, is cut");
        Tree zero = new Tree();
        check(LabelScan.sample(zero, zero.obtain(card(null, leaf("a"))), 0).isPartial() && zero.held.isEmpty(),
                "a zero share still visits the root, and only it");

        // Deep nesting is bounded by visited nodes, not recursion.
        Node deep = image(null);
        for (int i = 0; i < 2000; i++) deep = card(i == 1999 ? "Top" : null, deep);
        VisibleTextSample bounded = scan(deep);
        check(bounded.isPartial() && bounded.text().isEmpty(), "deep trees stop at the budget without reading the top");

        // Long descriptions: never more held than the sample can take.
        String huge = "x ".repeat(VisibleTextSample.LIMIT);
        VisibleTextSample large = scan(card(null, card(huge, image(null)), card("Small card", image(null))));
        check(large.isPartial() && large.text().length() <= VisibleTextSample.LIMIT, "held descriptions are bounded");
        VisibleTextSample skipped = scan(card("Outer card", card(huge, image(null))));
        check(skipped.isPartial() && skipped.text().isEmpty(), "a description too long to hold still covers its outer card");

        // A node that goes stale mid-scan: everything held is still released.
        Tree failing = new Tree();
        failing.failAfter = 3;
        try {
            LabelScan.sample(failing, failing.obtain(card("Card", image(null), image(null), image(null), image(null))));
            throw new AssertionError("stale node error propagates");
        } catch (IllegalStateException expected) {
            check(failing.held.isEmpty(), "nodes are released when a node goes stale");
        }

        // Random trees. Without parent descriptions the scan equals the old leaf-only scan,
        // budget cuts and partial flags included; small trees match a direct recursive rule.
        java.util.Random random = new java.util.Random(515);
        for (int round = 0; round < 400; round++) {
            Node tree = randomTree(random, 1 + (round % 4 == 0 ? 500 + random.nextInt(2500) : random.nextInt(60)), false);
            VisibleTextSample actual = scan(tree), old = leafOnly(tree);
            check(actual.text().equals(old.text()) && actual.isPartial() == old.isPartial(),
                    "without parent descriptions the scan is the old leaf-only scan");
        }
        for (int round = 0; round < 2000; round++) {
            Node tree = randomTree(random, 1 + random.nextInt(60), true);
            VisibleTextSample actual = scan(tree);
            check(!actual.isPartial() && actual.text().equals(reference(tree)), "small trees follow the direct rule");
        }
        System.out.println("LabelScan tests passed (leaf labels, card descriptions, input fields, budget, release"
                + " and 2,400 random trees)");
    }

    /** A random tree of about {@code size} nodes: texts, images, cards, hidden nodes and input fields. */
    static Node randomTree(java.util.Random random, int size, boolean descriptions) {
        // Children go under the root, containers (cards or plain layouts) and input fields only.
        List<Node> parents = new ArrayList<>();
        Node root = card(descriptions && random.nextInt(4) == 0 ? "root" : null);
        parents.add(root);
        for (int at = 1; at < size; at++) {
            int kind = random.nextInt(10);
            String label = "n" + at;
            boolean visible = random.nextInt(8) != 0;
            Node node = kind < 3 ? new Node(label, null, false, visible)
                    : kind < 5 ? new Node(random.nextBoolean() ? "" : null, random.nextBoolean() ? label : null, false, visible)
                    : kind < 6 ? new Node(random.nextBoolean() ? " " : null, null, false, visible)
                    : kind < 7 ? new Node(label, random.nextBoolean() ? label : null, true, visible)
                    : new Node(null, descriptions && random.nextBoolean() ? label : null, false, visible);
            parents.get(random.nextInt(parents.size())).children.add(node);
            if (kind >= 6) parents.add(node);
        }
        return root;
    }

    /** The scan before parent descriptions: childless labels only, the same 512-node budget. */
    static VisibleTextSample leafOnly(Node root) {
        java.util.ArrayDeque<Node> queue = new java.util.ArrayDeque<>();
        queue.add(root);
        VisibleTextSample text = new VisibleTextSample(true);
        int visited = 0, scheduled = 1;
        while (!queue.isEmpty() && visited++ < 512 && !text.isFull()) {
            Node node = queue.removeFirst();
            if (node.secret || !node.visible) continue;
            if (!node.children.isEmpty()) {
                for (Node child : node.children) {
                    if (scheduled >= 512) { text.markPartial(); break; }
                    scheduled++;
                    if (child != null) queue.addLast(child); else text.markPartial();
                }
            } else text.append(node.text == null || node.text.isEmpty() ? node.description : node.text);
        }
        if (!queue.isEmpty()) text.markPartial();
        return text;
    }

    /** Direct rule for a fully inspected tree: leaf labels in breadth-first order, then each parent
     * whose visible subtree has no text, no input field and no read inner parent, in the same order. */
    static String reference(Node root) {
        Map<Node, Boolean> read = new IdentityHashMap<>();
        settle(root, read);
        StringBuilder leaves = new StringBuilder(), cards = new StringBuilder();
        java.util.ArrayDeque<Node> queue = new java.util.ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Node node = queue.removeFirst();
            if (node.secret || !node.visible) continue;
            if (node.children.isEmpty()) {
                String value = node.text == null || node.text.isEmpty() ? node.description : node.text;
                if (value != null && !value.isEmpty()) leaves.append(value).append('\n');
            } else {
                if (Boolean.TRUE.equals(read.get(node))) cards.append(node.description).append('\n');
                queue.addAll(node.children);
            }
        }
        return leaves.toString() + cards;
    }

    /** 0: nothing below; 1: text or a read parent below; 2: an input field below (rules out every parent). */
    static int settle(Node node, Map<Node, Boolean> read) {
        if (node.secret) return 2;
        if (!node.visible) return node.children.isEmpty() ? 0 : 2; // May hide an input field.
        if (node.children.isEmpty()) {
            String value = node.text == null || node.text.isEmpty() ? node.description : node.text;
            return value != null && !value.trim().isEmpty() ? 1 : 0;
        }
        int below = 0;
        for (Node child : node.children) below = Math.max(below, settle(child, read));
        boolean own = node.description != null && !node.description.trim().isEmpty() && below == 0;
        read.put(node, own);
        return own ? 1 : below;
    }
}
