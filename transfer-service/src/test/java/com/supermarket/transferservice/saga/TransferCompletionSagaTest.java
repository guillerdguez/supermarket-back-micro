package com.supermarket.transferservice.saga;

import com.supermarket.commons.exception.RemoteServiceException;
import com.supermarket.commons.security.CurrentUserProvider;
import com.supermarket.transferservice.client.BranchRemoteService;
import com.supermarket.transferservice.client.CatalogRemoteService;
import com.supermarket.transferservice.client.StockItem;
import com.supermarket.transferservice.client.StockMovementRequest;
import com.supermarket.transferservice.event.TransferEventPublisher;
import com.supermarket.transferservice.mapper.TransferMapper;
import com.supermarket.transferservice.model.transfer.CompletionState;
import com.supermarket.transferservice.model.transfer.StockTransfer;
import com.supermarket.transferservice.model.transfer.TransferStatus;
import com.supermarket.transferservice.repository.StockTransferRepository;
import com.supermarket.transferservice.service.business.impl.TransferServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.supermarket.transferservice.fixtures.transfer.TransferFixtures.WAREHOUSE_ID;
import static com.supermarket.transferservice.fixtures.transfer.TransferFixtures.approvedTransfer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class TransferCompletionSagaTest {

    private static final Long TARGET_ID = 2L;

    private FakeInventoryBranchClient inventory;
    private StockTransfer transfer;
    private TransferServiceImpl transferService;

    @BeforeEach
    void setUp() {
        inventory = new FakeInventoryBranchClient(WAREHOUSE_ID, 100, TARGET_ID, 5);
        transfer = approvedTransfer();
        StockTransferRepository repository = mock(StockTransferRepository.class);
        given(repository.findById(1L)).willAnswer(inv -> Optional.of(transfer));
        given(repository.save(any(StockTransfer.class))).willAnswer(inv -> {
            transfer = inv.getArgument(0);
            return transfer;
        });
        transferService = new TransferServiceImpl(repository, new BranchRemoteService(inventory),
                mock(CatalogRemoteService.class), new TransferMapper(), mock(CurrentUserProvider.class),
                mock(TransferEventPublisher.class));
    }

    @Test
    @DisplayName("a normal completion moves exactly the transferred quantity")
    void completion_ShouldMoveQuantity() {
        transferService.completeTransfer(1L);

        assertThat(inventory.stockOf(WAREHOUSE_ID)).isEqualTo(90);
        assertThat(inventory.stockOf(TARGET_ID)).isEqualTo(15);
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
    }

    @Test
    @DisplayName("a failure crediting the target is compensated and a later retry completes without double debit")
    void failureMidway_ShouldCompensateAndRetryConsistently() {
        inventory.failNextTargetCredits(1);

        assertThatThrownBy(() -> transferService.completeTransfer(1L)).isInstanceOf(RemoteServiceException.class);
        assertThat(inventory.stockOf(WAREHOUSE_ID)).isEqualTo(100);
        assertThat(inventory.stockOf(TARGET_ID)).isEqualTo(5);
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.APPROVED);

        transferService.completeTransfer(1L);

        assertThat(inventory.stockOf(WAREHOUSE_ID)).isEqualTo(90);
        assertThat(inventory.stockOf(TARGET_ID)).isEqualTo(15);
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
    }

    @Test
    @DisplayName("if the compensation itself fails the saga remembers it and finishes it on the next attempt")
    void compensationFailure_ShouldBeResumed() {
        inventory.failNextTargetCredits(1);
        inventory.failNextCompensations(1);

        assertThatThrownBy(() -> transferService.completeTransfer(1L)).isInstanceOf(RemoteServiceException.class);
        assertThat(transfer.getCompletionState()).isEqualTo(CompletionState.COMPENSATING);
        assertThat(inventory.stockOf(WAREHOUSE_ID)).isEqualTo(90);

        transferService.completeTransfer(1L);

        assertThat(inventory.stockOf(WAREHOUSE_ID)).isEqualTo(90);
        assertThat(inventory.stockOf(TARGET_ID)).isEqualTo(15);
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
    }

    @Test
    @DisplayName("repeating a completion after a crash that happened right after debiting never debits twice")
    void crashAfterDebit_ShouldReplaySafely() {
        transfer.setCompletionAttempt(1);
        inventory.decreaseStock(WAREHOUSE_ID, TransferServiceImpl.sourceKey(transfer),
                new StockMovementRequest(List.of(new StockItem(1L, 10))));
        assertThat(inventory.stockOf(WAREHOUSE_ID)).isEqualTo(90);

        transferService.completeTransfer(1L);

        assertThat(inventory.stockOf(WAREHOUSE_ID)).isEqualTo(90);
        assertThat(inventory.stockOf(TARGET_ID)).isEqualTo(15);
    }
}
