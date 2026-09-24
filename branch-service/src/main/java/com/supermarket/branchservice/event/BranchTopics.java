package com.supermarket.branchservice.event;

public final class BranchTopics {
    public static final String STOCK_LOW = "stock.low";
    public static final String PRODUCT_CREATED = "product.created";
    public static final String PRODUCT_DELETED = "product.deleted";

    private BranchTopics() {
    }
}
