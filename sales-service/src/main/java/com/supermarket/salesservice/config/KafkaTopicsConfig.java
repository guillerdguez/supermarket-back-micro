package com.supermarket.salesservice.config;

import com.supermarket.salesservice.event.SalesTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {

    @Bean
    NewTopic saleCompletedTopic() {
        return TopicBuilder.name(SalesTopics.SALE_COMPLETED).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic saleCancelledTopic() {
        return TopicBuilder.name(SalesTopics.SALE_CANCELLED).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic cashRegisterDiscrepancyTopic() {
        return TopicBuilder.name(SalesTopics.CASH_REGISTER_DISCREPANCY).partitions(3).replicas(1).build();
    }
}
