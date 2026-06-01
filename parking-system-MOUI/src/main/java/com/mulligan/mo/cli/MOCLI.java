package com.mulligan.mo.cli;

import com.mulligan.mo.controller.MOController;
import com.mulligan.mo.model.Citation;
import com.mulligan.mo.model.PaymentTransaction;
import com.mulligan.mo.service.ParkingService;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Command-line entry point for the Municipal Officer (MO) UI.
 *
 * <p>The MO uses this CLI to read the persistent transaction and citation
 * report tables. Reports are pulled through {@link MOController} and
 * formatted line-by-line on stdout.
 *
 * <p>Implementation note (matches TESTING.md SUC-6): the MO uses the
 * least-privilege {@code mulligan_mo} RabbitMQ user, which has read-only
 * access to the {@code Transactions} and {@code Citations} queues, but the
 * report itself is read from the durable Postgres tables so that historic
 * rows are available even after parking-server has acknowledged the queue
 * messages.
 *
 * <p>Supported commands:
 * <ul>
 *   <li>{@code transactions} — prints every payment transaction.</li>
 *   <li>{@code citations} — prints every issued citation.</li>
 * </ul>
 *
 * <p>{@link IOException} and {@link TimeoutException} thrown by the queue
 * layer, as well as runtime exceptions thrown by the report layer, are
 * caught here and surfaced as short user-facing messages so the operator
 * never sees a Java stack trace (see Defense.md §V11).
 */
public class MOCLI {

    /**
     * Parses the command-line arguments and dispatches to the MO controller.
     * Empty input prints usage; an unknown command prints usage; queue or
     * runtime errors are mapped to a short message before returning.
     *
     * @param args first element is the sub-command name; remaining elements
     *             are the sub-command's arguments
     */
    public static void main(String[] args) {
        ParkingService parkingService = new ParkingService();
        MOController controller = new MOController(parkingService);

        if (args.length == 0) {
            printUsage();
            return;
        }

        String command = args[0].toLowerCase();

        try {
            switch (command) {
                case "transactions":
                    List<PaymentTransaction> transactions = controller.getTransactionReport();
                    if (transactions.isEmpty()) {
                        System.out.println("No transactions found.");
                    } else {
                        for (PaymentTransaction transaction : transactions) {
                            System.out.println(transaction);
                        }
                    }
                    break;

                case "citations":
                    List<Citation> citations = controller.getCitationReport();
                    if (citations.isEmpty()) {
                        System.out.println("No citations found.");
                    } else {
                        for (Citation citation : citations) {
                            System.out.println(citation);
                        }
                    }
                    break;

                default:
                    System.out.println("Unknown command.");
                    printUsage();
            }
        } catch (IOException | TimeoutException e) {
            System.out.println("Queue connection error: " + e.getMessage());
        } catch (RuntimeException e) {
            System.out.println("Runtime error: " + e.getMessage());
        }
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  transactions");
        System.out.println("  citations");
    }
}
