package com.supermarket.salesservice.unit.service;

import com.supermarket.salesservice.dto.payment.PaymentRequest;
import com.supermarket.salesservice.dto.payment.PaymentResponse;
import com.supermarket.commons.exception.InvalidOperationException;
import com.supermarket.commons.exception.ResourceNotFoundException;
import com.supermarket.salesservice.fixtures.payment.PaymentFixtures;
import com.supermarket.salesservice.fixtures.sale.SaleFixtures;
import com.supermarket.salesservice.mapper.PaymentMapper;
import com.supermarket.salesservice.model.sale.Payment;
import com.supermarket.salesservice.model.sale.PaymentType;
import com.supermarket.salesservice.model.sale.Sale;
import com.supermarket.salesservice.model.sale.SaleStatus;
import com.supermarket.salesservice.repository.PaymentRepository;
import com.supermarket.salesservice.repository.SaleRepository;
import com.supermarket.salesservice.service.business.impl.PaymentServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private SaleRepository saleRepository;
    @Mock
    private PaymentMapper paymentMapper;
    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Test
    void registerPayment_shouldSavePayment() {
        Sale sale = SaleFixtures.saleWithDetails();
        sale.setStatus(SaleStatus.REGISTERED);
        sale.setTotal(new BigDecimal("100.00"));

        PaymentRequest request = new PaymentRequest(sale.getId(), new BigDecimal("50.00"), PaymentType.CASH, null);
        Payment payment = PaymentFixtures.cashPayment(10L, sale);
        PaymentResponse response = PaymentResponse.builder().id(10L).build();

        given(saleRepository.findById(sale.getId())).willReturn(Optional.of(sale));
        given(paymentRepository.findBySaleId(sale.getId())).willReturn(List.of());
        given(paymentRepository.save(any(Payment.class))).willReturn(payment);
        given(paymentMapper.toResponse(payment)).willReturn(response);

        PaymentResponse result = paymentService.registerPayment(request);

        assertThat(result.getId()).isEqualTo(10L);
        then(paymentRepository).should().save(any(Payment.class));
    }

    @Test
    void registerPayment_whenSaleNotFound_shouldThrowException() {
        given(saleRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.registerPayment(new PaymentRequest(99L, BigDecimal.TEN, PaymentType.CASH, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void registerPayment_whenSaleCancelled_shouldThrowException() {
        Sale sale = SaleFixtures.saleWithDetails();
        sale.setStatus(SaleStatus.CANCELLED);
        given(saleRepository.findById(sale.getId())).willReturn(Optional.of(sale));

        assertThatThrownBy(() -> paymentService.registerPayment(new PaymentRequest(sale.getId(), BigDecimal.TEN, PaymentType.CASH, null)))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    void registerPayment_whenTotalExceeded_shouldThrowException() {
        Sale sale = SaleFixtures.saleWithDetails();
        sale.setStatus(SaleStatus.REGISTERED);
        sale.setTotal(new BigDecimal("50.00"));

        given(saleRepository.findById(sale.getId())).willReturn(Optional.of(sale));
        Payment existingPayment = PaymentFixtures.cashPayment(1L, sale);
        existingPayment.setAmount(new BigDecimal("30.00"));
        given(paymentRepository.findBySaleId(sale.getId())).willReturn(List.of(existingPayment));

        assertThatThrownBy(() -> paymentService.registerPayment(new PaymentRequest(sale.getId(), new BigDecimal("30.00"), PaymentType.CASH, null)))
                .isInstanceOf(InvalidOperationException.class);
    }
}
