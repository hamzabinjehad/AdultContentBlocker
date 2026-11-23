package app.hisn.core;

public final class PeopleRegionsTest {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static boolean near(float a, float b) { return Math.abs(a - b) < 1e-5f; }

    public static void main(String[] args) {
        float[] pad = PeopleRegions.padded(0.4f, 0.2f, 0.2f, 0.2f);
        check(near(pad[0], 0.35f) && near(pad[2], 0.3f), "a face gets a quarter of its width on each side");
        check(near(pad[1], 0.11f), "the top margin is larger, for hair above the detected box");
        check(near(pad[3], 0.34f), "height adds the top and bottom margins");
        check(PeopleRegions.padded(0f, 0f, 0.1f, 0.1f)[0] < 0f, "a patch may run off screen; it is not clamped");

        int[] torso = PeopleRegions.torso(0.4f, 0.1f, 0.1f, 0.1f, 200, 400);
        check(torso != null && torso[0] == 60 && torso[1] == 80 && torso[2] == 60 && torso[3] == 160,
                "the torso is three faces wide, from the chin to four faces down");
        check(PeopleRegions.torso(0.4f, 0.95f, 0.1f, 0.05f, 200, 400) == null,
                "a face at the bottom edge leaves too little below it to judge");
        int[] clamped = PeopleRegions.torso(0f, 0.5f, 0.3f, 0.2f, 100, 100);
        check(clamped != null && clamped[0] == 0 && clamped[1] + clamped[3] == 100, "the torso is clamped to the frame");

        float[] body = PeopleRegions.body(0.45f, 0.1f, 0.1f, 0.1f);
        check(body[1] < 0.1f && body[1] + body[3] > 0.8f, "the whole person reaches from above the head to well below");
        check(body[0] >= 0f && body[0] + body[2] <= 1f && body[1] + body[3] <= 1f, "the whole person stays in the frame");

        check(!PeopleRegions.wholePerson(new float[]{0.1f, 0.0f, 0.85f, 0.02f, 0.03f}), "ordinary clothes blur only the face");
        check(PeopleRegions.wholePerson(new float[]{0f, 0f, 0.4f, 0f, 0.6f}), "revealing clothes blur the whole person");
        check(PeopleRegions.wholePerson(new float[]{0f, 0f, 0.35f, 0.2f, 0.45f}), "revealing plus explicit together is enough");
        check(PeopleRegions.wholePerson(new float[]{0f, 0f, 0.2f, 0.8f, 0f}), "an explicit-looking area blurs the person");
        check(!PeopleRegions.wholePerson(null) && !PeopleRegions.wholePerson(new float[3]), "missing scores never widen the blur");

        check(!PeopleRegions.knownScroll(-1, -1), "-1 in both directions means Android gave no distance");
        check(PeopleRegions.knownScroll(0, -1) && PeopleRegions.knownScroll(0, 120), "a real step is known");
        check(!PeopleRegions.knownScroll(0, 9000), "an absurd step is treated as unknown");

        check(near(PeopleRegions.stretch(40, 1000f), 60f), "a step stretches one and a half times its size");
        check(near(PeopleRegions.stretch(-400, 150f), 150f), "the stretch is capped and ignores direction");
        System.out.println("PeopleRegionsTest passed");
    }
}
