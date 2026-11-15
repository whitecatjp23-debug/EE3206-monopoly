package wintervillage.dice;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.shape.MeshView;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;
import wintervillage.WinterVillageProfile;
import wintervillage.asset.ModelAssetLoader;
import wintervillage.board.MonopolyBoardModel;
import wintervillage.tokens.PlayerTokenLayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Two 3D dice, deterministic face orientation, rolling animation and the
 * dice-to-movement turn loop.
 *
 * <p>The model is loaded once and copied into two independent Groups. Each
 * instance has this hierarchy:</p>
 *
 * <pre>
 * root (board placement)
 *   tumble (X/Y/Z rotation)
 *     yaw (vertical spin)
 *       face (value-to-top rotation)
 *         dice model
 * </pre>
 *
 * The face mapping is fixed before the animation starts. The tumble angles
 * always end on exact multiples of 360 degrees, so the settled top face is the
 * generated value. The animation therefore cannot disagree with the game
 * logic, and no random visible result is generated after the roll.
 */
public final class DiceController {

    public enum State { IDLE, ROLLING, RESULT_READY, MOVING }

    public record RollResult(int die1, int die2) {
        public int total() {
            return die1 + die2;
        }
    }

    /** The exported dice is 0.342 m; 1 mm clearance for the raised pips. */
    private static final double DICE_HALF_HEIGHT = 0.172;

    private final Group layer = new Group();
    private final PlayerTokenLayer playerTokens;
    private final Dice die1;
    private final Dice die2;
    private final Random random = new Random();
    private final Point3D focusPoint;

    private State state = State.IDLE;
    private Consumer<RollResult> onRollResult;
    private Consumer<State> onStateChanged;
    private Runnable onTurnComplete;
    private BooleanSupplier externalBusy = () -> false;
    private boolean autoMovement = true;
    private RollResult lastResult;

    public DiceController(Group parent,
                          WinterVillageProfile profile,
                          PlayerTokenLayer playerTokens) {
        this.playerTokens = playerTokens;
        layer.setId("DiceLayer");
        parent.getChildren().add(layer);

        Path path = resolveDicePath();
        Group prototype;
        try {
            prototype = ModelAssetLoader.load(path);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load dice: " + path, ex);
        }

        double fieldTop = MonopolyBoardModel.fieldTopY(
                profile.boardWidth, profile.boardY) + 0.001;
        double centerY = fieldTop + DICE_HALF_HEIGHT;
        double homeZ = profile.boardZ + profile.boardWidth * 0.25;
        double homeX = profile.boardWidth * 0.041;

        die1 = new Dice((Group) copyNode(prototype), profile.boardX - homeX, centerY, homeZ, 0);
        die2 = new Dice((Group) copyNode(prototype), profile.boardX + homeX, centerY, homeZ, 1);
        die1.setFinalOrientation(1, 12);
        die2.setFinalOrientation(3, 48);

        focusPoint = new Point3D(profile.boardX, fieldTop + 0.24, homeZ);

        playerTokens.setOnMovementFinished((player, tile) -> {
            if (state == State.MOVING) {
                setState(State.IDLE);
                if (onTurnComplete != null) {
                    onTurnComplete.run();
                }
            }
        });

        System.out.printf(Locale.ROOT,
                "[dice] loaded %s; two instances at z=%.3f, centreY=%.3f%n",
                path.getFileName(), homeZ, centerY);
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

    public boolean canRoll() {
        return state == State.IDLE
                && !playerTokens.isAnimating()
                && !externalBusy.getAsBoolean();
    }

    /** Additional view-level guard, e.g. a running card animation. */
    public void setExternalBusy(BooleanSupplier supplier) {
        externalBusy = supplier == null ? () -> false : supplier;
    }

    public Point3D getFocusPoint() {
        return focusPoint;
    }

    public RollResult getLastResult() {
        return lastResult;
    }

    public void setOnRollResult(Consumer<RollResult> listener) {
        this.onRollResult = listener;
    }

    public void setOnStateChanged(Consumer<State> listener) {
        this.onStateChanged = listener;
    }

    public void setOnTurnComplete(Runnable listener) {
        this.onTurnComplete = listener;
    }

    /**
     * Default true preserves the built-in loop: dice settle, then the current
     * player automatically moves the rolled total. Rules code can disable it,
     * receive onRollResult, apply modifiers, and call beginMovement(steps) or
     * completeTurn() itself.
     */
    public void setAutoMovement(boolean enabled) {
        this.autoMovement = enabled;
    }

    public boolean isAutoMovement() {
        return autoMovement;
    }

    /**
     * Move the current player by a rules-decided number of steps after a
     * settled result. Only valid in RESULT_READY.
     */
    public boolean beginMovement(int steps) {
        if (state != State.RESULT_READY) {
            return false;
        }
        setState(State.MOVING);
        if (!playerTokens.moveNextPlayer(steps)) {
            setState(State.IDLE);
            return false;
        }
        return true;
    }

    /**
     * Finish a turn without moving (for example a card that teleports the
     * token). Only valid in RESULT_READY; the normal movement callback handles
     * turns that use beginMovement.
     */
    public void completeTurn() {
        if (state == State.RESULT_READY) {
            setState(State.IDLE);
            if (onTurnComplete != null) {
                onTurnComplete.run();
            }
        }
    }

    /** Production roll: two independent uniform values from 1 to 6. */
    public boolean rollRandom() {
        int first = random.nextInt(6) + 1;
        int second = random.nextInt(6) + 1;
        return roll(first, second);
    }

    /**
     * Deterministic roll hook for tests and review captures. The values are
     * fixed before the animation begins; production code uses
     * {@link #rollRandom()}.
     */
    public boolean roll(int first, int second) {
        if (!canRoll() || first < 1 || first > 6 || second < 1 || second > 6) {
            return false;
        }
        lastResult = new RollResult(first, second);
        setState(State.ROLLING);

        // The values are fixed before any animation starts. The face mapping
        // is applied a fraction of a second into the tumble, so the initial
        // resting dice do not visibly snap to their result at click time.
        PauseTransition faceSwap = new PauseTransition(Duration.millis(110));
        faceSwap.setOnFinished(event -> {
            die1.setFinalOrientation(first, settledYaw(first, 0));
            die2.setFinalOrientation(second, settledYaw(second, 1));
        });
        faceSwap.play();

        Timeline roll1 = rollTimeline(die1, 1280, -1);
        Timeline roll2 = rollTimeline(die2, 1450, 1);
        ParallelTransition rolling = new ParallelTransition(roll1, roll2);
        rolling.setOnFinished(event -> {
            setState(State.RESULT_READY);
            if (onRollResult != null) {
                onRollResult.accept(lastResult);
            }
            if (!autoMovement) {
                // Rules code owns movement now. It can call beginMovement(...)
                // from onRollResult or later, or completeTurn() for no move.
                return;
            }
            // Let the settled result read for a moment before movement.
            PauseTransition resultHold = new PauseTransition(Duration.millis(380));
            resultHold.setOnFinished(hold -> {
                if (autoMovement && state == State.RESULT_READY) {
                    beginMovement(lastResult.total());
                }
            });
            resultHold.play();
        });
        rolling.play();
        return true;
    }

    /** Set two stable faces without rolling; used for deterministic review captures. */
    public boolean setPreviewValues(int first, int second) {
        if (state != State.IDLE || first < 1 || first > 6 || second < 1 || second > 6) {
            return false;
        }
        lastResult = new RollResult(first, second);
        die1.setFinalOrientation(first, settledYaw(first, 0));
        die2.setFinalOrientation(second, settledYaw(second, 1));
        die1.resetPose();
        die2.resetPose();
        return true;
    }

    // ------------------------------------------------------------------
    // Animation
    // ------------------------------------------------------------------

    private Timeline rollTimeline(Dice die, int durationMs, int direction) {
        double startX = die.root.getTranslateX();
        double startZ = die.root.getTranslateZ();
        double homeX = die.homeX;
        double homeZ = die.homeZ;
        double homeY = die.homeY;

        double startRx = die.rotX.getAngle();
        double startRy = die.rotY.getAngle();
        double startRz = die.rotZ.getAngle();
        double endRx = ceilToTurn(startRx + 1440 + (direction > 0 ? 360 : 0));
        double endRy = ceilToTurn(startRy + 1080 + (direction > 0 ? 360 : 0));
        double endRz = ceilToTurn(startRz + 720 + (direction > 0 ? 360 : 0));

        Timeline timeline = new Timeline();
        timeline.getKeyFrames().add(new KeyFrame(Duration.ZERO,
                new KeyValue(die.root.translateXProperty(), startX),
                new KeyValue(die.root.translateYProperty(), homeY),
                new KeyValue(die.root.translateZProperty(), startZ),
                new KeyValue(die.rotX.angleProperty(), startRx),
                new KeyValue(die.rotY.angleProperty(), startRy),
                new KeyValue(die.rotZ.angleProperty(), startRz)));

        timeline.getKeyFrames().add(new KeyFrame(Duration.millis(100),
                new KeyValue(die.root.translateYProperty(), homeY + 0.055,
                        Interpolator.EASE_OUT),
                new KeyValue(die.root.translateXProperty(), startX + direction * 0.035),
                new KeyValue(die.root.translateZProperty(), startZ + 0.020)));

        timeline.getKeyFrames().add(new KeyFrame(
                Duration.millis(durationMs * 0.24),
                new KeyValue(die.root.translateXProperty(), homeX + direction * 0.24,
                        Interpolator.EASE_BOTH),
                new KeyValue(die.root.translateZProperty(), homeZ + 0.16,
                        Interpolator.EASE_BOTH),
                new KeyValue(die.root.translateYProperty(), homeY + 0.24,
                        Interpolator.EASE_OUT),
                new KeyValue(die.rotX.angleProperty(), interpolate(startRx, endRx, 0.22),
                        Interpolator.EASE_IN),
                new KeyValue(die.rotY.angleProperty(), interpolate(startRy, endRy, 0.28),
                        Interpolator.EASE_IN),
                new KeyValue(die.rotZ.angleProperty(), interpolate(startRz, endRz, 0.18),
                        Interpolator.EASE_IN)));

        timeline.getKeyFrames().add(new KeyFrame(
                Duration.millis(durationMs * 0.56),
                new KeyValue(die.root.translateXProperty(), homeX + direction * 0.15,
                        Interpolator.EASE_BOTH),
                new KeyValue(die.root.translateZProperty(), homeZ + 0.20,
                        Interpolator.EASE_BOTH),
                new KeyValue(die.root.translateYProperty(), homeY + 0.18,
                        Interpolator.EASE_BOTH),
                new KeyValue(die.rotX.angleProperty(), interpolate(startRx, endRx, 0.58),
                        Interpolator.EASE_BOTH),
                new KeyValue(die.rotY.angleProperty(), interpolate(startRy, endRy, 0.52),
                        Interpolator.EASE_BOTH),
                new KeyValue(die.rotZ.angleProperty(), interpolate(startRz, endRz, 0.62),
                        Interpolator.EASE_BOTH)));

        timeline.getKeyFrames().add(new KeyFrame(
                Duration.millis(durationMs * 0.82),
                new KeyValue(die.root.translateXProperty(), homeX + direction * 0.05,
                        Interpolator.EASE_BOTH),
                new KeyValue(die.root.translateZProperty(), homeZ + 0.05,
                        Interpolator.EASE_BOTH),
                new KeyValue(die.root.translateYProperty(), homeY + 0.09,
                        Interpolator.EASE_BOTH),
                new KeyValue(die.rotX.angleProperty(), interpolate(startRx, endRx, 0.84),
                        Interpolator.EASE_OUT),
                new KeyValue(die.rotY.angleProperty(), interpolate(startRy, endRy, 0.82),
                        Interpolator.EASE_OUT),
                new KeyValue(die.rotZ.angleProperty(), interpolate(startRz, endRz, 0.86),
                        Interpolator.EASE_OUT)));

        timeline.getKeyFrames().add(new KeyFrame(
                Duration.millis(durationMs - 130),
                new KeyValue(die.root.translateXProperty(), homeX,
                        Interpolator.EASE_OUT),
                new KeyValue(die.root.translateZProperty(), homeZ,
                        Interpolator.EASE_OUT),
                new KeyValue(die.root.translateYProperty(), homeY + 0.028,
                        Interpolator.EASE_OUT),
                new KeyValue(die.rotX.angleProperty(), endRx - 45,
                        Interpolator.EASE_OUT),
                new KeyValue(die.rotY.angleProperty(), endRy - 35,
                        Interpolator.EASE_OUT),
                new KeyValue(die.rotZ.angleProperty(), endRz - 25,
                        Interpolator.EASE_OUT)));

        timeline.getKeyFrames().add(new KeyFrame(
                Duration.millis(durationMs),
                new KeyValue(die.root.translateXProperty(), homeX),
                new KeyValue(die.root.translateZProperty(), homeZ),
                new KeyValue(die.root.translateYProperty(), homeY),
                new KeyValue(die.rotX.angleProperty(), endRx),
                new KeyValue(die.rotY.angleProperty(), endRy),
                new KeyValue(die.rotZ.angleProperty(), endRz)));
        return timeline;
    }

    private static double interpolate(double start, double end, double fraction) {
        return start + (end - start) * fraction;
    }

    private static double ceilToTurn(double angle) {
        return Math.ceil(angle / 360.0) * 360.0;
    }

    private static double settledYaw(int value, int dieIndex) {
        // Fixed per-value yaw makes review captures reproducible; the top face
        // is unaffected by a vertical spin.
        return (value * 37.0 + dieIndex * 61.0) % 360.0;
    }

    private void setState(State next) {
        state = next;
        if (onStateChanged != null) {
            onStateChanged.accept(next);
        }
    }

    // ------------------------------------------------------------------
    // Model cloning / assets
    // ------------------------------------------------------------------

    private static Node copyNode(Node node) {
        if (node instanceof MeshView source) {
            MeshView copy = new MeshView(source.getMesh());
            copy.setId(source.getId());
            copy.setMaterial(source.getMaterial());
            copy.setCullFace(source.getCullFace());
            copy.setMouseTransparent(true);
            return copy;
        }
        if (node instanceof Group source) {
            Group copy = new Group();
            copy.setId(source.getId());
            copy.getTransforms().addAll(source.getTransforms());
            for (Node child : source.getChildren()) {
                copy.getChildren().add(copyNode(child));
            }
            return copy;
        }
        return new Group();
    }

    private static Path resolveDicePath() {
        String configured = System.getProperty("winter.village.dice.root");
        if (configured != null && !configured.isBlank()) {
            Path path = Path.of(configured);
            return Files.isDirectory(path)
                    ? path.resolve("dice_01.glb").toAbsolutePath().normalize()
                    : path.toAbsolutePath().normalize();
        }
        String monopolyRoot = System.getProperty("monopoly.assets.root");
        if (monopolyRoot != null && !monopolyRoot.isBlank()) {
            Path path = Path.of(monopolyRoot, "props", "dice_01", "dice_01.glb");
            if (Files.isRegularFile(path)) {
                return path.toAbsolutePath().normalize();
            }
        }
        Path local = Path.of("assets", "dice", "dice_01.glb");
        if (Files.isRegularFile(local)) {
            return local.toAbsolutePath().normalize();
        }
        Path fromRoot = Path.of("winter_village_javafx", "assets", "dice", "dice_01.glb");
        if (Files.isRegularFile(fromRoot)) {
            return fromRoot.toAbsolutePath().normalize();
        }
        return local.toAbsolutePath().normalize();
    }

    // ------------------------------------------------------------------
    // One die instance
    // ------------------------------------------------------------------

    private final class Dice {

        private final Group root = new Group();
        private final Group tumble = new Group();
        private final Group yawGroup = new Group();
        private final Group faceGroup = new Group();
        private final Rotate rotX = new Rotate(0, Rotate.X_AXIS);
        private final Rotate rotY = new Rotate(0, Rotate.Y_AXIS);
        private final Rotate rotZ = new Rotate(0, Rotate.Z_AXIS);
        private final Rotate yawRotate = new Rotate(0, Rotate.Y_AXIS);
        private final Rotate faceRotate = new Rotate(0, Rotate.Z_AXIS);

        private final double homeX;
        private final double homeY;
        private final double homeZ;

        private Dice(Group model, double x, double y, double z, int index) {
            homeX = x;
            homeY = y;
            homeZ = z;
            root.setId("DiceRoot_" + index);
            root.setTranslateX(x);
            root.setTranslateY(y);
            root.setTranslateZ(z);
            root.setScaleX(1.0);
            root.setScaleY(1.0);
            root.setScaleZ(1.0);

            tumble.getTransforms().addAll(rotX, rotY, rotZ);
            yawGroup.getTransforms().add(yawRotate);
            faceGroup.getTransforms().add(faceRotate);
            faceGroup.getChildren().add(model);
            yawGroup.getChildren().add(faceGroup);
            tumble.getChildren().add(yawGroup);
            root.getChildren().add(tumble);
            layer.getChildren().add(root);
        }

        private void setFinalOrientation(int value, double yaw) {
            switch (value) {
                case 1 -> {
                    faceRotate.setAxis(Rotate.Z_AXIS);
                    faceRotate.setAngle(0);
                }
                case 2 -> {
                    faceRotate.setAxis(Rotate.X_AXIS);
                    faceRotate.setAngle(-90);
                }
                case 3 -> {
                    faceRotate.setAxis(Rotate.Z_AXIS);
                    faceRotate.setAngle(90);
                }
                case 4 -> {
                    faceRotate.setAxis(Rotate.Z_AXIS);
                    faceRotate.setAngle(-90);
                }
                case 5 -> {
                    faceRotate.setAxis(Rotate.X_AXIS);
                    faceRotate.setAngle(90);
                }
                case 6 -> {
                    faceRotate.setAxis(Rotate.X_AXIS);
                    faceRotate.setAngle(180);
                }
                default -> throw new IllegalArgumentException("dice value " + value);
            }
            yawRotate.setAngle(yaw);
        }

        private void resetPose() {
            root.setTranslateX(homeX);
            root.setTranslateY(homeY);
            root.setTranslateZ(homeZ);
            rotX.setAngle(0);
            rotY.setAngle(0);
            rotZ.setAngle(0);
        }
    }
}
