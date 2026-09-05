package com.ner.landslide.notification;

public enum NotificationLanguage {

    ENGLISH("en"),
    HINDI("hi"),
    ASSAMESE("as");

    private final String code;

    NotificationLanguage(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static NotificationLanguage fromCode(String code) {
        if (code == null || code.isBlank()) {
            return ENGLISH;
        }

        return switch (code.toLowerCase()) {
            case "hi" -> HINDI;
            case "as" -> ASSAMESE;
            default -> ENGLISH;
        };
    }
}