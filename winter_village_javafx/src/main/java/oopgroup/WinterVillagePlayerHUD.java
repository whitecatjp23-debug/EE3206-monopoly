package oopgroup;

import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Glow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import monopoly.board.Board;
import monopoly.board.BoardFactory;
import monopoly.board.BoardPropertyInfo;
import monopoly.property.Property;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class WinterVillagePlayerHUD extends Application {

    public interface WinterVillageUIBridge {
        void onMatchStarted(List<Player> players);
        void onRollDiceRequested(Player activePlayer, int d1, int d2);
        default void onPlayerMoved(Player player, int fromTile, int toTile) { }
        default void onPlayerTeleported(Player player, int fromTile, int toTile) { onPlayerMoved(player, fromTile, toTile); }
        default void onPlayerBankrupt(Player player) { }
        void onPropertyPurchased(Player buyer, PropertyData property);
        void onBuildingUpdated(Player owner, PropertyData property, int newBuildingLevel);
        void onTradeExecuted(Player p1, Player p2);
        void onTurnEnded(Player nextPlayer);
        void onDrawChanceCard();
        void onDrawCommunityCard();
    }

    private WinterVillageUIBridge bridge;
    public void setWinterVillageUIBridge(WinterVillageUIBridge bridge) { this.bridge = bridge; }

    public static class SoundFX {
        private static boolean muted = false;
        public static void setMuted(boolean m) { muted = m; }
        public static void playCash() { playTone(880, 70, 1175, 110); }
        public static void playDice() { playTone(320, 45, 420, 45); }
        public static void playAlert() { playTone(260, 120, 200, 150); }
        public static void playFanfare() { playTone(523, 80, 784, 160); }
        private static void playTone(int freq1, int ms1, int freq2, int ms2) {
            if (muted) return;
            new Thread(() -> {
                try {
                    AudioFormat af = new AudioFormat(16000f, 8, 1, true, false);
                    SourceDataLine sdl = AudioSystem.getSourceDataLine(af);
                    sdl.open(af); sdl.start();
                    writeFreq(sdl, freq1, ms1); writeFreq(sdl, freq2, ms2);
                    sdl.drain(); sdl.close();
                } catch (Exception ignored) {}
            }).start();
        }
        private static void writeFreq(SourceDataLine sdl, int hz, int msecs) {
            byte[] buf = new byte[msecs * 16];
            for (int i = 0; i < buf.length; i++) {
                double angle = i / (16000.0 / hz) * 2.0 * Math.PI;
                buf[i] = (byte) (Math.sin(angle) * 50.0);
            }
            sdl.write(buf, 0, buf.length);
        }
    }

    public static class InsufficientFundsException extends Exception {
        public InsufficientFundsException(String message) { super(message); }
    }
    public static class InvalidPropertyOperationException extends Exception {
        public InvalidPropertyOperationException(String message) { super(message); }
    }

    public enum ColorGroup {
        BROWN("Timber Creek", 2, 50, "#8D5B4C"), LIGHT_BLUE("Glacier Pass", 3, 50, "#38BDF8"),
        PINK("Aurora Woods", 3, 100, "#F472B6"), ORANGE("Fireside Lodge", 3, 100, "#FB923C"),
        RED("Cardinal Peak", 3, 150, "#F87171"), YELLOW("Sunlit Ridge", 3, 150, "#FACC15"),
        GREEN("Evergreen Pine", 3, 200, "#4ADE80"), DARK_BLUE("Frostbite Summit", 2, 200, "#60A5FA"),
        RAILROAD("Polar Express", 4, 0, "#94A3B8"), UTILITY("Alpine Works", 2, 0, "#CBD5E1");

        private final String themeName; private final int setSize; private final int buildingCost; private final String hexColor;
        ColorGroup(String themeName, int setSize, int buildingCost, String hexColor) {
            this.themeName = themeName; this.setSize = setSize; this.buildingCost = buildingCost; this.hexColor = hexColor;
        }
        public String getThemeName() { return themeName; }
        public int getSetSize() { return setSize; }
        public int getBuildingCost() { return buildingCost; }
        public String getHexColor() { return hexColor; }
        public Color getFxColor() { return Color.web(hexColor); }
        public boolean isBuildable() { return buildingCost > 0; }
    }

    public static class PropertyData {
        public static final int HOTEL_LEVEL = 5;
        private final int tileIndex; private final String name; private final ColorGroup colorGroup;
        private final int purchasePrice; private final int[] rentTiers;
        private final ObjectProperty<Player> owner = new SimpleObjectProperty<>(null);
        private final IntegerProperty buildingCount = new SimpleIntegerProperty(0);
        private final BooleanProperty mortgaged = new SimpleBooleanProperty(false);

        public PropertyData(int tileIndex, String name, ColorGroup colorGroup, int purchasePrice, int[] rentTiers) {
            this.tileIndex = tileIndex; this.name = name; this.colorGroup = colorGroup;
            this.purchasePrice = purchasePrice; this.rentTiers = rentTiers.clone();
        }
        public int calculateRent(int diceSum) {
            Player o = owner.get();
            if (o == null || mortgaged.get()) return 0;
            if (colorGroup == ColorGroup.RAILROAD) return 25 * (int) Math.pow(2, o.countOwnedInGroup(ColorGroup.RAILROAD) - 1);
            if (colorGroup == ColorGroup.UTILITY) return diceSum * (o.countOwnedInGroup(ColorGroup.UTILITY) >= 2 ? 10 : 4);
            int b = buildingCount.get();
            return b == 0 ? (o.hasFullColorSet(colorGroup) ? rentTiers[0] * 2 : rentTiers[0]) : rentTiers[b];
        }
        public void addBuilding() throws InvalidPropertyOperationException {
            if (!colorGroup.isBuildable()) throw new InvalidPropertyOperationException(name + " cannot be developed.");
            if (mortgaged.get()) throw new InvalidPropertyOperationException("Cannot build on mortgaged property.");
            if (buildingCount.get() >= HOTEL_LEVEL) throw new InvalidPropertyOperationException(name + " already has a Grand Hotel.");
            buildingCount.set(buildingCount.get() + 1);
        }
        public void removeBuilding() throws InvalidPropertyOperationException {
            if (buildingCount.get() <= 0) throw new InvalidPropertyOperationException("No buildings to sell.");
            buildingCount.set(buildingCount.get() - 1);
        }
        public int getTileIndex() { return tileIndex; }
        public String getName() { return name; }
        public ColorGroup getColorGroup() { return colorGroup; }
        public int getPurchasePrice() { return purchasePrice; }
        public int getMortgageValue() { return purchasePrice / 2; }
        public int getBuildingCost() { return colorGroup.getBuildingCost(); }
        public int[] getRentTiers() { return rentTiers.clone(); }
        public Player getOwner() { return owner.get(); }
        public void setOwner(Player p) { owner.set(p); }
        public int getBuildingCount() { return buildingCount.get(); }
        public boolean hasHotel() { return buildingCount.get() == HOTEL_LEVEL; }
        public int getHouseCount() { return buildingCount.get() < HOTEL_LEVEL ? buildingCount.get() : 0; }
        public boolean isMortgaged() { return mortgaged.get(); }
        public void setMortgaged(boolean m) { mortgaged.set(m); }
        @Override public String toString() { return name + " ($" + purchasePrice + ")"; }
    }

    public static class Player {
        public static final int BOARD_SIZE = 40; public static final int JAIL_POSITION = 10; public static final int GO_SALARY = 200;
        private final String id, name, tokenEmoji, hexColor;
        private final IntegerProperty balance = new SimpleIntegerProperty();
        private final IntegerProperty position = new SimpleIntegerProperty(0);
        private final BooleanProperty inJail = new SimpleBooleanProperty(false);
        private final BooleanProperty bankrupt = new SimpleBooleanProperty(false);
        private final ObservableList<PropertyData> ownedProperties = FXCollections.observableArrayList();

        private int jailTurns = 0;
        private int getOutOfJailCards = 0;

        public Player(String id, String name, String tokenEmoji, String hexColor, int startingCash) {
            this.id = id; this.name = name; this.tokenEmoji = tokenEmoji; this.hexColor = hexColor;
            this.balance.set(startingCash);
        }
        public void addMoney(int amount) { balance.set(balance.get() + amount); }
        public void deductMoney(int amount) throws InsufficientFundsException {
            if (balance.get() < amount) throw new InsufficientFundsException(name + " needs $" + amount + " but only has $" + balance.get());
            balance.set(balance.get() - amount);
        }
        public void moveSteps(int steps) {
            int next = position.get() + steps;
            if (next >= BOARD_SIZE) addMoney(GO_SALARY);
            position.set(((next % BOARD_SIZE) + BOARD_SIZE) % BOARD_SIZE);
        }
        public void sendToJail() { inJail.set(true); jailTurns = 0; position.set(JAIL_POSITION); }
        public void releaseFromJail() { inJail.set(false); jailTurns = 0; }
        public int getJailTurns() { return jailTurns; }
        public void incrementJailTurns() { this.jailTurns++; }
        public int getGetOutOfJailCards() { return getOutOfJailCards; }
        public void addGetOutOfJailCard() { getOutOfJailCards++; }
        public void useGetOutOfJailCard() { if (getOutOfJailCards > 0) getOutOfJailCards--; }

        public void addProperty(PropertyData p) { if (!ownedProperties.contains(p)) { ownedProperties.add(p); p.setOwner(this); } }
        public void removeProperty(PropertyData p) { if (ownedProperties.remove(p)) p.setOwner(null); }
        public int countOwnedInGroup(ColorGroup g) {
            int count = 0; for (PropertyData p : ownedProperties) if (p.getColorGroup() == g) count++; return count;
        }
        public boolean hasFullColorSet(ColorGroup g) { return countOwnedInGroup(g) == g.getSetSize(); }
        public int calculateNetWorth() {
            int total = balance.get();
            for (PropertyData p : ownedProperties) {
                total += p.getBuildingCount() * (p.getBuildingCost() / 2);
                if (!p.isMortgaged()) total += p.getMortgageValue();
            }
            return total;
        }
        public String getId() { return id; }
        public String getName() { return name; }
        public String getTokenEmoji() { return tokenEmoji; }
        public String getHexColor() { return hexColor; }
        public Color getFxColor() { return Color.web(hexColor); }
        public int getBalance() { return balance.get(); }
        public void setBalance(int amount) { balance.set(amount); }
        public IntegerProperty balanceProperty() { return balance; }
        public int getPosition() { return position.get(); }
        public IntegerProperty positionProperty() { return position; }
        public void setPosition(int pos) { position.set(pos); }
        public boolean isInJail() { return inJail.get(); }
        public boolean isBankrupt() { return bankrupt.get(); }
        public void setBankrupt(boolean b) { bankrupt.set(b); }
        public void restoreJailState(boolean inJail, int jailTurns) {
            this.inJail.set(inJail);
            this.jailTurns = jailTurns;
        }
        public ObservableList<PropertyData> getOwnedProperties() { return ownedProperties; }
        @Override public String toString() { return tokenEmoji + " " + name + " ($" + balance.get() + ")"; }
    }

    public static class PropertyService {
        public void buyProperty(Player buyer, PropertyData prop) throws InsufficientFundsException, InvalidPropertyOperationException {
            if (prop.getOwner() != null) throw new InvalidPropertyOperationException(prop.getName() + " is already owned.");
            buyer.deductMoney(prop.getPurchasePrice());
            buyer.addProperty(prop);
        }
        public void buildHouseOrHotel(Player owner, PropertyData target) throws InsufficientFundsException, InvalidPropertyOperationException {
            if (target.getOwner() != owner) throw new InvalidPropertyOperationException("Not owner.");
            ColorGroup g = target.getColorGroup();
            if (!g.isBuildable()) throw new InvalidPropertyOperationException("Cannot build here.");
            if (!owner.hasFullColorSet(g)) throw new InvalidPropertyOperationException("Collect all " + g.getSetSize() + " " + g.getThemeName() + " deeds first!");
            int min = Integer.MAX_VALUE;
            for (PropertyData p : owner.getOwnedProperties()) {
                if (p.getColorGroup() == g) {
                    if (p.isMortgaged()) throw new InvalidPropertyOperationException("Unmortgage all deeds in set first.");
                    if (p.getBuildingCount() < min) min = p.getBuildingCount();
                }
            }
            if (target.getBuildingCount() > min) throw new InvalidPropertyOperationException("Build evenly across set!");
            owner.deductMoney(target.getBuildingCost());
            target.addBuilding();
        }
        public void sellHouseOrHotel(Player owner, PropertyData target) throws InvalidPropertyOperationException {
            if (target.getOwner() != owner) throw new InvalidPropertyOperationException("Not owner.");
            ColorGroup g = target.getColorGroup();
            int max = 0;
            for (PropertyData p : owner.getOwnedProperties()) {
                if (p.getColorGroup() == g && p.getBuildingCount() > max) max = p.getBuildingCount();
            }
            if (target.getBuildingCount() < max) throw new InvalidPropertyOperationException("Sell evenly across set!");
            target.removeBuilding();
            owner.addMoney(target.getBuildingCost() / 2);
        }
        public void mortgageProperty(Player owner, PropertyData prop) throws InvalidPropertyOperationException {
            if (prop.isMortgaged()) throw new InvalidPropertyOperationException("Already mortgaged.");
            if (prop.getBuildingCount() > 0) throw new InvalidPropertyOperationException("Sell buildings first.");
            prop.setMortgaged(true);
            owner.addMoney(prop.getMortgageValue());
        }
        public void unmortgageProperty(Player owner, PropertyData prop) throws InsufficientFundsException, InvalidPropertyOperationException {
            if (!prop.isMortgaged()) throw new InvalidPropertyOperationException("Not mortgaged.");
            int cost = (int) Math.ceil(prop.getMortgageValue() * 1.1);
            owner.deductMoney(cost);
            prop.setMortgaged(false);
        }
        public void executeTrade(Player p1, List<PropertyData> p1GiveProps, int p1Cash, Player p2, List<PropertyData> p2GiveProps, int p2Cash) throws InsufficientFundsException, InvalidPropertyOperationException {
            if (p1Cash > 0 && p1.getBalance() < p1Cash) throw new InsufficientFundsException(p1.getName() + " lacks $" + p1Cash);
            if (p2Cash > 0 && p2.getBalance() < p2Cash) throw new InsufficientFundsException(p2.getName() + " lacks $" + p2Cash);
            if (p1Cash > 0) { p1.deductMoney(p1Cash); p2.addMoney(p1Cash); }
            if (p2Cash > 0) { p2.deductMoney(p2Cash); p1.addMoney(p2Cash); }
            for (PropertyData p : p1GiveProps) { p1.removeProperty(p); p2.addProperty(p); }
            for (PropertyData p : p2GiveProps) { p2.removeProperty(p); p1.addProperty(p); }
        }
        public int payRent(Player tenant, PropertyData prop, int diceSum) throws InsufficientFundsException {
            Player owner = prop.getOwner();
            if (owner == null || owner.equals(tenant) || prop.isMortgaged()) return 0;
            int rent = prop.calculateRent(diceSum);
            tenant.deductMoney(rent);
            owner.addMoney(rent);
            return rent;
        }
    }

    private final StackPane rootStack = new StackPane();
    private final BorderPane mainMenuPane = new BorderPane();
    private final BorderPane gameHudPane = new BorderPane();
    private final StackPane modalOverlayLayer = new StackPane();

    private final PropertyService propertyService = new PropertyService();
    private final ObservableList<Player> players = FXCollections.observableArrayList();
    private final ObservableList<PropertyData> boardProperties = FXCollections.observableArrayList();
    private final IntegerProperty currentPlayerIndex = new SimpleIntegerProperty(0);

    private Label activeTurnTitle;
    private Label activeTurnSub;
    private Label activeCashDisplay;
    private HBox diceDisplayBox;
    private Button rollDiceButton;
    private ComboBox<PropertyData> tileLandingCombo;
    private VBox leftPlayerPanel;
    private VBox rightPlayerPanel;
    private int lastDiceSum = 7;
    private int turnNumber = 1;

    private VBox centerBoardArea;
    private VBox centerActionsCard;
    private Node board3DView;

    @Override
    public void start(Stage primaryStage) {
        setupWinterBoardData();
        Pane snowCanvas = createAmbientSnowLayer();
        buildMainMenuView();
        buildGameHUDView();

        modalOverlayLayer.setVisible(false);
        modalOverlayLayer.setStyle("-fx-background-color: rgba(10, 25, 47, 0.75);");

        rootStack.getChildren().addAll(snowCanvas, mainMenuPane, gameHudPane, modalOverlayLayer);
        gameHudPane.setVisible(false);

        Scene scene = new Scene(rootStack, 1400, 900);
        primaryStage.setTitle("Winter Village Monopoly — Deluxe Borderless Edition");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void buildMainMenuView() {
        mainMenuPane.setStyle("-fx-background-color: linear-gradient(to bottom right, #0A192F, #1E3A8A, #0284C7);");
        VBox centerCard = new VBox(22);
        centerCard.setAlignment(Pos.CENTER);
        centerCard.setMaxWidth(620);
        centerCard.setPadding(new Insets(40, 50, 45, 50));
        centerCard.setStyle("-fx-background-color: rgba(255, 255, 255, 0.97); -fx-background-radius: 30; -fx-border-color: #38BDF8; -fx-border-width: 4; -fx-border-radius: 30;");
        centerCard.setEffect(new DropShadow(50, Color.rgb(0, 15, 30, 0.75)));

        Label titleBadge = new Label("❄  WINTER VILLAGE MONOPOLY  ❄");
        titleBadge.setFont(Font.font("System", FontWeight.EXTRA_BOLD, 28));
        titleBadge.setTextFill(Color.WHITE);
        titleBadge.setPadding(new Insets(14, 32, 14, 32));
        titleBadge.setStyle("-fx-background-color: linear-gradient(to right, #DC2626, #EF4444); -fx-background-radius: 16; -fx-border-color: #FDE047; -fx-border-width: 2.5; -fx-border-radius: 16;");

        Label subtitle = new Label("DELUXE ANIMATED TABLETOP • IMMERSIVE VISUAL & AUDIO FX");
        subtitle.setFont(Font.font("System", FontWeight.BOLD, 11));
        subtitle.setTextFill(Color.web("#334155"));

        HBox configRow = new HBox(16);
        configRow.setAlignment(Pos.CENTER);
        ComboBox<Integer> playerCountCombo = new ComboBox<>(FXCollections.observableArrayList(2, 3, 4, 5, 6));
        playerCountCombo.setValue(4); styleComboBox(playerCountCombo);
        ComboBox<String> cashCombo = new ComboBox<>(FXCollections.observableArrayList("$1500 (Classic Winter)", "$2000 (Alpine Tycoon)", "$1000 (Blizzard Rush)"));
        cashCombo.setValue("$1500 (Classic Winter)"); styleComboBox(cashCombo);
        configRow.getChildren().addAll(new Label("Players:"), playerCountCombo, new Label("Cash:"), cashCombo);

        GridPane rosterGrid = new GridPane();
        rosterGrid.setHgap(12); rosterGrid.setVgap(10); rosterGrid.setAlignment(Pos.CENTER);
        String[] defaultNames = {"Alice", "Bob", "Charlie", "Diana", "Evan", "Fiona"};
        String[] tokenOptions = {"🎩 Crimson Top Hat", "🚗 Cobalt Roadster", "🐕 Amber Husky", "⛵ Emerald Icebreaker", "👢 Violet Snowboot", "🔔 Gold Sleigh Bell"};
        String[] hexColors = {"#DC2626", "#2563EB", "#D97706", "#16A34A", "#9333EA", "#0891B2"};

        TextField[] nameInputs = new TextField[6];
        ComboBox<?>[] tokenSelects = new ComboBox<?>[6];
        Circle[] colorSwatches = new Circle[6];

        for (int i = 0; i < 6; i++) {
            final int idx = i;
            colorSwatches[i] = new Circle(10, Color.web(hexColors[i]));
            colorSwatches[i].setStroke(Color.web("#0F172A")); colorSwatches[i].setStrokeWidth(1.5);
            nameInputs[i] = new TextField(defaultNames[i]); nameInputs[i].setPrefWidth(160);
            nameInputs[i].setStyle("-fx-background-radius: 8; -fx-border-color: #CBD5E1; -fx-border-radius: 8; -fx-padding: 6 10;");
            ComboBox<String> tCombo = new ComboBox<>(FXCollections.observableArrayList(tokenOptions));
            tCombo.setValue(tokenOptions[i]); tCombo.setPrefWidth(190); styleComboBox(tCombo); tokenSelects[i] = tCombo;
            rosterGrid.add(colorSwatches[i], 0, idx); rosterGrid.add(new Label("P" + (idx + 1)), 1, idx);
            rosterGrid.add(nameInputs[i], 2, idx); rosterGrid.add(tCombo, 3, idx);
        }

        Runnable updateRowAvailability = () -> {
            int count = playerCountCombo.getValue();
            for (int i = 0; i < 6; i++) {
                boolean active = i < count;
                nameInputs[i].setDisable(!active); tokenSelects[i].setDisable(!active); colorSwatches[i].setOpacity(active ? 1.0 : 0.25);
            }
        };
        playerCountCombo.setOnAction(e -> updateRowAvailability.run());
        updateRowAvailability.run();

        Button startMatchBtn = createInteractiveButton("▶  ENTER WINTER VILLAGE", "#16A34A", "#15803D");
        startMatchBtn.setFont(Font.font("System", FontWeight.BOLD, 16));
        startMatchBtn.setPrefWidth(320); startMatchBtn.setPrefHeight(48);

        startMatchBtn.setOnAction(e -> {
            int count = playerCountCombo.getValue();
            int startCash = cashCombo.getValue().contains("2000") ? 2000 : (cashCombo.getValue().contains("1000") ? 1000 : 1500);
            players.clear();
            for (int i = 0; i < count; i++) {
                String pName = nameInputs[i].getText().trim();
                if (pName.isEmpty()) pName = "Player " + (i + 1);
                String emoji = tokenSelects[i].getValue().toString().split(" ")[0];
                players.add(new Player("P" + (i + 1), pName, emoji, hexColors[i], startCash));
            }
            setupWinterBoardData();
            currentPlayerIndex.set(0);
            turnNumber = 1;

            try {
                Node board3D = wintervillage.WinterVillageApp.createBoard3D();
                attachBoardView(board3D);
                wintervillage.tokens.PlayerTokenLayer tokenLayer = wintervillage.WinterVillageApp.getTokenLayer();
                setWinterVillageUIBridge(new wintervillage.WinterVillageBridge(this, board3D, tokenLayer));
            } catch (Throwable t) {
                System.err.println("[HUD] 3D integration skipped: " + t);
                t.printStackTrace();
            }

            refreshPlayerPortfolios();
            updateTopTurnBanner();
            SoundFX.playFanfare();
            if (bridge != null) bridge.onMatchStarted(new ArrayList<>(players));
            showCinematicAlert("❄ MATCH STARTED!", "Welcome to Winter Village Monopoly!\n" + count + " players are seated around the board with $" + startCash + " each.", "#1E3A8A");
            switchScreenWithFade(mainMenuPane, gameHudPane);
        });

        centerCard.getChildren().addAll(titleBadge, subtitle, configRow, rosterGrid, startMatchBtn);
        mainMenuPane.setCenter(centerCard);
    }

    private void showGameOverAlert(Player winner) {
        SoundFX.playFanfare();
        VBox alertBox = new VBox(16);
        alertBox.setMaxWidth(440);
        alertBox.setPadding(new Insets(28));
        alertBox.setAlignment(Pos.CENTER);
        alertBox.setStyle("-fx-background-color: white; -fx-background-radius: 28; -fx-border-color: #16A34A; -fx-border-width: 4; -fx-border-radius: 28;");
        alertBox.setEffect(new DropShadow(45, Color.rgb(0, 0, 0, 0.65)));

        Label title = new Label("🏆 GAME OVER!");
        title.setFont(Font.font("System", FontWeight.EXTRA_BOLD, 22));
        title.setTextFill(Color.web("#16A34A"));

        Label body = new Label(winner != null ? winner.getTokenEmoji() + " " + winner.getName() + " WINS!" : "Everyone went bankrupt!");
        body.setFont(Font.font("System", FontWeight.BOLD, 14));
        body.setTextFill(Color.web("#1E293B"));
        body.setWrapText(true);
        body.setAlignment(Pos.CENTER);

        Button okBtn = createInteractiveButton("Back to Lobby", "#16A34A", "#15803D");
        okBtn.setPrefWidth(200);
        okBtn.setOnAction(e -> {
            closeModalOverlay();
            switchScreenWithFade(gameHudPane, mainMenuPane);
        });

        alertBox.getChildren().addAll(title, body, okBtn);
        showModalCard(alertBox);
    }

    private void buildGameHUDView() {
        gameHudPane.setStyle("-fx-background-color: linear-gradient(to bottom, #0F172A, #1E293B, #0F172A);");
        gameHudPane.setPadding(new Insets(16));

        HBox topBar = new HBox(20);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(12, 22, 12, 22));
        topBar.setStyle("-fx-background-color: rgba(15, 23, 42, 0.9); -fx-background-radius: 18; -fx-border-color: #38BDF8; -fx-border-width: 1.5; -fx-border-radius: 18;");
        topBar.setEffect(new DropShadow(15, Color.rgb(0, 0, 0, 0.5)));

        VBox turnInfoBox = new VBox(3);
        activeTurnTitle = new Label("ACTIVE TURN: 🎩 ALICE");
        activeTurnTitle.setFont(Font.font("System", FontWeight.EXTRA_BOLD, 20)); activeTurnTitle.setTextFill(Color.WHITE);
        activeTurnSub = new Label("Tile #0 (GO)  •  Deeds: 0  •  Net Worth: $1500");
        activeTurnSub.setFont(Font.font("System", FontWeight.NORMAL, 12)); activeTurnSub.setTextFill(Color.web("#94A3B8"));
        turnInfoBox.getChildren().addAll(activeTurnTitle, activeTurnSub);

        Region spacer1 = new Region(); HBox.setHgrow(spacer1, Priority.ALWAYS);
        activeCashDisplay = new Label("$1500");
        activeCashDisplay.setFont(Font.font("System", FontWeight.EXTRA_BOLD, 28)); activeCashDisplay.setTextFill(Color.web("#4ADE80")); activeCashDisplay.setEffect(new Glow(0.4));
        diceDisplayBox = new HBox(10); diceDisplayBox.setAlignment(Pos.CENTER); renderDiceFaces(4, 3);
        rollDiceButton = createInteractiveButton("🎲 Roll Dice", "#DC2626", "#B91C1C");
        rollDiceButton.setOnAction(e -> triggerAnimatedDiceRoll());
        Region spacer2 = new Region(); HBox.setHgrow(spacer2, Priority.ALWAYS);
        Button lobbyBtn = createInteractiveButton("⌂ Lobby", "#475569", "#334155");
        lobbyBtn.setOnAction(e -> switchScreenWithFade(gameHudPane, mainMenuPane));

        Button saveBtn = createInteractiveButton("💾 Save", "#0F766E", "#0D5D57");
        saveBtn.setOnAction(e -> handleSaveGame());
        Button loadBtn = createInteractiveButton("📂 Load", "#B45309", "#92400E");
        loadBtn.setOnAction(e -> handleLoadGame());

        topBar.getChildren().addAll(turnInfoBox, spacer1, activeCashDisplay, diceDisplayBox, rollDiceButton, spacer2, saveBtn, loadBtn, lobbyBtn);
        gameHudPane.setTop(topBar);

        leftPlayerPanel = new VBox(12); leftPlayerPanel.setPrefWidth(330); leftPlayerPanel.setPadding(new Insets(10, 6, 10, 6));
        rightPlayerPanel = new VBox(12); rightPlayerPanel.setPrefWidth(330); rightPlayerPanel.setPadding(new Insets(10, 6, 10, 6));

        centerBoardArea = new VBox(20);
        centerBoardArea.setAlignment(Pos.CENTER);
        centerBoardArea.setPadding(new Insets(20));
        centerBoardArea.setStyle("-fx-background-color: #0B132B; -fx-background-radius: 24; -fx-border-color: #38BDF8; -fx-border-width: 2.5; -fx-border-radius: 24;");
        centerBoardArea.setEffect(new DropShadow(25, Color.rgb(0, 0, 0, 0.6)));

        Label boardWatermark = new Label("❄ WINTER VILLAGE 3D BOARD ❄");
        boardWatermark.setFont(Font.font("System", FontWeight.EXTRA_BOLD, 18)); boardWatermark.setTextFill(Color.web("#38BDF8")); boardWatermark.setEffect(new Glow(0.5));
        Label boardSubText = new Label("Centered Tabletop Control Hub\n(Synchronized with S5 3D View)");
        boardSubText.setFont(Font.font("System", FontWeight.NORMAL, 12)); boardSubText.setTextFill(Color.web("#64748B")); boardSubText.setAlignment(Pos.CENTER);

        centerActionsCard = new VBox(12);
        centerActionsCard.setAlignment(Pos.CENTER); centerActionsCard.setPadding(new Insets(18));
        centerActionsCard.setStyle("-fx-background-color: rgba(255, 255, 255, 0.05); -fx-background-radius: 16;");

        Label actionTitle = new Label("PLAYER OPERATIONS DOCK");
        actionTitle.setFont(Font.font("System", FontWeight.BOLD, 13)); actionTitle.setTextFill(Color.web("#E2E8F0"));
        tileLandingCombo = new ComboBox<>(boardProperties); tileLandingCombo.setPrefWidth(260); styleComboBox(tileLandingCombo);

        HBox actionRow1 = new HBox(10); actionRow1.setAlignment(Pos.CENTER);
        Button buyLandBtn = createInteractiveButton("🏠 Buy / Auction", "#16A34A", "#15803D"); buyLandBtn.setOnAction(e -> openBuyOrAuctionOverlay(tileLandingCombo.getValue()));
        Button portfolioBtn = createInteractiveButton("🏗 Build Cabins", "#2563EB", "#1D4ED8"); portfolioBtn.setOnAction(e -> openRealEstateManagerOverlay());
        actionRow1.getChildren().addAll(buyLandBtn, portfolioBtn);

        HBox actionRow2 = new HBox(10); actionRow2.setAlignment(Pos.CENTER);
        Button tradeBtn = createInteractiveButton("🤝 Trade Offer", "#D97706", "#B45309"); tradeBtn.setOnAction(e -> openInteractiveTradeOverlay());
        Button jailBtn = createInteractiveButton("🚔 Jail Controls", "#9333EA", "#7E22CE"); jailBtn.setOnAction(e -> openJailOverlay());
        actionRow2.getChildren().addAll(tradeBtn, jailBtn);

        Button endTurnBtn = createInteractiveButton("End Turn ➔", "#334155", "#1E293B"); endTurnBtn.setPrefWidth(260); endTurnBtn.setOnAction(e -> advanceToNextPlayer());
        centerActionsCard.getChildren().addAll(actionTitle, tileLandingCombo, actionRow1, actionRow2, endTurnBtn);
        centerBoardArea.getChildren().addAll(boardWatermark, boardSubText, centerActionsCard);

        BorderPane tabletopLayout = new BorderPane();
        tabletopLayout.setLeft(leftPlayerPanel); tabletopLayout.setRight(rightPlayerPanel); tabletopLayout.setCenter(centerBoardArea);
        BorderPane.setMargin(centerBoardArea, new Insets(10, 16, 10, 16));
        gameHudPane.setCenter(tabletopLayout);
    }

    public VBox attachBoardView(Node board3D) {
        if (centerBoardArea == null || centerActionsCard == null) throw new IllegalStateException("buildGameHUDView() must run first");
        this.board3DView = board3D;
        VBox boardContainer = new VBox(); VBox.setVgrow(boardContainer, Priority.ALWAYS);
        boardContainer.getChildren().add(board3D);
        if (board3D instanceof Region r) {
            r.prefWidthProperty().bind(boardContainer.widthProperty());
            r.prefHeightProperty().bind(boardContainer.heightProperty());
        }
        centerBoardArea.getChildren().setAll(boardContainer, centerActionsCard);
        return boardContainer;
    }

    public Player getCurrentPlayer() { return players.get(currentPlayerIndex.get()); }
    public ObservableList<Player> getPlayers() { return players; }
    public int getLastDiceSum() { return lastDiceSum; }
    public Node getBoard3DView() { return board3DView; }

    private void refreshPlayerPortfolios() {
        Platform.runLater(() -> {
            leftPlayerPanel.getChildren().clear(); rightPlayerPanel.getChildren().clear();
            for (int i = 0; i < players.size(); i++) {
                Player p = players.get(i);
                VBox card = createPlayerPortfolioCard(p, i == currentPlayerIndex.get());
                if (i % 2 == 0) leftPlayerPanel.getChildren().add(card); else rightPlayerPanel.getChildren().add(card);
            }
        });
    }

    private VBox createPlayerPortfolioCard(Player player, boolean isTurn) {
        VBox card = new VBox(10); card.setPadding(new Insets(12));
        String borderCol = isTurn ? player.getHexColor() : "#64748B";
        String borderWidth = isTurn ? "3" : "1.5";
        String bgCol = player.isBankrupt() ? "#E2E8F0" : "rgba(255, 255, 255, 0.96)";
        card.setStyle("-fx-background-color: " + bgCol + "; -fx-background-radius: 16; -fx-border-color: " + borderCol + "; -fx-border-width: " + borderWidth + "; -fx-border-radius: 16;");
        card.setEffect(new DropShadow(isTurn ? 20 : 6, isTurn ? player.getFxColor().deriveColor(0, 1, 1, 0.5) : Color.rgb(0, 0, 0, 0.1)));

        HBox header = new HBox(10); header.setAlignment(Pos.CENTER_LEFT);
        StackPane avatarBadge = new StackPane();
        Circle ring = new Circle(18, player.getFxColor()); ring.setStroke(Color.web("#FDE047")); ring.setStrokeWidth(2);
        Label emojiLbl = new Label(player.getTokenEmoji()); emojiLbl.setFont(Font.font(16));
        avatarBadge.getChildren().addAll(ring, emojiLbl);

        VBox nameCol = new VBox(1);
        Label nameLbl = new Label(player.getName() + (player.isInJail() ? " [JAIL]" : "") + (player.isBankrupt() ? " [BANKRUPT]" : ""));
        nameLbl.setFont(Font.font("System", FontWeight.BOLD, 14)); nameLbl.setTextFill(Color.web("#0F172A"));
        Label posLbl = new Label("Tile #" + player.getPosition() + " • Worth: $" + player.calculateNetWorth());
        posLbl.setFont(Font.font(10)); posLbl.setTextFill(Color.web("#64748B"));
        nameCol.getChildren().addAll(nameLbl, posLbl);

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Label cashLbl = new Label("$" + player.getBalance());
        cashLbl.setFont(Font.font("System", FontWeight.EXTRA_BOLD, 15)); cashLbl.setTextFill(Color.web("#16A34A"));

        player.balanceProperty().addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> {
                cashLbl.setText("$" + newVal);
                posLbl.setText("Tile #" + player.getPosition() + " • Worth: $" + player.calculateNetWorth());
                playPopAnimation(cashLbl);
                if (player == getCurrentPlayer()) { activeCashDisplay.setText("$" + newVal); playPopAnimation(activeCashDisplay); }
            });
        });

        header.getChildren().addAll(avatarBadge, nameCol, spacer, cashLbl);

        FlowPane setBadges = new FlowPane(4, 4);
        for (ColorGroup g : ColorGroup.values()) {
            int ownedCount = player.countOwnedInGroup(g);
            if (ownedCount > 0) {
                boolean complete = (ownedCount == g.getSetSize());
                Label pill = new Label(g.getThemeName() + " " + ownedCount + "/" + g.getSetSize() + (complete ? " ★" : ""));
                pill.setFont(Font.font("System", FontWeight.BOLD, 9)); pill.setTextFill(Color.WHITE); pill.setPadding(new Insets(2, 6, 2, 6));
                pill.setStyle("-fx-background-color: " + g.getHexColor() + "; -fx-background-radius: 8;");
                setBadges.getChildren().add(pill);
            }
        }

        VBox deedListContainer = new VBox(6); deedListContainer.setPadding(new Insets(6));
        deedListContainer.setStyle("-fx-background-color: #F1F5F9; -fx-background-radius: 10;");
        if (player.getOwnedProperties().isEmpty()) {
            Label empty = new Label("No properties owned yet");
            empty.setFont(Font.font(10)); empty.setTextFill(Color.web("#94A3B8")); empty.setPadding(new Insets(8));
            deedListContainer.getChildren().add(empty);
        } else {
            for (PropertyData p : player.getOwnedProperties()) deedListContainer.getChildren().add(createClickableDeedChip(p));
        }

        ScrollPane deedScroll = new ScrollPane(deedListContainer);
        deedScroll.setFitToWidth(true); deedScroll.setPrefHeight(130); deedScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        card.getChildren().addAll(header, setBadges, deedScroll);
        return card;
    }

    private HBox createClickableDeedChip(PropertyData prop) {
        HBox chip = new HBox(6); chip.setAlignment(Pos.CENTER_LEFT); chip.setPadding(new Insets(5, 8, 5, 6));
        chip.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #CBD5E1; -fx-border-radius: 8; -fx-cursor: hand;");
        Rectangle colorStripe = new Rectangle(6, 20, prop.isMortgaged() ? Color.GRAY : prop.getColorGroup().getFxColor());
        colorStripe.setArcWidth(4); colorStripe.setArcHeight(4);
        Label nameLbl = new Label(prop.getName() + (prop.isMortgaged() ? " [M]" : ""));
        nameLbl.setFont(Font.font("System", FontWeight.BOLD, 11)); nameLbl.setTextFill(prop.isMortgaged() ? Color.GRAY : Color.web("#0F172A"));
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        String buildingEmoji = prop.hasHotel() ? "🏨" : "🏡".repeat(Math.max(0, prop.getHouseCount()));
        Label buildLbl = new Label(buildingEmoji); buildLbl.setFont(Font.font(10));
        Label rentLbl = new Label("$" + prop.calculateRent(lastDiceSum));
        rentLbl.setFont(Font.font("System", FontWeight.BOLD, 10)); rentLbl.setTextFill(Color.web("#334155"));
        chip.getChildren().addAll(colorStripe, nameLbl, spacer, buildLbl, rentLbl);
        chip.setOnMouseClicked(e -> showModalCard(buildTitleDeedVisual(prop, true)));
        return chip;
    }

    private void showCinematicAlert(String titleText, String bodyText, String accentHex) {
        SoundFX.playAlert();
        VBox alertBox = new VBox(16); alertBox.setMaxWidth(440); alertBox.setPadding(new Insets(28)); alertBox.setAlignment(Pos.CENTER);
        alertBox.setStyle("-fx-background-color: white; -fx-background-radius: 28; -fx-border-color: " + accentHex + "; -fx-border-width: 4; -fx-border-radius: 28;");
        alertBox.setEffect(new DropShadow(45, Color.rgb(0, 0, 0, 0.65)));
        Label title = new Label(titleText); title.setFont(Font.font("System", FontWeight.EXTRA_BOLD, 22)); title.setTextFill(Color.web(accentHex));
        Label body = new Label(bodyText); body.setFont(Font.font("System", FontWeight.BOLD, 14)); body.setTextFill(Color.web("#1E293B")); body.setWrapText(true); body.setAlignment(Pos.CENTER);
        Button okBtn = createInteractiveButton("Awesome!", accentHex, "#0F172A"); okBtn.setPrefWidth(170); okBtn.setOnAction(e -> closeModalOverlay());
        alertBox.getChildren().addAll(title, body, okBtn);
        showModalCard(alertBox);
    }

    private VBox buildTitleDeedVisual(PropertyData prop, boolean includeCloseButton) {
        VBox deed = new VBox(12); deed.setMaxWidth(340); deed.setPadding(new Insets(18)); deed.setAlignment(Pos.TOP_CENTER);
        deed.setStyle("-fx-background-color: white; -fx-background-radius: 20; -fx-border-color: #0F172A; -fx-border-width: 3; -fx-border-radius: 20;");
        deed.setEffect(new DropShadow(30, Color.rgb(0, 0, 0, 0.5)));
        VBox banner = new VBox(2); banner.setAlignment(Pos.CENTER); banner.setPadding(new Insets(10));
        banner.setStyle("-fx-background-color: " + prop.getColorGroup().getHexColor() + "; -fx-background-radius: 12;");
        Label sub = new Label("TITLE DEED • " + prop.getColorGroup().getThemeName().toUpperCase());
        sub.setFont(Font.font("System", FontWeight.BOLD, 10)); sub.setTextFill(Color.WHITE);
        Label title = new Label(prop.getName().toUpperCase()); title.setFont(Font.font("System", FontWeight.EXTRA_BOLD, 18)); title.setTextFill(Color.WHITE);
        banner.getChildren().addAll(sub, title);
        GridPane table = new GridPane(); table.setHgap(20); table.setVgap(7); table.setAlignment(Pos.CENTER);
        int[] tiers = prop.getRentTiers();
        if (prop.getColorGroup().isBuildable()) {
            addRentRow(table, 0, "Base Rent:", "$" + tiers[0], prop.getBuildingCount() == 0);
            addRentRow(table, 1, "Monopoly Set (2x):", "$" + (tiers[0] * 2), false);
            addRentRow(table, 2, "1 Winter Cabin 🏡:", "$" + tiers[1], prop.getBuildingCount() == 1);
            addRentRow(table, 3, "2 Winter Cabins 🏡🏡:", "$" + tiers[2], prop.getBuildingCount() == 2);
            addRentRow(table, 4, "3 Winter Cabins:", "$" + tiers[3], prop.getBuildingCount() == 3);
            addRentRow(table, 5, "4 Winter Cabins:", "$" + tiers[4], prop.getBuildingCount() == 4);
            addRentRow(table, 6, "Grand Alpine Hotel 🏨:", "$" + tiers[5], prop.hasHotel());
            addRentRow(table, 7, "Cabin Build Cost:", "$" + prop.getBuildingCost() + " each", false);
        } else {
            addRentRow(table, 0, "Purchase Price:", "$" + prop.getPurchasePrice(), false);
            addRentRow(table, 1, "Current Rent:", "$" + prop.calculateRent(lastDiceSum), true);
        }
        addRentRow(table, 8, "Mortgage Value:", "$" + prop.getMortgageValue(), prop.isMortgaged());
        Label ownerTag = new Label(prop.getOwner() == null ? "STATUS: UNOWNED ($" + prop.getPurchasePrice() + ")" : "OWNER: " + prop.getOwner().getTokenEmoji() + " " + prop.getOwner().getName());
        ownerTag.setFont(Font.font("System", FontWeight.BOLD, 12)); ownerTag.setTextFill(Color.web("#1E293B"));
        deed.getChildren().addAll(banner, table, ownerTag);
        if (includeCloseButton) {
            Button closeBtn = createInteractiveButton("Close Deed", "#475569", "#334155"); closeBtn.setOnAction(e -> closeModalOverlay()); deed.getChildren().add(closeBtn);
        }
        return deed;
    }

    private void addRentRow(GridPane grid, int row, String label, String val, boolean highlight) {
        Label l = new Label(label); Label v = new Label(val);
        Font f = Font.font("System", highlight ? FontWeight.EXTRA_BOLD : FontWeight.NORMAL, 12);
        l.setFont(f); v.setFont(f);
        if (highlight) { l.setTextFill(Color.web("#16A34A")); v.setTextFill(Color.web("#16A34A")); }
        grid.add(l, 0, row); grid.add(v, 1, row);
    }

    public void openBuyOrAuctionOverlay(PropertyData prop) {
        if (prop == null) return;
        Player current = getCurrentPlayer();
        if (prop.getOwner() != null && !prop.getOwner().equals(current)) {
            int rent = prop.calculateRent(lastDiceSum);
            try {
                propertyService.payRent(current, prop, lastDiceSum);
                SoundFX.playCash();
                showCinematicAlert("💸 RENT PAID!", current.getName() + " landed on " + prop.getName() + " and paid $" + rent + " rent to " + prop.getOwner().getName() + "!", "#EF4444");
                refreshPlayerPortfolios();
            } catch (InsufficientFundsException ex) {
                showCinematicAlert("⚠ BANKRUPT!", current.getName() + " cannot afford rent!", "#DC2626");
                handleBankruptcy(current);
            }
            return;
        }
        if (prop.getOwner() != null) {
            showCinematicAlert("ℹ ALREADY OWNED", prop.getName() + " is already owned by " + prop.getOwner().getName() + ".", "#2563EB");
            return;
        }
        VBox modal = new VBox(16); modal.setMaxWidth(400); modal.setAlignment(Pos.CENTER); modal.setPadding(new Insets(22)); modal.setStyle("-fx-background-color: #F8FAFC; -fx-background-radius: 24;");
        VBox deedCard = buildTitleDeedVisual(prop, false);
        HBox buttons = new HBox(12); buttons.setAlignment(Pos.CENTER);
        Button buyBtn = createInteractiveButton("Buy ($" + prop.getPurchasePrice() + ")", "#16A34A", "#15803D");
        Button auctionBtn = createInteractiveButton("🔨 Auction", "#D97706", "#B45309");
        Button cancelBtn = createInteractiveButton("Cancel", "#64748B", "#475569");
        buyBtn.setOnAction(e -> {
            try {
                propertyService.buyProperty(current, prop);
                SoundFX.playCash();
                if (bridge != null) bridge.onPropertyPurchased(current, prop);
                closeModalOverlay(); refreshPlayerPortfolios(); updateTopTurnBanner();
                showCinematicAlert("🏠 PROPERTY PURCHASED!", current.getName() + " successfully bought " + prop.getName() + " for $" + prop.getPurchasePrice() + "!", "#16A34A");
            } catch (Exception ex) { buyBtn.setText(ex.getMessage()); }
        });
        auctionBtn.setOnAction(e -> openLiveAuctionOverlay(prop));
        cancelBtn.setOnAction(e -> closeModalOverlay());
        buttons.getChildren().addAll(buyBtn, auctionBtn, cancelBtn);
        modal.getChildren().addAll(deedCard, buttons);
        showModalCard(modal);
    }

    private void openLiveAuctionOverlay(PropertyData prop) {
        VBox box = new VBox(16); box.setMaxWidth(480); box.setPadding(new Insets(24)); box.setAlignment(Pos.CENTER);
        box.setStyle("-fx-background-color: white; -fx-background-radius: 24; -fx-border-color: #D97706; -fx-border-width: 3.5; -fx-border-radius: 24;");
        Label title = new Label("🔨 WINTER VILLAGE LIVE AUCTION"); title.setFont(Font.font("System", FontWeight.EXTRA_BOLD, 18)); title.setTextFill(Color.web("#B45309"));
        Label propTitle = new Label(prop.getName() + " (List Price: $" + prop.getPurchasePrice() + ")"); propTitle.setFont(Font.font("System", FontWeight.BOLD, 14));
        final int[] highBid = {10}; final Player[] highBidder = {null};
        Label statusLbl = new Label("Starting Bid: $10 — Place your bids!"); statusLbl.setFont(Font.font("System", FontWeight.BOLD, 15)); statusLbl.setTextFill(Color.web("#0F172A"));
        VBox biddersList = new VBox(8);
        for (Player bidder : players) {
            if (bidder.isBankrupt()) continue;
            HBox row = new HBox(10); row.setAlignment(Pos.CENTER_LEFT); row.setPadding(new Insets(6, 10, 6, 10)); row.setStyle("-fx-background-color: #F8FAFC; -fx-background-radius: 10;");
            Label nameLbl = new Label(bidder.getTokenEmoji() + " " + bidder.getName() + " ($" + bidder.getBalance() + ")"); nameLbl.setFont(Font.font("System", FontWeight.BOLD, 13));
            Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
            Button b10 = createInteractiveButton("+$10", "#2563EB", "#1D4ED8"); Button b50 = createInteractiveButton("+$50", "#16A34A", "#15803D");
            b10.setOnAction(ev -> { int next = (highBidder[0] == null) ? 10 : highBid[0] + 10; if (bidder.getBalance() >= next) { highBid[0] = next; highBidder[0] = bidder; statusLbl.setText("Highest Bid: $" + next + " by " + bidder.getTokenEmoji() + " " + bidder.getName()); SoundFX.playDice(); playPopAnimation(statusLbl); } });
            b50.setOnAction(ev -> { int next = (highBidder[0] == null) ? 50 : highBid[0] + 50; if (bidder.getBalance() >= next) { highBid[0] = next; highBidder[0] = bidder; statusLbl.setText("Highest Bid: $" + next + " by " + bidder.getTokenEmoji() + " " + bidder.getName()); SoundFX.playDice(); playPopAnimation(statusLbl); } });
            row.getChildren().addAll(nameLbl, sp, b10, b50); biddersList.getChildren().add(row);
        }
        Button soldBtn = createInteractiveButton("🔨 SOLD! Award Property", "#DC2626", "#B91C1C");
        soldBtn.setOnAction(e -> {
            if (highBidder[0] != null) {
                try {
                    highBidder[0].deductMoney(highBid[0]); highBidder[0].addProperty(prop); SoundFX.playFanfare();
                    if (bridge != null) bridge.onPropertyPurchased(highBidder[0], prop);
                    refreshPlayerPortfolios(); updateTopTurnBanner(); closeModalOverlay();
                    showCinematicAlert("🔨 AUCTION WON!", highBidder[0].getName() + " won " + prop.getName() + " at auction for $" + highBid[0] + "!", "#D97706");
                } catch (InsufficientFundsException ignored) {}
            } else { closeModalOverlay(); }
        });
        box.getChildren().addAll(title, propTitle, statusLbl, biddersList, soldBtn); showModalCard(box);
    }

    public void openRealEstateManagerOverlay() {
        Player p = getCurrentPlayer();
        if (p.getOwnedProperties().isEmpty()) { showCinematicAlert("ℹ PORTFOLIO EMPTY", p.getName() + " doesn't own any properties yet to manage.", "#2563EB"); return; }
        VBox box = new VBox(14); box.setMaxWidth(640); box.setPadding(new Insets(24)); box.setStyle("-fx-background-color: white; -fx-background-radius: 24;");
        Label header = new Label("🏗 " + p.getName().toUpperCase() + "'s REAL ESTATE PORTFOLIO"); header.setFont(Font.font("System", FontWeight.EXTRA_BOLD, 18));
        Label feedbackLbl = new Label("Select a property below to build Winter Cabins, Hotels, or Mortgage."); feedbackLbl.setTextFill(Color.web("#475569"));
        ListView<PropertyData> listView = new ListView<>(p.getOwnedProperties()); listView.setPrefHeight(240); listView.getSelectionModel().selectFirst();
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(PropertyData item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); } else { setGraphic(createClickableDeedChip(item)); }
            }
        });
        HBox actions = new HBox(10); actions.setAlignment(Pos.CENTER);
        Button buildBtn = createInteractiveButton("+ Build Cabin/Hotel", "#16A34A", "#15803D");
        Button sellBtn = createInteractiveButton("- Sell Building", "#D97706", "#B45309");
        Button mortBtn = createInteractiveButton("Mortgage", "#78350F", "#451A03");
        Button unmortBtn = createInteractiveButton("Unmortgage", "#2563EB", "#1D4ED8");
        Button closeBtn = createInteractiveButton("Done", "#475569", "#334155");
        buildBtn.setOnAction(e -> { PropertyData sel = listView.getSelectionModel().getSelectedItem(); if (sel == null) return;
            try { propertyService.buildHouseOrHotel(p, sel); SoundFX.playCash(); feedbackLbl.setText("✔ Built on " + sel.getName() + "! New Rent: $" + sel.calculateRent(lastDiceSum)); feedbackLbl.setTextFill(Color.web("#16A34A")); if (bridge != null) bridge.onBuildingUpdated(p, sel, sel.getBuildingCount()); listView.refresh(); refreshPlayerPortfolios(); showCinematicAlert("🏗 BUILDING UPGRADED!", p.getName() + " upgraded " + sel.getName() + " (Level " + sel.getBuildingCount() + ")!", "#16A34A"); }
            catch (Exception ex) { feedbackLbl.setText("⚠ " + ex.getMessage()); feedbackLbl.setTextFill(Color.web("#DC2626")); } });
        sellBtn.setOnAction(e -> { PropertyData sel = listView.getSelectionModel().getSelectedItem(); if (sel == null) return;
            try { propertyService.sellHouseOrHotel(p, sel); SoundFX.playCash(); feedbackLbl.setText("💰 Sold building on " + sel.getName() + " for +$" + (sel.getBuildingCost() / 2)); listView.refresh(); refreshPlayerPortfolios(); }
            catch (Exception ex) { feedbackLbl.setText("⚠ " + ex.getMessage()); feedbackLbl.setTextFill(Color.web("#DC2626")); } });
        mortBtn.setOnAction(e -> { PropertyData sel = listView.getSelectionModel().getSelectedItem(); if (sel == null) return;
            try { propertyService.mortgageProperty(p, sel); SoundFX.playCash(); feedbackLbl.setText("🏦 Mortgaged " + sel.getName() + " for +$" + sel.getMortgageValue()); listView.refresh(); refreshPlayerPortfolios(); }
            catch (Exception ex) { feedbackLbl.setText("⚠ " + ex.getMessage()); feedbackLbl.setTextFill(Color.web("#DC2626")); } });
        unmortBtn.setOnAction(e -> { PropertyData sel = listView.getSelectionModel().getSelectedItem(); if (sel == null) return;
            try { propertyService.unmortgageProperty(p, sel); SoundFX.playCash(); feedbackLbl.setText("🔓 Unmortgaged " + sel.getName()); listView.refresh(); refreshPlayerPortfolios(); }
            catch (Exception ex) { feedbackLbl.setText("⚠ " + ex.getMessage()); feedbackLbl.setTextFill(Color.web("#DC2626")); } });
        closeBtn.setOnAction(e -> closeModalOverlay());
        actions.getChildren().addAll(buildBtn, sellBtn, mortBtn, unmortBtn, closeBtn);
        box.getChildren().addAll(header, feedbackLbl, listView, actions); showModalCard(box);
    }

    public void openInteractiveTradeOverlay() {
        Player p1 = getCurrentPlayer();
        ObservableList<Player> partners = FXCollections.observableArrayList();
        for (Player p : players) { if (!p.equals(p1) && !p.isBankrupt()) partners.add(p); }
        if (partners.isEmpty()) return;
        VBox modal = new VBox(16); modal.setMaxWidth(760); modal.setPadding(new Insets(24)); modal.setStyle("-fx-background-color: white; -fx-background-radius: 24;");
        Label title = new Label("🤝 WINTER VILLAGE TRADE EXCHANGE"); title.setFont(Font.font("System", FontWeight.EXTRA_BOLD, 18));
        Label statusLbl = new Label("Select properties and adjust cash sliders on both sides."); statusLbl.setTextFill(Color.web("#475569"));
        HBox splitPane = new HBox(16); splitPane.setAlignment(Pos.CENTER);
        VBox leftCol = new VBox(10); leftCol.setPrefWidth(340); leftCol.setPadding(new Insets(14)); leftCol.setStyle("-fx-background-color: #F8FAFC; -fx-background-radius: 16; -fx-border-color: #CBD5E1; -fx-border-radius: 16;");
        Label p1Header = new Label(p1.getTokenEmoji() + " " + p1.getName() + " Gives (Max $" + p1.getBalance() + ")"); p1Header.setFont(Font.font("System", FontWeight.BOLD, 13));
        ListView<PropertyData> p1List = new ListView<>(p1.getOwnedProperties()); p1List.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE); p1List.setPrefHeight(180);
        Slider p1CashSlider = new Slider(0, Math.max(0, p1.getBalance()), 0);
        Label p1CashLbl = new Label("Cash Offered: $0");
        p1CashSlider.valueProperty().addListener((o, oldV, newV) -> p1CashLbl.setText("Cash Offered: $" + (newV.intValue() / 25 * 25)));
        leftCol.getChildren().addAll(p1Header, p1List, p1CashLbl, p1CashSlider);

        VBox rightCol = new VBox(10); rightCol.setPrefWidth(340); rightCol.setPadding(new Insets(14)); rightCol.setStyle("-fx-background-color: #F8FAFC; -fx-background-radius: 16; -fx-border-color: #CBD5E1; -fx-border-radius: 16;");
        ComboBox<Player> partnerCombo = new ComboBox<>(partners); partnerCombo.getSelectionModel().selectFirst(); styleComboBox(partnerCombo);
        ListView<PropertyData> p2List = new ListView<>(); p2List.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE); p2List.setPrefHeight(180);
        Slider p2CashSlider = new Slider(0, 1500, 0);
        Label p2CashLbl = new Label("Partner Cash: $0");
        p2CashSlider.valueProperty().addListener((o, oldV, newV) -> p2CashLbl.setText("Partner Cash: $" + (newV.intValue() / 25 * 25)));

        Runnable refreshPartnerSide = () -> {
            Player p2 = partnerCombo.getValue();
            if (p2 != null) { p2List.setItems(p2.getOwnedProperties()); p2CashSlider.setMax(Math.max(0, p2.getBalance())); p2CashSlider.setValue(0); }
        };
        partnerCombo.setOnAction(e -> refreshPartnerSide.run());
        refreshPartnerSide.run();
        rightCol.getChildren().addAll(partnerCombo, p2List, p2CashLbl, p2CashSlider);
        splitPane.getChildren().addAll(leftCol, rightCol);

        HBox footer = new HBox(12); footer.setAlignment(Pos.CENTER);
        Button executeBtn = createInteractiveButton("✔ Accept & Execute Trade", "#16A34A", "#15803D");
        Button cancelBtn = createInteractiveButton("Cancel", "#64748B", "#475569");
        executeBtn.setOnAction(e -> {
            Player p2 = partnerCombo.getValue();
            List<PropertyData> give1 = new ArrayList<>(p1List.getSelectionModel().getSelectedItems());
            List<PropertyData> give2 = new ArrayList<>(p2List.getSelectionModel().getSelectedItems());
            int c1 = ((int) p1CashSlider.getValue()) / 25 * 25; int c2 = ((int) p2CashSlider.getValue()) / 25 * 25;
            try {
                propertyService.executeTrade(p1, give1, c1, p2, give2, c2);
                SoundFX.playFanfare();
                if (bridge != null) bridge.onTradeExecuted(p1, p2);
                closeModalOverlay(); refreshPlayerPortfolios(); updateTopTurnBanner();
                showCinematicAlert("🤝 TRADE SUCCESSFUL!", "Deal executed seamlessly between " + p1.getName() + " and " + p2.getName() + "!", "#D97706");
            } catch (Exception ex) { statusLbl.setText("⚠ " + ex.getMessage()); statusLbl.setTextFill(Color.web("#DC2626")); }
        });
        cancelBtn.setOnAction(e -> closeModalOverlay());
        footer.getChildren().addAll(executeBtn, cancelBtn);
        modal.getChildren().addAll(title, statusLbl, splitPane, footer); showModalCard(modal);
    }

    private void openJailOverlay() {
        Player p = getCurrentPlayer();
        VBox box = new VBox(16); box.setMaxWidth(400); box.setPadding(new Insets(24)); box.setAlignment(Pos.CENTER); box.setStyle("-fx-background-color: white; -fx-background-radius: 24;");
        Label title = new Label("🚔 WINTER VILLAGE JAIL"); title.setFont(Font.font("System", FontWeight.EXTRA_BOLD, 18)); title.setTextFill(Color.web("#9333EA"));
        Label info = new Label("You are in jail. Turn " + p.getJailTurns() + "/3.\nRoll a double to get out, pay $50, or use a card.");
        info.setWrapText(true); info.setAlignment(Pos.CENTER);

        HBox btns = new HBox(12); btns.setAlignment(Pos.CENTER);

        Button rollJailBtn = createInteractiveButton("🎲 Roll", "#2563EB", "#1D4ED8");
        rollJailBtn.setOnAction(e -> performRoll(p, true));

        Button bailBtn = createInteractiveButton("Pay $50", "#16A34A", "#15803D");
        bailBtn.setOnAction(e -> {
            try {
                p.deductMoney(50); p.releaseFromJail();
                SoundFX.playCash(); closeModalOverlay();
                refreshPlayerPortfolios(); updateTopTurnBanner();
                showCinematicAlert("🔓 RELEASED", p.getName() + " paid $50 bail.", "#16A34A");
                rollDiceButton.setDisable(false);
            } catch (InsufficientFundsException ex) { info.setText(ex.getMessage()); }
        });

        btns.getChildren().addAll(rollJailBtn, bailBtn);

        if (p.getGetOutOfJailCards() > 0) {
            Button cardBtn = createInteractiveButton("🎫 Use Card", "#D97706", "#B45309");
            cardBtn.setOnAction(e -> {
                p.useGetOutOfJailCard(); p.releaseFromJail();
                SoundFX.playFanfare(); closeModalOverlay();
                refreshPlayerPortfolios(); updateTopTurnBanner();
                showCinematicAlert("🎫 CARD USED", p.getName() + " used a Get Out of Jail Free card!", "#D97706");
                rollDiceButton.setDisable(false);
            });
            btns.getChildren().add(cardBtn);
        }

        box.getChildren().addAll(title, info, btns); showModalCard(box);
    }

    private void triggerAnimatedDiceRoll() {
        Player p = getCurrentPlayer();
        if (p.isInJail()) {
            openJailOverlay();
            return;
        }
        performRoll(p, false);
    }

    private void performRoll(Player p, boolean isJailRoll) {
        rollDiceButton.setDisable(true);
        SoundFX.playDice();
        Random rand = new Random();
        final int d1 = rand.nextInt(6) + 1;
        final int d2 = rand.nextInt(6) + 1;

        if (bridge != null) bridge.onRollDiceRequested(p, d1, d2);

        Timeline hudShake = new Timeline(new KeyFrame(Duration.millis(75), e -> {
            renderDiceFaces(rand.nextInt(6) + 1, rand.nextInt(6) + 1);
        }));
        hudShake.setCycleCount(20);
        hudShake.setOnFinished(e -> {
            lastDiceSum = d1 + d2;
            renderDiceFaces(d1, d2);
            PauseTransition settle = new PauseTransition(Duration.millis(150));
            settle.setOnFinished(evt -> {
                if (isJailRoll) handleJailRoll(p, d1, d2);
                else            handleNormalRoll(p, d1, d2);
            });
            settle.play();
        });
        hudShake.play();
    }

    private void handleJailRoll(Player p, int d1, int d2) {
        closeModalOverlay();
        int oldPos = p.getPosition();

        if (d1 == d2) {
            p.releaseFromJail();
            p.moveSteps(d1 + d2);
            if (bridge != null) bridge.onPlayerMoved(p, oldPos, p.getPosition());
            showCinematicAlert("🔓 DOUBLE ROLLED!", p.getName() + " rolled doubles and gets out of jail!", "#16A34A");
            refreshPlayerPortfolios();
            updateTopTurnBanner();
            triggerLandingEvent(p);
        } else {
            p.incrementJailTurns();
            if (p.getJailTurns() >= 3) {
                showCinematicAlert("🚔 FORCED BAIL", "Failed 3 times! " + p.getName() + " must pay $50 and move.", "#DC2626");
                try {
                    p.deductMoney(50);
                    p.releaseFromJail();
                    p.moveSteps(d1 + d2);
                    if (bridge != null) bridge.onPlayerMoved(p, oldPos, p.getPosition());
                    refreshPlayerPortfolios();
                    updateTopTurnBanner();
                    triggerLandingEvent(p);
                } catch (InsufficientFundsException ex) {
                    handleBankruptcy(p);
                    rollDiceButton.setDisable(false);
                }
            } else {
                showCinematicAlert("🚔 NO DOUBLES", "You stay in jail. Turn " + p.getJailTurns() + "/3.", "#9333EA");
                // ★ 延遲換人，等 3D 骰子轉完
                PauseTransition pt = new PauseTransition(Duration.seconds(1.5));
                pt.setOnFinished(e -> advanceToNextPlayer());
                pt.play();
            }
        }
    }

    private void handleNormalRoll(Player p, int d1, int d2) {
        int oldPos = p.getPosition();
        p.moveSteps(lastDiceSum);
        if (bridge != null) bridge.onPlayerMoved(p, oldPos, p.getPosition());

        if (d1 == d2) {
            showCinematicAlert("✨ DOUBLES ROLLED!", p.getName() + " gets another turn after this.", "#2563EB");
        }
        refreshPlayerPortfolios();
        updateTopTurnBanner();
        triggerLandingEvent(p);
    }

    private void triggerLandingEvent(Player p) {
        int pos = p.getPosition();

        PauseTransition waitFor3DMove = new PauseTransition(Duration.seconds(1.2));
        waitFor3DMove.setOnFinished(evt -> {
            if (pos == 30) {
                int oldPos = p.getPosition();
                p.sendToJail();
                if (bridge != null) bridge.onPlayerTeleported(p, oldPos, Player.JAIL_POSITION);
                refreshPlayerPortfolios();
                updateTopTurnBanner();
                rollDiceButton.setDisable(false);
                showCinematicAlert("🚔 GO TO JAIL", "You have been arrested and sent to Jail!", "#DC2626");
                return;
            }

            if (pos == 4 || pos == 38) {
                String taxName = (pos == 4) ? "Income Tax" : "Luxury Tax";
                processTaxTile(p, 200, taxName);
                return;
            }

            if (pos == 20 || pos == 10) {
                if (pos == 20) showCinematicAlert("🅿 FREE PARKING", "Take a break, nothing happens here.", "#94A3B8");
                else           showCinematicAlert("👮 JUST VISITING", "Don't worry, you are just visiting the jail.", "#94A3B8");
                rollDiceButton.setDisable(false);
                return;
            }

            if (pos == 2 || pos == 17 || pos == 33) {
                processCardEffect(p, false);
            } else if (pos == 7 || pos == 22 || pos == 36) {
                processCardEffect(p, true);
            } else {
                PropertyData prop = findPropertyAt(pos);
                if (prop != null) openBuyOrAuctionOverlay(prop);
                else               rollDiceButton.setDisable(false);
            }
        });
        waitFor3DMove.play();
    }

    private void processTaxTile(Player p, int amount, String taxName) {
        try {
            p.deductMoney(amount);
            SoundFX.playCash();
            refreshPlayerPortfolios();
            updateTopTurnBanner();
            showCinematicAlert("💸 " + taxName.toUpperCase(), p.getName() + " paid $" + amount + " " + taxName + "!", "#DC2626");
            rollDiceButton.setDisable(false);
        } catch (InsufficientFundsException ex) {
            showCinematicAlert("⚠ BANKRUPT!", p.getName() + " cannot afford " + taxName + "!", "#DC2626");
            handleBankruptcy(p);
        }
    }

    public PropertyData findPropertyAt(int tileIndex) {
        for (PropertyData p : boardProperties) {
            if (p.getTileIndex() == tileIndex) return p;
        }
        return null;
    }

    private void processCardEffect(Player p, boolean isChance) {
        if (isChance && bridge != null) bridge.onDrawChanceCard();
        if (!isChance && bridge != null) bridge.onDrawCommunityCard();

        Random rand = new Random();
        int effect = rand.nextInt(isChance ? 12 : 10);
        String title = isChance ? "❓ CHANCE" : "🎁 COMMUNITY CHEST";

        final String[] msg = {""};
        final int[] newPos = {p.getPosition()};
        final boolean[] needMove = {false};
        final boolean[] goToJail = {false};
        final boolean[] passGo = {false};

        if (isChance) {
            switch(effect) {
                case 0 -> { p.addMoney(50); msg[0] = "Bank pays you dividend of $50!"; }
                case 1 -> { try { p.deductMoney(50); msg[0] = "Pay poor tax of $50!"; } catch(InsufficientFundsException e){ handleBankruptcy(p); msg[0] = "Pay poor tax of $50! (BANKRUPT)"; } }
                case 2, 3 -> { newPos[0] = 0; needMove[0] = true; passGo[0] = true; msg[0] = "Advance to GO! (Collect $200)"; }
                case 4, 5 -> { goToJail[0] = true; msg[0] = "Go directly to Jail! Do not pass GO!"; }
                case 6 -> { newPos[0] = 39; needMove[0] = true; if(newPos[0] < p.getPosition()) passGo[0] = true; msg[0] = "Advance to Winter Summit Citadel!"; }
                case 7 -> { newPos[0] = 5; needMove[0] = true; if(newPos[0] < p.getPosition()) passGo[0] = true; msg[0] = "Advance to North Star Station!"; }
                case 8 -> { newPos[0] = 12; needMove[0] = true; if(newPos[0] < p.getPosition()) passGo[0] = true; msg[0] = "Advance to Alpine Power!"; }
                case 9 -> { newPos[0] = (p.getPosition() - 3 + 40) % 40; needMove[0] = true; msg[0] = "Go back 3 spaces!"; }
                case 10 -> { p.addGetOutOfJailCard(); msg[0] = "Keep this Get Out of Jail Free card!"; }
                case 11 -> { try { p.deductMoney(15); msg[0] = "Speeding fine, pay $15!"; } catch(InsufficientFundsException e){ handleBankruptcy(p); msg[0] = "Speeding fine, pay $15! (BANKRUPT)"; } }
            }
        } else {
            switch(effect) {
                case 0 -> { p.addMoney(200); msg[0] = "Bank error in your favor. Collect $200!"; }
                case 1 -> { try { p.deductMoney(50); msg[0] = "Doctor's fee. Pay $50!"; } catch(InsufficientFundsException e){ handleBankruptcy(p); msg[0] = "Doctor's fee. Pay $50! (BANKRUPT)"; } }
                case 2 -> { newPos[0] = 0; needMove[0] = true; passGo[0] = true; msg[0] = "Advance to GO! (Collect $200)"; }
                case 3 -> { goToJail[0] = true; msg[0] = "Go to Jail!"; }
                case 4 -> { p.addGetOutOfJailCard(); msg[0] = "Keep this Get Out of Jail Free card!"; }
                case 5 -> { p.addMoney(100); msg[0] = "You inherit $100!"; }
                case 6 -> { try { p.deductMoney(100); msg[0] = "Hospital Fees. Pay $100!"; } catch(InsufficientFundsException e){ handleBankruptcy(p); msg[0] = "Hospital Fees. Pay $100! (BANKRUPT)"; } }
                case 7 -> { p.addMoney(25); msg[0] = "Receive $25 consultancy fee."; }
                case 8 -> { goToJail[0] = true; msg[0] = "Go to Jail!"; }
                case 9 -> { p.addMoney(10); msg[0] = "Collect $10 from the snowbank!"; }
            }
        }

        int oldPos = p.getPosition();

        if (passGo[0]) p.addMoney(200);

        if (goToJail[0]) {
            p.sendToJail();
            if (bridge != null) bridge.onPlayerTeleported(p, oldPos, Player.JAIL_POSITION);
        } else if (needMove[0]) {
            p.setPosition(newPos[0]);
            if (bridge != null) bridge.onPlayerTeleported(p, oldPos, newPos[0]);
        }

        SoundFX.playAlert();
        refreshPlayerPortfolios();
        updateTopTurnBanner();

        VBox modal = new VBox(16); modal.setMaxWidth(400); modal.setPadding(new Insets(24)); modal.setAlignment(Pos.CENTER);
        modal.setStyle("-fx-background-color: white; -fx-background-radius: 24; -fx-border-color: " + (isChance ? "#FB923C" : "#38BDF8") + "; -fx-border-width: 4;");
        Label lblTitle = new Label(title); lblTitle.setFont(Font.font("System", FontWeight.EXTRA_BOLD, 22)); lblTitle.setTextFill(Color.web(isChance ? "#FB923C" : "#38BDF8"));
        Label lblMsg = new Label(msg[0]); lblMsg.setFont(Font.font("System", FontWeight.BOLD, 15)); lblMsg.setWrapText(true); lblMsg.setAlignment(Pos.CENTER);
        Button okBtn = createInteractiveButton("Continue", "#475569", "#334155");

        okBtn.setOnAction(e -> {
            closeModalOverlay();
            if (needMove[0] && !goToJail[0]) {
                PauseTransition p3 = new PauseTransition(Duration.seconds(1.5));
                p3.setOnFinished(ev -> triggerLandingEvent(p));
                p3.play();
            } else {
                rollDiceButton.setDisable(false);
            }
        });

        modal.getChildren().addAll(lblTitle, lblMsg, okBtn);
        showModalCard(modal);
    }

    private void renderDiceFaces(int d1, int d2) { diceDisplayBox.getChildren().setAll(createDieGraphic(d1), createDieGraphic(d2)); }

    private StackPane createDieGraphic(int val) {
        StackPane die = new StackPane();
        Rectangle bg = new Rectangle(40, 40, Color.WHITE); bg.setArcWidth(10); bg.setArcHeight(10); bg.setStroke(Color.web("#94A3B8")); bg.setStrokeWidth(1.5); bg.setEffect(new DropShadow(4, Color.rgb(0, 0, 0, 0.3)));
        Label num = new Label(String.valueOf(val)); num.setFont(Font.font("System", FontWeight.EXTRA_BOLD, 18)); num.setTextFill(val == 1 || val == 6 ? Color.web("#DC2626") : Color.web("#0F172A"));
        die.getChildren().addAll(bg, num);
        return die;
    }

    private void advanceToNextPlayer() {
        if (players.isEmpty()) return;

        if (MonopolyGame.hasWinner(players)) {
            Player winner = MonopolyGame.getWinner(players);
            showGameOverAlert(winner);
            return;
        }

        currentPlayerIndex.set(MonopolyGame.nextActivePlayerIndex(players, currentPlayerIndex.get()));
        turnNumber = MonopolyGame.bumpTurn(turnNumber);

        rollDiceButton.setDisable(false);
        if (bridge != null) bridge.onTurnEnded(getCurrentPlayer());
        refreshPlayerPortfolios();
        updateTopTurnBanner();
    }

    private void handleBankruptcy(Player p) {
        SoundFX.playAlert();
        MonopolyGame.liquidateAndMarkBankrupt(p);

        if (bridge != null) bridge.onPlayerBankrupt(p);

        refreshPlayerPortfolios();
        updateTopTurnBanner();

        if (MonopolyGame.hasWinner(players)) {
            Player winner = MonopolyGame.getWinner(players);
            showGameOverAlert(winner);
            rollDiceButton.setDisable(true);
        } else {
            showCinematicAlert("💀 BANKRUPT!", p.getName() + " has gone bankrupt! All houses and properties have been released back to the bank.", "#DC2626");
        }
    }

    private void handleSaveGame() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Winter Village Monopoly");
        fc.setInitialFileName("winter_village_save.mono");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Monopoly Save", "*.mono"));
        File file = fc.showSaveDialog(null);
        if (file == null) return;
        try {
            MonopolyGame.save(file.toPath(), players, boardProperties, currentPlayerIndex.get(), lastDiceSum, turnNumber);
            SoundFX.playFanfare();
            showCinematicAlert("💾 GAME SAVED", "Saved to:\n" + file.getAbsolutePath(), "#0F766E");
        } catch (IOException ex) {
            showCinematicAlert("⚠ SAVE FAILED", ex.getMessage(), "#DC2626");
        }
    }

    private void handleLoadGame() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Load Winter Village Monopoly");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Monopoly Save", "*.mono"));
        File file = fc.showOpenDialog(null);
        if (file == null) return;
        try {
            MonopolyGame.SaveState st = MonopolyGame.load(file.toPath(), players, boardProperties);
            currentPlayerIndex.set(st.currentPlayerIndex);
            lastDiceSum = st.lastDiceSum;
            turnNumber = st.turnNumber;

            refreshPlayerPortfolios();
            updateTopTurnBanner();
            if (bridge != null) bridge.onMatchStarted(new ArrayList<>(players));

            SoundFX.playFanfare();
            showCinematicAlert("📂 GAME LOADED", "Restored " + players.size() + " players, turn " + turnNumber + ".", "#16A34A");

            if (!gameHudPane.isVisible()) {
                switchScreenWithFade(mainMenuPane, gameHudPane);
            }
        } catch (IOException ex) {
            showCinematicAlert("⚠ LOAD FAILED", ex.getMessage(), "#DC2626");
        }
    }

    private void updateTopTurnBanner() {
        Player p = getCurrentPlayer();
        activeTurnTitle.setText("ACTIVE TURN: " + p.getTokenEmoji() + " " + p.getName().toUpperCase());
        activeTurnSub.setText("Tile #" + p.getPosition() + "  •  Turn " + turnNumber + "  •  Deeds: " + p.getOwnedProperties().size() + "  •  Worth: $" + p.calculateNetWorth());
        activeCashDisplay.setText("$" + p.getBalance());
    }

    private void showModalCard(Node content) {
        modalOverlayLayer.getChildren().setAll(content); modalOverlayLayer.setVisible(true);
        content.setScaleX(0.70); content.setScaleY(0.70); content.setOpacity(0);
        ScaleTransition st = new ScaleTransition(Duration.millis(250), content); st.setToX(1.0); st.setToY(1.0);
        FadeTransition ft = new FadeTransition(Duration.millis(200), content); ft.setToValue(1.0);
        new ParallelTransition(st, ft).play();
    }

    private void closeModalOverlay() {
        Node content = modalOverlayLayer.getChildren().isEmpty() ? null : modalOverlayLayer.getChildren().get(0);
        if (content != null) {
            ScaleTransition st = new ScaleTransition(Duration.millis(150), content); st.setToX(0.7); st.setToY(0.7);
            FadeTransition ft = new FadeTransition(Duration.millis(150), content); ft.setToValue(0);
            ParallelTransition pt = new ParallelTransition(st, ft);
            pt.setOnFinished(e -> {
                modalOverlayLayer.setVisible(false);
                modalOverlayLayer.getChildren().clear();
                if (rollDiceButton != null && !getCurrentPlayer().isInJail()) {
                    rollDiceButton.setDisable(false);
                }
            });
            pt.play();
        } else { modalOverlayLayer.setVisible(false); }
    }

    private void switchScreenWithFade(Node hideNode, Node showNode) {
        showNode.setOpacity(0); showNode.setVisible(true); hideNode.setVisible(false);
        FadeTransition ft = new FadeTransition(Duration.millis(300), showNode); ft.setToValue(1.0); ft.play();
    }

    private void playPopAnimation(Node node) {
        ScaleTransition st = new ScaleTransition(Duration.millis(140), node);
        st.setFromX(1.0); st.setFromY(1.0); st.setToX(1.2); st.setToY(1.2);
        st.setAutoReverse(true); st.setCycleCount(2); st.play();
    }

    private Pane createAmbientSnowLayer() {
        Pane pane = new Pane(); pane.setMouseTransparent(true); Random r = new Random();
        for (int i = 0; i < 45; i++) {
            Circle flake = new Circle(r.nextDouble() * 2.5 + 1.5, Color.rgb(255, 255, 255, 0.7));
            flake.setCenterX(r.nextDouble() * 1400); flake.setCenterY(r.nextDouble() * 900); pane.getChildren().add(flake);
            TranslateTransition tt = new TranslateTransition(Duration.seconds(6 + r.nextDouble() * 8), flake);
            tt.setFromY(-50); tt.setToY(950); tt.setCycleCount(Animation.INDEFINITE); tt.setDelay(Duration.seconds(r.nextDouble() * 5)); tt.play();
        }
        return pane;
    }

    private Button createInteractiveButton(String text, String colorTop, String colorBottom) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: linear-gradient(to bottom, " + colorTop + ", " + colorBottom + "); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 9 16; -fx-background-radius: 12; -fx-cursor: hand;");
        btn.setEffect(new DropShadow(6, Color.rgb(0, 0, 0, 0.3)));
        btn.setOnMouseEntered(e -> { btn.setScaleX(1.05); btn.setScaleY(1.05); });
        btn.setOnMouseExited(e -> { btn.setScaleX(1.0); btn.setScaleY(1.0); });
        return btn;
    }

    private void styleComboBox(ComboBox<?> box) { box.setStyle("-fx-background-color: white; -fx-border-color: #94A3B8; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 3 8;"); }

    private void setupWinterBoardData() {
        boardProperties.clear();
        // 地產資料一律由 monopoly.board 的棋盤模型產生，這裡只負責轉成 UI 的 PropertyData，
        // 避免主程式再手抄一份名稱、價格與租金表而與模型不同步。
        Board modelBoard = BoardFactory.createWinterVillageBoard();
        for (int index = 0; index < Board.TILE_COUNT; index++) {
            Property modelProperty = modelBoard.getPropertyAt(index);
            if (modelProperty == null) continue; // 事件格（GO、機會、稅…）不是地產
            BoardPropertyInfo info = new BoardPropertyInfo(index, modelProperty);
            boardProperties.add(new PropertyData(
                    info.tileIndex(), info.name(), ColorGroup.valueOf(info.groupKey()),
                    info.purchasePrice(), info.rentTiers()));
        }
    }

    public static void main(String[] args) { launch(args); }
}