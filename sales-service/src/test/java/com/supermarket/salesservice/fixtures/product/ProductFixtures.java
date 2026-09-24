package com.supermarket.salesservice.fixtures.product;

import com.supermarket.salesservice.client.ProductSummary;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;

@UtilityClass
public class ProductFixtures {
    public static ProductSummary defaultProduct() {
        return productWithId(1L);
    }

    public static ProductSummary productWithId(Long id) {
        return new ProductSummary(id, "Premium Rice", "8410000000001", "Food", new BigDecimal("2.50"));
    }
}
