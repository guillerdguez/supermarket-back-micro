package com.supermarket.catalogservice.service.business.impl;

import com.supermarket.catalogservice.dto.product.ProductRequest;
import com.supermarket.catalogservice.dto.product.ProductResponse;
import com.supermarket.commons.exception.DuplicateResourceException;
import com.supermarket.commons.exception.InvalidOperationException;
import com.supermarket.commons.exception.ResourceNotFoundException;
import com.supermarket.catalogservice.mapper.ProductMapper;
import com.supermarket.catalogservice.model.product.Product;
import com.supermarket.catalogservice.repository.ProductRepository;
import com.supermarket.catalogservice.client.SalesUsageService;
import com.supermarket.catalogservice.config.CacheConfig;
import com.supermarket.catalogservice.event.ProductEventPublisher;
import com.supermarket.catalogservice.service.business.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepo;
    private final ProductMapper productMapper;
    private final SalesUsageService salesUsageService;
    private final ProductEventPublisher productEventPublisher;

    @Transactional(readOnly = true)
    @Override
    public List<ProductResponse> getAll(Specification<Product> spec) {
        log.info("Fetching products");
        return productMapper.toResponseList(productRepo.findAll(spec, Sort.by("name")));
    }

    @Transactional(readOnly = true)
    @Override
    @Cacheable(value = CacheConfig.PRODUCTS_CACHE, key = "#id")
    public ProductResponse getById(Long id) {
        log.info("Fetching product with ID: {}", id);
        return productMapper.toResponse(findProduct(id));
    }

    @Transactional(readOnly = true)
    @Override
    public List<ProductResponse> getByIds(List<Long> ids) {
        return productMapper.toResponseList(productRepo.findAllById(ids));
    }

    @Override
    public ProductResponse create(ProductRequest request) {
        log.info("Creating new product: {}", request.getName());
        if (productRepo.existsByName(request.getName())) {
            throw new DuplicateResourceException("Product already exists with name: " + request.getName());
        }
        if (StringUtils.hasText(request.getBarcode()) && productRepo.existsByBarcode(request.getBarcode())) {
            throw new DuplicateResourceException("Product already exists with barcode: " + request.getBarcode());
        }
        Product product = productMapper.toEntity(request);
        Product saved = productRepo.save(product);
        productEventPublisher.productCreated(saved);
        return productMapper.toResponse(saved);
    }

    @Override
    @CacheEvict(value = CacheConfig.PRODUCTS_CACHE, key = "#id")
    public ProductResponse update(Long id, ProductRequest request) {
        log.info("Updating product with ID: {}", id);
        Product product = findProduct(id);
        if (request.getName() != null && !request.getName().equals(product.getName())) {
            if (productRepo.existsByName(request.getName())) {
                throw new DuplicateResourceException("Product name already in use: " + request.getName());
            }
        }
        if (StringUtils.hasText(request.getBarcode()) && !request.getBarcode().equals(product.getBarcode())) {
            if (productRepo.existsByBarcode(request.getBarcode())) {
                throw new DuplicateResourceException("Product barcode already in use: " + request.getBarcode());
            }
        }
        productMapper.updateEntity(request, product);
        return productMapper.toResponse(productRepo.save(product));
    }

    @Override
    @CacheEvict(value = CacheConfig.PRODUCTS_CACHE, key = "#id")
    public void delete(Long id) {
        log.info("Attempting to delete product with ID: {}", id);
        Product product = findProduct(id);
        if (salesUsageService.isProductInUse(id)) {
            throw new InvalidOperationException("Cannot delete product: It has associated sales records");
        }
        productRepo.delete(product);
        productEventPublisher.productDeleted(product);
        log.info("Product deleted successfully - ID: {}", id);
    }

    private Product findProduct(Long id) {
        return productRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + id));
    }

}
