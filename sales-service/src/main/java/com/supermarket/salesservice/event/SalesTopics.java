package com.supermarket.salesservice.event;

public final class SalesTopics {
    public static final String SALE_COMPLETED = "sale.completed";
    public static final String SALE_CANCELLED = "sale.cancelled";
    public static final String CASH_REGISTER_DISCREPANCY = "cashregister.discrepancy";

    private SalesTopics() {
    }
}
