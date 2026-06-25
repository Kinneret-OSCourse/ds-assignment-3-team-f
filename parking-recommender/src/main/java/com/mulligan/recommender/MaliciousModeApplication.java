package com.mulligan.recommender;

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

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Small GUI for switching recommender servers into malicious mode.
 */
public class MaliciousModeApplication extends Application {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        TextField rec1Url = new TextField(envOr("MULLIGAN_REC1_URL", "http://localhost:8081"));
        TextField rec2Url = new TextField(envOr("MULLIGAN_REC2_URL", "http://localhost:8082"));
        TextField rec3Url = new TextField(envOr("MULLIGAN_REC3_URL", "http://localhost:8083"));
        TextField testSpace = new TextField(envOr("MULLIGAN_TEST_SPACE", "S003"));

        CheckBox rec1 = new CheckBox("rec-1");
        CheckBox rec2 = new CheckBox("rec-2");
        CheckBox rec3 = new CheckBox("rec-3");

        TextArea output = new TextArea();
        output.setEditable(false);
        output.setWrapText(true);
        output.setPrefRowCount(12);

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.addRow(0, new Label("rec-1 URL"), rec1Url);
        form.addRow(1, new Label("rec-2 URL"), rec2Url);
        form.addRow(2, new Label("rec-3 URL"), rec3Url);
        form.addRow(3, new Label("Test space"), testSpace);
        form.addRow(4, new Label("Malicious servers"), new HBox(12, rec1, rec2, rec3));

        Button apply = new Button("Apply Mode");
        Button reset = new Button("All Normal");
        Button refresh = new Button("Show Returned Tables");
        Button recommend = new Button("Test Consensus");

        apply.setOnAction(e -> runAsync(() -> {
            List<NodeTarget> nodes = nodes(rec1Url.getText(), rec2Url.getText(), rec3Url.getText());
            List<String> selected = selectedNodes(rec1, rec2, rec3);
            StringBuilder result = new StringBuilder();
            for (NodeTarget node : nodes) {
                boolean malicious = selected.contains(node.id());
                result.append(setMode(node, malicious)).append(System.lineSeparator());
            }
            result.append(System.lineSeparator()).append(loadVotes(nodes, testSpace.getText()));
            return result.toString();
        }, output));

        reset.setOnAction(e -> {
            rec1.setSelected(false);
            rec2.setSelected(false);
            rec3.setSelected(false);
            runAsync(() -> {
                StringBuilder result = new StringBuilder();
                for (NodeTarget node : nodes(rec1Url.getText(), rec2Url.getText(), rec3Url.getText())) {
                    result.append(setMode(node, false)).append(System.lineSeparator());
                }
                return result.toString();
            }, output);
        });

        refresh.setOnAction(e -> runAsync(
                () -> loadVotes(nodes(rec1Url.getText(), rec2Url.getText(), rec3Url.getText()), testSpace.getText()),
                output
        ));

        recommend.setOnAction(e -> runAsync(() -> {
            String encodedSpace = URLEncoder.encode(testSpace.getText(), StandardCharsets.UTF_8);
            StringBuilder result = new StringBuilder();
            for (NodeTarget node : nodes(rec1Url.getText(), rec2Url.getText(), rec3Url.getText())) {
                result.append(node.id())
                        .append(" consensus result: ")
                        .append(get(trimSlash(node.url()) + "/recommend?space=" + encodedSpace))
                        .append(System.lineSeparator());
            }
            return result.toString();
        }, output));

        HBox buttons = new HBox(10, apply, reset, refresh, recommend);
        buttons.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Mulligan Malicious Recommender Control");
        Label subtitle = new Label("Choose one or more servers to return the incorrect table row MAL-rec-X;999.");
        VBox root = new VBox(16, title, subtitle, form, buttons, output);
        root.setPadding(new Insets(22));

        Scene scene = new Scene(root, 760, 500);
        stage.setTitle("Mulligan - Malicious Recommender");
        stage.setScene(scene);
        stage.show();
    }

    private List<NodeTarget> nodes(String rec1Url, String rec2Url, String rec3Url) {
        return List.of(
                new NodeTarget("rec-1", rec1Url),
                new NodeTarget("rec-2", rec2Url),
                new NodeTarget("rec-3", rec3Url)
        );
    }

    private List<String> selectedNodes(CheckBox rec1, CheckBox rec2, CheckBox rec3) {
        return List.of(rec1, rec2, rec3).stream()
                .filter(CheckBox::isSelected)
                .map(CheckBox::getText)
                .toList();
    }

    private String setMode(NodeTarget node, boolean malicious) {
        String mode = malicious ? "malicious" : "normal";
        String response = get(trimSlash(node.url()) + "/mode?malicious=" + malicious);
        return node.id() + " set to " + mode + " -> " + response;
    }

    private String loadVotes(List<NodeTarget> nodes, String space) {
        String encodedSpace = URLEncoder.encode(space, StandardCharsets.UTF_8);
        StringBuilder result = new StringBuilder("Returned tables for ").append(space).append(":")
                .append(System.lineSeparator());
        for (NodeTarget node : nodes) {
            result.append(node.id())
                    .append(" -> ")
                    .append(get(trimSlash(node.url()) + "/internal/vote?space=" + encodedSpace))
                    .append(System.lineSeparator());
        }
        return result.toString();
    }

    private void runAsync(CheckedSupplier<String> action, TextArea output) {
        output.setText("Working...");
        Thread worker = new Thread(() -> {
            String result;
            try {
                result = action.get();
            } catch (Exception e) {
                result = "Error: " + e.getMessage();
            }
            String finalResult = result;
            Platform.runLater(() -> output.setText(finalResult));
        }, "malicious-mode-gui");
        worker.setDaemon(true);
        worker.start();
    }

    private String get(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() + " " + response.body();
        } catch (IOException e) {
            return "unavailable";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "interrupted";
        } catch (IllegalArgumentException e) {
            return "bad URL";
        }
    }

    private String trimSlash(String url) {
        String value = url == null ? "" : url.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private record NodeTarget(String id, String url) {
    }

    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
