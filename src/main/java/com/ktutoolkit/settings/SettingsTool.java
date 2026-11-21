package com.ktutoolkit.settings;

import com.ktutoolkit.core.CLITool;
import com.ktutoolkit.core.AnsiColors;
import com.ktutoolkit.core.Input;
import com.ktutoolkit.db.DatabaseConnection;
import com.ktutoolkit.etlab.EtLabSyncService;
import com.ktutoolkit.exceptions.LoginFailedException;
import com.ktutoolkit.pyq.DownloadStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

public class SettingsTool implements CLITool {
    @Override
    public void run() {
        Scanner scanner = Input.scanner();

        while (true) {
            AnsiColors.clearScreen();
            AnsiColors.printBanner("TOOLKIT SETTINGS");
            System.out.println("  " + AnsiColors.bold("DATABASE SYNC") + ": "
                    + (DatabaseConnection.isConfigured()
                        ? AnsiColors.green("ENABLED (JDBC CONFIGURED)")
                        : AnsiColors.yellow("DISABLED (OPTIONAL - NO JDBC CONFIGURED)")));
            AnsiColors.horizontalRule();
            AnsiColors.printMenuOption(1, "SYNC WITH ETLAB (OPTIONAL DB)");
            AnsiColors.printMenuOption(2, "CLEAR ALL DOWNLOADED PYQ PAPERS");
            AnsiColors.printMenuOption(3, "SET PYQ DOWNLOAD FOLDER");
            AnsiColors.printMenuOption(4, "LOGOUT & CLEAR SAVED ETLAB CREDENTIALS");
            AnsiColors.printMenuOption(0, "BACK TO MAIN MENU");
            AnsiColors.horizontalRule();
            System.out.print("  " + AnsiColors.bold("ENTER CHOICE") + ": ");

            int choice;
            try {
                choice = Integer.parseInt(scanner.nextLine());
            } catch (NumberFormatException e) {
                System.out.println("  " + AnsiColors.red("INVALID INPUT. PLEASE ENTER A NUMBER."));
                continue;
            }

            switch (choice) {
                case 0:
                    return;
                case 1:
                    if (!DatabaseConnection.isConfigured()) {
                        System.out.println();
                        System.out.println("  " + AnsiColors.yellow(
                                "SUPABASE JDBC IS NOT CONFIGURED."));
                        System.out.println("  " + AnsiColors.green(
                                "ALL OTHER FEATURES WORK WITHOUT IT."));
                        System.out.println("  " + AnsiColors.cyan(
                                "TO ENABLE SYNC, SET DB_URL, DB_USER AND DB_PASSWORD"
                                        + " (SEE README.md)."));
                        pause(scanner);
                        break;
                    }
                    syncWithEtLab(scanner);
                    break;
                case 2:
                    clearDownloads(scanner);
                    break;
                case 3:
                    setDownloadFolder(scanner);
                    break;
                case 4:
                    logout(scanner);
                    break;
                default:
                    System.out.println("  " + AnsiColors.red("INVALID INPUT. PLEASE TRY AGAIN."));
                    pause(scanner);
            }
        }
    }

    private void syncWithEtLab(Scanner scanner) {
        System.out.println();
        System.out.println("  " + AnsiColors.bold("SYNC WITH ETLAB"));
        AnsiColors.horizontalRule();
        System.out.println("  " + AnsiColors.yellow(
                "FETCHING FROM ETLAB AND SAVING TO SUPABASE VIA JDBC..."));
        AnsiColors.horizontalRule();

        try {
            new EtLabSyncService().sync();
            System.out.println();
            System.out.println("  " + AnsiColors.green("SYNC COMPLETED SUCCESSFULLY."));
        } catch (IllegalStateException e) {
            System.out.println("  " + AnsiColors.red(e.getMessage().toUpperCase()));
        } catch (LoginFailedException e) {
            System.out.println("  " + AnsiColors.red(e.getMessage().toUpperCase()));
        } catch (IOException e) {
            System.out.println("  " + AnsiColors.red("NETWORK ERROR: " + e.getMessage()));
        } catch (Exception e) {
            System.out.println("  " + AnsiColors.red("SYNC FAILED: " + e.getMessage()));
        }
        pause(scanner);
    }

    private void clearDownloads(Scanner scanner) {
        System.out.println();
        System.out.println("  " + AnsiColors.bold("DELETE ALL DOWNLOADED PAPERS"));
        System.out.println("  " + AnsiColors.yellow("FOLDER") + ": " + DownloadStore.root());
        System.out.print("  " + AnsiColors.red("THIS CANNOT BE UNDONE.") + " "
                + AnsiColors.bold("TYPE YES TO CONTINUE") + ": ");
        if (!scanner.nextLine().trim().equalsIgnoreCase("yes")) {
            System.out.println("  " + AnsiColors.yellow("CANCELLED."));
            pause(scanner);
            return;
        }

        try {
            int deleted = DownloadStore.deleteAll();
            if (deleted == 0) {
                System.out.println("  " + AnsiColors.yellow("THERE WERE NO DOWNLOADED PAPERS TO DELETE."));
            } else {
                System.out.println("  " + AnsiColors.green(
                        "DELETED " + deleted + " DOWNLOADED FILE" + (deleted == 1 ? "" : "S") + "."));
            }
        } catch (IOException e) {
            System.out.println("  " + AnsiColors.red(
                    "COULD NOT DELETE DOWNLOADED PAPERS: " + e.getMessage()));
        }
        pause(scanner);
    }

    private void setDownloadFolder(Scanner scanner) {
        AppConfig config = new AppConfig();
        System.out.println();
        System.out.println("  " + AnsiColors.bold("PYQ DOWNLOAD FOLDER"));
        System.out.println("  " + AnsiColors.cyan("CURRENT") + ": " + DownloadStore.root());
        System.out.println("  " + AnsiColors.yellow(
                "PAPERS ARE SAVED HERE IN A SUBFOLDER FOR EACH SUBJECT CODE."));
        System.out.print("  " + AnsiColors.bold("NEW FOLDER PATH")
                + " (BLANK = USE ~/Downloads/papers)");
        System.out.print(": ");

        String input = scanner.nextLine().trim();
        if (input.isEmpty()) {
            config.setPyqDownloadDirectory("");
            System.out.println("  " + AnsiColors.green("RESET TO THE DEFAULT DOWNLOADS FOLDER."));
            pause(scanner);
            return;
        }

        Path folder;
        try {
            folder = Paths.get(expandHome(input)).toAbsolutePath().normalize();
            if (Files.exists(folder) && !Files.isDirectory(folder)) {
                System.out.println("  " + AnsiColors.red(
                        "THAT PATH EXISTS BUT IS NOT A FOLDER."));
                pause(scanner);
                return;
            }
            Files.createDirectories(folder);
        } catch (IOException | RuntimeException e) {
            System.out.println("  " + AnsiColors.red(
                    "COULD NOT USE THAT FOLDER: " + e.getMessage()));
            pause(scanner);
            return;
        }

        config.setPyqDownloadDirectory(folder.toString());
        System.out.println("  " + AnsiColors.green("PYQ DOWNLOAD FOLDER SET TO:") + " "
                + AnsiColors.bold(folder.toString()));
        pause(scanner);
    }

    private static String expandHome(String path) {
        if (path.equals("~") || path.startsWith("~/") || path.startsWith("~\\")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }

    private void logout(Scanner scanner) {
        AppConfig config = new AppConfig();
        System.out.println();
        System.out.println("  " + AnsiColors.bold("LOGOUT"));
        System.out.print("  " + AnsiColors.yellow("CLEAR THE SAVED ETLAB USERNAME, PASSWORD AND SESSION?")
                + " " + AnsiColors.bold("(YES/NO)") + ": ");
        if (!scanner.nextLine().trim().equalsIgnoreCase("yes")) {
            System.out.println("  " + AnsiColors.yellow("CANCELLED."));
            pause(scanner);
            return;
        }

        config.clearCredentials();
        System.out.println("  " + AnsiColors.green("SAVED ETLAB CREDENTIALS AND SESSION CLEARED."));
        pause(scanner);
    }

    private void pause(Scanner scanner) {
        System.out.println();
        System.out.println("  " + AnsiColors.bold("PRESS ENTER TO CONTINUE..."));
        scanner.nextLine();
    }
}
