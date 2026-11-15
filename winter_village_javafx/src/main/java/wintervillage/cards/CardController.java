package wintervillage.cards;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;
import wintervillage.OrbitCameraController;
import wintervillage.WinterVillageProfile;
import wintervillage.board.BoardArt;
import wintervillage.board.MonopolyBoardModel;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Two physical-looking card decks on the green field above the MONOPOLY
 * banner, with a lift/stand/hold/reverse draw animation.
 *
 * <p>The card is printed on both exposed sides and is already turned toward
 * the camera before the animation starts. It rises toward the board centre,
 * stands upright around its bottom edge, holds long enough to read, then
 * retraces the exact same path back to the deck. There is no 180-degree
 * reveal flip and no independent random rotation after the draw begins.</p>
 */
public final class CardController {

    public enum State { IDLE, DRAWING }

    public record DrawnCard(CardType type, CardDefinition card) {
    }

    private static final double CARD_W = 0.56;
    private static final double CARD_H = 0.784;
    private static final double CARD_THICKNESS = 0.012;

    private final Group layer = new Group();
    private final WinterVillageProfile profile;
    private final OrbitCameraController camera;
    private final Map<CardType, Integer> cursors = new EnumMap<>(CardType.class);
    private final Map<CardType, List<CardDefinition>> decks = new EnumMap<>(CardType.class);

    private final double fieldTop;
    private final double deckTop;
    private final double deckZ;

    private State state = State.IDLE;
    private Consumer<State> onStateChanged;
    private Consumer<DrawnCard> onCardDrawn;
    private Consumer<DrawnCard> onCardDismissed;
    private BooleanSupplier externalBusy = () -> false;
    private DrawnCard lastDrawn;

    public CardController(Group parent,
                          WinterVillageProfile profile,
                          OrbitCameraController camera) {
        this.profile = profile;
        this.camera = camera;
        layer.setId("CardLayer");
        parent.getChildren().add(layer);
        for (CardType type : CardType.values()) {
            decks.put(type, new ArrayList<>(type.cards()));
        }

        fieldTop = MonopolyBoardModel.fieldTopY(
                profile.boardWidth, profile.boardY) + 0.002;
        deckTop = fieldTop + 0.055;
        deckZ = profile.boardZ - profile.boardWidth * 0.24;

        double xOffset = profile.boardWidth * 0.105;
        buildDeck(CardType.ACTIVATION,
                profile.boardX - xOffset, deckZ);
        buildDeck(CardType.RESOURCE,
                profile.boardX + xOffset, deckZ);

        System.out.printf(Locale.ROOT,
                "[cards] two decks above MONOPOLY at z=%.3f, deckTopY=%.3f%n",
                deckZ, deckTop);
    }

    public Group getLayer() {
        return layer;
    }

    public State getState() {
        return state;
    }

    public boolean isBusy() {
        return state != State.IDLE;
    }

    public Point3D getFocusPoint() {
        return new Point3D(profile.boardX, fieldTop + 0.62, deckZ);
    }

    public DrawnCard getLastDrawn() {
        return lastDrawn;
    }

    public void setOnStateChanged(Consumer<State> listener) {
        this.onStateChanged = listener;
    }

    public void setOnCardDrawn(Consumer<DrawnCard> listener) {
        this.onCardDrawn = listener;
    }

    /** Fired after the card has returned to its deck and removed from the scene. */
    public void setOnCardDismissed(Consumer<DrawnCard> listener) {
        this.onCardDismissed = listener;
    }

    /**
     * Replace the card list for a family. Rules code can supply its own
     * CardDefinition instances; the next draw starts at the first card.
     */
    public void setDeck(CardType type, List<CardDefinition> cards) {
        if (type == null || cards == null || cards.isEmpty()) {
            throw new IllegalArgumentException("Card deck must not be empty");
        }
        decks.put(type, List.copyOf(cards));
        cursors.put(type, 0);
    }

    public List<CardDefinition> getDeck(CardType type) {
        List<CardDefinition> deck = decks.get(type);
        return deck == null ? List.of() : List.copyOf(deck);
    }

    /** Additional view-level guard, e.g. a running dice/movement turn. */
    public void setExternalBusy(BooleanSupplier supplier) {
        externalBusy = supplier == null ? () -> false : supplier;
    }

    /** Draw the next card from one deck; ignored while another draw is running. */
    public boolean draw(CardType type) {
        if (state != State.IDLE || externalBusy.getAsBoolean()) {
            return false;
        }
        List<CardDefinition> deck = decks.get(type);
        if (deck == null || deck.isEmpty()) {
            return false;
        }
        int index = cursors.merge(type, 1, Integer::sum) - 1;
        return drawInternal(type, deck.get(index % deck.size()));
    }

    /** Display a specific card without advancing the deck cursor (rules/testing). */
    public boolean draw(CardType type, CardDefinition card) {
        if (state != State.IDLE || externalBusy.getAsBoolean() || card == null) {
            return false;
        }
        return drawInternal(type, card);
    }

    private boolean drawInternal(CardType type, CardDefinition card) {
        lastDrawn = new DrawnCard(type, card);
        state = State.DRAWING;
        notifyState();

        double deckX = type == CardType.ACTIVATION
                ? profile.boardX - profile.boardWidth * 0.105
                : profile.boardX + profile.boardWidth * 0.105;
        double yaw = Math.toDegrees(Math.atan2(
                camera.cameraPosition().getX() - profile.boardX,
                camera.cameraPosition().getZ() - deckZ));

        Group cardModel = buildCard(type, card);
        // The card is always printed on both exposed sides and is already
        // turned toward the camera. There is no reveal flip; the stand-up
        // rotation is the whole pull-out motion.
        Rotate yawRotate = new Rotate(yaw, Rotate.Y_AXIS);
        Rotate lift = new Rotate(0, 0, 0, -CARD_H / 2.0, Rotate.X_AXIS);

        Group liftGroup = new Group(cardModel);
        liftGroup.getTransforms().add(lift);
        Group yawGroup = new Group(liftGroup);
        yawGroup.getTransforms().add(yawRotate);
        Group cardRoot = new Group(yawGroup);
        cardRoot.setTranslateX(deckX);
        cardRoot.setTranslateY(deckTop + 0.002);
        cardRoot.setTranslateZ(deckZ);
        layer.getChildren().add(cardRoot);

        Timeline timeline = new Timeline();
        // Outbound: deck -> stand above MONOPOLY.
        timeline.getKeyFrames().add(new KeyFrame(Duration.ZERO,
                new KeyValue(cardRoot.translateXProperty(), deckX),
                new KeyValue(cardRoot.translateYProperty(), deckTop + 0.002),
                new KeyValue(cardRoot.scaleXProperty(), 1.0),
                new KeyValue(cardRoot.scaleYProperty(), 1.0),
                new KeyValue(cardRoot.scaleZProperty(), 1.0),
                new KeyValue(lift.angleProperty(), 0)));

        timeline.getKeyFrames().add(new KeyFrame(Duration.millis(180),
                new KeyValue(cardRoot.translateXProperty(),
                        deckX * 0.82, Interpolator.EASE_BOTH),
                new KeyValue(cardRoot.translateYProperty(),
                        deckTop + 0.10, Interpolator.EASE_OUT),
                new KeyValue(cardRoot.scaleXProperty(), 1.05, Interpolator.EASE_BOTH),
                new KeyValue(cardRoot.scaleYProperty(), 1.05, Interpolator.EASE_BOTH),
                new KeyValue(cardRoot.scaleZProperty(), 1.05, Interpolator.EASE_BOTH)));

        timeline.getKeyFrames().add(new KeyFrame(Duration.millis(640),
                new KeyValue(cardRoot.translateXProperty(),
                        profile.boardX, Interpolator.EASE_BOTH),
                new KeyValue(cardRoot.translateYProperty(),
                        deckTop + 0.14, Interpolator.EASE_BOTH),
                new KeyValue(cardRoot.scaleXProperty(), 1.60, Interpolator.EASE_BOTH),
                new KeyValue(cardRoot.scaleYProperty(), 1.60, Interpolator.EASE_BOTH),
                new KeyValue(cardRoot.scaleZProperty(), 1.60, Interpolator.EASE_BOTH),
                new KeyValue(lift.angleProperty(), -90, Interpolator.EASE_BOTH)));

        timeline.getKeyFrames().add(new KeyFrame(Duration.millis(860),
                new KeyValue(cardRoot.translateYProperty(),
                        deckTop + 0.20, Interpolator.EASE_OUT),
                new KeyValue(cardRoot.scaleXProperty(), 1.65),
                new KeyValue(cardRoot.scaleYProperty(), 1.65),
                new KeyValue(cardRoot.scaleZProperty(), 1.65)));

        // Readable hold.
        timeline.getKeyFrames().add(new KeyFrame(Duration.millis(3000),
                new KeyValue(cardRoot.translateXProperty(), profile.boardX),
                new KeyValue(cardRoot.translateYProperty(), deckTop + 0.20),
                new KeyValue(cardRoot.scaleXProperty(), 1.65),
                new KeyValue(cardRoot.scaleYProperty(), 1.65),
                new KeyValue(cardRoot.scaleZProperty(), 1.65),
                new KeyValue(lift.angleProperty(), -90)));

        // Exact reverse path: D -> C -> B -> A.
        timeline.getKeyFrames().add(new KeyFrame(Duration.millis(3220),
                new KeyValue(cardRoot.translateXProperty(),
                        profile.boardX, Interpolator.EASE_BOTH),
                new KeyValue(cardRoot.translateYProperty(),
                        deckTop + 0.14, Interpolator.EASE_BOTH),
                new KeyValue(cardRoot.scaleXProperty(), 1.60, Interpolator.EASE_BOTH),
                new KeyValue(cardRoot.scaleYProperty(), 1.60, Interpolator.EASE_BOTH),
                new KeyValue(cardRoot.scaleZProperty(), 1.60, Interpolator.EASE_BOTH),
                new KeyValue(lift.angleProperty(), -90, Interpolator.EASE_BOTH)));

        timeline.getKeyFrames().add(new KeyFrame(Duration.millis(3680),
                new KeyValue(cardRoot.translateXProperty(),
                        deckX * 0.82, Interpolator.EASE_BOTH),
                new KeyValue(cardRoot.translateYProperty(),
                        deckTop + 0.10, Interpolator.EASE_BOTH),
                new KeyValue(cardRoot.scaleXProperty(), 1.05, Interpolator.EASE_BOTH),
                new KeyValue(cardRoot.scaleYProperty(), 1.05, Interpolator.EASE_BOTH),
                new KeyValue(cardRoot.scaleZProperty(), 1.05, Interpolator.EASE_BOTH),
                new KeyValue(lift.angleProperty(), 0, Interpolator.EASE_BOTH)));

        timeline.getKeyFrames().add(new KeyFrame(Duration.millis(3860),
                new KeyValue(cardRoot.translateXProperty(), deckX),
                new KeyValue(cardRoot.translateYProperty(), deckTop + 0.002),
                new KeyValue(cardRoot.scaleXProperty(), 1.0),
                new KeyValue(cardRoot.scaleYProperty(), 1.0),
                new KeyValue(cardRoot.scaleZProperty(), 1.0),
                new KeyValue(lift.angleProperty(), 0)));

        PauseTransition showResult = new PauseTransition(Duration.millis(700));
        showResult.setOnFinished(event -> {
            if (onCardDrawn != null) {
                onCardDrawn.accept(lastDrawn);
            }
        });
        showResult.play();

        timeline.setOnFinished(event -> {
            layer.getChildren().remove(cardRoot);
            state = State.IDLE;
            notifyState();
            if (onCardDismissed != null) {
                onCardDismissed.accept(lastDrawn);
            }
        });
        timeline.play();
        return true;
    }

    private void notifyState() {
        if (onStateChanged != null) {
            onStateChanged.accept(state);
        }
    }

    // ------------------------------------------------------------------
    // Geometry
    // ------------------------------------------------------------------

    private void buildDeck(CardType type, double x, double z) {
        Group deck = new Group();
        deck.setId("CardDeck_" + type.id());
        deck.setTranslateX(x);
        deck.setTranslateY(fieldTop);
        deck.setTranslateZ(z);

        double baseW = CARD_W + 0.035;
        double baseD = CARD_H + 0.035;
        Box base = new Box(baseW, 0.018, baseD);
        base.setTranslateY(0.009);
        base.setMaterial(mat(type.accent().darker().darker()));
        deck.getChildren().add(base);

        for (int i = 0; i < 3; i++) {
            Box stacked = new Box(CARD_W, 0.012, CARD_H);
            stacked.setTranslateY(0.024 + i * 0.012);
            stacked.setTranslateZ((i - 1) * 0.004);
            stacked.setMaterial(mat(i == 2
                    ? type.accent().darker()
                    : type.accent().darker().darker()));
            deck.getChildren().add(stacked);
        }

        MeshView top = BoardArt.texturedPlane(CARD_W, CARD_H,
                BoardArt.cardTop(type.deckLabel(), type.accent()));
        top.setTranslateY(0.055);
        deck.getChildren().add(top);
        layer.getChildren().add(deck);
    }

    private Group buildCard(CardType type, CardDefinition card) {
        Group root = new Group();
        root.setId("DrawnCard_" + type.id());

        Box backing = new Box(CARD_W, CARD_THICKNESS, CARD_H);
        backing.setMaterial(mat(type.accent().darker().darker()));
        root.getChildren().add(backing);

        Image faceImage = BoardArt.cardFace(
                type.deckLabel(), card.title(), card.description(), type.accent());
        MeshView bottomFace = cardFacePlane(CARD_W, CARD_H, faceImage);
        bottomFace.setTranslateY(-CARD_THICKNESS / 2.0 - 0.0015);
        root.getChildren().add(bottomFace);

        // The pulled card is printed on both sides. This keeps the text
        // visible from the moment it leaves the deck; no reveal flip is used.
        MeshView topFace = BoardArt.texturedPlane(CARD_W, CARD_H, faceImage);
        topFace.setTranslateY(CARD_THICKNESS / 2.0 + 0.0015);
        root.getChildren().add(topFace);
        return root;
    }

    /**
     * A horizontal card face whose printed side points down (-Y). The image
     * top maps to local +Z, so lifting the card -90° around its bottom edge
     * makes the text upright and the face point toward +Z.
     */
    private static MeshView cardFacePlane(double width, double height, Image image) {
        double hw = width / 2.0;
        double hh = height / 2.0;
        TriangleMesh mesh = new TriangleMesh(VertexFormat.POINT_NORMAL_TEXCOORD);
        mesh.getPoints().addAll(
                (float) -hw, 0, (float) -hh,
                (float) hw, 0, (float) -hh,
                (float) hw, 0, (float) hh,
                (float) -hw, 0, (float) hh);
        mesh.getNormals().addAll(
                0, -1, 0,
                0, -1, 0,
                0, -1, 0,
                0, -1, 0);
        // Image top at local +Z, image left at local -X.
        mesh.getTexCoords().addAll(
                0, 1,
                1, 1,
                1, 0,
                0, 0);
        mesh.getFaces().addAll(
                0, 0, 0, 1, 1, 1, 2, 2, 2,
                0, 0, 0, 2, 2, 2, 3, 3, 3);

        MeshView view = new MeshView(mesh);
        view.setCullFace(CullFace.NONE);
        PhongMaterial material = new PhongMaterial(Color.WHITE);
        material.setDiffuseMap(image);
        // The winter scene is intentionally dim; a faint self-illuminated
        // copy keeps the card title and body readable while it stands.
        material.setSelfIlluminationMap(BoardArt.glowMap(image, 0.34));
        material.setSpecularColor(Color.TRANSPARENT);
        material.setSpecularPower(0);
        view.setMaterial(material);
        return view;
    }

    private static PhongMaterial mat(Color color) {
        PhongMaterial material = new PhongMaterial(color);
        material.setSpecularColor(Color.TRANSPARENT);
        material.setSpecularPower(0);
        return material;
    }
}
