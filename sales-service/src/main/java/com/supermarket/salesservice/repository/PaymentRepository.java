package com.supermarket.salesservice.repository;

import com.supermarket.salesservice.model.sale.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findBySaleId(Long saleId);

    List<Payment> findBySaleIdIn(List<Long> saleIds);
}