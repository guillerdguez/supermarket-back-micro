package com.supermarket.transferservice.config;

import com.supermarket.transferservice.event.TransferTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {

    @Bean
    NewTopic transferRequestedTopic() {
        return TopicBuilder.name(TransferTopics.REQUESTED).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic transferApprovedTopic() {
        return TopicBuilder.name(TransferTopics.APPROVED).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic transferRejectedTopic() {
        return TopicBuilder.name(TransferTopics.REJECTED).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic transferCompletedTopic() {
        return TopicBuilder.name(TransferTopics.COMPLETED).partitions(3).replicas(1).build();
    }
}
