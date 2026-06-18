package com.mulligan.recommender;

import com.mulligan.recommender.cli.RecommenderCLI;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.nio.charset.StandardCharsets;

/**
 * JavaFX control panel for running one recommender node in normal or malicious
 * testing mode. Command-line arguments are delegated to {@link RecommenderCLI}
 * so the same artifact supports GUI and CLI operation.
 */
public class RecommenderServerApplication extends Application {

    /**
     * Starts CLI mode when arguments are supplied; otherwise opens the GUI.
     *
     * @param args optional CLI arguments
     */
    public static void main(String[] args) {
        if (args.length > 0) {
            RecommenderCLI.main(args);
            return;
        }
        launch(args);
    }

    /**
     * Builds the recommender GUI.
     *
     * @param stage JavaFX stage
     */
    @Override
    public void start(Stage stage) {
        TextField nodeId = new TextField(envOr("MULLIGAN_RECOMMENDER_NODE_ID", "rec-1"));
        TextField port = new TextField(envOr("MULLIGAN_RECOMMENDER_PORT", "8081"));
        TextField peers = new TextField(envOr("MULLIGAN_RECOMMENDER_PEERS", "http://rec-2:8082,http://rec-3:8083"));
        TextField testSpace = new TextField("S003");
        CheckBox malicious = new CheckBox("Malicious mode");
        malicious.setSelected(Boolean.parseBoolean(envOr("MULLIGAN_RECOMMENDER_MALICIOUS", "false")));

        TextArea output = new TextArea();
        output.setEditable(false);
        output.setPrefRowCount(9);

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.addRow(0, new Label("Node ID"), nodeId);
        form.addRow(1, new Label("Port"), port);
        form.addRow(2, new Label("Peers"), peers);
        form.addRow(3, new Label("Test space"), testSpace);
        form.add(malicious, 1, 4);

        Button start = new Button("Start Node");
        Button applyMode = new Button("Apply Mode");
        Button recommend = new Button("Test Recommend");
        applyMode.setDisable(true);
        recommend.setDisable(true);

        start.setOnAction(e -> {
            start.setDisable(true);
            applyMode.setDisable(false);
            recommend.setDisable(false);
            Thread worker = new Thread(() -> RecommenderCLI.main(new String[] {
                    "serve", nodeId.getText(), port.getText(), peers.getText(), String.valueOf(malicious.isSelected())
            }), "recommender-gui-server");
            worker.setDaemon(true);
            worker.start();
            output.setText("Node started on port " + port.getText() + ".");
        });

        applyMode.setOnAction(e -> {
            Thread worker = new Thread(() -> {
                String mode = malicious.isSelected() ? "malicious" : "normal";
                String result = runCli("mode", "http://localhost:" + port.getText(), mode);
                Platform.runLater(() -> output.setText("Mode changed to " + result.trim() + "."));
            }, "recommender-gui-mode");
            worker.setDaemon(true);
            worker.start();
        });

        recommend.setOnAction(e -> {
            Thread worker = new Thread(() -> {
                String result = runCli("recommend", "http://localhost:" + port.getText(), testSpace.getText());
                Platform.runLater(() -> output.setText(result));
            }, "recommender-gui-test");
            worker.setDaemon(true);
            worker.start();
        });

        HBox buttons = new HBox(10, start, applyMode, recommend);
        buttons.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Mulligan Recommender Node");
        VBox root = new VBox(18, title, form, buttons, output);
        root.setPadding(new Insets(24));

        Scene scene = new Scene(root, 760, 520);
        stage.setTitle("Mulligan Recommender");
        stage.setScene(scene);
        stage.show();
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String runCli(String... args) {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.PrintStream original = System.out;
        try (java.io.PrintStream capture = new java.io.PrintStream(baos, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            RecommenderCLI.main(args);
        } finally {
            System.setOut(original);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }
}
