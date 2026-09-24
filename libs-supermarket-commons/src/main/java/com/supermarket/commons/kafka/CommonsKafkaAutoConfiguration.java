package com.supermarket.commons.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.LinkedHashMap;
import java.util.Map;

@AutoConfiguration(after = KafkaAutoConfiguration.class)
@ConditionalOnClass(KafkaTemplate.class)
public class CommonsKafkaAutoConfiguration {

    public static final String DLT_SUFFIX = ".DLT";

    @Bean
    @ConditionalOnMissingBean
    public DomainEventPublisher domainEventPublisher(KafkaTemplate<Object, Object> kafkaTemplate) {
        return new DomainEventPublisher(kafkaTemplate);
    }

    @Bean
    public DefaultKafkaProducerFactoryCustomizer isoDatesJsonSerializerCustomizer(ObjectMapper objectMapper) {
        return factory -> factory.setValueSerializerSupplier(() -> new JsonSerializer<>(objectMapper.copy()));
    }

    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    public DefaultErrorHandler deadLetterErrorHandler(KafkaProperties kafkaProperties) {
        Map<String, Object> producerProperties = kafkaProperties.buildProducerProperties(null);
        KafkaTemplate<String, byte[]> rawTemplate = new KafkaTemplate<>(
                new DefaultKafkaProducerFactory<>(producerProperties, new StringSerializer(), new ByteArraySerializer()));
        KafkaTemplate<String, Object> jsonTemplate = new KafkaTemplate<>(
                new DefaultKafkaProducerFactory<>(producerProperties, new StringSerializer(), new JsonSerializer<>()));
        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        templates.put(byte[].class, rawTemplate);
        templates.put(Object.class, jsonTemplate);
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(templates,
                (record, exception) -> new TopicPartition(record.topic() + DLT_SUFFIX, -1));
        ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxElapsedTime(7_000L);
        return new DefaultErrorHandler(recoverer, backOff);
    }
}
