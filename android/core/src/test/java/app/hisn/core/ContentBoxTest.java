package app.hisn.core;

public final class ContentBoxTest {
    private static void check(boolean value, String label) { if (!value) throw new AssertionError("Content box: " + label); }

    private static int[] frame(int width, int height, int background) {
        int[] pixels = new int[width * height];
        java.util.Arrays.fill(pixels, background);
        return pixels;
    }

    private static void paint(int[] pixels, int width, int left, int top, int right, int bottom, boolean busy) {
        for (int y = top; y < bottom; y++) for (int x = left; x < right; x++) {
            pixels[y * width + x] = busy ? 0xFF000000 | ((x * 37 + y * 11) % 255) << 16 | ((x * 13) % 255) << 8 | ((y * 7) % 255)
                    : 0xFFC08040;
        }
    }

    public static void main(String[] args) {
        int w = 44, h = 96, black = 0xFF000000;
        int[] viewer = frame(w, h, black);
        paint(viewer, w, 0, 30, 44, 63, true);       // the photo across the width
        paint(viewer, w, 4, 3, 40, 6, false);        // a toolbar strip above it, apart from the photo
        int[] box = ContentBox.find(viewer, w, h);
        check(box != null && box[0] == 0 && box[1] == 30 && box[2] == 44 && box[3] == 63,
                "a photo on a black screen is cut to the photo, leaving out the toolbar: " + java.util.Arrays.toString(box));

        int[] full = frame(w, h, black);
        paint(full, w, 0, 0, 44, 96, true);
        check(ContentBox.find(full, w, h) == null, "a full-screen picture has nothing to cut");

        int[] empty = frame(w, h, 0xFFFFFFFF);
        check(ContentBox.find(empty, w, h) == null, "a blank screen has no picture");

        int[] tiny = frame(w, h, black);
        paint(tiny, w, 20, 40, 26, 46, true);
        check(ContentBox.find(tiny, w, h) == null, "a speck is too small to be a picture");

        int[] white = frame(w, h, 0xFFFAFAFA);
        paint(white, w, 6, 20, 38, 80, true);
        int[] whiteBox = ContentBox.find(white, w, h);
        check(whiteBox != null && whiteBox[0] == 6 && whiteBox[1] == 20 && whiteBox[2] == 38 && whiteBox[3] == 80,
                "a light background works the same: " + java.util.Arrays.toString(whiteBox));

        check(ContentBox.find(null, w, h) == null && ContentBox.find(new int[4], 2, 2) == null, "malformed input");
        System.out.println("ContentBox tests passed (photo on black, toolbar left out, full frame, blank, speck, light)");
    }
}
