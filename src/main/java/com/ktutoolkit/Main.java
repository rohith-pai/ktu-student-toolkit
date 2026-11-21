package com.ktutoolkit;

import com.ktutoolkit.core.AnsiColors;
import com.ktutoolkit.core.CLITool;
import com.ktutoolkit.core.EnvFile;
import com.ktutoolkit.core.Input;
import com.ktutoolkit.db.DatabaseConnection;
import com.ktutoolkit.etlab.EtLabDashboardTool;
import com.ktutoolkit.etlab.EtLabSyncService;
import com.ktutoolkit.exceptions.LoginFailedException;
import com.ktutoolkit.pyq.PyqDownloaderTool;
import com.ktutoolkit.settings.SettingsTool;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        EnvFile.load();
        Scanner scanner = Input.scanner();

        if (!chooseDataMode(scanner)) {
            AnsiColors.clearScreen();
            System.out.println("  " + AnsiColors.green("GOODBYE!"));
            return;
        }

        CLITool[] tools = {
            new EtLabDashboardTool(),
            new PyqDownloaderTool(),
            new SettingsTool()
        };

        while (true) {
            AnsiColors.clearScreen();
            AnsiColors.printBanner("KTU STUDENT TOOLKIT");
            AnsiColors.printMenuOption(1, "ETLAB DASHBOARD");
            AnsiColors.printMenuOption(2, "PYQ DOWNLOADER");
            AnsiColors.printMenuOption(3, "SETTINGS");
            AnsiColors.printMenuOption(0, "EXIT");
            AnsiColors.horizontalRule();
            System.out.print("  " + AnsiColors.bold("ENTER CHOICE") + ": ");

            int choice;
            try {
                choice = Integer.parseInt(scanner.nextLine());
            } catch (NumberFormatException e) {
                System.out.println("  " + AnsiColors.red("INVALID INPUT. PLEASE ENTER A NUMBER."));
                continue;
            }

            if (choice == 0) {
                AnsiColors.clearScreen();
                System.out.println("  " + AnsiColors.green("GOODBYE!"));
                break;
            } else if (choice >= 1 && choice <= 3) {
                tools[choice - 1].run();
            } else {
                System.out.println("  " + AnsiColors.red("INVALID INPUT. PLEASE TRY AGAIN."));
            }
        }

        scanner.close();
    }

    /**
     * First screen: the user explicitly picks whether this run uses the
     * optional Supabase JDBC sync or pure live fetching.
     */
    private static boolean chooseDataMode(Scanner scanner) {
        while (true) {
            AnsiColors.clearScreen();
            AnsiColors.printBanner("KTU STUDENT TOOLKIT");
            System.out.println("  " + AnsiColors.bold("CHOOSE HOW TO STORE DATA"));
            AnsiColors.horizontalRule();
            AnsiColors.printMenuOption(1, "WITH JDBC (SYNC TO SUPABASE)");
            AnsiColors.printMenuOption(2, "WITHOUT JDBC (LIVE FETCH ONLY)");
            AnsiColors.printMenuOption(0, "EXIT");
            AnsiColors.horizontalRule();
            System.out.println("  " + AnsiColors.yellow(
                    "JDBC NEEDS YOUR OWN SUPABASE ACCOUNT AND PROJECT."));
            System.out.println("  " + AnsiColors.cyan(
                    "WITHOUT JDBC, ETLAB DATA IS FETCHED LIVE EACH TIME YOU OPEN A SECTION."));
            AnsiColors.horizontalRule();
            System.out.print("  " + AnsiColors.bold("ENTER CHOICE") + ": ");

            int choice;
            try {
                choice = Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("  " + AnsiColors.red("INVALID INPUT. PLEASE ENTER A NUMBER."));
                pause(scanner);
                continue;
            }

            switch (choice) {
                case 0:
                    return false;
                case 1:
                    if (!DatabaseConnection.isConfigured()) {
                        printSetupInstructions();
                        System.out.println("  " + AnsiColors.yellow(
                                "RETURNING TO THE MODE SELECTION."));
                        pause(scanner);
                        continue;
                    }

                    System.out.println();
                    System.out.println("  " + AnsiColors.yellow(
                            "RUNNING INITIAL ETLAB -> SUPABASE SYNC..."));
                    try {
                        new EtLabSyncService().sync();
                        System.out.println();
                        System.out.println("  " + AnsiColors.green(
                                "SYNC COMPLETED. JDBC MODE ENABLED."));
                        System.out.println("  " + AnsiColors.cyan(
                                "THE DASHBOARD WILL NOW READ FROM SUPABASE."));
                        pause(scanner);
                        return true;
                    } catch (IllegalStateException e) {
                        System.out.println("  " + AnsiColors.red(e.getMessage().toUpperCase()));
                    } catch (LoginFailedException e) {
                        System.out.println("  " + AnsiColors.red(e.getMessage().toUpperCase()));
                    } catch (Exception e) {
                        System.out.println("  " + AnsiColors.red("SYNC FAILED: " + e.getMessage()));
                    }
                    System.out.println("  " + AnsiColors.yellow(
                            "JDBC MODE NOT ACTIVE. CHOOSE AGAIN."));
                    pause(scanner);
                    continue;
                case 2:
                    DatabaseConnection.disableJdbc();
                    System.out.println();
                    System.out.println("  " + AnsiColors.green("WITHOUT JDBC SELECTED."));
                    System.out.println("  " + AnsiColors.cyan(
                            "ETLAB DATA IS FETCHED LIVE FROM ETLAB EVERY TIME YOU OPEN A"));
                    System.out.println("  " + AnsiColors.cyan(
                            "DASHBOARD SECTION. NOTHING IS SAVED TO A DATABASE."));
                    pause(scanner);
                    return true;
                default:
                    System.out.println("  " + AnsiColors.red("INVALID CHOICE. PLEASE TRY AGAIN."));
                    pause(scanner);
            }
        }
    }

    private static void printSetupInstructions() {
        System.out.println();
        System.out.println("  " + AnsiColors.bold("JDBC IS NOT CONFIGURED"));
        System.out.println("  " + AnsiColors.red("MISSING IN THIS PROCESS: "
                + String.join(", ", DatabaseConnection.missingSettings())));
        System.out.println("  " + AnsiColors.yellow("TO USE JDBC YOU NEED TO:"));
        System.out.println("    1. CREATE A SUPABASE ACCOUNT AT https://supabase.com");
        System.out.println("    2. CREATE A NEW PROJECT IN SUPABASE");
        System.out.println("    3. RUN db/schema.sql IN THE PROJECT'S SQL EDITOR");
        System.out.println("    4. GET DB_URL, DB_USER AND DB_PASSWORD FROM");
        System.out.println("       PROJECT SETTINGS -> DATABASE -> CONNECTION STRING");
        System.out.println("    5. PUT THOSE THREE VALUES IN THE .env FILE IN THIS FOLDER");
        System.out.println("  " + AnsiColors.cyan(
                "FULL STEPS: README.md"));
        System.out.println("  " + AnsiColors.yellow(
                "YOU CAN FIX THIS AND TRY AGAIN, OR CHOOSE WITHOUT JDBC."));
    }

    private static void pause(Scanner scanner) {
        System.out.println();
        System.out.println("  " + AnsiColors.bold("PRESS ENTER TO CONTINUE..."));
        scanner.nextLine();
    }
}
