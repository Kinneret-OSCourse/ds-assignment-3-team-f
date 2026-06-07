package com.mulligan.customer.view;

import com.mulligan.customer.controller.CustomerController;
import com.mulligan.model.ParkingEvent;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;

import java.util.List;

/**
 * Main type definition for CustomerView.
 */
public class CustomerView {

    private final CustomerController customerController;

    /**
     * Creates a new CustomerView instance.
     * @param customerController input value for the constructor
     */
    public CustomerView(CustomerController customerController) {
        this.customerController = customerController;
    }

    /**
     * Executes the show operation.
     * @param stage input value used by the operation
     */
    public void show(Stage stage) {
        Label eyebrow = new Label("Customer Login");
        eyebrow.getStyleClass().add("eyebrow");

        Label titleLabel = new Label("Sign in to manage your parking.");
        titleLabel.getStyleClass().add("hero-title");
        titleLabel.setWrapText(true);

        Label subtitleLabel = new Label("Use your customer id to open your parking dashboard.");
        subtitleLabel.getStyleClass().add("hero-subtitle");
        subtitleLabel.setWrapText(true);

        TextField customerIdField = new TextField();
        customerIdField.setPromptText("Customer ID");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("error-message");
        errorLabel.setWrapText(true);

        Button loginButton = new Button("Login");
        loginButton.getStyleClass().addAll("action-button", "primary-button");

        VBox loginFields = new VBox(12, customerIdField, passwordField, loginButton, errorLabel);
        loginFields.setFillWidth(true);
        loginFields.setMaxWidth(420);

        StackPane heroGraphic = createHeroGraphic(
                "M 35 95 L 58 63 H 142 L 166 95 V 132 H 153 C 151 146 140 156 125 156 C 110 156 99 146 97 132 H 69 C 67 146 56 156 41 156 C 26 156 15 146 13 132 H 0 V 95 Z M 48 118 C 38 118 30 126 30 136 C 30 146 38 154 48 154 C 58 154 66 146 66 136 C 66 126 58 118 48 118 Z M 118 118 C 108 118 100 126 100 136 C 100 146 108 154 118 154 C 128 154 136 146 136 136 C 136 126 128 118 118 118 Z M 48 75 V 95 H 154 L 138 75 Z",
                "Customer Access"
        );

        VBox loginText = new VBox(10, eyebrow, titleLabel, subtitleLabel, loginFields);
        loginText.setFillWidth(true);

        HBox loginCard = new HBox(28, loginText, heroGraphic);
        loginCard.getStyleClass().add("hero-card");
        loginCard.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(loginText, Priority.ALWAYS);

        StackPane root = new StackPane(loginCard);
        root.getStyleClass().add("app-shell");
        root.setPadding(new Insets(40));

        Scene scene = new Scene(root, 900, 620);
        String stylesheet = getClass().getResource("/ui/customer-theme.css").toExternalForm();
        scene.getStylesheets().add(stylesheet);

        loginButton.setDefaultButton(true);
        loginButton.setOnAction(e -> {
            String customerId = customerIdField.getText().trim();
            String password = passwordField.getText();
            String result = customerController.login(customerId, password);

            if ("Login successful.".equals(result)) {
                showDashboard(stage, customerId);
            } else {
                errorLabel.setText(result);
            }
        });

        stage.setTitle("Mulligan - Customer Login");
        stage.setMinWidth(760);
        stage.setMinHeight(540);
        stage.setScene(scene);
        stage.show();
    }

    private void showDashboard(Stage stage, String loggedInCustomerId) {
        Label eyebrow = new Label("Customer Dashboard");
        eyebrow.getStyleClass().add("eyebrow");

        Label titleLabel = new Label("Start, stop, and review parking with one clean control panel.");
        titleLabel.getStyleClass().add("hero-title");
        titleLabel.setWrapText(true);

        Label subtitleLabel = new Label(
                "Track active sessions, review your parking history, and keep total payments visible at a glance.");
        subtitleLabel.getStyleClass().add("hero-subtitle");
        subtitleLabel.setWrapText(true);

        HBox statRow = new HBox(
                12,
                createStatCard("Live Status", "Backend services ready"),
                createStatCard("Quick Action", "Start parking in one step"),
                createStatCard("History", "Review all past parking events")
        );

        VBox heroText = new VBox(10, eyebrow, titleLabel, subtitleLabel, statRow);
        heroText.setFillWidth(true);

        StackPane heroGraphic = createHeroGraphic(
                "M 35 95 L 58 63 H 142 L 166 95 V 132 H 153 C 151 146 140 156 125 156 C 110 156 99 146 97 132 H 69 C 67 146 56 156 41 156 C 26 156 15 146 13 132 H 0 V 95 Z M 48 118 C 38 118 30 126 30 136 C 30 146 38 154 48 154 C 58 154 66 146 66 136 C 66 126 58 118 48 118 Z M 118 118 C 108 118 100 126 100 136 C 100 146 108 154 118 154 C 128 154 136 146 136 136 C 136 126 128 118 118 118 Z M 48 75 V 95 H 154 L 138 75 Z",
                "Park Smarter"
        );

        HBox hero = new HBox(24, heroText, heroGraphic);
        hero.getStyleClass().add("hero-card");
        hero.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(heroText, Priority.ALWAYS);

        Label customerIdLabel = new Label("Customer ID");
        TextField customerIdField = new TextField();
        customerIdField.setText(loggedInCustomerId);
        customerIdField.setEditable(false);
        customerIdField.setFocusTraversable(false);

        Label vehicleNumberLabel = new Label("Vehicle Number");
        TextField vehicleNumberField = new TextField();
        vehicleNumberField.setText(customerController.getAssignedVehicleNumber(loggedInCustomerId));
        vehicleNumberField.setEditable(false);
        vehicleNumberField.setFocusTraversable(false);

        Label parkingSpaceLabel = new Label("Parking Space");
        TextField parkingSpaceField = new TextField();
        parkingSpaceField.setPromptText("e.g. S001");

        Label recommendSpaceLabel = new Label("Preferred Space");
        TextField recommendSpaceField = new TextField();
        recommendSpaceField.setPromptText("e.g. S003");

        GridPane formGrid = new GridPane();
        formGrid.getStyleClass().add("form-grid");
        formGrid.setHgap(18);
        formGrid.setVgap(10);
        formGrid.add(customerIdLabel, 0, 0);
        formGrid.add(customerIdField, 0, 1);
        formGrid.add(vehicleNumberLabel, 1, 0);
        formGrid.add(vehicleNumberField, 1, 1);
        formGrid.add(parkingSpaceLabel, 2, 0);
        formGrid.add(parkingSpaceField, 2, 1);
        formGrid.add(recommendSpaceLabel, 0, 2);
        formGrid.add(recommendSpaceField, 0, 3);

        ColumnConstraints flexible = new ColumnConstraints();
        flexible.setHgrow(Priority.ALWAYS);
        flexible.setFillWidth(true);
        formGrid.getColumnConstraints().addAll(flexible, flexible, flexible);

        Button startParkingButton = new Button("Start Parking");
        startParkingButton.getStyleClass().addAll("action-button", "primary-button");
        Button stopParkingButton = new Button("Stop Parking");
        stopParkingButton.getStyleClass().addAll("action-button", "secondary-button");
        Button getEventsButton = new Button("Load Parking Events");
        getEventsButton.getStyleClass().addAll("action-button", "ghost-button");
        Button recommendButton = new Button("Recommend Parking");
        recommendButton.getStyleClass().addAll("action-button", "secondary-button");
        Button logoutButton = new Button("Logout");
        logoutButton.getStyleClass().addAll("action-button", "ghost-button");

        HBox actions = new HBox(12, startParkingButton, stopParkingButton, getEventsButton, recommendButton, logoutButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        Label formTitle = new Label("Parking Controls");
        formTitle.getStyleClass().add("section-title");
        Label formHint = new Label("Your login is linked to the vehicle shown below.");
        formHint.getStyleClass().add("section-subtitle");
        Label vehicleHint = new Label("Only this assigned vehicle can be used from this customer account.");
        vehicleHint.getStyleClass().add("inline-hint");

        VBox formCard = new VBox(16, formTitle, formHint, vehicleHint, formGrid, actions);
        formCard.getStyleClass().add("content-card");

        TextArea messageArea = new TextArea();
        messageArea.setEditable(false);
        messageArea.setWrapText(true);
        messageArea.setPrefRowCount(5);
        messageArea.setPromptText("System messages and operation results appear here.");

        Label messageTitle = new Label("System Feedback");
        messageTitle.getStyleClass().add("section-title");
        VBox feedbackCard = new VBox(12, messageTitle, messageArea);
        feedbackCard.getStyleClass().add("content-card");

        TableView<ParkingEvent> eventsTable = new TableView<>();
        eventsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        eventsTable.setPlaceholder(new Label("No parking events loaded yet."));
        eventsTable.setPrefHeight(320);
        eventsTable.setMinHeight(320);

        TableColumn<ParkingEvent, String> eventIdCol = new TableColumn<>("Event ID");
        eventIdCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getEventId()));

        TableColumn<ParkingEvent, String> startTimeCol = new TableColumn<>("Start Time");
        startTimeCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getStartTime() == null ? "" : data.getValue().getStartTime().toString()));

        TableColumn<ParkingEvent, String> endTimeCol = new TableColumn<>("End Time");
        endTimeCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getEndTime() == null ? "" : data.getValue().getEndTime().toString()));

        TableColumn<ParkingEvent, String> spaceIdCol = new TableColumn<>("Parking Space");
        spaceIdCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getParkingSpaceId()));

        TableColumn<ParkingEvent, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatus()));

        TableColumn<ParkingEvent, String> amountCol = new TableColumn<>("Amount Paid");
        amountCol.setCellValueFactory(data -> new SimpleStringProperty(
                "STARTED".equalsIgnoreCase(data.getValue().getStatus())
                        ? "-"
                        : String.format("%.2f", data.getValue().getAmount())));

        eventsTable.getColumns().addAll(eventIdCol, startTimeCol, endTimeCol, spaceIdCol, statusCol, amountCol);

        Label eventsTitle = new Label("Parking Timeline");
        eventsTitle.getStyleClass().add("section-title");
        Label eventsHint = new Label("Review each session, including the active status and completed stop time.");
        eventsHint.getStyleClass().add("section-subtitle");

        Label totalAmountLabel = new Label("Total Amount Paid: 0.0");
        totalAmountLabel.getStyleClass().add("total-chip");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox eventsHeader = new HBox(12, new VBox(4, eventsTitle, eventsHint), spacer, totalAmountLabel);
        eventsHeader.setAlignment(Pos.CENTER_LEFT);

        VBox tableCard = new VBox(16, eventsHeader, eventsTable);
        tableCard.getStyleClass().add("content-card");
        VBox.setVgrow(eventsTable, Priority.ALWAYS);

        startParkingButton.setOnAction(e -> {
            String customerId = customerIdField.getText().trim();
            String vehicleNumber = vehicleNumberField.getText().trim();
            String parkingSpaceId = parkingSpaceField.getText().trim();

            if (customerId.isEmpty() || vehicleNumber.isEmpty() || parkingSpaceId.isEmpty()) {
                messageArea.setText("Error: Please fill in Customer ID, Vehicle Number, and Parking Space ID.");
                return;
            }

            String result = customerController.startParking(customerId, vehicleNumber, parkingSpaceId);
            messageArea.setText(result);
            refreshParkingEvents(customerId, vehicleNumber, eventsTable, totalAmountLabel);
        });

        stopParkingButton.setOnAction(e -> {
            String customerId = customerIdField.getText().trim();
            String vehicleNumber = vehicleNumberField.getText().trim();

            if (customerId.isEmpty() || vehicleNumber.isEmpty()) {
                messageArea.setText("Error: Please fill in Customer ID and Vehicle Number.");
                return;
            }

            String result = customerController.stopParking(customerId, vehicleNumber);
            messageArea.setText(result);
            refreshParkingEvents(customerId, vehicleNumber, eventsTable, totalAmountLabel);
        });

        getEventsButton.setOnAction(e -> {
            String customerId = customerIdField.getText().trim();
            String vehicleNumber = vehicleNumberField.getText().trim();

            if (customerId.isEmpty() || vehicleNumber.isEmpty()) {
                messageArea.setText("Error: Please fill in Customer ID and Vehicle Number.");
                return;
            }

            List<ParkingEvent> events = refreshParkingEvents(customerId, vehicleNumber, eventsTable, totalAmountLabel);

            if (events.isEmpty()) {
                messageArea.setText("No parking events found for this customer and vehicle.");
            } else {
                messageArea.setText("Parking events loaded successfully. Scroll down to review the timeline table.");
                eventsTable.scrollTo(0);
            }
        });

        recommendButton.setOnAction(e -> {
            String requestedSpace = recommendSpaceField.getText().trim();
            if (requestedSpace.isEmpty()) {
                requestedSpace = parkingSpaceField.getText().trim();
            }
            if (requestedSpace.isEmpty()) {
                messageArea.setText("Error: Please enter a preferred parking space.");
                return;
            }
            messageArea.setText(customerController.recommendParking(requestedSpace));
        });

        logoutButton.setOnAction(e -> show(stage));

        VBox content = new VBox(20, hero, formCard, feedbackCard, tableCard);
        content.setPadding(new Insets(28));

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);
        scrollPane.getStyleClass().add("app-scroll");

        BorderPane root = new BorderPane(scrollPane);
        root.getStyleClass().add("app-shell");

        Scene scene = new Scene(root, 1180, 860);
        String stylesheet = getClass().getResource("/ui/customer-theme.css").toExternalForm();
        scene.getStylesheets().add(stylesheet);

        stage.setTitle("Mulligan - Customer Command Center");
        stage.setMinWidth(1050);
        stage.setMinHeight(760);
        stage.setScene(scene);
        stage.show();
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

        StackPane badgeWrap = new StackPane(badge);
        StackPane.setAlignment(badge, Pos.BOTTOM_CENTER);

        StackPane graphic = new StackPane(icon, badgeWrap);
        graphic.getStyleClass().add("hero-graphic");
        graphic.setMinSize(270, 220);
        graphic.setPrefSize(270, 220);
        return graphic;
    }

    private List<ParkingEvent> refreshParkingEvents(String customerId, String vehicleNumber,
                                                    TableView<ParkingEvent> eventsTable,
                                                    Label totalAmountLabel) {
        List<ParkingEvent> events = customerController.getParkingEvents(customerId, vehicleNumber);
        eventsTable.setItems(FXCollections.observableArrayList(events));

        double total = customerController.getTotalAmountPaid(customerId, vehicleNumber);
        totalAmountLabel.setText(String.format("Total Amount Paid: %.2f", total));
        return events;
    }
}
