package com.ner.landslide.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Kafka wiring for the event-driven pipeline described in the README:
 * WEATHER_UPDATED -> risk evaluation -> PREDICTION_GENERATED -> ALERT_TRIGGERED.
 * Topics are declared here; producers/consumers live in the relevant services.
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "ner-landslide-backend");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }

    @Bean
    public NewTopic weatherUpdatedTopic() {
        return TopicBuilder.name("WEATHER_UPDATED").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic satelliteDataUpdatedTopic() {
        return TopicBuilder.name("SATELLITE_DATA_UPDATED").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic predictionGeneratedTopic() {
        return TopicBuilder.name("PREDICTION_GENERATED").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic riskLevelChangedTopic() {
        return TopicBuilder.name("RISK_LEVEL_CHANGED").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic fieldReportCreatedTopic() {
        return TopicBuilder.name("FIELD_REPORT_CREATED").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic alertTriggeredTopic() {
        return TopicBuilder.name("ALERT_TRIGGERED").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic alertResolvedTopic() {
        return TopicBuilder.name("ALERT_RESOLVED").partitions(3).replicas(1).build();
    }
}
