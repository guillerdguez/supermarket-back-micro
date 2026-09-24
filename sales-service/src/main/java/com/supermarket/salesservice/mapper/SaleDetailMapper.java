package com.supermarket.salesservice.mapper;

import com.supermarket.salesservice.dto.saleDetail.SaleDetailRequest;
import com.supermarket.salesservice.dto.saleDetail.SaleDetailResponse;
import com.supermarket.salesservice.model.sale.SaleDetail;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class SaleDetailMapper {

    public SaleDetailResponse toResponse(SaleDetail entity) {
        if (entity == null)
            return null;

        BigDecimal subtotal = BigDecimal.ZERO;
        if (entity.getPrice() != null && entity.getQuantity() != null) {
            subtotal = entity.getPrice().multiply(BigDecimal.valueOf(entity.getQuantity()));
        }

        return SaleDetailResponse.builder()
                .id(entity.getId())
                .quantity(entity.getQuantity())
                .productName(entity.getProductName())
                .unitPrice(entity.getPrice())
                .subtotal(subtotal)
                .build();
    }

    public SaleDetail toEntity(SaleDetailRequest request) {
        if (request == null)
            return null;

        return SaleDetail.builder()
                .quantity(request.getQuantity())
                .build();
    }

    public List<SaleDetailResponse> toResponseList(List<SaleDetail> entities) {
        if (entities == null)
            return null;

        return entities.stream()
                .map(this::toResponse)
                .toList();
    }
}