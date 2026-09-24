package com.supermarket.salesservice.mapper;

import com.supermarket.salesservice.dto.payment.PaymentResponse;
import com.supermarket.salesservice.model.sale.Payment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaymentMapper {
    public PaymentResponse toResponse(Payment payment) {
        if (payment == null) return null;
        return PaymentResponse.builder()
                .id(payment.getId())
                .saleId(payment.getSale() != null ? payment.getSale().getId() : null)
                .amount(payment.getAmount())
                .paymentType(payment.getPaymentType())
                .paymentDate(payment.getPaymentDate())
                .reference(payment.getReference())
                .build();
    }

    public List<PaymentResponse> toResponseList(List<Payment> payments) {
        if (payments == null) return null;
        return payments.stream()
                .map(this::toResponse)
                .toList();
    }
}