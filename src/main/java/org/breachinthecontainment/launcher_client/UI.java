package org.breachinthecontainment.launcher_client;

import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public class UI extends Application {

    public static void launchApp() {
        launch();
    }

    @Override
    public void start(Stage stage) {
        Label title = new Label("BREACH IN THE CONTAINMENT");
        title.setFont(Font.font("Arial", 28));
        Label subtitle = new Label("Launcher [CLIENT]");
        subtitle.setFont(Font.font("Arial", 20));

        Button playBtn = new Button("Play!");
        playBtn.setStyle("-fx-font-size: 16pt");

        Label errorLabel = new Label("Oops! It's not ready yet!... That's awkward.");
        errorLabel.setStyle("-fx-text-fill: red; -fx-font-size: 14px;");

        VBox layout = new VBox(15, title, subtitle, playBtn, errorLabel);
        layout.setAlignment(Pos.TOP_CENTER);
        layout.setStyle("-fx-padding: 30");

        Scene scene = new Scene(layout, 450, 300);
        stage.setTitle("Breach Launcher");
        stage.setScene(scene);
        stage.show();
    }
}
