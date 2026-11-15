package wintervillage.board;

/**
 * Pure geometry for a 40-tile Monopoly board laid out as a square ring.
 * <p>
 * All coordinates live in a fixed "content" space of {@link #CONTENT} by
 * {@link #CONTENT} logical units, centred around the board ring. This decouples
 * the layout from the final on-screen size: the 3D board model scales this
 * space to fit the diorama, so the board stays crisp at any resolution.
 * <p>
 * Tile index ordering follows the classic board, moving counter-clockwise from
 * GO (bottom-right corner): 0=GO, 1..9 bottom row (right to left), 10=Jail,
 * 11..19 left column (bottom to top), 20=Free Parking, 21..29 top row (left to
 * right), 30=Go To Jail, 31..39 right column (top to bottom).
 */
public final class BoardGeometry {

    private BoardGeometry() {
    }

    /** Gap between the content edge and the outer edge of the board ring. */
    public static final double MARGIN = 20.0;

    /** The board ring's outer square size (a 900x900 square). */
    public static final double BOARD = 900.0;

    /** Side length of each corner tile. */
    public static final double CORNER = 150.0;

    /** Total logical content size (board + margin on both sides). */
    public static final double CONTENT = MARGIN * 2 + BOARD; // 940

    /** Number of tiles around the ring. */
    public static final int TILES = 40;

    /** Length of a non-corner tile along its edge. */
    public static final double EDGE = (BOARD - 2 * CORNER) / 9.0; // ~66.67

    /** An axis-aligned rectangle in content space. */
    public record Rect(double x, double y, double w, double h) {
        public double cx() {
            return x + w / 2;
        }

        public double cy() {
            return y + h / 2;
        }
    }

    /** Which edge of the ring a tile sits on. */
    public enum Side { BOTTOM, LEFT, TOP, RIGHT, CORNER_BR, CORNER_BL, CORNER_TL, CORNER_TR }

    /** Normalise any integer to a valid tile index in [0, 40). */
    public static int norm(int i) {
        int n = i % TILES;
        return n < 0 ? n + TILES : n;
    }

    /** The rectangle occupied by tile {@code i} in content space. */
    public static Rect rect(int i) {
        int n = norm(i);
        switch (n) {
            case 0:
                return new Rect(MARGIN + BOARD - CORNER, MARGIN + BOARD - CORNER, CORNER, CORNER);
            case 10:
                return new Rect(MARGIN, MARGIN + BOARD - CORNER, CORNER, CORNER);
            case 20:
                return new Rect(MARGIN, MARGIN, CORNER, CORNER);
            case 30:
                return new Rect(MARGIN + BOARD - CORNER, MARGIN, CORNER, CORNER);
            default:
                break;
        }

        if (n >= 1 && n <= 9) {           // bottom row, right -> left
            int k = n;
            return new Rect(MARGIN + BOARD - CORNER - k * EDGE,
                    MARGIN + BOARD - CORNER, EDGE, CORNER);
        }
        if (n >= 11 && n <= 19) {         // left column, bottom -> top
            int k = n - 10;
            return new Rect(MARGIN,
                    MARGIN + BOARD - CORNER - k * EDGE, CORNER, EDGE);
        }
        if (n >= 21 && n <= 29) {         // top row, left -> right
            int k = n - 20;
            return new Rect(MARGIN + CORNER + (k - 1) * EDGE,
                    MARGIN, EDGE, CORNER);
        }
        // right column, top -> bottom (31..39)
        int k = n - 30;
        return new Rect(MARGIN + BOARD - CORNER,
                MARGIN + CORNER + (k - 1) * EDGE, CORNER, EDGE);
    }

    /** The edge of the ring a tile sits on. */
    public static Side side(int i) {
        int n = norm(i);
        switch (n) {
            case 0:
                return Side.CORNER_BR;
            case 10:
                return Side.CORNER_BL;
            case 20:
                return Side.CORNER_TL;
            case 30:
                return Side.CORNER_TR;
            default:
                break;
        }
        if (n <= 9) {
            return Side.BOTTOM;
        }
        if (n <= 19) {
            return Side.LEFT;
        }
        if (n <= 29) {
            return Side.TOP;
        }
        return Side.RIGHT;
    }

    /** X of the centre of tile {@code i}. */
    public static double centerX(int i) {
        return rect(i).cx();
    }

    /** Y of the centre of tile {@code i}. */
    public static double centerY(int i) {
        return rect(i).cy();
    }
}
