package com.supermarket.salesservice.service.business;

import com.supermarket.salesservice.dto.cashregister.CashRegisterResponse;
import com.supermarket.salesservice.dto.cashregister.CloseRegisterRequest;
import com.supermarket.salesservice.dto.cashregister.OpenRegisterRequest;
import com.supermarket.salesservice.model.cashregister.CashRegister;

import java.util.List;

public interface CashRegisterService {
    List<CashRegisterResponse> getAll();

    CashRegisterResponse openRegister(OpenRegisterRequest request);

    CashRegisterResponse closeRegister(Long registerId, CloseRegisterRequest request);

    CashRegisterResponse getCurrentRegisterByBranch(Long branchId);

    CashRegister getRegisterEntityByBranch(Long branchId);

}