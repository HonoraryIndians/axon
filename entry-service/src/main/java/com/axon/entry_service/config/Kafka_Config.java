package com.axon.entry_service.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
public class Kafka_Config {

    private final String broker_port ="localhost:9092";
    /**
     * Creates a ProducerFactory for sending messages with String keys and JSON-serialized values.
     *
     * The factory is configured to connect to the configured Kafka broker address and to use
     * a String serializer for keys and a JSON serializer for values.
     *
     * @return a ProducerFactory<String, Object> configured for the application's Kafka broker and serializers
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> data = new HashMap<>();
        data.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker_port);
        data.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        data.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        return new DefaultKafkaProducerFactory<>(data);
    }

    /**
     * Create a KafkaTemplate for sending messages to the configured Kafka broker.
     *
     * @return the KafkaTemplate used to send messages to Kafka
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * Creates a ConsumerFactory configured to consume JSON-serialized messages from the Kafka broker.
     *
     * The factory is configured with the application's broker address, consumer group ID "axon-group",
     * a String key deserializer, and a JsonDeserializer for values that trusts all packages.
     *
     * @return a ConsumerFactory<String, Object> using a String key deserializer and a JsonDeserializer for values
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        JsonDeserializer<Object> deserializer = new JsonDeserializer<>();
        deserializer.addTrustedPackages("*");

        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker_port);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "axon-group");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);

        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
    }

    /**
     * Creates a ConcurrentKafkaListenerContainerFactory configured for the application's Kafka consumers.
     *
     * The factory is wired with the configured ConsumerFactory and is used to build listener containers for @KafkaListener methods.
     *
     * @return a ConcurrentKafkaListenerContainerFactory<String, Object> configured with the application's ConsumerFactory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
}