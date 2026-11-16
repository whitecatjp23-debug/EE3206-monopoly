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

public final class DiceController {

    public enum State { IDLE, ROLLING, RESULT_READY, MOVING }

    public record RollResult(int die1, int die2) {
        public int total() { return die1 + die2; }
    }

    private static final double DICE_HALF_HEIGHT = 0.172;
    private static final int JAIL_TILE = 10;

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
    private int consecutiveDoubles = 0;

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

                boolean wasDouble = lastResult != null
                        && lastResult.die1() == lastResult.die2();

                if (wasDouble) {
                    System.out.println("[dice] Double → same player rolls again");
                } else {
                    if (onTurnComplete != null) {
                        onTurnComplete.run();
                    }
                }
            }
        });

        System.out.printf(Locale.ROOT,
                "[dice] loaded %s; two instances at z=%.3f, centreY=%.3f%n",
                path.getFileName(), homeZ, centerY);
    }

    public Group getLayer() { return layer; }
    public State getState() { return state; }
    public boolean isBusy() { return state != State.IDLE; }

    public boolean canRoll() {
        return state == State.IDLE
                && !playerTokens.isAnimating()
                && !externalBusy.getAsBoolean();
    }

    public void setExternalBusy(BooleanSupplier supplier) {
        externalBusy = supplier == null ? () -> false : supplier;
    }

    public Point3D getFocusPoint() { return focusPoint; }
    public RollResult getLastResult() { return lastResult; }
    public void setOnRollResult(Consumer<RollResult> listener) { this.onRollResult = listener; }
    public void setOnStateChanged(Consumer<State> listener) { this.onStateChanged = listener; }
    public void setOnTurnComplete(Runnable listener) { this.onTurnComplete = listener; }
    public void setAutoMovement(boolean enabled) { this.autoMovement = enabled; }
    public boolean isAutoMovement() { return autoMovement; }

    // ============================================================
    // FIXED: Use moveCurrentPlayer on doubles, moveNextPlayer otherwise
    // ============================================================
    public boolean beginMovement(int steps) {
        if (state != State.RESULT_READY) return false;
        setState(State.MOVING);

        boolean wasDouble = lastResult != null
                && lastResult.die1() == lastResult.die2();

        boolean moved = wasDouble
                ? playerTokens.moveCurrentPlayer(steps)   // same player, no turn advance
                : playerTokens.moveNextPlayer(steps);     // advance turn

        if (!moved) {
            setState(State.IDLE);
            return false;
        }
        return true;
    }

    public void completeTurn() {
        if (state == State.RESULT_READY) {
            setState(State.IDLE);
            if (onTurnComplete != null) onTurnComplete.run();
        }
    }

    public boolean rollRandom() {
        int first = random.nextInt(6) + 1;
        int second = random.nextInt(6) + 1;
        return roll(first, second);
    }

    public boolean roll(int first, int second) {
        if (!canRoll() || first < 1 || first > 6 || second < 1 || second > 6) return false;
        lastResult = new RollResult(first, second);
        setState(State.ROLLING);

        boolean isDouble = (first == second);
        if (isDouble) consecutiveDoubles++; else consecutiveDoubles = 0;

        if (consecutiveDoubles >= 3) {
            consecutiveDoubles = 0;
            animateRoll(first, second, () -> {
                setState(State.RESULT_READY);
                if (onRollResult != null) onRollResult.accept(lastResult);
                int currentPlayer = playerTokens.getTurnIndex();
                playerTokens.teleportPlayer(currentPlayer, JAIL_TILE);
                setState(State.IDLE);
                if (onTurnComplete != null) onTurnComplete.run();
            });
            return true;
        }

        animateRoll(first, second, () -> {
            setState(State.RESULT_READY);
            if (onRollResult != null) onRollResult.accept(lastResult);
            if (!autoMovement) return;
            PauseTransition hold = new PauseTransition(Duration.millis(380));
            hold.setOnFinished(h -> {
                if (autoMovement && state == State.RESULT_READY) {
                    beginMovement(lastResult.total());
                }
            });
            hold.play();
        });
        return true;
    }

    private void animateRoll(int first, int second, Runnable onDone) {
        PauseTransition faceSwap = new PauseTransition(Duration.millis(110));
        faceSwap.setOnFinished(e -> {
            die1.setFinalOrientation(first, settledYaw(first, 0));
            die2.setFinalOrientation(second, settledYaw(second, 1));
        });
        faceSwap.play();

        Timeline roll1 = rollTimeline(die1, 1280, -1);
        Timeline roll2 = rollTimeline(die2, 1450, 1);
        ParallelTransition rolling = new ParallelTransition(roll1, roll2);
        rolling.setOnFinished(e -> onDone.run());
        rolling.play();
    }

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
                new KeyValue(die.root.translateYProperty(), homeY + 0.055, Interpolator.EASE_OUT),
                new KeyValue(die.root.translateXProperty(), startX + direction * 0.035),
                new KeyValue(die.root.translateZProperty(), startZ + 0.020)));

        timeline.getKeyFrames().add(new KeyFrame(Duration.millis(durationMs * 0.24),
                new KeyValue(die.root.translateXProperty(), homeX + direction * 0.24, Interpolator.EASE_BOTH),
                new KeyValue(die.root.translateZProperty(), homeZ + 0.16, Interpolator.EASE_BOTH),
                new KeyValue(die.root.translateYProperty(), homeY + 0.24, Interpolator.EASE_OUT),
                new KeyValue(die.rotX.angleProperty(), interpolate(startRx, endRx, 0.22), Interpolator.EASE_IN),
                new KeyValue(die.rotY.angleProperty(), interpolate(startRy, endRy, 0.28), Interpolator.EASE_IN),
                new KeyValue(die.rotZ.angleProperty(), interpolate(startRz, endRz, 0.18), Interpolator.EASE_IN)));

        timeline.getKeyFrames().add(new KeyFrame(Duration.millis(durationMs * 0.56),
                new KeyValue(die.root.translateXProperty(), homeX + direction * 0.15, Interpolator.EASE_BOTH),
                new KeyValue(die.root.translateZProperty(), homeZ + 0.20, Interpolator.EASE_BOTH),
                new KeyValue(die.root.translateYProperty(), homeY + 0.18, Interpolator.EASE_BOTH),
                new KeyValue(die.rotX.angleProperty(), interpolate(startRx, endRx, 0.58), Interpolator.EASE_BOTH),
                new KeyValue(die.rotY.angleProperty(), interpolate(startRy, endRy, 0.52), Interpolator.EASE_BOTH),
                new KeyValue(die.rotZ.angleProperty(), interpolate(startRz, endRz, 0.62), Interpolator.EASE_BOTH)));

        timeline.getKeyFrames().add(new KeyFrame(Duration.millis(durationMs * 0.82),
                new KeyValue(die.root.translateXProperty(), homeX + direction * 0.05, Interpolator.EASE_BOTH),
                new KeyValue(die.root.translateZProperty(), homeZ + 0.05, Interpolator.EASE_BOTH),
                new KeyValue(die.root.translateYProperty(), homeY + 0.09, Interpolator.EASE_BOTH),
                new KeyValue(die.rotX.angleProperty(), interpolate(startRx, endRx, 0.84), Interpolator.EASE_OUT),
                new KeyValue(die.rotY.angleProperty(), interpolate(startRy, endRy, 0.82), Interpolator.EASE_OUT),
                new KeyValue(die.rotZ.angleProperty(), interpolate(startRz, endRz, 0.86), Interpolator.EASE_OUT)));

        timeline.getKeyFrames().add(new KeyFrame(Duration.millis(durationMs - 130),
                new KeyValue(die.root.translateXProperty(), homeX, Interpolator.EASE_OUT),
                new KeyValue(die.root.translateZProperty(), homeZ, Interpolator.EASE_OUT),
                new KeyValue(die.root.translateYProperty(), homeY + 0.028, Interpolator.EASE_OUT),
                new KeyValue(die.rotX.angleProperty(), endRx - 45, Interpolator.EASE_OUT),
                new KeyValue(die.rotY.angleProperty(), endRy - 35, Interpolator.EASE_OUT),
                new KeyValue(die.rotZ.angleProperty(), endRz - 25, Interpolator.EASE_OUT)));

        timeline.getKeyFrames().add(new KeyFrame(Duration.millis(durationMs),
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
        return (value * 37.0 + dieIndex * 61.0) % 360.0;
    }

    private void setState(State next) {
        state = next;
        if (onStateChanged != null) onStateChanged.accept(next);
    }

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
            for (Node child : source.getChildren()) copy.getChildren().add(copyNode(child));
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
            if (Files.isRegularFile(path)) return path.toAbsolutePath().normalize();
        }
        Path local = Path.of("assets", "dice", "dice_01.glb");
        if (Files.isRegularFile(local)) return local.toAbsolutePath().normalize();
        Path fromRoot = Path.of("winter_village_javafx", "assets", "dice", "dice_01.glb");
        if (Files.isRegularFile(fromRoot)) return fromRoot.toAbsolutePath().normalize();
        return local.toAbsolutePath().normalize();
    }

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

        private final double homeX, homeY, homeZ;

        private Dice(Group model, double x, double y, double z, int index) {
            homeX = x; homeY = y; homeZ = z;
            root.setId("DiceRoot_" + index);
            root.setTranslateX(x); root.setTranslateY(y); root.setTranslateZ(z);
            root.setScaleX(1.0); root.setScaleY(1.0); root.setScaleZ(1.0);

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
                case 1 -> { faceRotate.setAxis(Rotate.Z_AXIS); faceRotate.setAngle(0); }
                case 2 -> { faceRotate.setAxis(Rotate.X_AXIS); faceRotate.setAngle(-90); }
                case 3 -> { faceRotate.setAxis(Rotate.Z_AXIS); faceRotate.setAngle(90); }
                case 4 -> { faceRotate.setAxis(Rotate.Z_AXIS); faceRotate.setAngle(-90); }
                case 5 -> { faceRotate.setAxis(Rotate.X_AXIS); faceRotate.setAngle(90); }
                case 6 -> { faceRotate.setAxis(Rotate.X_AXIS); faceRotate.setAngle(180); }
                default -> throw new IllegalArgumentException("dice value " + value);
            }
            yawRotate.setAngle(yaw);
        }

        private void resetPose() {
            root.setTranslateX(homeX); root.setTranslateY(homeY); root.setTranslateZ(homeZ);
            rotX.setAngle(0); rotY.setAngle(0); rotZ.setAngle(0);
        }
    }
}