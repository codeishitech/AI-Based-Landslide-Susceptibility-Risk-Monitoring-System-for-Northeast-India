package com.ner.landslide.notification;

import com.ner.landslide.entity.Alert;

public class MultilingualNotificationService {

    public NotificationMessage translate(
            Alert alert,
            NotificationLanguage language
    ) {

        if (language == null || language == NotificationLanguage.ENGLISH) {
            return new NotificationMessage(
                    alert.getTitle(),
                    alert.getMessage()
            );
        }

        String title;
        String message;

        switch (language) {

            case HINDI -> {
                title = "भूस्खलन जोखिम चेतावनी";
                message = "चेतावनी: इस क्षेत्र में भूस्खलन का जोखिम "
                        + "उच्च है। कृपया सावधानी बरतें और सुरक्षित स्थान पर रहें।";
            }

            case ASSAMESE -> {
                title = "ভূমিস্খলনৰ বিপদৰ সতৰ্কবাণী";
                message = "সতৰ্কবাণী: এই অঞ্চলত ভূমিস্খলনৰ আশংকা "
                        + "বেছি। অনুগ্ৰহ কৰি সাৱধানতা অৱলম্বন কৰক আৰু "
                        + "নিৰাপদ স্থানত থাকক।";
            }

            default -> {
                title = alert.getTitle();
                message = alert.getMessage();
            }
        }

        return new NotificationMessage(title, message);
    }

    public record NotificationMessage(
            String title,
            String message
    ) {}
}