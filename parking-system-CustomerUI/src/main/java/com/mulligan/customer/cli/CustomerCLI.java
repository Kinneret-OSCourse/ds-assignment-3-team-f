package com.mulligan.customer.cli;

import com.mulligan.customer.controller.CustomerController;
import com.mulligan.model.ParkingEvent;
import com.mulligan.service.ParkingService;
import com.mulligan.service.ParkingDataImporter;

import java.nio.file.Path;
import java.util.List;

/**
 * Command-line entry point for the Customer UI.
 *
 * <p>Dispatches the first positional argument to one of the supported
 * sub-commands and delegates the actual work to {@link CustomerController}.
 * The CLI is the primary surface used by the grading harness and the
 * classroom demo (see {@code DEPLOY.md} and {@code TESTING.md}).
 *
 * <p>Supported commands:
 * <ul>
 *   <li>{@code import <filePath>} — load vehicle/zone/space rows from a CSV
 *       via {@link ParkingDataImporter}.</li>
 *   <li>{@code start <customerId> <vehicleNumber> <spaceId>} — opens a
 *       parking event through the controller, which also signs the queue
 *       message via the shared {@code SecurePublisher}.</li>
 *   <li>{@code stop <customerId> <vehicleNumber>} — closes the active
 *       parking event and prints the resulting payment transaction.</li>
 *   <li>{@code events <customerId> <vehicleNumber>} — prints the historical
 *       parking events and the running total amount paid for the vehicle.</li>
 * </ul>
 *
 * <p>All error paths are intentionally caught and surfaced as short,
 * user-facing strings — Java stack traces and JDBC driver messages must
 * never reach the CLI's stdout (see Defense.md §V11).
 */
public class CustomerCLI {

    /**
     * Parses the command-line arguments and dispatches the matching
     * sub-command. Prints usage and returns when no arguments are supplied
     * or the command is unrecognised.
     *
     * @param args first element is the sub-command name; remaining elements
     *             are the sub-command's arguments (see class-level docs)
     */
    public static void main(String[] args) {
        ParkingService parkingService = new ParkingService();
        CustomerController controller = new CustomerController(parkingService);

        if (args.length == 0) {
            printUsage();
            return;
        }

        String command = args[0].toLowerCase();

        switch (command) {
            case "import":
                if (args.length != 2) {
                    System.out.println("Usage: import <filePath>");
                    return;
                }
                try {
                    ParkingDataImporter.ImportResult result = new ParkingDataImporter().importFile(Path.of(args[1]));
                    System.out.println("Import completed successfully.");
                    System.out.println("Vehicles processed: " + result.vehiclesImported());
                    System.out.println("Zones processed: " + result.zonesImported());
                    System.out.println("Spaces processed: " + result.spacesImported());
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
                break;

            case "start":
                if (args.length != 4) {
                    System.out.println("Usage: start <customerId> <vehicleNumber> <spaceId>");
                    return;
                }
                System.out.println(controller.startParking(args[1], args[2], args[3]));
                break;

            case "stop":
                if (args.length != 3) {
                    System.out.println("Usage: stop <customerId> <vehicleNumber>");
                    return;
                }
                System.out.println(controller.stopParking(args[1], args[2]));
                break;

            case "events":
                if (args.length != 3) {
                    System.out.println("Usage: events <customerId> <vehicleNumber>");
                    return;
                }

                List<ParkingEvent> events = controller.getParkingEvents(args[1], args[2]);
                if (events.isEmpty()) {
                    System.out.println("No parking events found.");
                } else {
                    for (ParkingEvent event : events) {
                        System.out.println(event);
                    }
                }

                double total = controller.getTotalAmountPaid(args[1], args[2]);
                System.out.println("Total Amount Paid: " + total);
                break;

            case "recommend":
                if (args.length != 2) {
                    System.out.println("Usage: recommend <spaceId>");
                    return;
                }
                System.out.println(controller.recommendParking(args[1]));
                break;

            default:
                System.out.println("Unknown command.");
                printUsage();
        }
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  import <filePath>");
        System.out.println("  start <customerId> <vehicleNumber> <spaceId>");
        System.out.println("  stop <customerId> <vehicleNumber>");
        System.out.println("  events <customerId> <vehicleNumber>");
        System.out.println("  recommend <spaceId>");
    }
}
