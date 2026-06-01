package com.mulligan.mo;

import com.mulligan.mo.controller.MOController;
import com.mulligan.mo.view.MOView;
import com.mulligan.mo.service.ParkingService;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Main type definition for MOUIApplication.
 */
public class MOUIApplication extends Application {

    @Override
    /**
     * Executes the start operation.
     * @param stage input value used by the operation
     */
    public void start(Stage stage) {
        ParkingService parkingService = new ParkingService();
        MOController moController = new MOController(parkingService);
        MOView moView = new MOView(moController);
        moView.show(stage);
    }

    /**
     * Executes the main operation.
     * @param args input value used by the operation
     */
    public static void main(String[] args) {
        launch(args);
    }
}
