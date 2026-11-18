package app.hisn.core;

/**
 * The picture inside a screen that shows it on a plain background: a photo viewer's black
 * bars, a video's letterbox, a chat's dark backdrop. The image model sees its whole input
 * squeezed to a square, so a photo in the middle of a tall black screen reaches it as a thin
 * strip, and the undistorted cross-check sees it smaller still. Cutting to the picture first
 * lets both views see the photo itself.
 *
 * <p>Works on a small sample of the frame (a 96-pixel long edge is plenty). The background is
 * the colour of the four corners; a row or column is background when at least 97% of its
 * pixels are within a small distance of it. The picture is the longest run of non-background
 * rows, then the longest run of non-background columns inside it, so a toolbar or caption
 * strip separated from the photo by background is left out. Null when there is nothing to
 * cut, or when what would remain is too small to be a picture.</p>
 */
public final class ContentBox {
    private static final int TOLERANCE = 24;
    private static final double BACKGROUND_SHARE = 0.97;

    private ContentBox() { }

    /** {left, top, right, bottom} in sample pixels, or null. */
    public static int[] find(int[] argb, int width, int height) {
        if (argb == null || width < 8 || height < 8 || argb.length < width * height) return null;
        int[] corners = {argb[0], argb[width - 1], argb[(height - 1) * width], argb[height * width - 1]};
        int r = median(corners, 16), g = median(corners, 8), b = median(corners, 0);
        boolean[] near = new boolean[width * height];
        for (int i = 0; i < width * height; i++) {
            int p = argb[i];
            near[i] = Math.abs(((p >> 16) & 255) - r) + Math.abs(((p >> 8) & 255) - g) + Math.abs((p & 255) - b) <= TOLERANCE;
        }
        boolean[] backgroundRow = new boolean[height];
        for (int y = 0; y < height; y++) {
            int count = 0;
            for (int x = 0; x < width; x++) if (near[y * width + x]) count++;
            backgroundRow[y] = count >= BACKGROUND_SHARE * width;
        }
        int[] rows = longestRun(backgroundRow);
        if (rows == null) return null;
        boolean[] backgroundColumn = new boolean[width];
        int span = rows[1] - rows[0];
        for (int x = 0; x < width; x++) {
            int count = 0;
            for (int y = rows[0]; y < rows[1]; y++) if (near[y * width + x]) count++;
            backgroundColumn[x] = count >= BACKGROUND_SHARE * span;
        }
        int[] columns = longestRun(backgroundColumn);
        if (columns == null) return null;
        int boxWidth = columns[1] - columns[0], boxHeight = rows[1] - rows[0];
        if (boxWidth < 8 || boxHeight < 8 || (long) boxWidth * boxHeight < 0.08 * width * height) return null;
        if (boxHeight >= height - 2 && boxWidth >= width - 2) return null;  // Nothing to cut.
        return new int[] {columns[0], rows[0], columns[1], rows[1]};
    }

    /** The longest run of false entries, as {start, end}, or null when there is none. */
    private static int[] longestRun(boolean[] background) {
        int bestStart = -1, bestEnd = -1, start = -1;
        for (int i = 0; i <= background.length; i++) {
            boolean edge = i == background.length || background[i];
            if (!edge && start < 0) start = i;
            if (edge && start >= 0) {
                if (i - start > bestEnd - bestStart) { bestStart = start; bestEnd = i; }
                start = -1;
            }
        }
        return bestStart < 0 ? null : new int[] {bestStart, bestEnd};
    }

    private static int median(int[] pixels, int shift) {
        int[] values = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) values[i] = (pixels[i] >> shift) & 255;
        java.util.Arrays.sort(values);
        return (values[1] + values[2]) / 2;
    }
}
