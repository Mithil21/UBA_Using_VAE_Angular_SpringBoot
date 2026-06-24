package com.forensic.audit.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;

@EnableKafka
@Configuration
public class KafkaConfig {

    public static final String TOPIC_NAME    = "uba-vae-requests";
    public static final String VAE_DEAD_LETTER_TOPIC = "uba-vae-dead-letter";
    public static final int    MAX_RETRIES           = 3;

    @Bean
    public NewTopic vaeRequestsTopic() {
        return new NewTopic(TOPIC_NAME, 3, (short) 1);
    }

    @Bean
    public NewTopic vaeDeadLetterTopic() {
        return new NewTopic(VAE_DEAD_LETTER_TOPIC, 1, (short) 1);
    }

    @Bean
    public KafkaTemplate<String, VAEAnalysisMessage> kafkaTemplate(
            ProducerFactory<String, VAEAnalysisMessage> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, VAEAnalysisMessage>
    kafkaListenerContainerFactory(
            ConsumerFactory<String, VAEAnalysisMessage> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, VAEAnalysisMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        // Manual ack — offset only advances after ack.acknowledge() is called
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}