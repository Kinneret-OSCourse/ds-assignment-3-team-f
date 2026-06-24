package com.mulligan.mo.view;

import com.mulligan.mo.controller.MOController;
import com.mulligan.mo.model.Citation;
import com.mulligan.mo.model.CitationCount;
import com.mulligan.mo.model.PaymentTransaction;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Main type definition for MOView.
 */
public class MOView {

    private final MOController moController;

    /**
     * Creates a new MOView instance.
     * @param moController input value for the constructor
     */
    public MOView(MOController moController) {
        this.moController = moController;
    }

    /**
     * Executes the show operation.
     * @param stage input value used by the operation
     */
    public void show(Stage stage) {
        Label eyebrow = new Label("Management Login");
        eyebrow.getStyleClass().add("eyebrow");

        Label titleLabel = new Label("Sign in to the management office.");
        titleLabel.getStyleClass().add("hero-title");
        titleLabel.setWrapText(true);

        Label subtitleLabel = new Label("Use an authorized MO user id to open the reporting console.");
        subtitleLabel.getStyleClass().add("hero-subtitle");
        subtitleLabel.setWrapText(true);

        TextField userIdField = new TextField();
        userIdField.setPromptText("MO User ID");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("error-message");
        errorLabel.setWrapText(true);

        Button loginButton = new Button("Login");
        loginButton.getStyleClass().addAll("action-button", "primary-button");

        VBox loginFields = new VBox(12, userIdField, passwordField, loginButton, errorLabel);
        loginFields.setMaxWidth(430);
        loginFields.setFillWidth(true);
        VBox loginText = new VBox(10, eyebrow, titleLabel, subtitleLabel, loginFields);
        loginText.setFillWidth(true);

        StackPane heroGraphic = createHeroGraphic(
                "M 10 148 H 40 V 93 H 78 V 148 H 109 V 59 H 147 V 148 H 177 V 18 H 215 V 148 H 246 V 0 H 284 V 148 H 316 V 114 H 354 V 148 H 386 V 73 H 424 V 148 H 454 V 180 H 10 Z",
                "MO Access"
        );

        HBox hero = new HBox(24, loginText, heroGraphic);
        hero.getStyleClass().add("hero-card");
        hero.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(loginText, Priority.ALWAYS);

        StackPane root = new StackPane(hero);
        root.getStyleClass().add("app-shell");
        root.setPadding(new Insets(28));

        Scene scene = new Scene(root, 1040, 680);
        String stylesheet = getClass().getResource("/ui/mo-theme.css").toExternalForm();
        scene.getStylesheets().add(stylesheet);

        loginButton.setOnAction(e -> {
            String result = moController.login(userIdField.getText(), passwordField.getText());
            if ("Login successful.".equals(result)) {
                showDashboard(stage);
            } else {
                errorLabel.setText(result);
            }
        });
        loginButton.setDefaultButton(true);

        stage.setTitle("Mulligan - Management Login");
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.setScene(scene);
        stage.show();
    }

    private void showDashboard(Stage stage) {
        Label eyebrow = new Label("Management Office");
        eyebrow.getStyleClass().add("eyebrow");

        Label titleLabel = new Label("Watch city-wide parking activity from a single reporting console.");
        titleLabel.getStyleClass().add("hero-title");
        titleLabel.setWrapText(true);

        Label subtitleLabel = new Label(
                "Load transaction and citation streams on demand, compare live outcomes, and keep queue-based reporting transparent.");
        subtitleLabel.getStyleClass().add("hero-subtitle");
        subtitleLabel.setWrapText(true);

        HBox statRow = new HBox(
                12,
                createStatCard("Transactions", "Pull completed parking payments"),
                createStatCard("Citations", "Review issued citations from queue"),
                createStatCard("Oversight", "Single view for live operational reporting")
        );

        VBox heroText = new VBox(10, eyebrow, titleLabel, subtitleLabel, statRow);
        heroText.setFillWidth(true);

        StackPane heroGraphic = createHeroGraphic(
                "M 10 148 H 40 V 93 H 78 V 148 H 109 V 59 H 147 V 148 H 177 V 18 H 215 V 148 H 246 V 0 H 284 V 148 H 316 V 114 H 354 V 148 H 386 V 73 H 424 V 148 H 454 V 180 H 10 Z",
                "Queue Reports"
        );

        HBox hero = new HBox(24, heroText, heroGraphic);
        hero.getStyleClass().add("hero-card");
        hero.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(heroText, Priority.ALWAYS);

        Button transactionsButton = new Button("Load Transaction Report");
        transactionsButton.getStyleClass().addAll("action-button", "primary-button");
        Button citationsButton = new Button("Load Citation Report");
        citationsButton.getStyleClass().addAll("action-button", "secondary-button");
        Button citationCountsButton = new Button("Load Citation Counts");
        citationCountsButton.getStyleClass().addAll("action-button", "secondary-button");

        Label actionsTitle = new Label("Queue Report Controls");
        actionsTitle.getStyleClass().add("section-title");
        Label actionsSubtitle = new Label("Refresh each report separately so the office can review one stream at a time.");
        actionsSubtitle.getStyleClass().add("section-subtitle");

        HBox actionsRow = new HBox(12, transactionsButton, citationsButton, citationCountsButton);
        VBox actionCard = new VBox(14, actionsTitle, actionsSubtitle, actionsRow);
        actionCard.getStyleClass().add("content-card");

        TableView<PaymentTransaction> transactionsTable = new TableView<>();
        ObservableList<PaymentTransaction> transactionItems = FXCollections.observableArrayList();
        transactionsTable.setItems(transactionItems);
        transactionsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        transactionsTable.setPlaceholder(new Label("No transactions loaded."));

        TableColumn<PaymentTransaction, String> txIdCol = new TableColumn<>("Transaction ID");
        txIdCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTransactionId()));

        TableColumn<PaymentTransaction, String> txVehicleCol = new TableColumn<>("Vehicle");
        txVehicleCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getVehicleNumber()));

        TableColumn<PaymentTransaction, String> txSpaceCol = new TableColumn<>("Space");
        txSpaceCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getParkingSpaceId()));

        TableColumn<PaymentTransaction, String> txZoneCol = new TableColumn<>("Zone");
        txZoneCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getParkingZoneId()));

        TableColumn<PaymentTransaction, String> txStartCol = new TableColumn<>("Start Time");
        txStartCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getStartTime() == null ? "" : data.getValue().getStartTime().toString()));

        TableColumn<PaymentTransaction, String> txStopCol = new TableColumn<>("Stop Time");
        txStopCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getStopTime() == null ? "" : data.getValue().getStopTime().toString()));

        TableColumn<PaymentTransaction, String> txAmountCol = new TableColumn<>("Amount");
        txAmountCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getAmount())));

        transactionsTable.getColumns().addAll(
                txIdCol, txVehicleCol, txSpaceCol, txZoneCol, txStartCol, txStopCol, txAmountCol
        );

        TableView<Citation> citationsTable = new TableView<>();
        ObservableList<Citation> citationItems = FXCollections.observableArrayList();
        citationsTable.setItems(citationItems);
        citationsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        citationsTable.setPlaceholder(new Label("No citations loaded."));

        TableColumn<Citation, String> citationIdCol = new TableColumn<>("Citation ID");
        citationIdCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCitationId()));

        TableColumn<Citation, String> citationVehicleCol = new TableColumn<>("Vehicle");
        citationVehicleCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getVehicleNumber()));

        TableColumn<Citation, String> citationSpaceCol = new TableColumn<>("Space");
        citationSpaceCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getParkingSpaceId()));

        TableColumn<Citation, String> citationZoneCol = new TableColumn<>("Zone");
        citationZoneCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getParkingZoneId()));

        TableColumn<Citation, String> citationTimeCol = new TableColumn<>("Inspection Time");
        citationTimeCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getInspectionTime() == null ? "" : data.getValue().getInspectionTime().toString()));

        TableColumn<Citation, String> citationAmountCol = new TableColumn<>("Amount");
        citationAmountCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getAmount())));

        citationsTable.getColumns().addAll(
                citationIdCol, citationVehicleCol, citationSpaceCol, citationZoneCol, citationTimeCol, citationAmountCol
        );

        TableView<CitationCount> citationCountsTable = new TableView<>();
        ObservableList<CitationCount> citationCountItems = FXCollections.observableArrayList();
        citationCountsTable.setItems(citationCountItems);
        citationCountsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        citationCountsTable.setPlaceholder(new Label("No citation counts loaded."));

        TableColumn<CitationCount, String> countSpaceCol = new TableColumn<>("Space");
        countSpaceCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getParkingSpaceId()));

        TableColumn<CitationCount, String> countZoneCol = new TableColumn<>("Zone");
        countZoneCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getParkingZoneId()));

        TableColumn<CitationCount, String> countTotalCol = new TableColumn<>("Citations");
        countTotalCol.setCellValueFactory(data -> new SimpleStringProperty(
                String.valueOf(data.getValue().getCitationCount())));

        citationCountsTable.getColumns().addAll(countSpaceCol, countZoneCol, countTotalCol);

        TabPane reportTabs = new TabPane();
        reportTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        reportTabs.getStyleClass().add("report-tabs");

        Tab transactionsTab = new Tab("Transactions", wrapTableCard(
                "Completed Parking Transactions",
                "These entries are read from the Transactions queue.",
                transactionsTable
        ));

        Tab citationsTab = new Tab("Citations", wrapTableCard(
                "Issued Citations",
                "These entries are read from the Citations queue.",
                citationsTable
        ));

        Tab citationCountsTab = new Tab("Citation Counts", wrapTableCard(
                "Citation Counts by Space",
                "These totals include every active parking space.",
                citationCountsTable
        ));

        reportTabs.getTabs().addAll(transactionsTab, citationsTab, citationCountsTab);

        TextArea messageArea = new TextArea();
        messageArea.setEditable(false);
        messageArea.setWrapText(true);
        messageArea.setPrefRowCount(5);
        messageArea.setPromptText("Operational messages and loading status appear here.");

        Label messageTitle = new Label("Office Feed");
        messageTitle.getStyleClass().add("section-title");
        VBox messageCard = new VBox(12, messageTitle, messageArea);
        messageCard.getStyleClass().add("content-card");

        transactionsButton.setOnAction(e -> {
            try {
                List<PaymentTransaction> transactions = moController.getTransactionReport();
                transactionItems.addAll(transactions);
                reportTabs.getSelectionModel().select(transactionsTab);
                messageArea.setText(transactions.isEmpty()
                        ? "No transactions found."
                        : "Loaded " + transactions.size() + " new transaction(s). Total shown: " + transactionItems.size() + ".");
            } catch (IOException | TimeoutException ex) {
                messageArea.setText("Error loading transactions: " + ex.getMessage());
            } catch (RuntimeException ex) {
                messageArea.setText("Runtime error loading transactions: " + ex.getMessage());
            }
        });

        citationsButton.setOnAction(e -> {
            try {
                List<Citation> citations = moController.getCitationReport();
                citationItems.addAll(citations);
                reportTabs.getSelectionModel().select(citationsTab);
                messageArea.setText(citations.isEmpty()
                        ? "No citations found."
                        : "Loaded " + citations.size() + " new citation(s). Total shown: " + citationItems.size() + ".");
            } catch (IOException | TimeoutException ex) {
                messageArea.setText("Error loading citations: " + ex.getMessage());
            } catch (RuntimeException ex) {
                messageArea.setText("Runtime error loading citations: " + ex.getMessage());
            }
        });

        citationCountsButton.setOnAction(e -> {
            try {
                List<CitationCount> citationCounts = moController.getCitationCountsBySpace();
                citationCountItems.setAll(citationCounts);
                reportTabs.getSelectionModel().select(citationCountsTab);
                messageArea.setText(citationCounts.isEmpty()
                        ? "No parking spaces found."
                        : "Loaded citation counts for " + citationCounts.size() + " parking space(s).");
            } catch (IOException | TimeoutException ex) {
                messageArea.setText("Error loading citation counts: " + ex.getMessage());
            } catch (RuntimeException ex) {
                messageArea.setText("Runtime error loading citation counts: " + ex.getMessage());
            }
        });

        VBox content = new VBox(20, hero, actionCard, reportTabs, messageCard);
        content.setPadding(new Insets(28));
        VBox.setVgrow(reportTabs, Priority.ALWAYS);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);
        scrollPane.getStyleClass().add("app-scroll");

        BorderPane root = new BorderPane(scrollPane);
        root.getStyleClass().add("app-shell");

        Scene scene = new Scene(root, 1240, 860);
        String stylesheet = getClass().getResource("/ui/mo-theme.css").toExternalForm();
        scene.getStylesheets().add(stylesheet);

        stage.setTitle("Mulligan - Management Office Reports");
        stage.setMinWidth(1120);
        stage.setMinHeight(760);
        stage.setScene(scene);
        stage.show();
    }

    private VBox wrapTableCard(String title, String subtitle, TableView<?> table) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("section-title");
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("section-subtitle");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(12, new VBox(4, titleLabel, subtitleLabel), spacer);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(14, header, table);
        card.getStyleClass().add("content-card");
        VBox.setVgrow(table, Priority.ALWAYS);
        return card;
    }

    private VBox createStatCard(String title, String body) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("stat-title");
        Label bodyLabel = new Label(body);
        bodyLabel.getStyleClass().add("stat-body");
        bodyLabel.setWrapText(true);

        VBox card = new VBox(6, titleLabel, bodyLabel);
        card.getStyleClass().add("stat-card");
        card.setPrefWidth(180);
        return card;
    }

    private StackPane createHeroGraphic(String svgContent, String badgeText) {
        SVGPath icon = new SVGPath();
        icon.setContent(svgContent);
        icon.getStyleClass().add("hero-icon");

        Label badge = new Label(badgeText);
        badge.getStyleClass().add("hero-badge");

        StackPane graphic = new StackPane(icon, badge);
        StackPane.setAlignment(badge, Pos.BOTTOM_CENTER);
        graphic.getStyleClass().add("hero-graphic");
        graphic.setMinSize(290, 220);
        graphic.setPrefSize(290, 220);
        return graphic;
    }

}
