package com.supermarket.salesservice.service.business;

import com.supermarket.salesservice.dto.payment.PaymentRequest;
import com.supermarket.salesservice.dto.payment.PaymentResponse;
import java.util.List;

public interface PaymentService {
    PaymentResponse registerPayment(PaymentRequest request);
    List<PaymentResponse> getPaymentsBySale(Long saleId);
}