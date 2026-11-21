package com.ktutoolkit.core;

public class AnsiColors {
    public static final String RESET = "\u001B[0m";
    public static final String GREEN = "\u001B[32m";
    public static final String RED = "\u001B[31m";
    public static final String YELLOW = "\u001B[33m";
    public static final String CYAN = "\u001B[36m";
    public static final String BOLD = "\u001B[1m";
    public static final String WHITE = "\u001B[37m";

    public static String colorize(String text, String color) {
        return color + text + RESET;
    }

    public static String green(String text) { return colorize(text, GREEN); }
    public static String red(String text) { return colorize(text, RED); }
    public static String yellow(String text) { return colorize(text, YELLOW); }
    public static String cyan(String text) { return colorize(text, CYAN); }
    public static String bold(String text) { return colorize(text, BOLD); }

    private static final String BOX_TL = "\u2554";
    private static final String BOX_TR = "\u2557";
    private static final String BOX_BL = "\u255A";
    private static final String BOX_BR = "\u255D";
    private static final String BOX_H  = "\u2550";
    private static final String BOX_V  = "\u2551";

    public static void clearScreen() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            try {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
                return;
            } catch (Exception ignored) {
                // Fall through to ANSI clearing.
            }
        }
        System.out.print("\u001B[H\u001B[2J");
        System.out.flush();
    }

    public static void printBanner(String title) {
        int pad = 2;
        int inner = title.length() + pad * 2;
        String top    = BOX_TL + BOX_H.repeat(inner) + BOX_TR;
        String middle = BOX_V + " ".repeat(pad) + bold(title) + " ".repeat(pad) + BOX_V;
        String bottom = BOX_BL + BOX_H.repeat(inner) + BOX_BR;
        System.out.println();
        System.out.println(top);
        System.out.println(middle);
        System.out.println(bottom);
        System.out.println();
    }

    public static void horizontalRule() {
        System.out.println("  " + "\u2500".repeat(50));
    }

    public static void printLoading(String message) {
        System.out.print(bold(message));
        for (int i = 0; i < 3; i++) {
            try { Thread.sleep(400); } catch (InterruptedException ignored) {}
            System.out.print(".");
        }
        System.out.println();
    }

    public static void printMenuOption(int number, String label) {
        System.out.println("  " + cyan(String.valueOf(number)) + ". " + label);
    }

}
