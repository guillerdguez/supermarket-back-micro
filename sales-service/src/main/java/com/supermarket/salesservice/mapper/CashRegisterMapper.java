package com.supermarket.salesservice.mapper;

import com.supermarket.salesservice.dto.cashregister.CashRegisterResponse;
import com.supermarket.salesservice.model.cashregister.CashRegister;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CashRegisterMapper {
    public CashRegisterResponse toResponse(CashRegister register) {
        if (register == null) return null;
        return CashRegisterResponse.builder()
                .id(register.getId())
                .branchId(register.getBranchId())
                .branchName(register.getBranchName())
                .openingBalance(register.getOpeningBalance())
                .closingBalance(register.getClosingBalance())
                .openingTime(register.getOpeningTime())
                .closingTime(register.getClosingTime())
                .status(register.getStatus())
                .openedById(register.getOpenedById())
                .openedByUsername(register.getOpenedByUsername())
                .closedById(register.getClosedById())
                .closedByUsername(register.getClosedByUsername())
                .build();
    }

    public List<CashRegisterResponse> toResponseList(List<CashRegister> registers) {
        if (registers == null) {
            return null;
        }
        return registers.stream()
                .map(this::toResponse)
                .toList();
    }
}