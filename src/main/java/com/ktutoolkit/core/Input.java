package com.ktutoolkit.core;

import java.io.Console;
import java.util.Scanner;

/**
 * Single shared console input source.
 *
 * <p>Creating a second Scanner while the first is still open can swallow
 * buffered lines, so every menu reads from this shared instance.</p>
 */
public final class Input {
    private static final Scanner SCANNER = new Scanner(System.in);

    private Input() {
    }

    public static Scanner scanner() {
        return SCANNER;
    }

    /**
     * Reads a password without echoing it when a real console is available.
     * IDEs and piped input return null from {@link System#console()}, so the
     * shared Scanner is used as a fallback.
     */
    public static String readPassword() {
        Console console = System.console();
        if (console != null) {
            char[] password = console.readPassword();
            return password == null ? "" : new String(password);
        }
        return SCANNER.nextLine();
    }
}
