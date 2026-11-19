package wintervillage.tokens;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.MeshView;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;
import wintervillage.WinterVillageProfile;
import wintervillage.asset.ModelAssetLoader;
import wintervillage.board.BoardGeometry;
import wintervillage.board.MonopolyBoardModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

public final class PlayerTokenLayer {

    private static final Color[] PLAYER_COLORS = {
            Color.web("#E0343E"), Color.web("#2E86C1"), Color.web("#2E9E5B"),
            Color.web("#F4D03F"), Color.web("#8E44AD"), Color.web("#E8871E"),
    };
    private static final String[] COLOR_NAMES = {"红", "蓝", "绿", "黄", "紫", "橙"};

    private static final double SHORT_SPACING = 0.132;
    private static final double LONG_SPACING = 0.36;
    private static final double STEP_MS = 230;
    private static final double HOP_HEIGHT = 0.09;
    private static final double SETTLE_MS = 190;

    private final Group layer = new Group();
    private final WinterVillageProfile profile;
    private final double boardScale;
    private final double tileTopY;
    private final List<PlayerToken> players = new ArrayList<>();

    private boolean animating;
    private int nextPlayer;
    private BiConsumer<Integer, Integer> onPlayerMoved;
    private BiConsumer<Integer, Integer> onMovementFinished;
    private BiConsumer<Integer, Integer> onPlayerMoveStarted;

    // ★ 新增：控制是否允許 3D 系統自動移動棋子
    private boolean autoMoveEnabled = true;
    // ★ 新增：記錄當前動畫，方便強制中斷
    private Timeline currentTimeline;

    public PlayerTokenLayer(Group parent, WinterVillageProfile profile) {
        this.profile = profile;
        this.boardScale = profile.boardWidth / MonopolyBoardModel.DESIGN_WIDTH;
        this.tileTopY = MonopolyBoardModel.tileTopY(profile.boardWidth, profile.boardY) + 0.002;
        layer.setId("PlayerTokenLayer");
        parent.getChildren().add(layer);
        loadPlayers();
        layoutTile(0, false);
    }

    public Group getLayer() { return layer; }
    public int getPlayerCount() { return players.size(); }
    public PlayerToken getPlayer(int index) { return players.get(index); }
    public int getPlayerPosition(int index) { return players.get(index).tileIndex(); }
    public int getTurnIndex() { return nextPlayer; }
    public boolean isAnimating() { return animating; }

    // ★ 新增：設定是否允許自動移動
    public void setAutoMoveEnabled(boolean enabled) {
        this.autoMoveEnabled = enabled;
    }

    public void setTokenScale(int index, double scale) {
        Group root = players.get(index).root();
        root.setScaleX(scale); root.setScaleY(scale); root.setScaleZ(scale);
    }

    public void setTokenRotation(int index, double degrees) {
        players.get(index).root().setRotate(degrees);
    }

    public double getTileTopY() { return tileTopY; }

    public Point3D getTileCenter(int tile) {
        double designX = BoardGeometry.centerX(tile) - 470.0;
        double designZ = BoardGeometry.centerY(tile) - 470.0;
        return new Point3D(
                profile.boardX + designX * boardScale,
                tileTopY,
                profile.boardZ + designZ * boardScale);
    }

    public void setOnPlayerMoved(BiConsumer<Integer, Integer> listener) { this.onPlayerMoved = listener; }
    public void setOnMovementFinished(BiConsumer<Integer, Integer> listener) { this.onMovementFinished = listener; }
    public void setOnPlayerMoveStarted(BiConsumer<Integer, Integer> listener) { this.onPlayerMoveStarted = listener; }

    public void setTurnIndex(int index) {
        if (index >= 0 && index < players.size()) nextPlayer = index;
    }

    public boolean movePlayerToTile(int index, int tile) {
        if (!autoMoveEnabled) return false;
        if (index < 0 || index >= players.size() || tile < 0 || tile >= BoardGeometry.TILES) {
            return false;
        }
        int current = players.get(index).tileIndex();
        int steps = (tile - current + BoardGeometry.TILES) % BoardGeometry.TILES;
        if (steps == 0) return true;
        return movePlayerSteps(index, steps);
    }

    public boolean setPlayerTile(int index, int tile, boolean animate) {
        if (index < 0 || index >= players.size() || tile < 0 || tile >= BoardGeometry.TILES) {
            return false;
        }
        if (animate) return movePlayerToTile(index, tile);
        teleportPlayer(index, tile);
        return true;
    }

    public boolean moveNextPlayer(int steps) {
        if (!autoMoveEnabled) return false;
        if (animating) return false;
        int index = nextPlayer;
        if (!movePlayerSteps(index, steps)) return false;
        nextPlayer = (nextPlayer + 1) % players.size();
        return true;
    }

    public boolean moveCurrentPlayer(int steps) {
        if (!autoMoveEnabled) return false;
        if (animating) return false;
        return movePlayerSteps(nextPlayer, steps);
    }

    // ★ 新增：強制停止目前動畫
    public void stopAnimation() {
        if (currentTimeline != null) {
            currentTimeline.stop();
            currentTimeline = null;
        }
        animating = false;
    }

    // ★ 新增：強制移動（無視 autoMoveEnabled 開關，由 HUD 手動呼叫）
    public boolean forceMovePlayerSteps(int index, int steps) {
        stopAnimation();
        boolean old = autoMoveEnabled;
        autoMoveEnabled = true;
        boolean res = movePlayerSteps(index, steps);
        autoMoveEnabled = old;
        return res;
    }

    public boolean movePlayerSteps(int index, int steps) {
        if (!autoMoveEnabled) return false; // 檢查開關
        if (animating || steps == 0 || index < 0 || index >= players.size()) return false;

        if (currentTimeline != null) currentTimeline.stop();

        PlayerToken player = players.get(index);
        Group root = player.root();
        int sourceTile = player.tileIndex();
        int direction = steps > 0 ? 1 : -1;
        int count = Math.abs(steps);
        int targetTile = BoardGeometry.norm(sourceTile + direction * count);

        currentTimeline = new Timeline();
        double yaw = root.getRotate();
        currentTimeline.getKeyFrames().add(new KeyFrame(Duration.ZERO,
                new KeyValue(root.translateXProperty(), root.getTranslateX()),
                new KeyValue(root.translateYProperty(), tileTopY),
                new KeyValue(root.translateZProperty(), root.getTranslateZ()),
                new KeyValue(root.rotateProperty(), yaw)));

        for (int step = 1; step <= count; step++) {
            int tile = BoardGeometry.norm(sourceTile + direction * step);
            double end = step * STEP_MS;
            double[] position;
            if (step == count) {
                double[] slot = arrivalSlot(tile, player);
                position = tilePosition(tile, slot[0], slot[1]);
            } else {
                position = tileCenter(tile);
            }
            double targetYaw = nearestAngle(yaw, yawFor(tile));
            currentTimeline.getKeyFrames().add(new KeyFrame(
                    Duration.millis(end - STEP_MS / 2.0),
                    new KeyValue(root.translateYProperty(), tileTopY + HOP_HEIGHT, Interpolator.EASE_OUT)));
            currentTimeline.getKeyFrames().add(new KeyFrame(
                    Duration.millis(end),
                    new KeyValue(root.translateXProperty(), position[0], Interpolator.EASE_BOTH),
                    new KeyValue(root.translateZProperty(), position[1], Interpolator.EASE_BOTH),
                    new KeyValue(root.translateYProperty(), tileTopY, Interpolator.EASE_IN),
                    new KeyValue(root.rotateProperty(), targetYaw, Interpolator.EASE_BOTH)));
            yaw = targetYaw;
        }

        if (onPlayerMoveStarted != null) onPlayerMoveStarted.accept(index, targetTile);
        animating = true;
        currentTimeline.setOnFinished(event -> {
            player.setTileIndex(targetTile);
            layoutTile(sourceTile, true);
            layoutTile(targetTile, true);
            PauseTransition settle = new PauseTransition(Duration.millis(SETTLE_MS));
            settle.setOnFinished(settled -> {
                animating = false;
                if (onPlayerMoved != null) onPlayerMoved.accept(index, targetTile);
                if (onMovementFinished != null) onMovementFinished.accept(index, targetTile);
            });
            settle.play();
        });
        currentTimeline.play();
        return true;
    }

    public void teleportPlayer(int index, int tile) {
        // ★ 強制中斷動畫並解除鎖定
        stopAnimation();

        PlayerToken player = players.get(index);
        int previous = player.tileIndex();
        player.setTileIndex(BoardGeometry.norm(tile));
        layoutTile(previous, false);
        layoutTile(player.tileIndex(), false);
    }

    public void setTokenVisible(int index, boolean visible) {
        if (index >= 0 && index < players.size()) {
            players.get(index).root().setVisible(visible);
        }
    }

    public void resetAll(boolean animate) {
        for (PlayerToken player : players) {
            player.setTileIndex(0);
        }
        nextPlayer = 0;
        if (!animate) {
            layoutTile(0, false);
            animating = false;
            return;
        }
        animating = true;
        layoutTile(0, true);
        PauseTransition settle = new PauseTransition(Duration.millis(SETTLE_MS));
        settle.setOnFinished(event -> animating = false);
        settle.play();
    }

    private void loadPlayers() {
        Path root = resolveTokenRoot();
        TokenType[] types = TokenType.values();
        for (int index = 0; index < types.length; index++) {
            TokenType type = types[index];
            Path path = root.resolve(type.fileName());
            try {
                Group model = ModelAssetLoader.load(path);
                Color color = PLAYER_COLORS[index];
                int bodyMeshes = recolorBody(model, color);
                Group tokenRoot = new Group(model);
                tokenRoot.setId("PlayerToken_" + index + "_" + type.id());
                tokenRoot.setRotationAxis(Rotate.Y_AXIS);
                tokenRoot.setTranslateY(tileTopY);
                layer.getChildren().add(tokenRoot);
                PlayerToken token = new PlayerToken(index, COLOR_NAMES[index], color, type, tokenRoot);
                token.setTileIndex(0);
                players.add(token);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to load player token: " + path, ex);
            }
        }
    }

    private static int recolorBody(Group model, Color color) {
        PhongMaterial bodyMaterial = null;
        int count = 0;
        List<Node> stack = new ArrayList<>();
        stack.add(model);
        while (!stack.isEmpty()) {
            Node node = stack.remove(stack.size() - 1);
            if (node instanceof MeshView meshView && meshView.getId() != null && meshView.getId().startsWith("TOKEN_BODY")) {
                if (bodyMaterial == null) {
                    bodyMaterial = new PhongMaterial(color);
                    if (meshView.getMaterial() instanceof PhongMaterial original) {
                        bodyMaterial.setSpecularColor(original.getSpecularColor());
                        bodyMaterial.setSpecularPower(original.getSpecularPower());
                    } else {
                        bodyMaterial.setSpecularColor(Color.TRANSPARENT);
                        bodyMaterial.setSpecularPower(0);
                    }
                }
                meshView.setMaterial(bodyMaterial);
                count++;
            }
            if (node instanceof javafx.scene.Parent parent) {
                stack.addAll(parent.getChildrenUnmodifiable());
            }
        }
        return count;
    }

    private void layoutTile(int tile, boolean animate) {
        List<PlayerToken> occupants = occupants(tile);
        if (occupants.isEmpty()) return;
        double[][] slots = slots(occupants.size());
        for (int i = 0; i < occupants.size(); i++) {
            place(occupants.get(i), tile, slots[i][0], slots[i][1], animate);
        }
    }

    private void place(PlayerToken player, int tile, double shortOffset, double longOffset, boolean animate) {
        double[] position = tilePosition(tile, shortOffset, longOffset);
        double yaw = yawFor(tile);
        Group root = player.root();
        if (!animate) {
            root.setTranslateX(position[0]);
            root.setTranslateZ(position[1]);
            root.setTranslateY(tileTopY);
            root.setRotate(yaw);
            return;
        }
        double targetYaw = nearestAngle(root.getRotate(), yaw);
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(160),
                new KeyValue(root.translateXProperty(), position[0], Interpolator.EASE_BOTH),
                new KeyValue(root.translateZProperty(), position[1], Interpolator.EASE_BOTH),
                new KeyValue(root.translateYProperty(), tileTopY, Interpolator.EASE_BOTH),
                new KeyValue(root.rotateProperty(), targetYaw, Interpolator.EASE_BOTH)));
        timeline.play();
    }

    private List<PlayerToken> occupants(int tile) {
        List<PlayerToken> occupants = new ArrayList<>();
        for (PlayerToken player : players) {
            if (player.tileIndex() == tile) occupants.add(player);
        }
        occupants.sort(Comparator.comparingInt(PlayerToken::index));
        return occupants;
    }

    private double[] arrivalSlot(int tile, PlayerToken arriving) {
        List<PlayerToken> occupants = occupants(tile);
        if (!occupants.contains(arriving)) {
            occupants.add(arriving);
            occupants.sort(Comparator.comparingInt(PlayerToken::index));
        }
        double[][] slots = slots(occupants.size());
        int index = occupants.indexOf(arriving);
        return slots[Math.max(0, Math.min(index, slots.length - 1))];
    }

    private double[] tilePosition(int tile, double shortOffset, double longOffset) {
        double[] center = tileCenter(tile);
        BoardGeometry.Side side = BoardGeometry.side(tile);
        boolean vertical = side == BoardGeometry.Side.LEFT || side == BoardGeometry.Side.RIGHT;
        double shortX = vertical ? 0 : 1;
        double shortZ = vertical ? 1 : 0;
        double longX = vertical ? 1 : 0;
        double longZ = vertical ? 0 : 1;
        return new double[]{
                center[0] + shortX * shortOffset + longX * longOffset,
                center[1] + shortZ * shortOffset + longZ * longOffset,
        };
    }

    private double[] tileCenter(int tile) {
        double designX = BoardGeometry.centerX(tile) - 470.0;
        double designZ = BoardGeometry.centerY(tile) - 470.0;
        return new double[]{
                profile.boardX + designX * boardScale,
                profile.boardZ + designZ * boardScale,
        };
    }

    private double yawFor(int tile) {
        BoardGeometry.Side side = BoardGeometry.side(tile);
        switch (side) {
            case BOTTOM: return 0;
            case TOP: return 180;
            case RIGHT: return 90;
            case LEFT: return -90;
            default:
                double[] center = tileCenter(tile);
                return Math.toDegrees(Math.atan2(center[0] - profile.boardX, center[1] - profile.boardZ));
        }
    }

    private static double[][] slots(int count) {
        return switch (count) {
            case 1 -> new double[][]{{0, 0}};
            case 2 -> new double[][]{{0, -0.22}, {0, 0.22}};
            case 3 -> new double[][]{{0, -0.38}, {0, 0}, {0, 0.38}};
            case 4 -> new double[][]{{-SHORT_SPACING, -0.20}, {SHORT_SPACING, -0.20}, {-SHORT_SPACING, 0.20}, {SHORT_SPACING, 0.20}};
            case 5 -> new double[][]{{-SHORT_SPACING, -LONG_SPACING}, {SHORT_SPACING, -LONG_SPACING}, {-SHORT_SPACING, 0}, {SHORT_SPACING, 0}, {-SHORT_SPACING, LONG_SPACING}};
            default -> new double[][]{{-SHORT_SPACING, -LONG_SPACING}, {SHORT_SPACING, -LONG_SPACING}, {-SHORT_SPACING, 0}, {SHORT_SPACING, 0}, {-SHORT_SPACING, LONG_SPACING}, {SHORT_SPACING, LONG_SPACING}};
        };
    }

    private static double nearestAngle(double current, double target) {
        double result = target;
        while (result - current > 180) result -= 360;
        while (result - current < -180) result += 360;
        return result;
    }

    private static Path resolveTokenRoot() {
        String configured = System.getProperty("winter.village.tokens.root");
        if (configured != null && !configured.isBlank()) return Path.of(configured).toAbsolutePath().normalize();
        String monopolyRoot = System.getProperty("monopoly.assets.root");
        if (monopolyRoot != null && !monopolyRoot.isBlank()) {
            Path pieces = Path.of(monopolyRoot, "pieces");
            if (Files.isDirectory(pieces)) return pieces.toAbsolutePath().normalize();
        }
        Path local = Path.of("assets", "tokens");
        if (Files.isDirectory(local)) return local.toAbsolutePath().normalize();
        Path fromRoot = Path.of("winter_village_javafx", "assets", "tokens");
        if (Files.isDirectory(fromRoot)) return fromRoot.toAbsolutePath().normalize();
        return local.toAbsolutePath().normalize();
    }
}