package com.devkanyanta.pos.auth.security;

public final class PermissionConstants {

    // Existing permissions
    public static final String PRODUCT_VIEW = "PRODUCT_VIEW";
    public static final String PRODUCT_CREATE = "PRODUCT_CREATE";
    public static final String PRODUCT_UPDATE = "PRODUCT_UPDATE";
    public static final String PRODUCT_DELETE = "PRODUCT_DELETE";

    public static final String CATEGORY_VIEW = "CATEGORY_VIEW";
    public static final String CATEGORY_CREATE = "CATEGORY_CREATE";
    public static final String CATEGORY_UPDATE = "CATEGORY_UPDATE";
    public static final String CATEGORY_DELETE = "CATEGORY_DELETE";

    // User permissions
    public static final String USER_VIEW = "USER_VIEW";
    public static final String USER_CREATE = "USER_CREATE";
    public static final String USER_UPDATE = "USER_UPDATE";
    public static final String USER_DELETE = "USER_DELETE";

    // Branch permissions
    public static final String BRANCH_VIEW = "BRANCH_VIEW";
    public static final String BRANCH_CREATE = "BRANCH_CREATE";
    public static final String BRANCH_UPDATE = "BRANCH_UPDATE";
    public static final String BRANCH_DELETE = "BRANCH_DELETE";

    // Customer permissions
    public static final String CUSTOMER_VIEW = "CUSTOMER_VIEW";
    public static final String CUSTOMER_CREATE = "CUSTOMER_CREATE";
    public static final String CUSTOMER_UPDATE = "CUSTOMER_UPDATE";
    public static final String CUSTOMER_DELETE = "CUSTOMER_DELETE";

    // Inventory permissions
    public static final String INVENTORY_VIEW = "INVENTORY_VIEW";
    public static final String INVENTORY_ADJUST = "INVENTORY_ADJUST";

    // Sale permissions
    public static final String SALE_VIEW = "SALE_VIEW";
    public static final String SALE_CREATE = "SALE_CREATE";
    public static final String SALE_VOID = "SALE_VOID";
    public static final String SALE_REFUND = "SALE_REFUND";

    // Payment Method permissions
    public static final String PAYMENT_METHOD_VIEW = "PAYMENT_METHOD_VIEW";
    public static final String PAYMENT_METHOD_CREATE = "PAYMENT_METHOD_CREATE";
    public static final String PAYMENT_METHOD_UPDATE = "PAYMENT_METHOD_UPDATE";
    public static final String PAYMENT_METHOD_DELETE = "PAYMENT_METHOD_DELETE";

    // Receipt permissions
    public static final String RECEIPT_VIEW = "RECEIPT_VIEW";
    public static final String RECEIPT_REPRINT = "RECEIPT_REPRINT";

    // ZRA permissions
    public static final String ZRA_VIEW = "ZRA_VIEW";

    // Report permissions
    public static final String REPORT_VIEW = "REPORT_VIEW";

    private PermissionConstants() {
    }
}
