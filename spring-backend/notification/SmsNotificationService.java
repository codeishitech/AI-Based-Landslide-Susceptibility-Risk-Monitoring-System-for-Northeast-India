package com.ner.landslide.notification;

import com.ner.landslide.entity.Alert;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Service
public class SmsNotificationService {

    private final WebClient webClient;

    @Value("${app.sms.enabled:false}")
    private boolean enabled;

    @Value("${app.sms.account-sid:}")
    private String accountSid;

    @Value("${app.sms.auth-token:}")
    private String authToken;

    @Value("${app.sms.from-number:}")
    private String fromNumber;

    @Value("${app.sms.recipients:}")
    private String recipients;

    @Value("${app.sms.language:en}")
    private String language;

    public SmsNotificationService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public void sendEarlyWarning(Alert alert) {

        if (!enabled) {
            return;
        }

        if (isBlank(accountSid)
                || isBlank(authToken)
                || isBlank(fromNumber)
                || isBlank(recipients)) {
            System.err.println(
                    "SMS is enabled but Twilio configuration is incomplete."
            );
            return;
        }

        NotificationLanguage notificationLanguage =
                NotificationLanguage.fromCode(language);

        MultilingualNotificationService translator =
                new MultilingualNotificationService();

        MultilingualNotificationService.NotificationMessage notification =
                translator.translate(alert, notificationLanguage);

        List<String> phoneNumbers = Arrays.stream(recipients.split(","))
                .map(String::trim)
                .filter(number -> !number.isBlank())
                .toList();

        for (String phoneNumber : phoneNumbers) {
            sendSms(
                    phoneNumber,
                    notification.title() + ": " + notification.message()
            );
        }
    }

    private void sendSms(String phoneNumber, String message) {

        try {

            webClient.post()
                    .uri("https://api.twilio.com/2010-04-01/Accounts/"
                            + accountSid + "/Messages.json")
                    .headers(headers ->
                            headers.setBasicAuth(
                                    accountSid,
                                    authToken,
                                    StandardCharsets.UTF_8
                            )
                    )
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(
                            "To=" + encode(phoneNumber)
                                    + "&From=" + encode(fromNumber)
                                    + "&Body=" + encode(message)
                    )
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

        } catch (Exception e) {

            // SMS failure should not stop the main alert workflow.
            System.err.println(
                    "Failed to send SMS to "
                            + phoneNumber
                            + ": "
                            + e.getMessage()
            );
        }
    }

    private String encode(String value) {
        return java.net.URLEncoder
                .encode(value, StandardCharsets.UTF_8);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}