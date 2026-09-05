package com.ner.landslide.config;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

/**
 * Fallback KafkaTemplate when Kafka is disabled (e.g. in local development).
 */
@Configuration
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "false")
public class FallbackKafkaConfig {

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<String, String>(new DefaultKafkaProducerFactory<>(Collections.emptyMap())) {
            @Override
            public CompletableFuture<SendResult<String, String>> send(String topic, String key, String data) {
                RecordMetadata metadata = new RecordMetadata(new TopicPartition(topic, 0), 0, 0, System.currentTimeMillis(), 0, 0);
                return CompletableFuture.completedFuture(new SendResult<>(null, metadata));
            }
        };
    }
}
