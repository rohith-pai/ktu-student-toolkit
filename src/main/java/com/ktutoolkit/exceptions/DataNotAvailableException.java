package com.ktutoolkit.exceptions;

/**
 * Thrown when ETLab responds successfully but the expected data could not be
 * found in the returned page.
 */
public class DataNotAvailableException extends Exception {
    public DataNotAvailableException(String message) {
        super(message);
    }

    public DataNotAvailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
