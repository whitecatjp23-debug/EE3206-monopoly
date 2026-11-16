package wintervillage.board;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.MeshView;
import javafx.scene.transform.Scale;

/**
 * Builds a compact, stand-alone Monopoly board model for the winter village.
 *
 * <p>Only the playable board is built: the 940-unit plinth, the green field,
 * the 40 printed tiles and the centre MONOPOLY banner. The parent project's
 * city ring, decks, dice, tokens and 8000-unit ground are intentionally not
 * included, so this board can sit on the frozen pond without swallowing the
 * diorama. Geometry is recycled from the parent board's design dimensions and
 * tile artwork, then uniformly scaled to the requested outer width.</p>
 */
public final class MonopolyBoardModel {

    /** Parent board design width: plinth 940, tile ring 900, field 800. */
    public static final double DESIGN_WIDTH = 940.0;

    /** Top of the printed tile faces in board design units. */
    public static final double TILE_TOP_DESIGN = 28.7;

    /** Top of the green centre field in board design units. */
    public static final double FIELD_TOP_DESIGN = 26.0;

    /** World Y of the printed tile surface for a board placed at {@code boardY}. */
    public static double tileTopY(double outerWidth, double boardY) {
        return boardY + TILE_TOP_DESIGN * (outerWidth / DESIGN_WIDTH);
    }

    /** World Y of the green centre field for a board placed at {@code boardY}. */
    public static double fieldTopY(double outerWidth, double boardY) {
        return boardY + FIELD_TOP_DESIGN * (outerWidth / DESIGN_WIDTH);
    }

    private static final double WORLD_CENTER = 470.0;
    private static final double FIELD_TOP = 26.0;
    private static final double TILE_TOP = 28.2;
    private static final double TILE_H = 5.2;

    private static final Color PLINTH_COLOR = Color.web("#F2E6C8");
    private static final Color PLINTH_SPECULAR = Color.web("#BDA982");
    private static final Color FIELD_COLOR = Color.web("#7DB868");
    private static final Color FIELD_SPECULAR = Color.web("#527F48");
    private static final Color TILE_SIDE_COLOR = Color.web("#DCCFAE");
    private static final Color TILE_SIDE_SPECULAR = Color.web("#B6A88C");

    private MonopolyBoardModel() {
    }

    /**
     * Build a board whose plinth outer width is {@code outerWidth} metres,
     * resting on {@code y} and centred at {@code x}/{@code z}.
     */
    public static Group create(double outerWidth, double x, double y, double z) {
        Group board = new Group();
        board.setId("MonopolyBoard");

        PhongMaterial plinthMaterial = material(PLINTH_COLOR, PLINTH_SPECULAR, 6);
        PhongMaterial fieldMaterial = material(FIELD_COLOR, FIELD_SPECULAR, 6);
        PhongMaterial tileSideMaterial = material(TILE_SIDE_COLOR, TILE_SIDE_SPECULAR, 4);

        // The plinth is the visible board slab; its bottom is local Y = 0.
        Box plinth = new Box(DESIGN_WIDTH, 24, DESIGN_WIDTH);
        plinth.setTranslateY(12);
        plinth.setMaterial(plinthMaterial);
        board.getChildren().add(plinth);

        Box field = new Box(800, 1.5, 800);
        field.setTranslateY(FIELD_TOP - 0.75);
        field.setMaterial(fieldMaterial);
        board.getChildren().add(field);

        for (TileDef def : TileDef.all()) {
            BoardGeometry.Rect rect = BoardGeometry.rect(def.index());
            double cx = BoardGeometry.centerX(def.index()) - WORLD_CENTER;
            double cz = BoardGeometry.centerY(def.index()) - WORLD_CENTER;

            Group tile = new Group();
            Box body = new Box(rect.w(), TILE_H, rect.h());
            body.setTranslateY(TILE_TOP - TILE_H / 2);
            body.setMaterial(tileSideMaterial);
            tile.getChildren().add(body);

            MeshView top = BoardArt.texturedPlane(rect.w(), rect.h(), BoardArt.tileTop(def));
            // Keep the printed face clear of the box top to avoid z-fighting.
            top.setTranslateY(TILE_TOP + 0.5);
            tile.getChildren().add(top);

            tile.setTranslateX(cx);
            tile.setTranslateZ(cz);
            board.getChildren().add(tile);
        }

        // Centre mat: tan 3D border plus the red MONOPOLY banner.
        Box bannerBase = new Box(574, 3, 120);
        bannerBase.setTranslateY(FIELD_TOP + 1.5);
        bannerBase.setMaterial(material(BoardArt.BANNER_TAN, BoardArt.BANNER_TAN.darker(), 2));
        board.getChildren().add(bannerBase);

        MeshView banner = BoardArt.texturedPlane(560, 110, BoardArt.banner());
        banner.setTranslateY(FIELD_TOP + 3.8);
        board.getChildren().add(banner);

        // Uniform scale around the board's bottom centre.
        double scale = outerWidth / DESIGN_WIDTH;
        Group scaled = new Group(board);
        scaled.getTransforms().add(new Scale(scale, scale, scale));

        Group placed = new Group(scaled);
        placed.setId("MonopolyBoardPlacement");
        placed.setTranslateX(x);
        placed.setTranslateY(y);
        placed.setTranslateZ(z);
        return placed;
    }

    private static PhongMaterial material(Color diffuse, Color specular, double power) {
        PhongMaterial material = new PhongMaterial(diffuse);
        material.setSpecularColor(specular);
        material.setSpecularPower(power);
        return material;
    }
}
