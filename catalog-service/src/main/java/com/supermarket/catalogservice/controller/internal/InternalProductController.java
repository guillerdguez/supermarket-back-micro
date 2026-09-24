package com.supermarket.catalogservice.controller.internal;

import com.supermarket.catalogservice.dto.product.ProductResponse;
import com.supermarket.catalogservice.service.business.ProductService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Hidden
@RestController
@RequestMapping("/internal/products")
@RequiredArgsConstructor
public class InternalProductController {

    private final ProductService productService;

    @GetMapping
    public List<ProductResponse> getProducts(@RequestParam(value = "ids", required = false) List<Long> ids) {
        return ids == null ? productService.getAll(null) : productService.getByIds(ids);
    }

    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable Long id) {
        return productService.getById(id);
    }
}
