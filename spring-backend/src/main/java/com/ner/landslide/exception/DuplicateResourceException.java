package com.ner.landslide.exception;

/** Thrown on unique-constraint style conflicts, e.g. registering an existing username/email. */
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}
