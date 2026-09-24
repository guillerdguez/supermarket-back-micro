package com.supermarket.catalogservice.unit.service;

import com.supermarket.catalogservice.dto.product.ProductRequest;
import com.supermarket.catalogservice.dto.product.ProductResponse;
import com.supermarket.commons.exception.DuplicateResourceException;
import com.supermarket.commons.exception.InvalidOperationException;
import com.supermarket.commons.exception.RemoteServiceException;
import com.supermarket.commons.exception.ResourceNotFoundException;
import com.supermarket.catalogservice.mapper.ProductMapper;
import com.supermarket.catalogservice.model.product.Product;
import com.supermarket.catalogservice.repository.ProductRepository;
import com.supermarket.catalogservice.client.SalesUsageService;
import com.supermarket.catalogservice.event.ProductEventPublisher;
import com.supermarket.catalogservice.service.business.impl.ProductServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static com.supermarket.catalogservice.fixtures.product.ProductFixtures.defaultProduct;
import static com.supermarket.catalogservice.fixtures.product.ProductFixtures.productResponse;
import static com.supermarket.catalogservice.fixtures.product.ProductFixtures.validProductRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private SalesUsageService salesUsageService;
    @Mock
    private ProductEventPublisher productEventPublisher;
    @InjectMocks
    private ProductServiceImpl productService;

    @Test
    @DisplayName("GET ALL - should return list")
    void getAll_ShouldReturnList() {
        Product product = defaultProduct();
        ProductResponse response = productResponse();
        given(productRepository.findAll(nullable(Specification.class), eq(Sort.by("name")))).willReturn(List.of(product));
        given(productMapper.toResponseList(List.of(product))).willReturn(List.of(response));
        List<ProductResponse> result = productService.getAll(null);
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("GET BY ID - should return product")
    void getById_ShouldReturnProduct() {
        Long id = 1L;
        Product product = defaultProduct();
        ProductResponse response = productResponse();
        given(productRepository.findById(id)).willReturn(Optional.of(product));
        given(productMapper.toResponse(product)).willReturn(response);
        ProductResponse result = productService.getById(id);
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Premium Rice");
    }

    @Test
    @DisplayName("GET BY ID - should throw exception when not found")
    void getById_WhenNotFound_ShouldThrowException() {
        given(productRepository.findById(1L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> productService.getById(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("CREATE - should save product when name is unique")
    void create_WhenNameUnique_ShouldSave() {
        ProductRequest request = validProductRequest();
        Product entity = defaultProduct();
        ProductResponse response = productResponse();

        given(productRepository.existsByName(request.getName())).willReturn(false);
        given(productMapper.toEntity(request)).willReturn(entity);
        given(productRepository.save(entity)).willReturn(entity);
        given(productMapper.toResponse(entity)).willReturn(response);

        ProductResponse result = productService.create(request);

        assertThat(result).isNotNull();
        then(productRepository).should().save(entity);
        then(productEventPublisher).should().productCreated(entity);
    }

    @Test
    @DisplayName("CREATE - should throw exception when name exists")
    void create_WhenNameExists_ShouldThrowException() {
        ProductRequest request = validProductRequest();
        given(productRepository.existsByName(request.getName())).willReturn(true);
        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(DuplicateResourceException.class);
        then(productRepository).should(never()).save(any(Product.class));
    }

    @Test
    @DisplayName("CREATE - should throw exception when barcode exists")
    void create_WhenBarcodeExists_ShouldThrowException() {
        ProductRequest request = validProductRequest();
        given(productRepository.existsByName(request.getName())).willReturn(false);
        given(productRepository.existsByBarcode(request.getBarcode())).willReturn(true);
        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(DuplicateResourceException.class);
        then(productRepository).should(never()).save(any(Product.class));
    }

    @Test
    @DisplayName("UPDATE - should update product")
    void update_ShouldUpdateProduct() {
        Long id = 1L;
        ProductRequest request = validProductRequest();
        Product existingProduct = defaultProduct();
        ProductResponse response = productResponse();
        given(productRepository.findById(id)).willReturn(Optional.of(existingProduct));
        given(productRepository.existsByName(request.getName())).willReturn(false);
        given(productRepository.save(existingProduct)).willReturn(existingProduct);
        given(productMapper.toResponse(existingProduct)).willReturn(response);
        ProductResponse result = productService.update(id, request);
        assertThat(result).isNotNull();
        then(productMapper).should().updateEntity(request, existingProduct);
    }

    @Test
    @DisplayName("UPDATE - should throw exception when duplicate name")
    void update_WithDuplicateName_ShouldThrowException() {
        Long id = 1L;
        ProductRequest request = validProductRequest();
        Product existingProduct = defaultProduct();
        given(productRepository.findById(id)).willReturn(Optional.of(existingProduct));
        given(productRepository.existsByName(request.getName())).willReturn(true);
        assertThatThrownBy(() -> productService.update(id, request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("UPDATE - should throw exception when duplicate barcode")
    void update_WithDuplicateBarcode_ShouldThrowException() {
        Long id = 1L;
        ProductRequest request = validProductRequest();
        Product existingProduct = defaultProduct();
        given(productRepository.findById(id)).willReturn(Optional.of(existingProduct));
        given(productRepository.existsByName(request.getName())).willReturn(false);
        given(productRepository.existsByBarcode(request.getBarcode())).willReturn(true);
        assertThatThrownBy(() -> productService.update(id, request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("DELETE - should delete product when no sales associated")
    void delete_ShouldDeleteProduct() {
        Long id = 1L;
        Product product = defaultProduct();
        given(productRepository.findById(id)).willReturn(Optional.of(product));
        given(salesUsageService.isProductInUse(id)).willReturn(false);
        productService.delete(id);
        then(productRepository).should().delete(product);
        then(productEventPublisher).should().productDeleted(product);
    }

    @Test
    @DisplayName("DELETE - should refuse to delete when sales-service cannot confirm the product is unused")
    void delete_WhenSalesServiceUnavailable_ShouldFailClosed() {
        Long id = 1L;
        Product product = defaultProduct();
        given(productRepository.findById(id)).willReturn(Optional.of(product));
        given(salesUsageService.isProductInUse(id))
                .willThrow(new RemoteServiceException("sales-service", "sales-service is temporarily unavailable"));
        assertThatThrownBy(() -> productService.delete(id))
                .isInstanceOf(RemoteServiceException.class);
        then(productRepository).should(never()).delete((Product) any());
        then(productEventPublisher).should(never()).productDeleted(any());
    }

    @Test
    @DisplayName("DELETE - should throw exception when product has associated sales")
    void delete_WhenProductHasSales_ShouldThrowException() {
        Long id = 1L;
        Product product = defaultProduct();
        given(productRepository.findById(id)).willReturn(Optional.of(product));
        given(salesUsageService.isProductInUse(id)).willReturn(true);
        assertThatThrownBy(() -> productService.delete(id))
                .isInstanceOf(InvalidOperationException.class);
        then(productRepository).should(never()).delete((Product) any());
    }

    @Test
    @DisplayName("DELETE - should throw exception when product not found")
    void delete_WhenNotFound_ShouldThrowException() {
        Long id = 999L;
        given(productRepository.findById(id)).willReturn(Optional.empty());
        assertThatThrownBy(() -> productService.delete(id))
                .isInstanceOf(ResourceNotFoundException.class);
        then(productRepository).should(never()).delete((Product) any());
    }
}