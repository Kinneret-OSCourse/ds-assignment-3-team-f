package com.mulligan.peo.cli;

import com.mulligan.peo.controller.PEOController;
import com.mulligan.service.ParkingService;

/**
 * Command-line entry point for the Parking Enforcement Officer (PEO) UI.
 *
 * <p>The PEO uses this CLI to inspect parked vehicles and to issue
 * citations. Both operations are routed through {@link PEOController},
 * which handles input validation and turns the citation into a signed
 * queue message published through the shared {@code SecurePublisher}.
 *
 * <p>Supported commands:
 * <ul>
 *   <li>{@code check <vehicleNumber> <spaceId>} — checks whether a vehicle
 *       has an active, paid parking event in the given space and prints
 *       {@code "Parking Ok"} or {@code "Parking Not Ok"}.</li>
 *   <li>{@code cite <vehicleNumber> <spaceId> <amount>} — issues a
 *       citation for the supplied amount in dollars; the controller
 *       enforces a positive amount before the citation row is created.</li>
 * </ul>
 *
 * <p>As with the other CLIs, parse failures and validation rejections are
 * mapped to short user-facing messages so the underlying exception type
 * never leaks to the operator (see Defense.md §V11).
 */
public class PEOCLI {

    /**
     * Parses the command-line arguments and dispatches to the PEO
     * controller. Prints usage and returns when no arguments are supplied
     * or the command is unrecognised. Amount parsing failures are caught
     * locally and reported as a generic message.
     *
     * @param args first element is the sub-command name; remaining
     *             elements are the sub-command's arguments
     */
    public static void main(String[] args) {
        ParkingService parkingService = new ParkingService();
        PEOController controller = new PEOController(parkingService);

        if (args.length == 0) {
            printUsage();
            return;
        }

        String command = args[0].toLowerCase();

        switch (command) {
            case "check":
                if (args.length != 3) {
                    System.out.println("Usage: check <vehicleNumber> <spaceId>");
                    return;
                }
                System.out.println(controller.checkVehicle(args[1], args[2]));
                break;

            case "cite":
                if (args.length != 4) {
                    System.out.println("Usage: cite <vehicleNumber> <spaceId> <amount>");
                    return;
                }

                try {
                    double amount = Double.parseDouble(args[3]);
                    System.out.println(controller.issueCitation(args[1], args[2], amount));
                } catch (NumberFormatException e) {
                    System.out.println("Error: amount must be a number.");
                }
                break;

            case "clear":
                if (args.length != 2) {
                    System.out.println("Usage: clear <spaceId>");
                    return;
                }
                System.out.println(controller.clearCitation(args[1]));
                break;

            case "clear-all":
                if (args.length != 1) {
                    System.out.println("Usage: clear-all");
                    return;
                }
                System.out.println(controller.clearAllCitations());
                break;

            case "count":
                if (args.length != 2) {
                    System.out.println("Usage: count <spaceId>");
                    return;
                }
                System.out.println(controller.countCitationsForSpace(args[1]));
                break;

            default:
                System.out.println("Unknown command.");
                printUsage();
        }
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  check <vehicleNumber> <spaceId>");
        System.out.println("  cite <vehicleNumber> <spaceId> <amount>");
        System.out.println("  clear <spaceId>");
        System.out.println("  clear-all");
        System.out.println("  count <spaceId>");
    }
}
