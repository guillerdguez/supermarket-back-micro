package com.supermarket.notificationservice.event;

public final class NotificationTopics {
    public static final String STOCK_LOW = "stock.low";
    public static final String TRANSFER_REQUESTED = "transfer.requested";
    public static final String TRANSFER_APPROVED = "transfer.approved";
    public static final String TRANSFER_REJECTED = "transfer.rejected";
    public static final String TRANSFER_COMPLETED = "transfer.completed";
    public static final String SALE_CANCELLED = "sale.cancelled";
    public static final String CASH_REGISTER_DISCREPANCY = "cashregister.discrepancy";

    private NotificationTopics() {
    }
}
