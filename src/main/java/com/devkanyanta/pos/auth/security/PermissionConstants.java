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

    public static final String ZRA_MANAGE = "ZRA_MANAGE";

    // Report permissions
    public static final String REPORT_VIEW = "REPORT_VIEW";

    // Product catalogue import (CSV)
    public static final String PRODUCT_IMPORT = "PRODUCT_IMPORT";

    // Offline sync: register a device and push/pull data
    public static final String SYNC_DEVICE = "SYNC_DEVICE";

    // Manage registered devices and review offline-sync conflicts
    public static final String DEVICE_MANAGE = "DEVICE_MANAGE";

    // End-of-day reconciliation (cash-up)
    public static final String RECONCILIATION_VIEW = "RECONCILIATION_VIEW";
    public static final String RECONCILIATION_MANAGE = "RECONCILIATION_MANAGE";

    // Merchant profile and plan
    public static final String MERCHANT_MANAGE = "MERCHANT_MANAGE";

    // Support guides (WhatsApp onboarding content)
    public static final String SUPPORT_MANAGE = "SUPPORT_MANAGE";

    // WhatsApp messaging (send receipts / reports through the provider)
    public static final String WHATSAPP_SEND = "WHATSAPP_SEND";

    private PermissionConstants() {
    }
}
