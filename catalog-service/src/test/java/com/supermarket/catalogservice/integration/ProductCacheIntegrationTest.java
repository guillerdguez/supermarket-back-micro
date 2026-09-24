package com.supermarket.catalogservice.integration;

import com.supermarket.catalogservice.dto.product.ProductRequest;
import com.supermarket.catalogservice.event.ProductEventPublisher;
import com.supermarket.catalogservice.model.product.Product;
import com.supermarket.catalogservice.repository.ProductRepository;
import com.supermarket.catalogservice.service.business.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;

@SpringBootTest(properties = "spring.cache.type=redis")
@ActiveProfiles("test")
@Testcontainers
class ProductCacheIntegrationTest {

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.0"))
            .withExposedPorts(6379);

    @Autowired
    private ProductService productService;
    @Autowired
    private CacheManager cacheManager;
    @MockitoSpyBean
    private ProductRepository productRepository;
    @MockitoBean
    private ProductEventPublisher productEventPublisher;

    private Long productId;

    @BeforeEach
    void setUp() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        productRepository.deleteAll();
        productId = productRepository.save(Product.builder()
                .name("Leche Entera 1L").barcode("8410000000001").category("Lacteos")
                .price(new BigDecimal("1.15")).build()).getId();
        clearInvocations(productRepository);
    }

    @Test
    @DisplayName("second read of the same product is served from Redis without hitting the database")
    void getById_ShouldBeCached() {
        productService.getById(productId);
        productService.getById(productId);

        then(productRepository).should(times(1)).findById(productId);
    }

    @Test
    @DisplayName("updating a product evicts its cached entry so the next read sees the new price")
    void update_ShouldEvictCache() {
        productService.getById(productId);

        productService.update(productId, ProductRequest.builder()
                .name("Leche Entera 1L").category("Lacteos").price(new BigDecimal("1.25")).build());

        assertThat(productService.getById(productId).getPrice()).isEqualByComparingTo("1.25");
    }
}
