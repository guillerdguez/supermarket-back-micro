package com.supermarket.notificationservice.integration;

import com.supermarket.notificationservice.client.RecipientDirectory;
import com.supermarket.notificationservice.event.StockLowEvent;
import com.supermarket.notificationservice.model.notification.NotificationType;
import com.supermarket.notificationservice.model.notification.Recipient;
import com.supermarket.notificationservice.model.processed.ProcessedEventRepository;
import com.supermarket.notificationservice.repository.NotificationRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.BDDMockito.given;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=true")
@ActiveProfiles("test")
@Testcontainers
class NotificationKafkaIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.8.1");

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @MockitoBean
    private RecipientDirectory recipientDirectory;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        processedEventRepository.deleteAll();
        given(recipientDirectory.managers()).willReturn(List.of(new Recipient(1L, "admin"), new Recipient(2L, "manager1")));
    }

    @Test
    @DisplayName("a stock.low event published by branch-service ends as one notification per manager, even if redelivered")
    void stockLowEvent_ShouldCreateNotificationsOnce() {
        KafkaTemplate<String, Object> producer = producerLikeBranchService();
        StockLowEvent event = new StockLowEvent(UUID.randomUUID().toString(), "stock.low", LocalDateTime.now(),
                5L, "Sucursal Malvarrosa", 47L, "Aceite de Oliva Virgen Extra 1L", 15, 20);

        producer.send("stock.low", "5-47", event);
        producer.send("stock.low", "5-47", event);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(notificationRepository.findAll())
                        .hasSize(2)
                        .allSatisfy(notification -> {
                            assertThat(notification.getType()).isEqualTo(NotificationType.LOW_STOCK);
                            assertThat(notification.getMessage()).isEqualTo(
                                    "Low stock alert: 'Aceite de Oliva Virgen Extra 1L' in branch 'Sucursal Malvarrosa'. Current: 15, Minimum: 20");
                        }));
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(notificationRepository.count()).isEqualTo(2));
    }

    @Test
    @DisplayName("a malformed message does not block the partition: it goes to stock.low.DLT and later events still flow")
    void poisonMessage_ShouldGoToDeadLetterTopic() {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        KafkaTemplate<String, byte[]> raw = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
        ProducerRecord<String, byte[]> poison = new ProducerRecord<>("stock.low", "5-47",
                "{not-json".getBytes(StandardCharsets.UTF_8));
        poison.headers().add("__TypeId__", "stockLow".getBytes(StandardCharsets.UTF_8));
        raw.send(poison);

        StockLowEvent valid = new StockLowEvent(UUID.randomUUID().toString(), "stock.low", LocalDateTime.now(),
                1L, "Sucursal Centro", 30L, "Pan de Barra Unidad", 2, 10);
        producerLikeBranchService().send("stock.low", "1-30", valid);

        try (KafkaConsumer<String, byte[]> dltConsumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-inspector",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class))) {
            dltConsumer.subscribe(List.of("stock.low.DLT"));
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                List<ConsumerRecord<String, byte[]>> records = new ArrayList<>();
                dltConsumer.poll(Duration.ofMillis(500)).forEach(records::add);
                assertThat(records).anySatisfy(record ->
                        assertThat(new String(record.value(), StandardCharsets.UTF_8)).isEqualTo("{not-json"));
            });
        }

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(notificationRepository.findAll())
                        .anySatisfy(notification -> assertThat(notification.getMessage()).contains("Pan de Barra Unidad")));
    }

    private KafkaTemplate<String, Object> producerLikeBranchService() {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class,
                JsonSerializer.TYPE_MAPPINGS, "stockLow:" + StockLowEvent.class.getName());
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }
}
