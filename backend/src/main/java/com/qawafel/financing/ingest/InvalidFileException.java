package com.qawafel.financing.ingest;

/** The file cannot be used at all (not Excel, no invoice sheet, required columns missing...). */
public class InvalidFileException extends RuntimeException {

    public InvalidFileException(String message) {
        super(message);
    }

    public InvalidFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
