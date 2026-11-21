package com.ktutoolkit.pyq;

import com.ktutoolkit.core.AnsiColors;
import com.ktutoolkit.core.CLITool;
import com.ktutoolkit.core.Input;
import com.ktutoolkit.pyq.model.Paper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public class PyqDownloaderTool implements CLITool {
    private final PaperRepository repository = new PaperRepository();

    @Override
    public void run() {
        Scanner scanner = Input.scanner();
        AnsiColors.clearScreen();
        AnsiColors.printBanner("PYQ DOWNLOADER");

        String subjectCode = promptSubjectCode(scanner);
        if (subjectCode == null) {
            return;
        }

        AnsiColors.horizontalRule();
        AnsiColors.printLoading("  FETCHING PAPERS FOR " + subjectCode);

        List<Paper> papers;
        try {
            papers = repository.findBySubjectCode(subjectCode);
        } catch (IOException e) {
            System.out.println("  " + AnsiColors.red(
                    "COULD NOT FETCH PAPERS: " + e.getMessage()));
            pause(scanner);
            return;
        }

        if (papers.isEmpty()) {
            System.out.println("  " + AnsiColors.red(
                    "NO QUESTION PAPERS FOUND FOR " + subjectCode + "."));
            System.out.println("  " + AnsiColors.yellow(
                    "CHECK THE SUBJECT CODE AND TRY AGAIN."));
            pause(scanner);
            return;
        }

        String subjectName = papers.get(0).getSubjectName();
        System.out.println("  " + AnsiColors.green(
                "FOUND " + papers.size() + " QUESTION PAPERS FOR " + subjectCode + "."));
        if (!subjectName.isBlank()) {
            System.out.println("  " + AnsiColors.bold("SUBJECT") + ": " + subjectName);
        }

        System.out.println();
        AnsiColors.printLoading("  DOWNLOADING PAPERS");

        Path downloadDirectory = downloadDirectory(subjectCode);
        int downloaded = 0;
        for (int i = 0; i < papers.size(); i++) {
            Paper paper = papers.get(i);
            Path target = downloadDirectory.resolve(paper.getFileName());
            String progress = "[" + (i + 1) + "/" + papers.size() + "] "
                    + target.getFileName();
            try {
                repository.download(paper, target);
                downloaded++;
                System.out.println("  " + AnsiColors.green(progress));
            } catch (IOException e) {
                System.out.println("  " + AnsiColors.red(
                        progress + " - FAILED: " + e.getMessage()));
            }
        }

        System.out.println();
        if (downloaded > 0) {
            System.out.println("  " + AnsiColors.green(
                    "DOWNLOADED " + downloaded + " OF " + papers.size() + " PAPERS TO:"));
            System.out.println("  " + AnsiColors.bold(
                    downloadDirectory.toAbsolutePath().toString()));
        } else {
            System.out.println("  " + AnsiColors.red(
                    "NO PAPERS COULD BE DOWNLOADED."));
        }

        pause(scanner);
    }

    private String promptSubjectCode(Scanner scanner) {
        while (true) {
            System.out.print("  " + AnsiColors.cyan("ENTER SUBJECT CODE")
                    + " " + AnsiColors.yellow("(0 TO GO BACK)") + ": ");
            String input = scanner.nextLine().trim().toUpperCase(Locale.ROOT);
            if (input.equals("0")) {
                return null;
            }

            String subjectCode = input.replaceAll("[^A-Z0-9]", "");
            if (subjectCode.length() >= 3) {
                return subjectCode;
            }
            System.out.println("  " + AnsiColors.red(
                    "PLEASE ENTER A VALID SUBJECT CODE."));
        }
    }

    private Path downloadDirectory(String subjectCode) {
        return DownloadStore.subjectDirectory(subjectCode);
    }

    private void pause(Scanner scanner) {
        System.out.println();
        System.out.println("  " + AnsiColors.bold("PRESS ENTER TO CONTINUE..."));
        scanner.nextLine();
    }
}
