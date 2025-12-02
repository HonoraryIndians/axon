package com.axon.entry_service.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
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
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String broker_port;
    /**
     * Create a ProducerFactory configured for String keys and JSON-serialized values using the configured broker address.
     *
     * @return a ProducerFactory that produces String-keyed, JSON-valued Kafka producers configured with the application's broker address
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
     * Create a KafkaTemplate configured to send messages with String keys and JSON-serialized values to the configured Kafka broker.
     *
     * @return a KafkaTemplate for sending messages with String keys and JSON-serialized values
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * Create a ConsumerFactory<String, Object> configured for JSON value deserialization.
     *
     * The factory is configured to connect to the broker at {@code broker_port}, use the consumer group
     * id "axon-group", use {@link org.apache.kafka.common.serialization.StringDeserializer} for keys,
     * and a {@link org.springframework.kafka.support.serializer.JsonDeserializer} for values with all
     * packages trusted.
     *
     * @return a {@code DefaultKafkaConsumerFactory<String,Object>} with the described key and value deserializers
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
     * Creates a ConcurrentKafkaListenerContainerFactory configured with the application's consumer factory for use by annotated Kafka listeners.
     *
     * @return a ConcurrentKafkaListenerContainerFactory<String, Object> that uses the configured ConsumerFactory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
}