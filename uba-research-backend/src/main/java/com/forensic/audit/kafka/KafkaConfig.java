package com.forensic.audit.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
public class KafkaConfig {

    public static final String TOPIC_NAME = "uba-vae-requests";
    public static final String VAE_DEAD_LETTER_TOPIC = "uba-vae-dead-letter";
    public static final int    MAX_RETRIES          = 3;

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;


    @Bean
    public NewTopic vaeRequestsTopic() {
        return new NewTopic(TOPIC_NAME, 3, (short) 1);
    }

    @Bean
    public NewTopic vaeDeadLetterTopic() {
        return new NewTopic(VAE_DEAD_LETTER_TOPIC, 1, (short) 1);
    }

    // -----------------------------------------------------------------------
    // ObjectMapper — handles LocalDateTime serialisation
    // -----------------------------------------------------------------------

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    public ProducerFactory<String, VAEAnalysisMessage> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Ensure delivery before returning
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, MAX_RETRIES);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public ConsumerFactory<String, VAEAnalysisMessage> consumerFactory() {
        JsonDeserializer<VAEAnalysisMessage> deserializer =
                new JsonDeserializer<>(VAEAnalysisMessage.class, false);
        deserializer.addTrustedPackages("com.forensic.audit.*");

        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,  bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG,           "uba-vae-group");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,  "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
        // Manual offset commit — only after successful processing
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, VAEAnalysisMessage>
    kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, VAEAnalysisMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        // Manual ack mode — offset committed only after processing succeeds
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL_IMMEDIATE
        );
        return factory;
    }
}
