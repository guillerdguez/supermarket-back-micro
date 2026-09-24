package com.supermarket.catalogservice.config;

import com.supermarket.catalogservice.event.ProductTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {

    @Bean
    NewTopic productCreatedTopic() {
        return TopicBuilder.name(ProductTopics.CREATED).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic productDeletedTopic() {
        return TopicBuilder.name(ProductTopics.DELETED).partitions(3).replicas(1).build();
    }
}
