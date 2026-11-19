package wintervillage;

import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;

public final class WinterVillageApp extends Application {

    public static WinterVillageView currentView;

    public static Parent createBoard3D() {
        // 【修改点】设为 false，隐藏 3D 自带的 HUD，只留纯 3D 画面
        currentView = new WinterVillageView(false);
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