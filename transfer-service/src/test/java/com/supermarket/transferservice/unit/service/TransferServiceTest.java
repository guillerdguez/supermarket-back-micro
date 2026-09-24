package com.supermarket.transferservice.unit.service;

import com.supermarket.commons.exception.InsufficientPermissionsException;
import com.supermarket.commons.exception.InsufficientStockException;
import com.supermarket.commons.exception.InvalidOperationException;
import com.supermarket.commons.exception.ResourceNotFoundException;
import com.supermarket.commons.security.CurrentUserProvider;
import com.supermarket.transferservice.client.BranchRemoteService;
import com.supermarket.transferservice.client.BranchSummary;
import com.supermarket.transferservice.client.CatalogRemoteService;
import com.supermarket.transferservice.dto.transfer.TransferRequest;
import com.supermarket.transferservice.dto.transfer.TransferResponse;
import com.supermarket.transferservice.event.TransferEventPublisher;
import com.supermarket.transferservice.mapper.TransferMapper;
import com.supermarket.transferservice.model.transfer.CompletionState;
import com.supermarket.transferservice.model.transfer.StockTransfer;
import com.supermarket.transferservice.model.transfer.TransferStatus;
import com.supermarket.transferservice.repository.StockTransferRepository;
import com.supermarket.transferservice.service.business.impl.TransferServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.supermarket.transferservice.fixtures.transfer.TransferFixtures.WAREHOUSE_ID;
import static com.supermarket.transferservice.fixtures.transfer.TransferFixtures.admin;
import static com.supermarket.transferservice.fixtures.transfer.TransferFixtures.approvedTransfer;
import static com.supermarket.transferservice.fixtures.transfer.TransferFixtures.cashier;
import static com.supermarket.transferservice.fixtures.transfer.TransferFixtures.manager;
import static com.supermarket.transferservice.fixtures.transfer.TransferFixtures.northBranch;
import static com.supermarket.transferservice.fixtures.transfer.TransferFixtures.pendingTransfer;
import static com.supermarket.transferservice.fixtures.transfer.TransferFixtures.product;
import static com.supermarket.transferservice.fixtures.transfer.TransferFixtures.sameBranchRequest;
import static com.supermarket.transferservice.fixtures.transfer.TransferFixtures.validRejectRequest;
import static com.supermarket.transferservice.fixtures.transfer.TransferFixtures.validTransferRequest;
import static com.supermarket.transferservice.fixtures.transfer.TransferFixtures.warehouse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private StockTransferRepository transferRepository;
    @Mock
    private BranchRemoteService branchRemoteService;
    @Mock
    private CatalogRemoteService catalogRemoteService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private TransferEventPublisher transferEventPublisher;

    private TransferServiceImpl transferService;

    @BeforeEach
    void setUp() {
        transferService = new TransferServiceImpl(transferRepository, branchRemoteService, catalogRemoteService,
                new TransferMapper(), currentUserProvider, transferEventPublisher);
        lenient().when(currentUserProvider.getCurrentUser()).thenReturn(cashier());
        lenient().when(transferRepository.save(any(StockTransfer.class))).thenAnswer(inv -> {
            StockTransfer transfer = inv.getArgument(0);
            if (transfer.getId() == null) {
                transfer.setId(1L);
            }
            return transfer;
        });
    }

    @Nested
    @DisplayName("requestTransfer")
    class RequestTransfer {

        @Test
        @DisplayName("should create PENDING transfer with names frozen from branch and catalog")
        void request_WhenStockSufficient_ShouldCreatePending() {
            given(branchRemoteService.requireBranch(WAREHOUSE_ID, "Source branch not found")).willReturn(warehouse());
            given(branchRemoteService.requireBranch(2L, "Target branch not found")).willReturn(northBranch());
            given(catalogRemoteService.requireProduct(1L)).willReturn(product());
            given(branchRemoteService.getStock(WAREHOUSE_ID, 1L)).willReturn(50);

            TransferResponse response = transferService.requestTransfer(validTransferRequest());

            assertThat(response.getStatus()).isEqualTo(TransferStatus.PENDING);
            assertThat(response.getSourceBranchName()).isEqualTo("Central Warehouse");
            assertThat(response.getTargetBranchName()).isEqualTo("North Branch");
            assertThat(response.getProductName()).isEqualTo("Premium Rice");
            assertThat(response.getRequestedByUsername()).isEqualTo("cashier-test");
            then(transferEventPublisher).should().transferRequested(any(StockTransfer.class));
        }

        @Test
        @DisplayName("should throw InvalidOperationException when source and target are the same branch")
        void request_WhenSameBranch_ShouldThrow() {
            assertThatThrownBy(() -> transferService.requestTransfer(sameBranchRequest()))
                    .isInstanceOf(InvalidOperationException.class);
            then(transferRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when source branch does not exist")
        void request_WhenSourceMissing_ShouldThrow() {
            given(branchRemoteService.requireBranch(WAREHOUSE_ID, "Source branch not found"))
                    .willThrow(new ResourceNotFoundException("Source branch not found"));

            assertThatThrownBy(() -> transferService.requestTransfer(validTransferRequest()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Source branch not found");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when target branch does not exist")
        void request_WhenTargetMissing_ShouldThrow() {
            given(branchRemoteService.requireBranch(WAREHOUSE_ID, "Source branch not found")).willReturn(warehouse());
            given(branchRemoteService.requireBranch(2L, "Target branch not found"))
                    .willThrow(new ResourceNotFoundException("Target branch not found"));

            assertThatThrownBy(() -> transferService.requestTransfer(validTransferRequest()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Target branch not found");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when product does not exist")
        void request_WhenProductMissing_ShouldThrow() {
            given(branchRemoteService.requireBranch(WAREHOUSE_ID, "Source branch not found")).willReturn(warehouse());
            given(branchRemoteService.requireBranch(2L, "Target branch not found")).willReturn(northBranch());
            given(catalogRemoteService.requireProduct(1L)).willThrow(new ResourceNotFoundException("Product not found"));

            assertThatThrownBy(() -> transferService.requestTransfer(validTransferRequest()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw InsufficientStockException when source has not enough stock")
        void request_WhenStockInsufficient_ShouldThrow() {
            given(branchRemoteService.requireBranch(WAREHOUSE_ID, "Source branch not found")).willReturn(warehouse());
            given(branchRemoteService.requireBranch(2L, "Target branch not found")).willReturn(northBranch());
            given(catalogRemoteService.requireProduct(1L)).willReturn(product());
            given(branchRemoteService.getStock(WAREHOUSE_ID, 1L)).willReturn(3);

            assertThatThrownBy(() -> transferService.requestTransfer(validTransferRequest()))
                    .isInstanceOf(InsufficientStockException.class)
                    .hasMessageContaining("Available: 3, requested: 10");
        }

        @Test
        @DisplayName("should throw InvalidOperationException when source is not the warehouse")
        void request_WhenSourceNotWarehouse_ShouldThrow() {
            given(branchRemoteService.requireBranch(WAREHOUSE_ID, "Source branch not found")).willReturn(northBranch());

            assertThatThrownBy(() -> transferService.requestTransfer(validTransferRequest()))
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("central warehouse");
        }

        @Test
        @DisplayName("should default source to the warehouse branch and target to the user's branch when omitted")
        void request_WhenBranchesOmitted_ShouldUseDefaults() {
            TransferRequest request = TransferRequest.builder().productId(1L).quantity(5).build();
            given(branchRemoteService.requireWarehouse()).willReturn(warehouse());
            given(branchRemoteService.requireBranch(2L, "Target branch not found")).willReturn(northBranch());
            given(catalogRemoteService.requireProduct(1L)).willReturn(product());
            given(branchRemoteService.getStock(WAREHOUSE_ID, 1L)).willReturn(50);

            TransferResponse response = transferService.requestTransfer(request);

            assertThat(response.getSourceBranchId()).isEqualTo(WAREHOUSE_ID);
            assertThat(response.getTargetBranchId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("should throw InvalidOperationException when source omitted and no warehouse is configured")
        void request_WhenNoWarehouse_ShouldThrow() {
            TransferRequest request = TransferRequest.builder().targetBranchId(2L).productId(1L).quantity(5).build();
            given(branchRemoteService.requireWarehouse())
                    .willThrow(new ResourceNotFoundException("No central warehouse branch is configured"));

            assertThatThrownBy(() -> transferService.requestTransfer(request))
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("No central warehouse");
        }

        @Test
        @DisplayName("should throw InvalidOperationException when target omitted and user has no branch")
        void request_WhenUserWithoutBranch_ShouldThrow() {
            given(currentUserProvider.getCurrentUser()).willReturn(manager());
            given(branchRemoteService.requireWarehouse()).willReturn(warehouse());
            TransferRequest request = TransferRequest.builder().productId(1L).quantity(5).build();

            assertThatThrownBy(() -> transferService.requestTransfer(request))
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("no branch assigned");
        }
    }

    @Nested
    @DisplayName("approve / reject / cancel")
    class Lifecycle {

        @Test
        @DisplayName("should approve a PENDING transfer and set approvedBy")
        void approve_ShouldApprove() {
            given(currentUserProvider.getCurrentUser()).willReturn(manager());
            given(transferRepository.findById(1L)).willReturn(Optional.of(pendingTransfer()));

            TransferResponse response = transferService.approveTransfer(1L);

            assertThat(response.getStatus()).isEqualTo(TransferStatus.APPROVED);
            assertThat(response.getApprovedByUsername()).isEqualTo("manager-test");
            then(transferEventPublisher).should().transferApproved(any());
        }

        @Test
        @DisplayName("should throw when approving a transfer that is not PENDING")
        void approve_WhenNotPending_ShouldThrow() {
            given(transferRepository.findById(1L)).willReturn(Optional.of(approvedTransfer()));

            assertThatThrownBy(() -> transferService.approveTransfer(1L)).isInstanceOf(InvalidOperationException.class);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when transfer does not exist")
        void approve_WhenMissing_ShouldThrow() {
            given(transferRepository.findById(9L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> transferService.approveTransfer(9L)).isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should reject a PENDING transfer and store reason")
        void reject_ShouldStoreReason() {
            given(currentUserProvider.getCurrentUser()).willReturn(manager());
            given(transferRepository.findById(1L)).willReturn(Optional.of(pendingTransfer()));

            TransferResponse response = transferService.rejectTransfer(1L, validRejectRequest());

            assertThat(response.getStatus()).isEqualTo(TransferStatus.REJECTED);
            assertThat(response.getRejectionReason()).contains("promotion");
            then(transferEventPublisher).should().transferRejected(any());
        }

        @Test
        @DisplayName("should throw when rejecting a transfer that is not PENDING")
        void reject_WhenNotPending_ShouldThrow() {
            given(transferRepository.findById(1L)).willReturn(Optional.of(approvedTransfer()));

            assertThatThrownBy(() -> transferService.rejectTransfer(1L, validRejectRequest()))
                    .isInstanceOf(InvalidOperationException.class);
        }

        @Test
        @DisplayName("should allow requester to cancel their own PENDING transfer")
        void cancel_ByRequester_ShouldCancel() {
            given(transferRepository.findById(1L)).willReturn(Optional.of(pendingTransfer()));

            assertThat(transferService.cancelTransfer(1L).getStatus()).isEqualTo(TransferStatus.CANCELLED);
        }

        @Test
        @DisplayName("should allow ADMIN to cancel any PENDING transfer")
        void cancel_ByAdmin_ShouldCancel() {
            given(currentUserProvider.getCurrentUser()).willReturn(admin());
            given(transferRepository.findById(1L)).willReturn(Optional.of(pendingTransfer()));

            assertThat(transferService.cancelTransfer(1L).getStatus()).isEqualTo(TransferStatus.CANCELLED);
        }

        @Test
        @DisplayName("should throw InsufficientPermissionsException when non-requester non-admin tries to cancel")
        void cancel_ByOther_ShouldThrow() {
            given(currentUserProvider.getCurrentUser()).willReturn(manager());
            given(transferRepository.findById(1L)).willReturn(Optional.of(pendingTransfer()));

            assertThatThrownBy(() -> transferService.cancelTransfer(1L))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("should throw InvalidOperationException when cancelling a transfer that is not PENDING")
        void cancel_WhenNotPending_ShouldThrow() {
            given(transferRepository.findById(1L)).willReturn(Optional.of(approvedTransfer()));

            assertThatThrownBy(() -> transferService.cancelTransfer(1L)).isInstanceOf(InvalidOperationException.class);
        }
    }

    @Nested
    @DisplayName("completeTransfer")
    class CompleteTransfer {

        @Test
        @DisplayName("should debit source, credit target with attempt-scoped keys and mark COMPLETED")
        void complete_ShouldMoveStock() {
            StockTransfer transfer = approvedTransfer();
            given(transferRepository.findById(1L)).willReturn(Optional.of(transfer));
            given(branchRemoteService.requireBranch(2L, "Target branch no longer exists")).willReturn(northBranch());

            TransferResponse response = transferService.completeTransfer(1L);

            assertThat(response.getStatus()).isEqualTo(TransferStatus.COMPLETED);
            then(branchRemoteService).should().decreaseStock(WAREHOUSE_ID, "transfer-1-attempt-1-source", 1L, 10);
            then(branchRemoteService).should().increaseStock(2L, "transfer-1-attempt-1-target", 1L, 10);
            then(transferEventPublisher).should().transferCompleted(transfer);
        }

        @Test
        @DisplayName("should throw InvalidOperationException when transfer is not APPROVED")
        void complete_WhenNotApproved_ShouldThrow() {
            given(transferRepository.findById(1L)).willReturn(Optional.of(pendingTransfer()));

            assertThatThrownBy(() -> transferService.completeTransfer(1L))
                    .isInstanceOf(InvalidOperationException.class);
            then(branchRemoteService).should(never()).decreaseStock(anyLong(), anyString(), anyLong(), anyInt());
        }

        @Test
        @DisplayName("should throw InsufficientStockException when source stock dropped since approval")
        void complete_WhenSourceStockDropped_ShouldThrowWithoutCrediting() {
            StockTransfer transfer = approvedTransfer();
            given(transferRepository.findById(1L)).willReturn(Optional.of(transfer));
            given(branchRemoteService.requireBranch(2L, "Target branch no longer exists")).willReturn(northBranch());
            given(branchRemoteService.decreaseStock(anyLong(), anyString(), anyLong(), anyInt()))
                    .willThrow(new InsufficientStockException("Insufficient stock"));

            assertThatThrownBy(() -> transferService.completeTransfer(1L))
                    .isInstanceOf(InsufficientStockException.class);
            then(branchRemoteService).should(never()).increaseStock(anyLong(), anyString(), anyLong(), anyInt());
            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.APPROVED);
            assertThat(transfer.getCompletionState()).isEqualTo(CompletionState.NONE);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when target branch no longer exists")
        void complete_WhenTargetGone_ShouldThrow() {
            given(transferRepository.findById(1L)).willReturn(Optional.of(approvedTransfer()));
            given(branchRemoteService.requireBranch(2L, "Target branch no longer exists"))
                    .willThrow(new ResourceNotFoundException("Target branch no longer exists"));

            assertThatThrownBy(() -> transferService.completeTransfer(1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Target branch no longer exists");
            then(branchRemoteService).should(never()).decreaseStock(anyLong(), anyString(), anyLong(), anyInt());
        }

        @Test
        @DisplayName("should compensate the source when crediting the target fails and open a new attempt")
        void complete_WhenTargetCreditFails_ShouldCompensateSource() {
            StockTransfer transfer = approvedTransfer();
            given(transferRepository.findById(1L)).willReturn(Optional.of(transfer));
            given(branchRemoteService.requireBranch(2L, "Target branch no longer exists")).willReturn(northBranch());
            given(branchRemoteService.increaseStock(2L, "transfer-1-attempt-1-target", 1L, 10))
                    .willThrow(new ResourceNotFoundException("Product 1 not found in branch 2"));

            assertThatThrownBy(() -> transferService.completeTransfer(1L))
                    .isInstanceOf(ResourceNotFoundException.class);

            then(branchRemoteService).should().increaseStock(WAREHOUSE_ID, "transfer-1-attempt-1-compensation", 1L, 10);
            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.APPROVED);
            assertThat(transfer.getCompletionState()).isEqualTo(CompletionState.NONE);
            assertThat(transfer.getCompletionAttempt()).isEqualTo(2);
            then(transferEventPublisher).should(never()).transferCompleted(any());
        }

        @Test
        @DisplayName("should resume a pending compensation before retrying when a previous attempt was interrupted")
        void complete_WhenCompensationPending_ShouldFinishItFirst() {
            StockTransfer transfer = approvedTransfer();
            transfer.setCompletionAttempt(1);
            transfer.setCompletionState(CompletionState.COMPENSATING);
            given(transferRepository.findById(1L)).willReturn(Optional.of(transfer));
            given(branchRemoteService.requireBranch(2L, "Target branch no longer exists")).willReturn(northBranch());

            transferService.completeTransfer(1L);

            ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
            then(branchRemoteService).should(times(2))
                    .increaseStock(anyLong(), keys.capture(), anyLong(), anyInt());
            assertThat(keys.getAllValues()).containsExactly(
                    "transfer-1-attempt-1-compensation", "transfer-1-attempt-2-target");
            then(branchRemoteService).should().decreaseStock(WAREHOUSE_ID, "transfer-1-attempt-2-source", 1L, 10);
            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        }

        @Test
        @DisplayName("should resume from the target credit when the source was already debited")
        void complete_WhenSourceAlreadyDebited_ShouldOnlyCreditTarget() {
            StockTransfer transfer = approvedTransfer();
            transfer.setCompletionAttempt(1);
            transfer.setCompletionState(CompletionState.SOURCE_DEBITED);
            given(transferRepository.findById(1L)).willReturn(Optional.of(transfer));
            given(branchRemoteService.requireBranch(2L, "Target branch no longer exists")).willReturn(northBranch());

            transferService.completeTransfer(1L);

            then(branchRemoteService).should(never()).decreaseStock(anyLong(), anyString(), anyLong(), anyInt());
            then(branchRemoteService).should().increaseStock(2L, "transfer-1-attempt-1-target", 1L, 10);
        }
    }

    @Nested
    @DisplayName("queries")
    class Queries {

        @Test
        @DisplayName("getMyTransfers should return transfers requested by the current user")
        void getMyTransfers_ShouldUseCurrentUser() {
            given(transferRepository.findByRequestedById(1L)).willReturn(List.of(pendingTransfer()));

            assertThat(transferService.getMyTransfers()).hasSize(1);
        }

        @Test
        @DisplayName("getTransfersByStatus should return transfers filtered by valid status string")
        void getByStatus_ShouldFilter() {
            given(transferRepository.findByStatus(TransferStatus.PENDING)).willReturn(List.of(pendingTransfer()));

            assertThat(transferService.getTransfersByStatus("pending")).hasSize(1);
        }

        @Test
        @DisplayName("getTransfersByStatus should throw when status string is invalid")
        void getByStatus_WhenInvalid_ShouldThrow() {
            assertThatThrownBy(() -> transferService.getTransfersByStatus("unknown"))
                    .isInstanceOf(InvalidOperationException.class);
        }

        @Test
        @DisplayName("getTransfersBySourceBranch should throw when branch not found")
        void getBySource_WhenBranchMissing_ShouldThrow() {
            given(branchRemoteService.requireBranch(99L, "Branch not found with id: 99"))
                    .willThrow(new ResourceNotFoundException("Branch not found with id: 99"));

            assertThatThrownBy(() -> transferService.getTransfersBySourceBranch(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("getTransfersByTargetBranch should throw when branch not found")
        void getByTarget_WhenBranchMissing_ShouldThrow() {
            given(branchRemoteService.requireBranch(99L, "Branch not found with id: 99"))
                    .willThrow(new ResourceNotFoundException("Branch not found with id: 99"));

            assertThatThrownBy(() -> transferService.getTransfersByTargetBranch(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("getTransfersBySourceBranch should list transfers of an existing branch")
        void getBySource_ShouldList() {
            BranchSummary warehouse = warehouse();
            given(branchRemoteService.requireBranch(WAREHOUSE_ID, "Branch not found with id: " + WAREHOUSE_ID))
                    .willReturn(warehouse);
            given(transferRepository.findBySourceBranchId(WAREHOUSE_ID)).willReturn(List.of(pendingTransfer()));

            assertThat(transferService.getTransfersBySourceBranch(WAREHOUSE_ID)).hasSize(1);
        }
    }
}
