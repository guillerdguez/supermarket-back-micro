package com.supermarket.catalogservice.service.business;

import com.supermarket.catalogservice.dto.product.ProductRequest;
import com.supermarket.catalogservice.dto.product.ProductResponse;
import com.supermarket.catalogservice.model.product.Product;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

public interface ProductService {

    List<ProductResponse> getAll(Specification<Product> spec);

    ProductResponse getById(Long id);

    List<ProductResponse> getByIds(List<Long> ids);

    ProductResponse create(ProductRequest product);

    ProductResponse update(Long id, ProductRequest product);

    void delete(Long id);

}
