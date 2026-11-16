package wintervillage;

import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point3D;
import javafx.geometry.Pos;
import javafx.scene.AmbientLight;
import javafx.scene.Cursor;
import javafx.scene.DepthTest;
import javafx.scene.DirectionalLight;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.MeshView;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Transform;
import wintervillage.asset.ModelAssetLoader;
import wintervillage.board.MonopolyBoardModel;
import wintervillage.board.TileDef;
import wintervillage.cards.CardController;
import wintervillage.cards.CardType;
import wintervillage.dice.DiceController;
import wintervillage.tokens.PlayerTokenLayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class WinterVillageView extends StackPane {

    private final WinterVillageProfile profile = WinterVillageProfile.active();

    private final Group worldRoot = new Group();
    private final PerspectiveCamera camera = new PerspectiveCamera(true);
    private final SubScene subScene;
    private final BorderPane hud = new BorderPane();
    private OrbitCameraController cameraController;
    private Group modelRoot;
    private PlayerTokenLayer playerTokens;
    private DiceController diceController;
    private CardController cardController;
    private Label tokenStatus;
    private Label diceStatus;
    private Label cardStatus;
    private Button rollButton;
    private Button resetTokensButton;
    private Button activationButton;
    private Button resourceButton;
    private String pendingTokenStatus = "6 枚棋子 · 全部在起点";
    private String pendingDiceStatus = "骰子：准备就绪";
    private String pendingCardStatus = "卡片：准备就绪";

    public WinterVillageView() {
        this(true);
    }

    public WinterVillageView(boolean showHud) {
        getStyleClass().add("winter-village-view");
        setStyle("-fx-background-color: radial-gradient(center 50% 42%, radius 78%,"
                + " #b9c8d6 0%, #9fb2c2 58%, #7f95aa 100%);");

        worldRoot.setDepthTest(DepthTest.ENABLE);
        configureCamera();
        configureLights();

        subScene = new SubScene(worldRoot, 1200, 800, true, SceneAntialiasing.DISABLED);
        subScene.setFill(Color.TRANSPARENT);
        subScene.setCache(false);
        subScene.setCamera(camera);

        Pane viewport = new Pane();
        viewport.setMinSize(0, 0);
        viewport.getChildren().add(subScene);
        subScene.widthProperty().bind(viewport.widthProperty());
        subScene.heightProperty().bind(viewport.heightProperty());
        getChildren().add(viewport);

        Pane interaction = new Pane();
        interaction.setPickOnBounds(true);
        interaction.setCursor(Cursor.OPEN_HAND);
        installMouseControls(interaction);
        getChildren().add(interaction);

        loadModel();

        if (showHud) {
            buildHud();
            getChildren().add(hud);
        }

        installKeyboardReset();
    }

    public PerspectiveCamera getCamera() { return camera; }
    public OrbitCameraController getCameraController() { return cameraController; }
    public Group getModelRoot() { return modelRoot; }
    public PlayerTokenLayer getPlayerTokens() { return playerTokens; }
    public DiceController getDiceController() { return diceController; }
    public CardController getCardController() { return cardController; }
    public WinterVillageProfile getProfile() { return profile; }

    private void configureCamera() {
        camera.setNearClip(0.1);
        camera.setFarClip(500);
        camera.setFieldOfView(30.2);
        camera.setVerticalFieldOfView(true);
        cameraController = new OrbitCameraController(camera, profile);
    }

    private void configureLights() {
        AmbientLight ambient = new AmbientLight(Color.rgb(72, 83, 96));
        DirectionalLight key = new DirectionalLight(Color.rgb(141, 135, 126));
        key.setDirection(new Point3D(0.42, -0.62, -0.66));
        DirectionalLight fill = new DirectionalLight(Color.rgb(48, 64, 82));
        fill.setDirection(new Point3D(-0.55, -0.25, 0.72));
        PointLight warmBounce = new PointLight(Color.rgb(52, 29, 13));
        warmBounce.setTranslateX(profile.boardX);
        warmBounce.setTranslateY(3.4);
        warmBounce.setTranslateZ(profile.boardZ);
        warmBounce.setMaxRange(24);
        PointLight boardLamp = new PointLight(Color.rgb(94, 80, 62));
        boardLamp.setTranslateX(profile.boardX);
        boardLamp.setTranslateY(5.4);
        boardLamp.setTranslateZ(profile.boardZ);
        boardLamp.setMaxRange(10);
        worldRoot.getChildren().addAll(ambient, key, fill, warmBounce, boardLamp);
    }

    private void loadModel() {
        Path asset = resolveAsset();
        try {
            long start = System.nanoTime();
            Group loaded = ModelAssetLoader.load(asset);
            Bounds bounds = loaded.getBoundsInLocal();
            int meshViews = countMeshViews(loaded);
            long millis = (System.nanoTime() - start) / 1_000_000;

            System.out.printf(Locale.ROOT,
                    "[winter-village] profile=%s asset=%s -> %.3f x %.3f x %.3f m,"
                            + " minY=%.3f, maxY=%.3f, MeshViews=%d, %d ms%n",
                    profile.id, asset.getFileName(), bounds.getWidth(), bounds.getHeight(),
                    bounds.getDepth(), bounds.getMinY(), bounds.getMaxY(), meshViews, millis);

            modelRoot = loaded;
            worldRoot.getChildren().add(loaded);

            if (Math.abs(profile.pine05ShiftX) > 1e-9) {
                Node pine05 = loaded.lookup("#PLACEMENT_PINE_05");
                if (pine05 != null) {
                    shiftNodeX(pine05, profile.pine05ShiftX);
                    System.out.printf(Locale.ROOT,
                            "[winter-village] moved PLACEMENT_PINE_05 by %.2f m in X%n",
                            profile.pine05ShiftX);
                }
            }

            Group board = MonopolyBoardModel.create(
                    profile.boardWidth, profile.boardX, profile.boardY, profile.boardZ);
            worldRoot.getChildren().add(board);

            playerTokens = new PlayerTokenLayer(worldRoot, profile);
            playerTokens.setOnPlayerMoved(this::onPlayerMoved);
            setTokenStatus("6 枚棋子 · 全部在起点");

            diceController = new DiceController(worldRoot, profile, playerTokens);
            diceController.setOnRollResult(this::onDiceResult);
            diceController.setOnStateChanged(this::onDiceStateChanged);
            diceController.setOnTurnComplete(this::onDiceTurnComplete);

            cardController = new CardController(worldRoot, profile, cameraController);
            cardController.setOnStateChanged(this::onCardStateChanged);
            cardController.setOnCardDrawn(this::onCardDrawn);
            diceController.setExternalBusy(() -> cardController != null && cardController.isBusy());
            cardController.setExternalBusy(() -> diceController != null && diceController.isBusy());
        } catch (IOException | RuntimeException ex) {
            ex.printStackTrace();
            Label error = new Label("无法加载 Winter Village 模型\n" + ex.getMessage());
            error.setWrapText(true);
            error.setMaxWidth(620);
            error.setStyle("-fx-font-family: 'PingFang SC'; -fx-font-size: 15px;"
                    + " -fx-text-fill: #5b1c1c; -fx-background-color: rgba(255,245,240,0.92);"
                    + " -fx-background-radius: 12; -fx-padding: 18 22 18 22;");
            StackPane.setAlignment(error, Pos.CENTER);
            getChildren().add(error);
        }
    }

    private void onPlayerMoved(int playerIndex, int tileIndex) {
        String color = playerTokens.getPlayer(playerIndex).colorName();
        String tileName = TileDef.all()[tileIndex].name();
        setTokenStatus("玩家 " + (playerIndex + 1) + "（" + color + "）→ "
                + tileName + " · 第 " + (tileIndex + 1) + " 格");
    }

    private void setTokenStatus(String text) {
        pendingTokenStatus = text;
        if (tokenStatus != null) tokenStatus.setText(text);
    }

    private void onDiceResult(DiceController.RollResult result) {
        setDiceStatus("骰子：" + result.die1() + " + " + result.die2()
                + " = " + result.total() + " · 前进 " + result.total() + " 格");
    }

    // ============================================================
    // FIXED: Re-enable the button + show "double" message when IDLE
    // ============================================================
    private void onDiceStateChanged(DiceController.State state) {
        updateBusyControls();
        if (state == DiceController.State.ROLLING) {
            setDiceStatus("骰子：滚动中…");
        } else if (state == DiceController.State.MOVING
                && diceController.getLastResult() != null) {
            DiceController.RollResult result = diceController.getLastResult();
            setDiceStatus("骰子：" + result.die1() + " + " + result.die2()
                    + " = " + result.total() + " · 移动中…");
        } else if (state == DiceController.State.IDLE) {
            DiceController.RollResult result = diceController.getLastResult();
            if (result != null && result.die1() == result.die2()) {
                setDiceStatus("骰子：" + result.die1() + " + " + result.die2()
                        + " = " + result.total() + " · 双数！请再掷一次");
            } else if (result != null) {
                setDiceStatus("骰子：" + result.die1() + " + " + result.die2()
                        + " = " + result.total() + " · 移动完成");
            }
        }
    }

    private void onDiceTurnComplete() {
        DiceController.RollResult result = diceController.getLastResult();
        if (result != null) {
            setDiceStatus("骰子：" + result.die1() + " + " + result.die2()
                    + " = " + result.total() + " · 移动完成");
        }
    }

    private void onCardStateChanged(CardController.State state) {
        updateBusyControls();
        if (state == CardController.State.DRAWING) {
            setCardStatus("卡片：抽取中…");
        }
    }

    private void onCardDrawn(CardController.DrawnCard drawn) {
        setCardStatus(drawn.type().displayName() + "：" + drawn.card().title());
    }

    private void updateBusyControls() {
        if (rollButton == null) return;
        boolean diceBusy = diceController != null && diceController.isBusy();
        boolean cardBusy = cardController != null && cardController.isBusy();
        boolean busy = diceBusy || cardBusy;
        rollButton.setDisable(busy);
        if (activationButton != null) activationButton.setDisable(busy);
        if (resourceButton != null) resourceButton.setDisable(busy);
        if (resetTokensButton != null) resetTokensButton.setDisable(busy);
        if (diceController != null) {
            rollButton.setText(switch (diceController.getState()) {
                case IDLE -> "掷骰子";
                case ROLLING -> "掷骰中…";
                case RESULT_READY -> "结算中…";
                case MOVING -> "移动中…";
            });
        }
    }

    private void setDiceStatus(String text) {
        pendingDiceStatus = text;
        if (diceStatus != null) diceStatus.setText(text);
    }

    private void setCardStatus(String text) {
        pendingCardStatus = text;
        if (cardStatus != null) cardStatus.setText(text);
    }

    private void rollDiceFromHud() {
        if (diceController != null && cardController != null && cardController.isBusy()) return;
        if (diceController != null) diceController.rollRandom();
    }

    private void drawActivationFromHud() {
        if (diceController != null && diceController.isBusy()) return;
        if (cardController != null && cardController.draw(CardType.ACTIVATION)) {
            setCardStatus("卡片：抽取激活卡…");
        }
    }

    private void drawResourceFromHud() {
        if (diceController != null && diceController.isBusy()) return;
        if (cardController != null && cardController.draw(CardType.RESOURCE)) {
            setCardStatus("卡片：抽取公共资源卡…");
        }
    }

    private void resetPlayersFromHud() {
        if (playerTokens == null
                || (diceController != null && diceController.isBusy())
                || (cardController != null && cardController.isBusy())) {
            return;
        }
        playerTokens.resetAll(true);
        setTokenStatus("6 枚棋子 · 归位到起点");
        setDiceStatus("骰子：准备就绪");
        setCardStatus("卡片：准备就绪");
    }

    private void installMouseControls(Pane interaction) {
        final double[] last = new double[2];
        interaction.setOnMousePressed(event -> {
            last[0] = event.getSceneX();
            last[1] = event.getSceneY();
            interaction.setCursor(Cursor.CLOSED_HAND);
        });
        interaction.setOnMouseDragged(event -> {
            double dx = event.getSceneX() - last[0];
            double dy = event.getSceneY() - last[1];
            last[0] = event.getSceneX();
            last[1] = event.getSceneY();
            cameraController.orbitBy(dx, dy);
        });
        interaction.setOnMouseReleased(event -> interaction.setCursor(Cursor.OPEN_HAND));
        interaction.setOnScroll(event -> cameraController.zoomBy(event.getDeltaY()));
    }

    private void installKeyboardReset() {
        sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene == null) return;
            newScene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.R || event.getCode() == KeyCode.HOME) {
                    cameraController.reset();
                    event.consume();
                } else if (event.getCode() == KeyCode.SPACE
                        || event.getCode() == KeyCode.D) {
                    rollDiceFromHud();
                    event.consume();
                } else if (event.getCode() == KeyCode.A) {
                    drawActivationFromHud();
                    event.consume();
                } else if (event.getCode() == KeyCode.C) {
                    drawResourceFromHud();
                    event.consume();
                } else if (event.getCode() == KeyCode.T) {
                    resetPlayersFromHud();
                    event.consume();
                }
            });
        });
    }

    private void buildHud() {
        hud.setPickOnBounds(false);
        hud.setMouseTransparent(false);

        Label eyebrow = new Label(profile.hudEyebrow);
        eyebrow.setStyle("-fx-font-family: 'Avenir Next'; -fx-font-size: 10px;"
                + " -fx-font-weight: bold; -fx-text-fill: rgba(244,250,255,0.78);");

        Label title = new Label(profile.hudTitle);
        title.setStyle("-fx-font-family: 'PingFang SC'; -fx-font-size: 30px;"
                + " -fx-font-weight: bold; -fx-text-fill: #fdfefe;"
                + " -fx-effect: dropshadow(gaussian, rgba(19,38,55,0.35), 14, 0.2, 0, 2);");

        Label subtitle = new Label(profile.hudSubtitle);
        subtitle.setStyle("-fx-font-family: 'Avenir Next'; -fx-font-size: 13px;"
                + " -fx-text-fill: rgba(244,250,255,0.88);");

        VBox titleBox = new VBox(3, eyebrow, title, subtitle);
        titleBox.setMouseTransparent(true);

        rollButton = new Button("掷骰子");
        styleRollButton(rollButton);
        rollButton.setOnAction(event -> rollDiceFromHud());

        activationButton = new Button("激活卡");
        styleAccentButton(activationButton, CardType.ACTIVATION.accent());
        activationButton.setOnAction(event -> drawActivationFromHud());

        resourceButton = new Button("资源卡");
        styleAccentButton(resourceButton, CardType.RESOURCE.accent());
        resourceButton.setOnAction(event -> drawResourceFromHud());

        resetTokensButton = new Button("棋子归位");
        styleHudButton(resetTokensButton);
        resetTokensButton.setOnAction(event -> resetPlayersFromHud());

        Button resetCamera = new Button("重置视角");
        styleHudButton(resetCamera);
        resetCamera.setOnAction(event -> cameraController.reset());

        HBox actions = new HBox(8, rollButton, activationButton, resourceButton,
                resetTokensButton, resetCamera);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPickOnBounds(false);

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        HBox top = new HBox(16, titleBox, topSpacer, actions);
        top.setAlignment(Pos.TOP_LEFT);
        top.setPadding(new Insets(24, 26, 0, 28));
        top.setPickOnBounds(false);
        hud.setTop(top);

        Label hint = new Label("拖动旋转  ·  滚轮缩放  ·  空格掷骰  ·  A 激活  ·  C 资源  ·  T 归位  ·  R 重置");
        hint.setMouseTransparent(true);
        hint.setStyle("-fx-font-family: 'PingFang SC'; -fx-font-size: 12px;"
                + " -fx-text-fill: #eef6fd; -fx-background-color: rgba(20,36,52,0.52);"
                + " -fx-background-radius: 14; -fx-padding: 8 14 8 14;");

        cardStatus = new Label(pendingCardStatus);
        cardStatus.setMouseTransparent(true);
        cardStatus.setStyle("-fx-font-family: 'PingFang SC'; -fx-font-size: 12px;"
                + " -fx-text-fill: #f4faff; -fx-background-color: rgba(20,36,52,0.58);"
                + " -fx-background-radius: 12; -fx-padding: 7 12 7 12;");

        diceStatus = new Label(pendingDiceStatus);
        diceStatus.setMouseTransparent(true);
        diceStatus.setStyle("-fx-font-family: 'PingFang SC'; -fx-font-size: 12px;"
                + " -fx-font-weight: bold; -fx-text-fill: #ffe6a8;"
                + " -fx-background-color: rgba(48,36,16,0.66);"
                + " -fx-background-radius: 12; -fx-padding: 7 12 7 12;");

        tokenStatus = new Label(pendingTokenStatus);
        tokenStatus.setMouseTransparent(true);
        tokenStatus.setStyle("-fx-font-family: 'PingFang SC'; -fx-font-size: 12px;"
                + " -fx-text-fill: #f8fcff; -fx-background-color: rgba(20,36,52,0.58);"
                + " -fx-background-radius: 12; -fx-padding: 7 12 7 12;");

        Label cameraInfo = new Label(profile.cameraInfo);
        cameraInfo.setMouseTransparent(true);
        cameraInfo.setStyle("-fx-font-family: 'Avenir Next'; -fx-font-size: 11px;"
                + " -fx-text-fill: rgba(238,247,255,0.80);"
                + " -fx-background-color: rgba(20,36,52,0.38);"
                + " -fx-background-radius: 12; -fx-padding: 7 12 7 12;");

        Region bottomSpacer = new Region();
        HBox.setHgrow(bottomSpacer, Priority.ALWAYS);
        HBox bottomRow = new HBox(12, hint, bottomSpacer, cameraInfo);
        bottomRow.setAlignment(Pos.CENTER_LEFT);
        bottomRow.setPickOnBounds(false);

        VBox bottom = new VBox(8, cardStatus, diceStatus, tokenStatus, bottomRow);
        bottom.setAlignment(Pos.BOTTOM_LEFT);
        bottom.setPadding(new Insets(0, 26, 20, 28));
        bottom.setPickOnBounds(false);
        hud.setBottom(bottom);

        updateBusyControls();
    }

    private static void styleAccentButton(Button button, Color accent) {
        button.setFocusTraversable(false);
        String normal = "-fx-font-family: 'PingFang SC'; -fx-font-size: 13px;"
                + " -fx-font-weight: bold; -fx-text-fill: #fffaf0;"
                + " -fx-background-color: " + web(accent) + "; -fx-background-radius: 18;"
                + " -fx-border-color: rgba(255,255,255,0.55); -fx-border-radius: 18;"
                + " -fx-padding: 9 16 9 16; -fx-cursor: hand;";
        String hover = "-fx-font-family: 'PingFang SC'; -fx-font-size: 13px;"
                + " -fx-font-weight: bold; -fx-text-fill: #ffffff;"
                + " -fx-background-color: " + web(accent.brighter()) + "; -fx-background-radius: 18;"
                + " -fx-border-color: rgba(255,255,255,0.85); -fx-border-radius: 18;"
                + " -fx-padding: 9 16 9 16; -fx-cursor: hand;";
        button.setStyle(normal);
        button.setOnMouseEntered(event -> button.setStyle(hover));
        button.setOnMouseExited(event -> button.setStyle(normal));
    }

    private static String web(Color color) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255));
    }

    private static void styleRollButton(Button button) {
        button.setFocusTraversable(false);
        String normal = "-fx-font-family: 'PingFang SC'; -fx-font-size: 13px;"
                + " -fx-font-weight: bold; -fx-text-fill: #2b2113;"
                + " -fx-background-color: #e3bf63; -fx-background-radius: 18;"
                + " -fx-border-color: rgba(255,248,220,0.85); -fx-border-radius: 18;"
                + " -fx-padding: 9 20 9 20; -fx-cursor: hand;";
        String hover = "-fx-font-family: 'PingFang SC'; -fx-font-size: 13px;"
                + " -fx-font-weight: bold; -fx-text-fill: #20180c;"
                + " -fx-background-color: #f4d37a; -fx-background-radius: 18;"
                + " -fx-border-color: rgba(255,252,235,0.95); -fx-border-radius: 18;"
                + " -fx-padding: 9 20 9 20; -fx-cursor: hand;";
        button.setStyle(normal);
        button.setOnMouseEntered(event -> button.setStyle(hover));
        button.setOnMouseExited(event -> button.setStyle(normal));
    }

    private static void styleHudButton(Button button) {
        button.setFocusTraversable(false);
        String normal = "-fx-font-family: 'PingFang SC'; -fx-font-size: 13px;"
                + " -fx-text-fill: #f4faff; -fx-background-color: rgba(23,41,58,0.50);"
                + " -fx-background-radius: 18; -fx-border-color: rgba(255,255,255,0.35);"
                + " -fx-border-radius: 18; -fx-padding: 9 18 9 18; -fx-cursor: hand;";
        String hover = "-fx-font-family: 'PingFang SC'; -fx-font-size: 13px;"
                + " -fx-text-fill: #ffffff; -fx-background-color: rgba(23,41,58,0.72);"
                + " -fx-background-radius: 18; -fx-border-color: rgba(255,255,255,0.55);"
                + " -fx-border-radius: 18; -fx-padding: 9 18 9 18; -fx-cursor: hand;";
        button.setStyle(normal);
        button.setOnMouseEntered(event -> button.setStyle(hover));
        button.setOnMouseExited(event -> button.setStyle(normal));
    }

    private Path resolveAsset() {
        String configured = System.getProperty("winter.village.asset");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        Path profileAsset = Path.of("assets", profile.assetFileName);
        if (Files.isRegularFile(profileAsset)) {
            return profileAsset.toAbsolutePath().normalize();
        }
        Path fromRoot = Path.of("winter_village_javafx", "assets", profile.assetFileName);
        if (Files.isRegularFile(fromRoot)) {
            return fromRoot.toAbsolutePath().normalize();
        }
        Path defaultAsset = Path.of("assets", "winter_village_sample.glb");
        if (Files.isRegularFile(defaultAsset)) {
            return defaultAsset.toAbsolutePath().normalize();
        }
        return profileAsset.toAbsolutePath().normalize();
    }

    private static int countMeshViews(Node node) {
        int count = node instanceof MeshView ? 1 : 0;
        if (node instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                count += countMeshViews(child);
            }
        }
        return count;
    }

    private static void shiftNodeX(Node node, double dx) {
        if (node == null) return;
        for (Transform transform : node.getTransforms()) {
            if (transform instanceof Affine affine) {
                affine.setTx(affine.getTx() + dx);
                return;
            }
        }
        node.setTranslateX(node.getTranslateX() + dx);
    }
}