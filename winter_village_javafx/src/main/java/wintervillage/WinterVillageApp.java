package wintervillage;

import wintervillage.tokens.PlayerTokenLayer;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;

public final class WinterVillageApp extends Application {

    private static PlayerTokenLayer currentTokenLayer;
    public static WinterVillageView currentView;

    public static PlayerTokenLayer getTokenLayer() {
        return currentTokenLayer;
    }

    public static Parent createBoard3D() {
        // 1. 建立 3D 視圖 (隱藏自帶的 HUD，交由外部控制)
        currentView = new WinterVillageView(false);

        // ★ 2. 從 View 裡面把 TokenLayer 抓出來並賦值
        currentTokenLayer = currentView.getPlayerTokenLayer();

        return new StackPane(currentView);
    }

    @Override
    public void start(Stage stage) {
        WinterVillageProfile profile = WinterVillageProfile.active();
        Parent root = createBoard3D();

        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double width = Math.min(1400, screen.getWidth() * 0.92);
        double height = Math.min(960, screen.getHeight() * 0.90);

        Scene scene = new Scene(root, width, height);
        stage.setTitle("冬季村庄样景 " + profile.label + " — Winter Village (JavaFX 3D)");
        stage.setMinWidth(900);
        stage.setMinHeight(650);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}