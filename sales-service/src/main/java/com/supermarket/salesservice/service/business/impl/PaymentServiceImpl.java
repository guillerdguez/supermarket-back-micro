package com.supermarket.salesservice.service.business.impl;

import com.supermarket.salesservice.dto.payment.PaymentRequest;
import com.supermarket.salesservice.dto.payment.PaymentResponse;
import com.supermarket.commons.exception.InvalidOperationException;
import com.supermarket.commons.exception.ResourceNotFoundException;
import com.supermarket.salesservice.mapper.PaymentMapper;
import com.supermarket.salesservice.model.sale.Payment;
import com.supermarket.salesservice.model.sale.Sale;
import com.supermarket.salesservice.model.sale.SaleStatus;
import com.supermarket.salesservice.repository.PaymentRepository;
import com.supermarket.salesservice.repository.SaleRepository;
import com.supermarket.salesservice.service.business.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRepository paymentRepository;
    private final SaleRepository saleRepository;
    private final PaymentMapper paymentMapper;

    @Override
    public PaymentResponse registerPayment(PaymentRequest request) {
        Sale sale = saleRepository.findById(request.getSaleId())
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found"));

        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new InvalidOperationException("Cannot register payment for a cancelled sale");
        }

        BigDecimal totalPaid = paymentRepository.findBySaleId(sale.getId()).stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalPaid.add(request.getAmount()).compareTo(sale.getTotal()) > 0) {
            throw new InvalidOperationException("Total payment exceeds sale total");
        }

        Payment payment = Payment.builder()
                .sale(sale)
                .amount(request.getAmount())
                .paymentType(request.getPaymentType())
                .paymentDate(LocalDateTime.now())
                .reference(request.getReference())
                .build();

        return paymentMapper.toResponse(paymentRepository.save(payment));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsBySale(Long saleId) {
        return paymentRepository.findBySaleId(saleId).stream()
                .map(paymentMapper::toResponse)
                .toList();
    }
}