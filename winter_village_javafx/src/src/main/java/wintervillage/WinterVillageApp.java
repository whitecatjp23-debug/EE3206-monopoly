package wintervillage;

import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Standalone JavaFX 3D viewer for the approved Winter Village sample scene.
 * Run with {@code ./run.sh}.
 */
public final class WinterVillageApp extends Application {

    @Override
    public void start(Stage stage) {
        WinterVillageProfile profile = WinterVillageProfile.active();
        WinterVillageView view = new WinterVillageView(true);
        StackPane root = new StackPane(view);

        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double width = Math.min(1400, screen.getWidth() * 0.92);
        double height = Math.min(960, screen.getHeight() * 0.90);

        Scene scene = new Scene(root, width, height);
        stage.setTitle("冬季村庄样景 " + profile.label
                + " · 湖面棋盘 + 六枚棋子 + 3D 骰子 + 双卡组 — Winter Village (JavaFX 3D)");
        stage.setMinWidth(900);
        stage.setMinHeight(650);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
