package com.supermarket.branchservice.config;

import com.supermarket.branchservice.event.BranchTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {

    @Bean
    NewTopic stockLowTopic() {
        return TopicBuilder.name(BranchTopics.STOCK_LOW).partitions(3).replicas(1).build();
    }
}
