package com.ktutoolkit.exceptions;

/**
 * Thrown when ETLab authentication fails, either because the supplied
 * credentials are wrong or because the saved session has expired and
 * cannot be renewed.
 */
public class LoginFailedException extends Exception {
    public LoginFailedException(String message) {
        super(message);
    }

    public LoginFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
