package wintervillage.board;

import javafx.geometry.VPos;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.paint.Stop;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;


/**
 * Canvas-painted textures used by the 3D board: tile faces, the MONOPOLY mat,
 * card faces and building facades. Keeping the artwork in textures means all
 * Chinese labels stay crisp even when the camera orbits close to the board.
 */
public final class BoardArt {

    static final double PX = 4.0; // texture pixels per world unit

    static final double CARD_W = 120;
    static final double CARD_H = 168;

    static final Color TILE_FACE = Color.web("#FBF7EA");
    static final Color TILE_BORDER = Color.web("#D6C8A6");
    static final Color TEXT_DARK = Color.web("#4A3A26");
    static final Color TEXT_SOFT = Color.web("#8A7650");
    static final Color BANNER_RED = Color.web("#D8443C");
    static final Color BANNER_TAN = Color.web("#E8CD93");
    public static final Color CARD_CREAM = Color.web("#FFFDF4");

    private BoardArt() {
    }

    // ------------------------------------------------------------------
    // Tile tops
    // ------------------------------------------------------------------

    static Image tileTop(TileDef def) {
        BoardGeometry.Rect r = BoardGeometry.rect(def.index());
        BoardGeometry.Side side = BoardGeometry.side(def.index());
        int w = (int) Math.round(r.w() * PX);
        int h = (int) Math.round(r.h() * PX);
        Canvas canvas = new Canvas(w, h);
        GraphicsContext g = canvas.getGraphicsContext2D();

        g.setFill(TILE_FACE);
        g.fillRect(0, 0, w, h);
        g.setStroke(TILE_BORDER);
        g.setLineWidth(6);
        g.strokeRect(3, 3, w - 6, h - 6);

        boolean corner = side == BoardGeometry.Side.CORNER_BR
                || side == BoardGeometry.Side.CORNER_BL
                || side == BoardGeometry.Side.CORNER_TL
                || side == BoardGeometry.Side.CORNER_TR;

        if (corner) {
            drawCornerTile(g, def, w, h);
        } else {
            if (def.type() == TileType.PROPERTY) {
                drawPropertyStrip(g, side, def.color(), w, h);
            } else {
                drawTileIcon(g, def, side, w, h);
            }
            drawTileLabel(g, def, side, w, h);
        }
        return snapshot(canvas);
    }

    private static void drawPropertyStrip(GraphicsContext g, BoardGeometry.Side side,
                                          Color color, int w, int h) {
        double strip = 16 * PX;
        double gap = 8;
        double sx;
        double sy;
        double sw;
        double sh;
        switch (side) {
            case TOP:
                sx = gap;
                sy = gap;
                sw = w - gap * 2;
                sh = strip;
                break;
            case BOTTOM:
                sx = gap;
                sy = h - gap - strip;
                sw = w - gap * 2;
                sh = strip;
                break;
            case LEFT:
                sx = gap;
                sy = gap;
                sw = strip;
                sh = h - gap * 2;
                break;
            default:
                sx = w - gap - strip;
                sy = gap;
                sw = strip;
                sh = h - gap * 2;
                break;
        }
        g.setFill(color);
        g.fillRect(sx, sy, sw, sh);
        g.setStroke(color.darker());
        g.setLineWidth(3);
        g.strokeRect(sx + 1.5, sy + 1.5, sw - 3, sh - 3);
    }

    private static void drawTileIcon(GraphicsContext g, TileDef def, BoardGeometry.Side side,
                                     int w, int h) {
        double cx;
        double cy;
        switch (side) {
            case LEFT:
                cx = w * 0.18;
                cy = h * 0.5;
                break;
            case RIGHT:
                cx = w * 0.82;
                cy = h * 0.5;
                break;
            case TOP:
                cx = w * 0.5;
                cy = h * 0.18;
                break;
            default:
                cx = w * 0.5;
                cy = h * 0.82;
                break;
        }
        Color accent = def.color();
        switch (def.type()) {
            case CHANCE:
                g.setFill(accent);
                g.setFont(Font.font("System", FontWeight.EXTRA_BOLD, 19 * PX));
                g.setTextAlign(TextAlignment.CENTER);
                g.setTextBaseline(VPos.CENTER);
                g.fillText("?", cx, cy);
                break;
            case COMMUNITY:
                drawChest(g, cx, cy, 26, accent);
                break;
            case TAX:
                g.setFill(accent.darker());
                g.setFont(Font.font("System", FontWeight.BOLD, 17 * PX));
                g.setTextAlign(TextAlignment.CENTER);
                g.setTextBaseline(VPos.CENTER);
                g.fillText("¥", cx, cy);
                break;
            case RAILROAD:
                drawTrain(g, cx, cy, accent);
                break;
            case UTILITY:
                g.setFill(accent.darker());
                g.setFont(Font.font("System", FontWeight.BOLD, 9 * PX));
                g.setTextAlign(TextAlignment.CENTER);
                g.setTextBaseline(VPos.CENTER);
                g.fillText(def.index() == 12 ? "电" : "水", cx, cy);
                break;
            case GO:
                drawGo(g, cx, cy);
                break;
            case JAIL:
                drawBars(g, cx, cy, accent);
                break;
            case FREE_PARKING:
                drawParking(g, cx, cy, accent);
                break;
            case GO_TO_JAIL:
                drawPolice(g, cx, cy, accent);
                break;
            default:
                break;
        }
    }

    private static void drawTileLabel(GraphicsContext g, TileDef def, BoardGeometry.Side side,
                                      int w, int h) {
        boolean vertical = side == BoardGeometry.Side.TOP || side == BoardGeometry.Side.BOTTOM;
        double nameSize = 11.5 * PX;
        double priceSize = 7 * PX;
        Font nameFont = Font.font("System", FontWeight.BOLD, nameSize);
        Font priceFont = Font.font("System", FontWeight.BOLD, priceSize);

        g.setFill(TEXT_DARK);
        g.setFont(nameFont);
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);

        if (vertical) {
            g.save();
            g.translate(w / 2.0, h / 2.0);
            g.rotate(-90);
            g.fillText(def.name(), 0, -20);
            if (def.price() > 0) {
                g.setFill(TEXT_SOFT);
                g.setFont(priceFont);
                g.fillText("¥" + def.price(), 0, 48);
            }
            g.restore();
        } else {
            g.fillText(def.name(), w / 2.0, h * 0.43);
            if (def.price() > 0) {
                g.setFill(TEXT_SOFT);
                g.setFont(priceFont);
                g.fillText("¥" + def.price(), w / 2.0, h * 0.66);
            }
        }
    }

    private static void drawCornerTile(GraphicsContext g, TileDef def, int w, int h) {
        Color accent = def.color();
        double cx = w / 2.0;
        double cy = h * 0.34;

        g.setFill(accent.deriveColor(0, 1, 1, 0.20));
        g.fillOval(cx - w * 0.16, cy - w * 0.16, w * 0.32, w * 0.32);
        g.setStroke(accent);
        g.setLineWidth(4);
        g.strokeOval(cx - w * 0.16, cy - w * 0.16, w * 0.32, w * 0.32);

        switch (def.type()) {
            case GO:
                g.setFill(accent.darker());
                g.setFont(Font.font("System", FontWeight.EXTRA_BOLD, 9 * PX));
                g.setTextAlign(TextAlignment.CENTER);
                g.setTextBaseline(VPos.CENTER);
                g.fillText("GO", cx, cy);
                break;
            case JAIL:
                drawBars(g, cx, cy, accent);
                break;
            case FREE_PARKING:
                drawParking(g, cx, cy, accent);
                break;
            case GO_TO_JAIL:
                drawPolice(g, cx, cy, accent);
                break;
            default:
                break;
        }

        g.setFill(TEXT_DARK);
        g.setFont(Font.font("System", FontWeight.BOLD, 8.5 * PX));
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        g.fillText(def.name(), cx, h * 0.72);
    }

    private static void drawChest(GraphicsContext g, double cx, double cy, double s, Color accent) {
        g.setFill(accent);
        g.fillRoundRect(cx - s, cy - s * 0.45, s * 2, s * 1.1, 8, 8);
        g.setFill(accent.brighter());
        g.fillRoundRect(cx - s, cy - s * 0.72, s * 2, s * 0.55, 7, 7);
        g.setFill(accent.darker());
        g.fillRect(cx - s * 0.16, cy - s * 0.2, s * 0.32, s * 0.55);
        g.setStroke(accent.darker());
        g.setLineWidth(2.5);
        g.strokeLine(cx - s, cy - s * 0.1, cx + s, cy - s * 0.1);
    }

    private static void drawTrain(GraphicsContext g, double cx, double cy, Color accent) {
        double s = 20;
        g.setFill(accent);
        g.fillRoundRect(cx - s, cy - s * 0.2, s * 2, s * 0.7, 6, 6);
        g.setFill(accent.brighter());
        g.fillRoundRect(cx + s * 0.2, cy - s * 0.8, s * 0.8, s * 0.65, 5, 5);
        g.setFill(accent.darker());
        g.fillRect(cx - s * 0.7, cy - s * 0.6, s * 0.35, s * 0.45);
        g.setFill(Color.web("#2B2B2B"));
        g.fillOval(cx - s * 0.55, cy + s * 0.35, s * 0.38, s * 0.38);
        g.fillOval(cx + s * 0.2, cy + s * 0.35, s * 0.38, s * 0.38);
    }

    private static void drawGo(GraphicsContext g, double cx, double cy) {
        g.setFill(Color.web("#2E9E5B"));
        double[] xs = {cx - 22, cx + 2, cx + 2, cx + 22, cx + 2, cx + 2, cx - 22};
        double[] ys = {cy - 8, cy - 8, cy - 16, cy, cy + 16, cy + 8, cy + 8};
        g.fillPolygon(xs, ys, xs.length);
    }

    private static void drawBars(GraphicsContext g, double cx, double cy, Color accent) {
        double w = 42;
        double h = 54;
        g.setStroke(accent.darker());
        g.setLineWidth(3);
        g.strokeRect(cx - w / 2, cy - h / 2, w, h);
        for (int i = 0; i < 4; i++) {
            g.strokeLine(cx - w / 2 + 7 + i * 10, cy - h / 2, cx - w / 2 + 7 + i * 10, cy + h / 2);
        }
    }

    private static void drawParking(GraphicsContext g, double cx, double cy, Color accent) {
        g.setStroke(accent.darker());
        g.setLineWidth(4);
        g.strokeOval(cx - 30, cy - 30, 60, 60);
        g.setFill(accent.darker());
        g.setFont(Font.font("System", FontWeight.EXTRA_BOLD, 38));
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        g.fillText("P", cx, cy);
    }

    private static void drawPolice(GraphicsContext g, double cx, double cy, Color accent) {
        g.setFill(accent);
        g.fillRoundRect(cx - 32, cy - 16, 64, 22, 8, 8);
        g.setFill(accent.brighter());
        g.fillArc(cx - 26, cy - 42, 52, 46, 0, 180, javafx.scene.shape.ArcType.ROUND);
        g.setFill(Color.web("#E0343E"));
        g.fillOval(cx - 7, cy - 34, 14, 14);
    }

    // ------------------------------------------------------------------
    // Sky / banner / cards
    // ------------------------------------------------------------------

    /**
     * Full-view sky backdrop. JavaFX 3D occasionally fails to clear the
     * previous frame on some GPU/driver combinations; an opaque dome that
     * covers the entire viewport guarantees every pixel is repainted each
     * frame, which eliminates the "ghosting" trails.
     */
    static Image skyTexture() {
        Canvas canvas = new Canvas(512, 512);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0.00, Color.web("#9FBBD3")),
                new Stop(0.18, Color.web("#C9DBE7")),
                new Stop(0.30, Color.web("#DCE4D5")),
                new Stop(0.45, Color.web("#CBD4BB")),
                new Stop(0.62, Color.web("#B7C1A9")),
                new Stop(1.00, Color.web("#A9B59C"))));
        g.fillRect(0, 0, 512, 512);
        // a few soft horizon streaks make the transition less flat
        g.setFill(Color.web("#FFFFFF", 0.10));
        for (int i = 0; i < 6; i++) {
            double y = 0.42 + i * 0.022;
            g.fillRect(0, 512 * y, 512, 2.0);
        }
        return snapshot(canvas);
    }

    static Image banner() {
        double w = 560;
        double h = 110;
        Canvas canvas = new Canvas((int) (w * PX), (int) (h * PX));
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(BANNER_RED);
        g.fillRoundRect(0, 0, canvas.getWidth(), canvas.getHeight(), 18 * PX, 18 * PX);
        g.setStroke(BANNER_TAN);
        g.setLineWidth(7 * PX);
        g.strokeRoundRect(3 * PX, 3 * PX, canvas.getWidth() - 6 * PX, canvas.getHeight() - 6 * PX,
                15 * PX, 15 * PX);
        g.setStroke(Color.web("#FFFFFF", 0.9));
        g.setLineWidth(2.5 * PX);
        g.strokeRoundRect(12 * PX, 12 * PX, canvas.getWidth() - 24 * PX, canvas.getHeight() - 24 * PX,
                11 * PX, 11 * PX);
        g.setFill(Color.WHITE);
        g.setFont(Font.font("System", FontWeight.EXTRA_BOLD, 52 * PX));
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        g.fillText("MONOPOLY", canvas.getWidth() / 2, canvas.getHeight() / 2 + 2 * PX);
        return snapshot(canvas);
    }

    public static Image cardTop(String deckLabel, Color accent) {
        return cardTexture(deckLabel, deckLabel + "卡", "点击抽取", accent);
    }

    public static Image cardFace(String deckLabel, String title, String description, Color accent) {
        return cardTexture(deckLabel, title, description, accent);
    }

    private static Image cardTexture(String deckLabel, String title, String description, Color accent) {
        Canvas canvas = new Canvas((int) (CARD_W * PX), (int) (CARD_H * PX));
        GraphicsContext g = canvas.getGraphicsContext2D();
        int W = (int) canvas.getWidth();
        int H = (int) canvas.getHeight();

        g.setFill(CARD_CREAM);
        g.fillRoundRect(0, 0, W, H, 10 * PX, 10 * PX);
        g.setStroke(Color.web("#B8A97E"));
        g.setLineWidth(1.5 * PX);
        g.strokeRoundRect(1.5 * PX, 1.5 * PX, W - 3 * PX, H - 3 * PX, 9 * PX, 9 * PX);

        double bandH = 34 * PX;
        g.setFill(new LinearGradient(0, 0, 0, bandH, false, CycleMethod.NO_CYCLE,
                new Stop(0, accent.brighter()), new Stop(1, accent.darker())));
        g.fillRoundRect(0, 0, W, bandH, 10 * PX, 10 * PX);
        g.fillRect(0, bandH - 10 * PX, W, 10 * PX);

        g.setFill(Color.WHITE);
        g.setFont(Font.font("System", FontWeight.BOLD, 13.5 * PX));
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        g.fillText(deckLabel, W / 2.0, bandH / 2.0);

        g.setFill(TEXT_DARK);
        g.setFont(Font.font("System", FontWeight.BOLD, 14 * PX));
        drawWrapped(g, title, W / 2.0, 64 * PX, W - 22 * PX, 17 * PX, 2);

        g.setFill(Color.web("#6A5C3E"));
        g.setFont(Font.font("System", FontWeight.NORMAL, 10 * PX));
        drawWrapped(g, description, W / 2.0, 98 * PX, W - 22 * PX, 13 * PX, 3);

        g.setFill(accent.deriveColor(0, 1, 1, 0.55));
        g.fillRoundRect(16 * PX, H - 14 * PX, W - 32 * PX, 2.5 * PX, 2.5 * PX, 2.5 * PX);
        return snapshot(canvas);
    }

    // ------------------------------------------------------------------
    // Buildings
    // ------------------------------------------------------------------

    static Image facade(Color wall, int variant) {
        Canvas canvas = new Canvas(256, 256);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(wall);
        g.fillRect(0, 0, 256, 256);
        g.setFill(wall.darker());
        g.fillRect(0, 0, 6, 256);
        g.fillRect(250, 0, 6, 256);
        g.fillRect(0, 0, 256, 6);

        int rows = 3;
        int cols = 2;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                double x = 40 + c * 100;
                double y = 34 + r * 66;
                Color glass = ((r * cols + c + variant) % 3 == 0)
                        ? Color.web("#FFE9A8")
                        : Color.web("#A9D6EA");
                g.setFill(glass);
                g.fillRoundRect(x, y, 66, 40, 7, 7);
                g.setStroke(wall.darker().darker());
                g.setLineWidth(2);
                g.strokeRoundRect(x, y, 66, 40, 7, 7);
                g.setStroke(Color.web("#FFFFFF", 0.40));
                g.setLineWidth(1.5);
                g.strokeLine(x + 5, y + 5, x + 30, y + 5);
            }
        }
        // door
        g.setFill(wall.darker().darker());
        g.fillRoundRect(103, 208, 50, 48, 5, 5);
        return snapshot(canvas);
    }

    // ------------------------------------------------------------------
    // Textured plane helper
    // ------------------------------------------------------------------

    /** A horizontal (XZ) textured quad at y=0, front face up. */
    public static MeshView texturedPlane(double width, double depth, Image image) {
        double hw = width / 2.0;
        double hd = depth / 2.0;

        TriangleMesh mesh = new TriangleMesh(VertexFormat.POINT_NORMAL_TEXCOORD);
        mesh.getPoints().addAll(
                (float) -hw, 0, (float) -hd,
                (float) hw, 0, (float) -hd,
                (float) hw, 0, (float) hd,
                (float) -hw, 0, (float) hd);
        mesh.getNormals().addAll(
                0, 1, 0,
                0, 1, 0,
                0, 1, 0,
                0, 1, 0);
        mesh.getTexCoords().addAll(
                0, 0,
                1, 0,
                1, 1,
                0, 1);
        // counter-clockwise from above -> +Y normal
        mesh.getFaces().addAll(
                0, 0, 0, 2, 2, 2, 1, 1, 1,
                0, 0, 0, 3, 3, 3, 2, 2, 2);

        MeshView view = new MeshView(mesh);
        view.setCullFace(CullFace.BACK);
        PhongMaterial material = new PhongMaterial(Color.WHITE, image, null, null, null);
        // Specular highlights on large planar meshes cause black shader
        // artifacts on some 3D drivers; keep the printed surfaces matte.
        material.setSpecularColor(Color.TRANSPARENT);
        material.setSpecularPower(0);
        // The winter scene is lit for cool dusk; a faint self-illuminated copy
        // of the printed art keeps the tile colours and MONOPOLY banner
        // readable without making the board look like an unlit decal.
        material.setSelfIlluminationMap(glowMap(image, 0.34));
        view.setMaterial(material);
        return view;
    }

    /**
     * Low-resolution, dimmed copy of a texture used as a subtle self-
     * illumination map. Sampling every third pixel is enough for this glow
     * pass and avoids spending time processing every full-resolution texel.
     */
    public static Image glowMap(Image source, double factor) {
        int srcW = Math.max(1, (int) source.getWidth());
        int srcH = Math.max(1, (int) source.getHeight());
        int w = Math.max(1, (srcW + 2) / 3);
        int h = Math.max(1, (srcH + 2) / 3);
        WritableImage glow = new WritableImage(w, h);
        PixelReader reader = source.getPixelReader();
        PixelWriter writer = glow.getPixelWriter();
        for (int y = 0; y < h; y++) {
            int sy = Math.min(srcH - 1, y * 3);
            for (int x = 0; x < w; x++) {
                int sx = Math.min(srcW - 1, x * 3);
                Color c = reader.getColor(sx, sy);
                writer.setColor(x, y, new Color(
                        c.getRed() * factor,
                        c.getGreen() * factor,
                        c.getBlue() * factor,
                        c.getOpacity()));
            }
        }
        return glow;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Image snapshot(Canvas canvas) {
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        return canvas.snapshot(params, null);
    }

    private static void drawWrapped(GraphicsContext g, String text, double cx, double y,
                                    double maxWidth, double lineHeight, int maxLines) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Font font = g.getFont();
        StringBuilder line = new StringBuilder();
        int lines = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            String candidate = line.toString() + ch;
            if (measure(candidate, font) > maxWidth && line.length() > 0) {
                g.fillText(line.toString(), cx, y + lines * lineHeight);
                lines++;
                if (lines >= maxLines) {
                    return;
                }
                line.setLength(0);
            }
            line.append(ch);
        }
        if (line.length() > 0) {
            g.fillText(line.toString(), cx, y + lines * lineHeight);
        }
    }

    private static double measure(String s, Font font) {
        Text t = new Text(s);
        t.setFont(font);
        return t.getLayoutBounds().getWidth();
    }
}