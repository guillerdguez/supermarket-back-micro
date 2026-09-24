package com.supermarket.transferservice.event;

public final class TransferTopics {
    public static final String REQUESTED = "transfer.requested";
    public static final String APPROVED = "transfer.approved";
    public static final String REJECTED = "transfer.rejected";
    public static final String COMPLETED = "transfer.completed";

    private TransferTopics() {
    }
}
