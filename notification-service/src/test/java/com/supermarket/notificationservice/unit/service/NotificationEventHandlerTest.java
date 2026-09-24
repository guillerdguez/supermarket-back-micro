package com.supermarket.notificationservice.unit.service;

import com.supermarket.commons.exception.RemoteServiceException;
import com.supermarket.notificationservice.client.RecipientDirectory;
import com.supermarket.notificationservice.event.CashRegisterDiscrepancyEvent;
import com.supermarket.notificationservice.event.NotificationEventHandler;
import com.supermarket.notificationservice.event.SaleCancelledEvent;
import com.supermarket.notificationservice.event.StockLowEvent;
import com.supermarket.notificationservice.event.TransferEvent;
import com.supermarket.notificationservice.model.notification.NotificationType;
import com.supermarket.notificationservice.model.notification.Recipient;
import com.supermarket.notificationservice.model.notification.ReferenceType;
import com.supermarket.notificationservice.model.processed.ProcessedEventRepository;
import com.supermarket.notificationservice.service.business.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class NotificationEventHandlerTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private RecipientDirectory recipientDirectory;
    @Mock
    private ProcessedEventRepository processedEventRepository;
    @InjectMocks
    private NotificationEventHandler handler;

    private final List<Recipient> managers = List.of(new Recipient(1L, "admin"), new Recipient(2L, "manager1"));

    @BeforeEach
    void setUp() {
        lenient().when(recipientDirectory.managers()).thenReturn(managers);
    }

    private TransferEvent transferEvent(String type, String reason) {
        return new TransferEvent("evt-" + type, type, LocalDateTime.now(), 7L, 29, 57L, "Papel Higienico Pack 12",
                6L, "Almacen Central", 1L, "Sucursal Centro", 15L, "bnavarro", reason);
    }

    @Test
    @DisplayName("stock.low - should notify admins and managers with the monolith message")
    void onLowStock_ShouldNotifyManagers() {
        handler.onLowStock(new StockLowEvent("evt-1", "stock.low", LocalDateTime.now(),
                1L, "Central Branch", 10L, "Rice", 3, 10));

        then(notificationService).should().createNotificationForUsers(
                eq(managers), eq(NotificationType.LOW_STOCK),
                eq("Low stock alert: 'Rice' in branch 'Central Branch'. Current: 3, Minimum: 10"),
                isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("transfer.requested - should notify admins and managers")
    void onTransferRequested_ShouldNotifyManagers() {
        handler.onTransferRequested(transferEvent("transfer.requested", null));

        then(notificationService).should().createNotificationForUsers(
                eq(managers), eq(NotificationType.TRANSFER_REQUESTED),
                eq("Transfer requested: 29 units of 'Papel Higienico Pack 12' from 'Almacen Central' to 'Sucursal Centro'"),
                isNull(), eq(ReferenceType.TRANSFER), eq(7L));
    }

    @Test
    @DisplayName("transfer.approved - should notify the requester only")
    void onTransferApproved_ShouldNotifyRequester() {
        handler.onTransferApproved(transferEvent("transfer.approved", null));

        then(notificationService).should().createNotification(
                eq(new Recipient(15L, "bnavarro")), eq(NotificationType.TRANSFER_APPROVED),
                argThat(message -> message.contains("has been approved")), isNull(),
                eq(ReferenceType.TRANSFER), eq(7L));
        then(recipientDirectory).should(never()).managers();
    }

    @Test
    @DisplayName("transfer.rejected - should notify the requester with the reason")
    void onTransferRejected_ShouldNotifyRequesterWithReason() {
        handler.onTransferRejected(transferEvent("transfer.rejected", "Stock needed locally"));

        then(notificationService).should().createNotification(
                eq(new Recipient(15L, "bnavarro")), eq(NotificationType.TRANSFER_REJECTED),
                argThat(message -> message.endsWith("Reason: Stock needed locally")), isNull(),
                eq(ReferenceType.TRANSFER), eq(7L));
    }

    @Test
    @DisplayName("transfer.completed - should notify admins and managers")
    void onTransferCompleted_ShouldNotifyManagers() {
        handler.onTransferCompleted(transferEvent("transfer.completed", null));

        then(notificationService).should().createNotificationForUsers(
                eq(managers), eq(NotificationType.TRANSFER_COMPLETED),
                argThat(message -> message.startsWith("Transfer completed: 29 units")), isNull(),
                eq(ReferenceType.TRANSFER), eq(7L));
    }

    @Test
    @DisplayName("cashregister.discrepancy - should notify admins and managers with two decimals")
    void onCashRegisterDiscrepancy_ShouldNotifyManagers() {
        handler.onCashRegisterDiscrepancy(new CashRegisterDiscrepancyEvent("evt-2", "cashregister.discrepancy",
                LocalDateTime.now(), 9L, 4L, "Sucursal Patraix", new BigDecimal("4.73")));

        then(notificationService).should().createNotificationForUsers(
                eq(managers), eq(NotificationType.CASH_REGISTER_DISCREPANCY),
                eq("Cash register discrepancy detected in branch 'Sucursal Patraix'. Variance: 4.73"),
                isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("sale.cancelled - should notify admins and managers")
    void onSaleCancelled_ShouldNotifyManagers() {
        handler.onSaleCancelled(new SaleCancelledEvent("evt-3", "sale.cancelled", LocalDateTime.now(),
                22L, 5L, "Sucursal Malvarrosa", "Venta duplicada por error de caja"));

        then(notificationService).should().createNotificationForUsers(
                eq(managers), eq(NotificationType.SALE_CANCELLED),
                eq("Sale #22 in branch 'Sucursal Malvarrosa' has been cancelled. Reason: Venta duplicada por error de caja"),
                isNull(), eq(ReferenceType.SALE), eq(22L));
    }

    @Test
    @DisplayName("a redelivered event is ignored so notifications are not duplicated")
    void duplicatedEvent_ShouldBeSkipped() {
        given(processedEventRepository.existsById("evt-1")).willReturn(true);

        handler.onLowStock(new StockLowEvent("evt-1", "stock.low", LocalDateTime.now(),
                1L, "Central Branch", 10L, "Rice", 3, 10));

        then(notificationService).shouldHaveNoInteractions();
        then(processedEventRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("if auth-service is unavailable the exception escapes so Kafka retries and finally sends it to the DLT")
    void recipientsUnavailable_ShouldPropagateForRetry() {
        given(recipientDirectory.managers())
                .willThrow(new RemoteServiceException("auth-service", "auth-service is temporarily unavailable"));

        assertThatThrownBy(() -> handler.onLowStock(new StockLowEvent("evt-9", "stock.low", LocalDateTime.now(),
                1L, "Central Branch", 10L, "Rice", 3, 10)))
                .isInstanceOf(RemoteServiceException.class);
        then(notificationService).should(never()).createNotificationForUsers(any(), any(), anyString(), any(), any(), any());
    }
}
