package com.ner.landslide.exception;

/**
 * Thrown when a prediction payload from the ML risk engine fails validation
 * beyond simple bean-validation checks (e.g. references an unknown zone,
 * or contains an internally inconsistent risk score / confidence pairing).
 */
public class InvalidPredictionException extends RuntimeException {

    public InvalidPredictionException(String message) {
        super(message);
    }
}
