package com.supermarket.salesservice.service.business.impl;

import com.supermarket.commons.exception.InsufficientPermissionsException;
import com.supermarket.commons.exception.InvalidOperationException;
import com.supermarket.commons.exception.InvalidSaleStateException;
import com.supermarket.commons.exception.ResourceNotFoundException;
import com.supermarket.commons.security.AuthenticatedUser;
import com.supermarket.commons.security.CurrentUserProvider;
import com.supermarket.salesservice.client.BranchRemoteService;
import com.supermarket.salesservice.client.BranchSummary;
import com.supermarket.salesservice.client.CatalogRemoteService;
import com.supermarket.salesservice.client.ProductSummary;
import com.supermarket.salesservice.client.StockItem;
import com.supermarket.salesservice.dto.sale.CancelSaleRequest;
import com.supermarket.salesservice.dto.sale.SaleRequest;
import com.supermarket.salesservice.dto.sale.SaleResponse;
import com.supermarket.salesservice.dto.saleDetail.SaleDetailRequest;
import com.supermarket.salesservice.event.SalesEventPublisher;
import com.supermarket.salesservice.mapper.SaleMapper;
import com.supermarket.salesservice.model.cashregister.CashRegister;
import com.supermarket.salesservice.model.sale.Payment;
import com.supermarket.salesservice.model.sale.Sale;
import com.supermarket.salesservice.model.sale.SaleDetail;
import com.supermarket.salesservice.model.sale.SaleStatus;
import com.supermarket.salesservice.repository.PaymentRepository;
import com.supermarket.salesservice.repository.SaleRepository;
import com.supermarket.salesservice.service.business.CashRegisterService;
import com.supermarket.salesservice.service.business.SaleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SaleServiceImpl implements SaleService {
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final SaleRepository saleRepo;
    private final PaymentRepository paymentRepository;
    private final SaleMapper saleMapper;
    private final CurrentUserProvider currentUserProvider;
    private final CashRegisterService cashRegisterService;
    private final BranchRemoteService branchRemoteService;
    private final CatalogRemoteService catalogRemoteService;
    private final SalesEventPublisher salesEventPublisher;
    private final PlatformTransactionManager transactionManager;

    @Override
    public SaleResponse create(SaleRequest request) {
        AuthenticatedUser currentUser = currentUserProvider.getCurrentUser();

        BranchSummary branch = branchRemoteService.requireBranch(request.getBranchId());
        if (!Boolean.TRUE.equals(branch.active())) {
            throw new InvalidOperationException("Cannot register a sale in an inactive branch");
        }
        CashRegister cashRegister = cashRegisterService.getRegisterEntityByBranch(branch.id());

        Map<Long, ProductSummary> products = catalogRemoteService.requireProducts(productIdsOf(request));
        List<SaleDetail> details = buildSaleDetails(request, products);
        BigDecimal total = calculateTotal(details);
        verifyPaymentCoversTotal(request.getAmount(), total);

        String operationKey = UUID.randomUUID().toString();
        List<StockItem> stockItems = stockItemsOf(request.getDetails());
        branchRemoteService.decreaseStock(branch.id(), decreaseKey(operationKey), stockItems);

        try {
            SaleResponse response = new TransactionTemplate(transactionManager).execute(status ->
                    persistSale(request, currentUser, branch, cashRegister, details, total, operationKey));
            log.info("Sale created with id: {}, branch: {}, total: {}", response.getId(), branch.id(), total);
            return response;
        } catch (RuntimeException persistenceFailure) {
            compensateStock(branch.id(), operationKey, stockItems, persistenceFailure);
            throw persistenceFailure;
        }
    }

    private SaleResponse persistSale(SaleRequest request, AuthenticatedUser currentUser, BranchSummary branch,
                                     CashRegister cashRegister, List<SaleDetail> details, BigDecimal total,
                                     String operationKey) {
        Sale sale = saleMapper.toEntity(request);
        sale.setStatus(SaleStatus.REGISTERED);
        sale.setBranchId(branch.id());
        sale.setBranchName(branch.name());
        sale.setCreatedById(currentUser.id());
        sale.setCreatedByUsername(currentUser.username());
        sale.setCreatedByEmail(currentUser.email());
        sale.setCashRegister(cashRegister);
        sale.setOperationKey(operationKey);
        sale.setTotal(total);
        sale.setDetails(new ArrayList<>());
        details.forEach(detail -> detail.setSale(sale));
        sale.getDetails().addAll(details);

        Sale saved = saleRepo.save(sale);

        Payment payment = paymentRepository.save(Payment.builder()
                .sale(saved)
                .amount(request.getAmount())
                .paymentType(request.getPaymentType())
                .paymentDate(LocalDateTime.now())
                .reference(request.getReference())
                .build());

        salesEventPublisher.saleCompleted(saved);
        return saleMapper.toResponse(saved, List.of(payment));
    }

    private void compensateStock(Long branchId, String operationKey, List<StockItem> items, RuntimeException cause) {
        log.error("Sale persistence failed after reducing stock in branch {}. Compensating operation {}",
                branchId, operationKey, cause);
        try {
            branchRemoteService.increaseStock(branchId, compensationKey(operationKey), items);
        } catch (RuntimeException compensationFailure) {
            log.error("Stock compensation {} failed for branch {}. Manual review required",
                    compensationKey(operationKey), branchId, compensationFailure);
        }
    }

    private Set<Long> productIdsOf(SaleRequest request) {
        return request.getDetails().stream()
                .map(SaleDetailRequest::getProductId)
                .collect(Collectors.toSet());
    }

    private List<SaleDetail> buildSaleDetails(SaleRequest request, Map<Long, ProductSummary> products) {
        return request.getDetails().stream()
                .map(detailRequest -> {
                    ProductSummary product = products.get(detailRequest.getProductId());
                    return SaleDetail.builder()
                            .productId(product.id())
                            .productName(product.name())
                            .productCategory(product.category())
                            .quantity(detailRequest.getQuantity())
                            .price(product.price())
                            .build();
                })
                .toList();
    }

    private List<StockItem> stockItemsOf(List<SaleDetailRequest> details) {
        return details.stream()
                .map(detail -> new StockItem(detail.getProductId(), detail.getQuantity()))
                .toList();
    }

    private BigDecimal calculateTotal(List<SaleDetail> details) {
        return details.stream()
                .map(detail -> detail.getPrice().multiply(BigDecimal.valueOf(detail.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void verifyPaymentCoversTotal(BigDecimal amount, BigDecimal total) {
        int comparison = amount.compareTo(total);
        if (comparison < 0) {
            throw new InvalidOperationException("Payment amount does not cover the sale total");
        }
        if (comparison > 0) {
            throw new InvalidOperationException("Payment amount exceeds the sale total");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaleResponse> getAll() {
        List<Sale> sales = saleRepo.findAll(DEFAULT_SORT);
        Map<Long, List<Payment>> paymentsBySaleId = paymentsGroupedBySaleId(
                sales.stream().map(Sale::getId).toList());
        return sales.stream()
                .map(sale -> saleMapper.toResponse(sale, paymentsBySaleId.getOrDefault(sale.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SaleResponse getById(Long id) {
        Sale sale = saleRepo.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found with id: " + id));
        return saleMapper.toResponse(sale, paymentRepository.findBySaleId(id));
    }

    private Map<Long, List<Payment>> paymentsGroupedBySaleId(List<Long> saleIds) {
        return paymentRepository.findBySaleIdIn(saleIds).stream()
                .collect(Collectors.groupingBy(payment -> payment.getSale().getId()));
    }

    @Override
    public SaleResponse cancel(Long id, CancelSaleRequest request) {
        AuthenticatedUser currentUser = currentUserProvider.getCurrentUser();
        Sale sale = saleRepo.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found with id: " + id));

        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new InvalidSaleStateException("Sale is already cancelled");
        }

        log.info("Cancelling sale id: {}", id);

        if (sale.getDetails() != null && !sale.getDetails().isEmpty()) {
            branchRemoteService.increaseStock(sale.getBranchId(), cancellationKey(sale.getId()),
                    sale.getDetails().stream()
                            .map(detail -> new StockItem(detail.getProductId(), detail.getQuantity()))
                            .toList());
        }

        SaleResponse response = new TransactionTemplate(transactionManager).execute(status -> {
            Sale current = saleRepo.findWithDetailsById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Sale not found with id: " + id));
            current.setStatus(SaleStatus.CANCELLED);
            current.setCancelledById(currentUser.id());
            current.setCancelledByUsername(currentUser.username());
            current.setCancellationReason(request.getReason());
            current.setCancelledAt(LocalDateTime.now());
            Sale saved = saleRepo.save(current);
            salesEventPublisher.saleCancelled(saved);
            return saleMapper.toResponse(saved, paymentRepository.findBySaleId(saved.getId()));
        });

        log.info("Sale cancelled with id: {}", id);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaleResponse> getSalesByCashier(Long cashierId) {
        List<Sale> sales = saleRepo.findByCreatedById(cashierId, DEFAULT_SORT);
        Map<Long, List<Payment>> paymentsBySaleId = paymentsGroupedBySaleId(
                sales.stream().map(Sale::getId).toList());
        return sales.stream()
                .map(sale -> saleMapper.toResponse(sale, paymentsBySaleId.getOrDefault(sale.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SaleResponse getSaleByIdAndCashier(Long saleId, Long cashierId) {
        Sale sale = saleRepo.findWithDetailsById(saleId)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found with id: " + saleId));
        if (sale.getCreatedById() == null || !sale.getCreatedById().equals(cashierId)) {
            throw new InsufficientPermissionsException("You are not allowed to view this sale");
        }
        return saleMapper.toResponse(sale, paymentRepository.findBySaleId(saleId));
    }

    public static String decreaseKey(String operationKey) {
        return "sale-" + operationKey + "-decrease";
    }

    public static String compensationKey(String operationKey) {
        return "sale-" + operationKey + "-compensation";
    }

    public static String cancellationKey(Long saleId) {
        return "sale-" + saleId + "-cancel";
    }
}
