package com.mulligan.peo.view;

import com.mulligan.peo.controller.PEOController;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
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

/**
 * Main type definition for PEOView.
 */
public class PEOView {

    private final PEOController peoController;

    /**
     * Creates a new PEOView instance.
     * @param peoController input value for the constructor
     */
    public PEOView(PEOController peoController) {
        this.peoController = peoController;
    }

    /**
     * Executes the show operation.
     * @param stage input value used by the operation
     */
    public void show(Stage stage) {
        Label eyebrow = new Label("PEO Login");
        eyebrow.getStyleClass().add("eyebrow");

        Label titleLabel = new Label("Sign in to enforcement tools.");
        titleLabel.getStyleClass().add("hero-title");
        titleLabel.setWrapText(true);

        Label subtitleLabel = new Label("Use an authorized PEO user id before checking vehicles or issuing citations.");
        subtitleLabel.getStyleClass().add("hero-subtitle");
        subtitleLabel.setWrapText(true);

        TextField userIdField = new TextField();
        userIdField.setPromptText("PEO User ID");

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
                "M 89 0 L 167 26 V 81 C 167 118 145 149 89 176 C 33 149 11 118 11 81 V 26 Z M 84 43 H 94 V 95 H 84 Z M 84 109 H 94 V 127 H 84 Z",
                "PEO Access"
        );

        HBox hero = new HBox(24, loginText, heroGraphic);
        hero.getStyleClass().add("hero-card");
        hero.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(loginText, Priority.ALWAYS);

        StackPane root = new StackPane(hero);
        root.getStyleClass().add("app-shell");
        root.setPadding(new Insets(28));

        Scene scene = new Scene(root, 1040, 680);
        String stylesheet = getClass().getResource("/ui/peo-theme.css").toExternalForm();
        scene.getStylesheets().add(stylesheet);

        loginButton.setOnAction(e -> {
            String result = peoController.login(userIdField.getText(), passwordField.getText());
            if ("Login successful.".equals(result)) {
                showDashboard(stage);
            } else {
                errorLabel.setText(result);
            }
        });
        loginButton.setDefaultButton(true);

        stage.setTitle("Mulligan - PEO Login");
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.setScene(scene);
        stage.show();
    }

    private void showDashboard(Stage stage) {
        Label eyebrow = new Label("PEO Enforcement");
        eyebrow.getStyleClass().add("eyebrow");

        Label titleLabel = new Label("Inspect vehicles quickly and issue citations with confidence.");
        titleLabel.getStyleClass().add("hero-title");
        titleLabel.setWrapText(true);

        Label subtitleLabel = new Label(
                "Built for field checks: fast legality validation, clear citation flow, and strong operator visibility.");
        subtitleLabel.getStyleClass().add("hero-subtitle");
        subtitleLabel.setWrapText(true);

        HBox statRow = new HBox(
                12,
                createStatCard("Fast Check", "Inspect plate and space in seconds"),
                createStatCard("Field Response", "Immediate parking legality result"),
                createStatCard("Citation Flow", "Publish citation events to queue")
        );

        VBox heroText = new VBox(10, eyebrow, titleLabel, subtitleLabel, statRow);
        heroText.setFillWidth(true);

        StackPane heroGraphic = createHeroGraphic(
                "M 89 0 L 167 26 V 81 C 167 118 145 149 89 176 C 33 149 11 118 11 81 V 26 Z M 84 43 H 94 V 95 H 84 Z M 84 109 H 94 V 127 H 84 Z",
                "Secure Enforcement"
        );

        HBox hero = new HBox(24, heroText, heroGraphic);
        hero.getStyleClass().add("hero-card");
        hero.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(heroText, Priority.ALWAYS);

        Label vehicleNumberLabel = new Label("Vehicle Number");
        TextField vehicleNumberField = new TextField();
        vehicleNumberField.setPromptText("e.g. TEST123");

        Label parkingSpaceLabel = new Label("Parking Space");
        TextField parkingSpaceField = new TextField();
        parkingSpaceField.setPromptText("e.g. S001");

        Label citationAmountLabel = new Label("Citation Amount");
        TextField citationAmountField = new TextField();
        citationAmountField.setPromptText("e.g. 45");

        GridPane formGrid = new GridPane();
        formGrid.setHgap(18);
        formGrid.setVgap(10);
        formGrid.add(vehicleNumberLabel, 0, 0);
        formGrid.add(vehicleNumberField, 0, 1);
        formGrid.add(parkingSpaceLabel, 1, 0);
        formGrid.add(parkingSpaceField, 1, 1);
        formGrid.add(citationAmountLabel, 2, 0);
        formGrid.add(citationAmountField, 2, 1);

        ColumnConstraints flexible = new ColumnConstraints();
        flexible.setHgrow(Priority.ALWAYS);
        flexible.setFillWidth(true);
        formGrid.getColumnConstraints().addAll(flexible, flexible, flexible);

        Button checkVehicleButton = new Button("Check Vehicle");
        checkVehicleButton.getStyleClass().addAll("action-button", "primary-button");

        Button issueCitationButton = new Button("Issue Citation");
        issueCitationButton.getStyleClass().addAll("action-button", "secondary-button");
        issueCitationButton.setDisable(true);

        Label resultValue = new Label("Awaiting inspection...");
        resultValue.getStyleClass().add("result-chip");
        String[] lastNotOkVehicle = {null};
        String[] lastNotOkSpace = {null};

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actionRow = new HBox(12, checkVehicleButton, issueCitationButton, spacer, resultValue);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        Label controlsTitle = new Label("Field Inspection Console");
        controlsTitle.getStyleClass().add("section-title");
        Label controlsSubtitle = new Label(
                "Enter the vehicle and parking space once, then validate legality or issue a citation from the same panel.");
        controlsSubtitle.getStyleClass().add("section-subtitle");

        VBox controlsCard = new VBox(16, controlsTitle, controlsSubtitle, formGrid, actionRow);
        controlsCard.getStyleClass().add("content-card");

        TextArea messageArea = new TextArea();
        messageArea.setEditable(false);
        messageArea.setWrapText(true);
        messageArea.setPrefRowCount(8);
        messageArea.setPromptText("Inspection details, errors, and citation results appear here.");

        Label logTitle = new Label("Inspection Output");
        logTitle.getStyleClass().add("section-title");
        Label logSubtitle = new Label("Use this area to review the final result before moving to the next vehicle.");
        logSubtitle.getStyleClass().add("section-subtitle");
        VBox messageCard = new VBox(12, logTitle, logSubtitle, messageArea);
        messageCard.getStyleClass().add("content-card");

        checkVehicleButton.setOnAction(e -> {
            String vehicleNumber = vehicleNumberField.getText().trim();
            String parkingSpaceId = parkingSpaceField.getText().trim();

            if (vehicleNumber.isEmpty() || parkingSpaceId.isEmpty()) {
                messageArea.setText("Error: Please fill in Vehicle Number and Parking Space ID.");
                resultValue.setText("Incomplete form");
                issueCitationButton.setDisable(true);
                lastNotOkVehicle[0] = null;
                lastNotOkSpace[0] = null;
                return;
            }

            String result = peoController.checkVehicle(vehicleNumber, parkingSpaceId);
            resultValue.setText(result);
            messageArea.setText(result);
            if ("Parking Not Ok".equals(result)) {
                issueCitationButton.setDisable(false);
                lastNotOkVehicle[0] = vehicleNumber;
                lastNotOkSpace[0] = parkingSpaceId;
            } else {
                issueCitationButton.setDisable(true);
                lastNotOkVehicle[0] = null;
                lastNotOkSpace[0] = null;
            }
        });

        issueCitationButton.setOnAction(e -> {
            String vehicleNumber = vehicleNumberField.getText().trim();
            String parkingSpaceId = parkingSpaceField.getText().trim();
            String amountText = citationAmountField.getText().trim();

            if (vehicleNumber.isEmpty() || parkingSpaceId.isEmpty() || amountText.isEmpty()) {
                messageArea.setText("Error: Please fill in Vehicle Number, Parking Space ID, and Citation Amount.");
                resultValue.setText("Incomplete form");
                return;
            }

            if (!vehicleNumber.equals(lastNotOkVehicle[0]) || !parkingSpaceId.equals(lastNotOkSpace[0])) {
                resultValue.setText("Check required");
                messageArea.setText("Error: Check this vehicle and space first. Citations are allowed only after Parking Not Ok.");
                issueCitationButton.setDisable(true);
                return;
            }

            try {
                double amount = Double.parseDouble(amountText);
                String result = peoController.issueCitation(vehicleNumber, parkingSpaceId, amount);
                resultValue.setText("Citation processed");
                messageArea.setText(result);
                issueCitationButton.setDisable(true);
                lastNotOkVehicle[0] = null;
                lastNotOkSpace[0] = null;
            } catch (NumberFormatException ex) {
                resultValue.setText("Invalid amount");
                messageArea.setText("Error: Citation amount must be a number.");
            }
        });

        VBox content = new VBox(20, hero, controlsCard, messageCard);
        content.setPadding(new Insets(28));

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);
        scrollPane.getStyleClass().add("app-scroll");

        BorderPane root = new BorderPane(scrollPane);
        root.getStyleClass().add("app-shell");

        Scene scene = new Scene(root, 1080, 760);
        String stylesheet = getClass().getResource("/ui/peo-theme.css").toExternalForm();
        scene.getStylesheets().add(stylesheet);

        stage.setTitle("Mulligan - PEO Command Desk");
        stage.setMinWidth(960);
        stage.setMinHeight(700);
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

        StackPane graphic = new StackPane(icon, badge);
        StackPane.setAlignment(badge, Pos.BOTTOM_CENTER);
        graphic.getStyleClass().add("hero-graphic");
        graphic.setMinSize(270, 220);
        graphic.setPrefSize(270, 220);
        return graphic;
    }

}
