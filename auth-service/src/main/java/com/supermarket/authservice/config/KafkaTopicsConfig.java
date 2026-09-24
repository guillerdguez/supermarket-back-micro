package com.supermarket.authservice.config;

import com.supermarket.authservice.event.AuthTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {

    @Bean
    NewTopic loginSuccessTopic() {
        return TopicBuilder.name(AuthTopics.LOGIN_SUCCESS).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic loginFailedTopic() {
        return TopicBuilder.name(AuthTopics.LOGIN_FAILED).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic logoutTopic() {
        return TopicBuilder.name(AuthTopics.LOGOUT).partitions(3).replicas(1).build();
    }
}
