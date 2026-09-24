package com.supermarket.salesservice.service.business;

import com.supermarket.salesservice.dto.sale.CancelSaleRequest;
import com.supermarket.salesservice.dto.sale.SaleRequest;
import com.supermarket.salesservice.dto.sale.SaleResponse;

import java.util.List;

public interface SaleService {
    List<SaleResponse> getAll();

    SaleResponse getById(Long id);

    SaleResponse create(SaleRequest sale);

    SaleResponse cancel(Long id, CancelSaleRequest request);

    List<SaleResponse> getSalesByCashier(Long cashierId);

    SaleResponse getSaleByIdAndCashier(Long saleId, Long cashierId);

}