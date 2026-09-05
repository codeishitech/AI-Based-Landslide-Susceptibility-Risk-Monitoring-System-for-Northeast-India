package com.ner.landslide.exception;

/**
 * Thrown when a call to an external dependency (weather API, satellite feed,
 * ML risk engine) fails after retries are exhausted.
 */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message) {
        super(message);
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
