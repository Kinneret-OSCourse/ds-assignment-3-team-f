package com.mulligan.customer;

import com.mulligan.customer.controller.CustomerController;
import com.mulligan.customer.view.CustomerView;
import com.mulligan.service.ParkingService;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Main type definition for CustomerUIApplication.
 */
public class CustomerUIApplication extends Application {

    @Override
    /**
     * Executes the start operation.
     * @param stage input value used by the operation
     */
    public void start(Stage stage) {
        ParkingService parkingService = new ParkingService();
        CustomerController customerController = new CustomerController(parkingService);
        CustomerView customerView = new CustomerView(customerController);
        customerView.show(stage);
    }

    /**
     * Executes the main operation.
     * @param args input value used by the operation
     */
    public static void main(String[] args) {
        launch(args);
    }
}
