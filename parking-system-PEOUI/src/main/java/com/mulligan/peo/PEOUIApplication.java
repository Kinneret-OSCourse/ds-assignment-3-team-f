package com.mulligan.peo;

import com.mulligan.peo.controller.PEOController;
import com.mulligan.peo.view.PEOView;
import com.mulligan.service.ParkingService;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Main type definition for PEOUIApplication.
 */
public class PEOUIApplication extends Application {

    @Override
    /**
     * Executes the start operation.
     * @param stage input value used by the operation
     */
    public void start(Stage stage) {
        ParkingService parkingService = new ParkingService();
        PEOController peoController = new PEOController(parkingService);
        PEOView peoView = new PEOView(peoController);
        peoView.show(stage);
    }

    /**
     * Executes the main operation.
     * @param args input value used by the operation
     */
    public static void main(String[] args) {
        launch(args);
    }
}
