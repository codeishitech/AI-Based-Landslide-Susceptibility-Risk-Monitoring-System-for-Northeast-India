package com.ner.landslide;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the NER Landslide Early-Warning backend.
 *
 * This Spring Boot application acts as the orchestration and decision-support
 * layer connecting raw environmental data, ML risk predictions, geospatial
 * intelligence (PostGIS), and emergency response workflows.
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableRetry
public class LandslideApplication {

    public static void main(String[] args) {
        SpringApplication.run(LandslideApplication.class, args);
    }
}
