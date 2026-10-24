# POS Backend – API Documentation

REST API for the POS backend (Spring Boot). All endpoints are JSON unless stated otherwise.

- [Conventions](#conventions)
- [Authentication & authorization](#authentication--authorization)
- [Error format](#error-format)
- [Endpoints](#endpoints)
  - [Health](#health) · [Auth](#auth) · [Users](#users) · [Branches](#branches) · [Merchant profile](#merchant-profile)
  - [Categories](#categories) · [Products](#products) · [Inventory movements](#inventory-movements)
  - [Customers](#customers) · [Payment methods](#payment-methods)
  - [Sales](#sales) · [Payments](#payments) · [Receipts](#receipts)
  - [Dashboard](#dashboard) · [Reports & Z-report](#reports--z-report) · [Reconciliations](#reconciliations)
  - [Offline sync](#offline-sync) · [ZRA](#zra-smart-invoice) · [ZRA submissions](#zra-submissions) · [Support](#support)
- [Enumerations](#enumerations)
- [Permissions & roles](#permissions--roles)
- [Plans (MICRO / SME)](#plans-micro--sme)

---

## Conventions

| Item | Value |
|---|---|
| Base URL | `http://localhost:8081` (`SERVER_PORT` overrides the port) |
| API prefix | `/api/v1` |
| Content type | `application/json` (requests and responses), except where noted (CSV, PDF, plain text, multipart) |
| Dates | `LocalDate` → `2026-10-24`; `LocalDateTime` → `2026-10-24T14:30:00` (no zone; business time zone is `pos.timezone`, default `Africa/Lusaka`) |
| Money | Decimal numbers (`BigDecimal`), e.g. `125.50` |
| IDs | `Long` for database ids; `clientSaleId` / `deviceId` are client-generated strings |
| Compression | Responses over 1 KB are gzipped (JSON, CSV, text, PDF) |
| CORS | All origins and methods currently allowed |
| Uploads | Max file 5 MB, max request 6 MB |

Success status codes: `200 OK` (reads/updates/actions), `201 Created` (creates), `204 No Content` (deletes).

---

## Authentication & authorization

The API is stateless and uses **JWT bearer tokens** (HS256).

1. Obtain a token from `POST /api/v1/auth/login` or `POST /api/v1/auth/register`.
2. Send it on every other request:

```
Authorization: Bearer <token>
```

- The token's subject is the username. It expires after `pos.jwt.expiration-minutes` (default 1440 = 24 h).
- Public endpoints (no token): `/api/v1/auth/**` and `GET /api/v1/health`. Everything else requires a valid token.
- Each protected endpoint also requires a **permission** (listed per endpoint below). Roles bundle permissions; see [Permissions & roles](#permissions--roles). A missing permission returns `403`.
- Some endpoints are only **signed-in** (no specific permission) – marked "any authenticated user".
- Some features are restricted by the merchant **plan**; see [Plans](#plans-micro--sme). A plan restriction returns `403`.

---

## Error format

Errors share one JSON shape:

```json
{
  "timestamp": "2026-10-24T14:30:00.123",
  "status": 400,
  "error": "Bad Request",
  "message": "Sale must have at least one item",
  "path": "/api/v1/sales"
}
```

| Status | When |
|---|---|
| 400 | Validation/business rule failure, malformed JSON, missing or invalid parameter, bad file part |
| 401 | Wrong username/password, or authentication failed |
| 403 | Missing permission, plan restriction, or no/invalid token on a protected route |
| 404 | Resource not found, or no endpoint matches the path |
| 405 | HTTP method not supported on the path |
| 409 | Duplicate value, record still in use, reused transaction reference, concurrent modification |
| 413 | Uploaded file too large |
| 415 | Unsupported media type |
| 500 | Unexpected server error |
| 503 | Required integration not configured/reachable (e.g. ZRA, WhatsApp), or health check failing |

---

## Endpoints

In the tables below, "Permission" is the authority required. Request/response field lists show JSON property names.

### Health

#### `GET /api/v1/health`
Public. Checks that the app is up **and** the database is reachable (connection validated with a 2 s timeout).

Response `200` when the database is up:
```json
{ "status": "UP", "database": "UP", "timestamp": "2026-10-24T12:00:00.000Z" }
```
Response `503` when the database is down: same shape with `"status": "DOWN"`, `"database": "DOWN"`.

---

### Auth

Public endpoints.

#### `POST /api/v1/auth/register`
Self-registration. The first account on a fresh installation becomes `ADMIN`; later accounts are `CASHIER`. Adding a second user requires the **SME** plan (`multiUser` feature).

Request:
| Field | Type | Notes |
|---|---|---|
| `firstName`, `lastName` | string | |
| `email` | string | **required**, unique |
| `phoneNumber` | string | |
| `username` | string | **required**, unique |
| `password` | string | **required**, min 6 characters |

Response `200` – `AuthResponse`:
```json
{ "token": "eyJhbGciOi...", "username": "mapalo", "roles": ["ADMIN"] }
```
Errors: `400` missing fields / short password, `409` email or username exists, `403` plan restriction (MICRO already has a user).

#### `POST /api/v1/auth/login`
Request: `{ "username": "...", "password": "..." }` (both required).
Response: `AuthResponse` (as above). Errors: `400` missing fields, `401` invalid credentials.

---

### Users

| Method & path | Permission | Description |
|---|---|---|
| `POST /api/v1/users` | `USER_CREATE` | Create user → `201` |
| `GET /api/v1/users` | `USER_VIEW` | List users |
| `GET /api/v1/users/{id}` | `USER_VIEW` | Get user |
| `PUT /api/v1/users/{id}` | `USER_UPDATE` | Update user |
| `DELETE /api/v1/users/{id}` | `USER_DELETE` | Delete user → `204` |

**CreateUserRequest / UpdateUserRequest:** `firstName`, `lastName`, `email`, `phoneNumber`, `username`, `password`, `roleIds` (array of role ids; on create defaults to CASHIER when omitted).

**UserResponse:** `id`, `firstName`, `lastName`, `email`, `phoneNumber`, `username`, `roles` (array of role names), `createdAt`.

---

### Branches

| Method & path | Permission | Description |
|---|---|---|
| `POST /api/v1/branches` | `BRANCH_CREATE` | Create → `201` |
| `GET /api/v1/branches` | `BRANCH_VIEW` | List |
| `GET /api/v1/branches/{id}` | `BRANCH_VIEW` | Get |
| `PUT /api/v1/branches/{id}` | `BRANCH_UPDATE` | Update |
| `DELETE /api/v1/branches/{id}` | `BRANCH_DELETE` | Delete → `204` |

**CreateBranchRequest:** `name`, `zraTpin`, `phone`, `email`, `address`. **UpdateBranchRequest:** same plus `isActive`.
**BranchResponse:** `id`, `name`, `zraTpin`, `phone`, `email`, `address`, `isActive`, `createdAt`.

---

### Merchant profile

| Method & path | Permission | Description |
|---|---|---|
| `GET /api/v1/merchant` | any authenticated user | Business profile, plan and feature flags |
| `PUT /api/v1/merchant` | `MERCHANT_MANAGE` | Update profile / plan |

**UpdateMerchantProfileRequest:** `businessName`, `tpin`, `phone`, `email`, `address`, `receiptFooter`, `plan` (`MICRO` or `SME`).

**MerchantProfileResponse:**
```json
{
  "accountId": "...", "businessName": "...", "tpin": "...", "phone": "...", "email": "...",
  "address": "...", "receiptFooter": "...", "plan": "SME",
  "features": { "zra": true, "multiUser": true, "reports": true, "reconciliation": true }
}
```

---

### Categories

| Method & path | Permission | Description |
|---|---|---|
| `POST /api/v1/categories` | `CATEGORY_CREATE` | Create → `201` |
| `GET /api/v1/categories` | `CATEGORY_VIEW` | List |
| `GET /api/v1/categories/{id}` | `CATEGORY_VIEW` | Get |
| `PUT /api/v1/categories/{id}` | `CATEGORY_UPDATE` | Update |
| `DELETE /api/v1/categories/{id}` | `CATEGORY_DELETE` | Delete → `204` |

**Request** (create and update): `name`, `description`. **CategoryResponse:** `id`, `name`, `description`.

---

### Products

| Method & path | Permission | Description |
|---|---|---|
| `POST /api/v1/products` | `PRODUCT_CREATE` | Create → `201` |
| `GET /api/v1/products` | `PRODUCT_VIEW` | List |
| `GET /api/v1/products/{id}` | `PRODUCT_VIEW` | Get |
| `PUT /api/v1/products/{id}` | `PRODUCT_UPDATE` | Update |
| `DELETE /api/v1/products/{id}` | `PRODUCT_DELETE` | Delete → `204` |
| `GET /api/v1/products/low-stock` | `PRODUCT_VIEW` | Products at or below their reorder level |
| `POST /api/v1/products/import` | `PRODUCT_IMPORT` | Bulk CSV import (see below) |
| `GET /api/v1/products/import/template` | `PRODUCT_IMPORT` | Download empty CSV template (`text/csv`) |

**CreateProductRequest** (UpdateProductRequest is the same plus `active`):

| Field | Type | Notes |
|---|---|---|
| `name`, `barcode`, `sku` | string | |
| `price` | decimal | VAT-exclusive selling price |
| `costPrice` | decimal | |
| `stockQuantity` | integer | |
| `vatRate` | decimal | e.g. `16.00` |
| `trackInventory` | boolean | |
| `categoryId` | long | |
| `reorderLevel` | integer | low-stock threshold |
| `zraTaxType` | string | `A`, `B`, `C1`, `C2`, `C3`, `D`, `E`, `RVAT` |
| `zraItemCode`, `zraItemClassCode` | string | |
| `zraItemTypeCode` | string | `1` raw material, `2` finished product, `3` service |
| `zraOriginCountryCode`, `zraPackagingUnitCode`, `zraQuantityUnitCode` | string | |

**ProductResponse:** `id`, `name`, `barcode`, `sku`, `price`, `costPrice`, `stockQuantity`, `active`, `vatRate`, `trackInventory`, `categoryId`, `categoryName`, `reorderLevel`, `lowStock`, the `zra*` fields above, `zraMapped` (all ZRA fields present), `zraRegistered`, `updatedAt`.

#### CSV import – `POST /api/v1/products/import`
Query: `dryRun` (boolean, default `false`) – validate without saving.
Two ways to send the CSV:
- `multipart/form-data` with the CSV in the `file` part;
- raw body with `Content-Type: text/csv` or `text/plain`.

Template header:
```
name,sku,barcode,price,costPrice,stockQuantity,taxType,category,reorderLevel,zraItemClassCode,zraItemTypeCode,zraPackagingUnitCode,zraQuantityUnitCode,zraOriginCountryCode
```

Response – `ProductImportResult`:
```json
{
  "dryRun": true, "totalRows": 2, "created": 0, "updated": 0, "failed": 1,
  "recognisedColumns": ["name", "sku", "price"], "ignoredColumns": ["foo"],
  "rows": [
    { "line": 2, "status": "WOULD_CREATE", "productId": null, "name": "Bread", "sku": "BR1", "barcode": null, "errors": [] },
    { "line": 3, "status": "FAILED", "name": "", "errors": ["name is required"] }
  ]
}
```
Row `status`: `CREATED`, `UPDATED`, `WOULD_CREATE`, `WOULD_UPDATE`, `FAILED`. `line` is 1-based with the header as line 1.

---

### Inventory movements

| Method & path | Permission | Description |
|---|---|---|
| `GET /api/v1/inventory-movements` | `INVENTORY_VIEW` | All stock movements |
| `GET /api/v1/inventory-movements/product/{productId}` | `INVENTORY_VIEW` | Movements for one product |
| `POST /api/v1/inventory-movements/adjustment` | `INVENTORY_ADJUST` | Manual stock correction → `201` |
| `POST /api/v1/inventory-movements/restock` | `INVENTORY_ADJUST` | Receive stock → `201` |

**AdjustmentRequest:** `productId`, `quantity` (signed, non-zero; must not make stock negative), `reason` (required). Product must track inventory.
**RestockRequest:** `productId`, `quantity` (positive), `unitCost` (optional, ≥ 0, updates the product cost price), `notes`.

**InventoryMovementResponse:** `id`, `productId`, `productName`, `movementType` (`SALE`, `RESTOCK`, `ADJUSTMENT`, `RETURN`, `TRANSFER`), `quantity`, `referenceId`, `referenceType`, `notes`, `performedBy`, `createdAt`.

---

### Customers

| Method & path | Permission | Description |
|---|---|---|
| `POST /api/v1/customers` | `CUSTOMER_CREATE` | Create → `201` |
| `GET /api/v1/customers` | `CUSTOMER_VIEW` | List |
| `GET /api/v1/customers/{id}` | `CUSTOMER_VIEW` | Get |
| `GET /api/v1/customers/search?phone=…` | `CUSTOMER_VIEW` | Find by phone number |
| `PUT /api/v1/customers/{id}` | `CUSTOMER_UPDATE` | Update |
| `DELETE /api/v1/customers/{id}` | `CUSTOMER_DELETE` | Delete → `204` |

**CreateCustomerRequest:** `firstName`, `lastName`, `phone`, `email`, `tpin`, `address`. **UpdateCustomerRequest:** same plus `isActive`.
**CustomerResponse:** `id`, `firstName`, `lastName`, `phone`, `email`, `tpin`, `address`, `isActive`, `createdAt`.

---

### Payment methods

| Method & path | Permission | Description |
|---|---|---|
| `POST /api/v1/payment-methods` | `PAYMENT_METHOD_CREATE` | Create → `201` |
| `GET /api/v1/payment-methods` | `PAYMENT_METHOD_VIEW` | List |
| `GET /api/v1/payment-methods/{id}` | `PAYMENT_METHOD_VIEW` | Get |
| `PUT /api/v1/payment-methods/{id}` | `PAYMENT_METHOD_UPDATE` | Update |
| `DELETE /api/v1/payment-methods/{id}` | `PAYMENT_METHOD_DELETE` | Delete → `204` |

**CreatePaymentMethodRequest:** `name`, `code`, `type` (`CASH`, `MOBILE_MONEY`, `CARD`, `BANK_TRANSFER`, `OTHER`), `requiresReference` (defaults to true for mobile money), `zraPaymentTypeCode` (ZRA `pmtTyCd`). **UpdatePaymentMethodRequest:** same plus `isActive`.
**PaymentMethodResponse:** `id`, `name`, `code`, `isActive`, `type`, `requiresReference`, `zraPaymentTypeCode`.

---

### Sales

| Method & path | Permission | Description |
|---|---|---|
| `POST /api/v1/sales` | `SALE_CREATE` | Create a sale (optionally with payments) → `201` |
| `GET /api/v1/sales` | `SALE_VIEW` | List sales |
| `GET /api/v1/sales/{id}` | `SALE_VIEW` | Get by id |
| `GET /api/v1/sales/number/{saleNumber}` | `SALE_VIEW` | Get by sale number |
| `GET /api/v1/sales/date-range?from=&to=` | `SALE_VIEW` | Sales between two dates (`yyyy-MM-dd`; `from` must not be after `to`) |
| `POST /api/v1/sales/{id}/void` | `SALE_VOID` | Void a sale (only if it has no payments) |
| `POST /api/v1/sales/{id}/refund` | `SALE_REFUND` | Full or partial refund |
| `GET /api/v1/sales/needs-review` | `DEVICE_MANAGE` | Offline-synced sales flagged with conflicts |
| `POST /api/v1/sales/{id}/review` | `DEVICE_MANAGE` | Mark a flagged sale as reviewed |

#### Create sale – `POST /api/v1/sales`
| Field | Type | Notes |
|---|---|---|
| `customerId` | long | optional |
| `notes` | string | |
| `discountAmount` | decimal | sale-level discount |
| `items[]` | array | **required, at least one** – `{ productId, quantity (>0), discountAmount }` |
| `clientSaleId` | string | optional idempotency key, 8–64 chars (`letters digits . _ : -`). Re-sending returns the existing sale |
| `deviceId` | string | optional capturing device |
| `payments[]` | array | optional tenders recorded atomically with the sale (split payments allowed) – see [Payments](#payments) for fields |

Fails with `400` for inactive products, insufficient stock for tracked items, etc.

Example:
```json
{
  "items": [{ "productId": 1, "quantity": 2 }],
  "payments": [{ "paymentMethodId": 1, "amount": 100.00 }],
  "clientSaleId": "5f8d1c2e-aaaa-bbbb-cccc-1234567890ab"
}
```

#### Refund – `POST /api/v1/sales/{id}/refund`
Body optional. Fields:
| Field | Notes |
|---|---|
| `saleItemIds` | items to refund in full (all remaining quantity) |
| `items[]` | partial refunds: `{ saleItemId, quantity }` |
| `reason` | free text |
| `zraReasonCode` | ZRA `rfdRsnCd`; defaults to `pos.zra.default-refund-reason-code` |
| `refundPaymentMethodId` | tender used to pay out; if omitted, refunds go against the sale's own payments, latest first |

#### SaleResponse
`id`, `saleNumber`, `customerId`, `customerName`, `customerPhone`, `subtotal`, `discountAmount`, `vatAmount`, `totalAmount`, `amountPaid`, `balanceDue`, `changeGiven`, `refundedAmount`, `paymentStatus`, `saleStatus`, `notes`, `createdBy`, `createdAt`, `soldAt`, `clientSaleId`, `deviceId`, `clientReceiptNumber`, `source` (`ONLINE` / `OFFLINE_SYNC`), `needsReview`, `syncConflicts[]`, `receiptId`, `receiptNumber`, `zraStatus`, `items[]`, `payments[]` (PaymentResponse), `refunds[]`.

**SaleItemResponse:** `id`, `productId`, `productName`, `quantity`, `unitPrice`, `grossAmount`, `lineTotal`, `saleDiscountShare`, `taxableAmount`, `vatRate`, `vatAmount`, `discountAmount`, `totalAmount`, `zraTaxType`, `itemStatus` (`ACTIVE`/`REFUNDED`), `refundedQuantity`, `refundedAmount`.

**RefundResponse:** `id`, `refundNumber`, `totalAmount`, `vatAmount`, `reason`, `zraReasonCode`, `createdBy`, `createdAt`, `zraStatus`, `zraReceiptNumber`, `items[]` (`saleItemId`, `productName`, `quantity`, `totalAmount`, `vatAmount`), `payouts[]` (`paymentId`, `paymentMethodId`, `paymentMethodName`, `amount`).

---

### Payments

| Method & path | Permission | Description |
|---|---|---|
| `POST /api/v1/sales/{saleId}/payments` | `SALE_CREATE` | Record a payment against a sale → `201` |
| `GET /api/v1/sales/{saleId}/payments` | `SALE_VIEW` | List payments for a sale |

**CreatePaymentRequest:**
| Field | Notes |
|---|---|
| `paymentMethodId` | **required**, method must be active |
| `amount` | **required**, positive. For cash it may exceed the amount due (the difference is returned as change); for other tenders it may not exceed the balance due |
| `transactionReference` | required for mobile money (3–64 chars: letters, digits, `. - _ / #`); a reference already used on the same method returns `409` |
| `payerPhone` | optional mobile-money payer number |

Cannot pay a voided or refunded sale, or one that is already fully paid.

**PaymentResponse:** `id`, `saleId`, `paymentMethodId`, `paymentMethodName`, `paymentMethodCode`, `paymentMethodType`, `amount` (applied), `amountTendered`, `changeAmount`, `refundedAmount`, `transactionReference`, `payerPhone`, `paymentStatus` (`SUCCESS`/`FAILED`/`REFUNDED`), `recordedBy`, `paidAt`, `createdAt`, `saleBalanceDue`, `salePaymentStatus`, `receiptId`, `receiptNumber`.

---

### Receipts

| Method & path | Permission | Description |
|---|---|---|
| `GET /api/v1/receipts/{id}` | `RECEIPT_VIEW` | Receipt by id |
| `GET /api/v1/receipts/sale/{saleId}` | `RECEIPT_VIEW` | Receipt for a sale |
| `POST /api/v1/receipts/{id}/reprint` | `RECEIPT_REPRINT` | Records a reprint (increments `printedCount`) → `200` |
| `GET /api/v1/receipts/{id}/print?width=42` | `RECEIPT_VIEW` | Plain text for thermal printers (`text/plain`); `width` 32–64 characters, default 42 |
| `GET /api/v1/receipts/{id}/pdf` | `RECEIPT_VIEW` | PDF (`application/pdf`, inline) |
| `POST /api/v1/receipts/{id}/share/whatsapp?send=false` | `RECEIPT_VIEW` (+ `WHATSAPP_SEND` when `send=true`) | WhatsApp share text and click-to-chat link |

**Share body:** `{ "phone": "0971234567" }` – local (`0971234567`) or international (`260971234567` / `+260971234567`).
**ShareResponse:** `text`, `shareUrl` (WhatsApp click-to-chat link), `sent` (provider result, only when `send=true`).

**ReceiptResponse:** `id`, `saleId`, `saleNumber`, `receiptNumber`, `printedCount`, `createdAt`, `totalAmount`, `vatAmount`, `zraStatus`, `zraVerified`, `zraInvoiceNo`, `zraReceiptNo`, `zraInternalData`, `zraReceiptSignature`, `zraSdcId`, `zraMrcNo`, `zraQrCodeUrl`, `zraReceiptDate`.

---

### Dashboard

#### `GET /api/v1/dashboard`
Permission: `SALE_VIEW`. Overview for the home screen.

**DashboardResponse:** `plan`, `today` (TodaySummary), `lowStockAlert`, `lowStockCount`, `lowStockItems[]` (up to 10 ProductResponse), `zraPending`, `zraFailed`, `salesNeedingReview`, `deviceQueuedTransactions` (sum of active devices' queued sales), `reconciliationStatusToday` (`BALANCED`, `SHORT`, `OVER` or `NOT_RECONCILED`).

---

### Reports & Z-report

Sales/inventory reports need the **SME** plan (`reports` feature); the Z-report endpoints need `reconciliation`.

| Method & path | Permission | Plan feature | Description |
|---|---|---|---|
| `GET /api/v1/reports/today` | `SALE_VIEW` | all plans | Today's total and money received per tender |
| `GET /api/v1/reports/sales/daily?date=` | `REPORT_VIEW` | reports | Daily sales report |
| `GET /api/v1/reports/sales/summary?from=&to=` | `REPORT_VIEW` | reports | Totals for a date range |
| `GET /api/v1/reports/products/top?from=&to=&limit=10` | `REPORT_VIEW` | reports | Top products by sales |
| `GET /api/v1/reports/inventory/value` | `REPORT_VIEW` | reports | Stock value and low-stock items |
| `GET /api/v1/reports/z-report?date=` | `RECONCILIATION_VIEW` | reconciliation | End-of-day Z-report (`date` optional, defaults to today) |
| `GET /api/v1/reports/z-report/csv?date=` | `RECONCILIATION_VIEW` | reconciliation | Z-report as CSV download |
| `GET /api/v1/reports/z-report/pdf?date=` | `RECONCILIATION_VIEW` | reconciliation | Z-report as PDF download |
| `GET /api/v1/reports/z-report/whatsapp?date=&phone=` | `RECONCILIATION_VIEW` | reconciliation | WhatsApp text + share link |
| `POST /api/v1/reports/z-report/whatsapp/send?date=` | `RECONCILIATION_VIEW` + `WHATSAPP_SEND` | reconciliation | Send summary via WhatsApp provider; body `{ "phone": "..." }` |

Date parameters use `yyyy-MM-dd`.

**TodaySummary:** `date`, `salesCount`, `totalSales`, `receivedByMethod` (map of tender name → amount).
**DailySalesReport:** `date`, `totalSales`, `totalRevenue`, `totalVat`, `paymentMethodBreakdown` (map), `topProducts[]`.
**SalesSummaryReport:** `from`, `to`, `totalSales`, `totalRevenue`, `totalVat`, `averageOrderValue`.
**ProductSummary:** `productName`, `totalQuantity`, `totalRevenue`.
**InventoryValueReport:** `totalProducts`, `totalStockValue`, `totalSellingValue`, `lowStockItems[]` (`productId`, `productName`, `stockQuantity`, `reorderLevel`).

**ZReportResponse:** `businessDate`, `generatedAt`, `businessName`, `tpin`, `accountId`, and sections:
- `sales` – `count`, `voidedCount`, `refundedCount`, `offlineCount`, `needsReviewCount`, `subtotal`, `discounts`, `vat`, `grossSales`, `outstanding`
- `vat[]` – `taxType`, `label`, `rate`, `taxableAmount`, `vatAmount`
- `refunds` – `count`, `total`, `vat`
- `tenders[]` – `paymentMethodId`, `name`, `code`, `type`, `transactionCount`, `received`, `refunded`, `expected`, `declared`, `variance`, `flagged`
- `totals` – `received`, `refunded`, `expected`, `declared`, `variance`
- `openingFloat`, `expectedCashInDrawer`, `reconciliationStatus`, `reconciledBy`, `reconciledAt`, `notes`
- `zra` – `signed`, `pending`, `failed`, `notReported`
- `topProducts[]`

WhatsApp text endpoint response: `{ "businessDate", "text", "shareUrl", "canSendDirectly" }`.

---

### Reconciliations

End-of-day cash-up (`reconciliation` feature, SME).

| Method & path | Permission | Description |
|---|---|---|
| `POST /api/v1/reconciliations` | `RECONCILIATION_MANAGE` | Submit (or resubmit) a cash-up → `201` |
| `GET /api/v1/reconciliations?from=&to=` | `RECONCILIATION_VIEW` | List, optionally filtered by date range |
| `GET /api/v1/reconciliations/{date}` | `RECONCILIATION_VIEW` | Get the cash-up for a business date (`yyyy-MM-dd`) |

**ReconciliationRequest:**
```json
{
  "businessDate": "2026-10-24",
  "openingFloat": 200.00,
  "declared": [ { "paymentMethodId": 1, "amount": 1850.00 }, { "paymentMethodId": 2, "amount": 640.00 } ],
  "notes": "Till 1"
}
```
`businessDate` defaults to today; tenders left out of `declared` are counted as 0.

**ReconciliationResponse:** `id`, `businessDate`, `openingFloat`, `expectedTotal`, `declaredTotal`, `varianceTotal`, `status` (`BALANCED`/`SHORT`/`OVER`), `notes`, `submittedBy`, `submittedAt`, `lines[]` (`paymentMethodId`, `paymentMethodName`, `transactionCount`, `expectedAmount`, `declaredAmount`, `variance`, `flagged`). A tender is flagged when its variance exceeds `pos.reconciliation.variance-tolerance` (default `0.00`).

---

### Offline sync

For POS devices that sell offline and upload later. Most endpoints need `SYNC_DEVICE`.

| Method & path | Permission | Description |
|---|---|---|
| `POST /api/v1/sync/devices/register` | `SYNC_DEVICE` | Register the calling device |
| `POST /api/v1/sync/heartbeat` | `SYNC_DEVICE` | Report connectivity and queue size |
| `POST /api/v1/sync/sales` | `SYNC_DEVICE` + `SALE_CREATE` | Upload a batch of offline sales (idempotent per `clientSaleId`) |
| `GET /api/v1/sync/catalog?since=` | `SYNC_DEVICE` | Catalogue snapshot or delta for the local DB |
| `GET /api/v1/sync/status?deviceId=` | `SYNC_DEVICE` | Sync status for a device |
| `GET /api/v1/sync/devices` | `DEVICE_MANAGE` | List registered devices |
| `POST /api/v1/sync/devices/{deviceId}/deactivate` | `DEVICE_MANAGE` | Deactivate a device |
| `POST /api/v1/sync/devices/{deviceId}/activate` | `DEVICE_MANAGE` | Re-activate a device |

**RegisterDeviceRequest:** `deviceId`, `name`, `model`, `appVersion`.
**HeartbeatRequest:** `deviceId`, `pendingQueueSize`, `online`, `appVersion`.
**DeviceResponse:** `id`, `deviceId`, `name`, `model`, `appVersion`, `active`, `registeredBy`, `lastUser`, `lastSeenAt`, `lastSyncAt`, `pendingQueueSize`, `reportedOnline`, `createdAt`.

#### Upload sales – `POST /api/v1/sync/sales`
Max batch size `pos.sync.max-batch-size` (default 100). Max offline age 60 days; clock-skew tolerance 10 minutes.

**SyncSalesRequest:** `deviceId`, `pendingQueueSize` (items still queued after this batch), `sales[]`.
**OfflineSaleRequest:**
| Field | Notes |
|---|---|
| `clientSaleId` | **required** – idempotency key |
| `clientReceiptNumber` | receipt number printed on the device |
| `soldAt` | **required**, device-local date/time |
| `customerId`, `notes`, `discountAmount` | |
| `expectedTotal` | device-calculated total, used to detect calculation drift |
| `items[]` | `productId`, `productName` (snapshot), `quantity`, `unitPrice` (VAT-exclusive), `vatRate`, `zraTaxType`, `discountAmount` |
| `payments[]` | same as [CreatePaymentRequest](#payments) |

**SyncSalesResponse:** `serverTime`, `created`, `duplicates`, `rejected`, `results[]`.
**SyncSaleResult:** `clientSaleId`, `status`, `saleId`, `saleNumber`, `receiptNumber`, `totalAmount`, `paymentStatus`, `conflicts[]`, `error`.
- `CREATED` – recorded now.
- `DUPLICATE` – already recorded earlier; safe to drop from the queue.
- `REJECTED` – invalid, see `error`; keep for user attention, do not resend unchanged.

Sales with conflicts (negative stock, price mismatch, …) are saved with `needsReview = true` – see [Sales](#sales).

#### Catalogue – `GET /api/v1/sync/catalog`
Query `since` (ISO date-time): pass the previous `serverTime`. Omit it, or send one that is too old, to get a full snapshot.
**CatalogResponse:** `serverTime`, `full`, `products[]` (`id`, `name`, `sku`, `barcode`, `price`, `vatRate`, `zraTaxType`, `trackInventory`, `stockQuantity`, `active`, `categoryId`, `updatedAt`), `categories[]` (`id`, `name`), `paymentMethods[]`, `productIds[]` (every product id on the server – delete local products not in this list).

**SyncStatusResponse:** `serverTime`, `deviceId`, `deviceRegistered`, `deviceActive`, `lastSyncAt`, `pendingQueueSize`, `zraPending`, `zraFailed`, `salesNeedingReview`.

---

### ZRA Smart Invoice

Integration with the Zambia Revenue Authority VSDC (SME plan, `zra` feature). Invoices are queued and submitted by a background job (`pos.zra.processor-interval-ms`, default 30 s) with exponential-backoff retries.

| Method & path | Permission | Description |
|---|---|---|
| `GET /api/v1/zra/tax-types` | `PRODUCT_VIEW` | VAT categories for the product form (all plans) |
| `GET /api/v1/zra/status` | `ZRA_VIEW` | Integration status and counters |
| `GET /api/v1/zra/device` | `ZRA_VIEW` | Initialized VSDC device |
| `POST /api/v1/zra/device/initialize` | `ZRA_MANAGE` | Initialize the VSDC device |
| `GET /api/v1/zra/codes?lastReqDt=` | `ZRA_MANAGE` | VSDC code lists (raw JSON) |
| `GET /api/v1/zra/item-classes?lastReqDt=` | `ZRA_MANAGE` | VSDC item classification codes (raw JSON) |
| `POST /api/v1/zra/items/{productId}/register` | `ZRA_MANAGE` | Register one product with ZRA |
| `POST /api/v1/zra/items/register-all` | `ZRA_MANAGE` | Queue all unregistered products → `{ "queued": n }` |
| `POST /api/v1/zra/submissions/process` | `ZRA_MANAGE` | Transmit everything due now → `{ "processed": n }` |

`lastReqDt` format: `yyyyMMddHHmmss` (defaults to everything).

**InitializeDeviceRequest:** `tpin` (10 digits), `bhfId` (branch id from ZRA, `000` for head office), `deviceSerialNo`.
**ZraDeviceResponse:** `id`, `tpin`, `bhfId`, `deviceSerialNo`, `sdcId`, `mrcNo`, `taxpayerName`, `branchName`, `lastInvoiceNo`, `initializedAt`, `integrationEnabled`, `vsdcBaseUrl`.
**ZraStatusResponse:** `enabled`, `planAllowsZra`, `deviceInitialized`, `pending`, `retrying`, `failed`, `succeeded`, `unmappedProducts`, `unregisteredProducts`.
**ZraTaxTypeResponse:** `code`, `label`, `rate`, `zeroRated`, `exempt`.

---

### ZRA submissions

| Method & path | Permission | Description |
|---|---|---|
| `GET /api/v1/zra-submissions?status=` | `ZRA_VIEW` | List submissions, optionally filtered by status (`PENDING`, `RETRYING`, `SUCCESS`, `FAILED`) |
| `GET /api/v1/zra-submissions/{id}` | `ZRA_VIEW` | One submission, including request/response payloads |
| `POST /api/v1/zra-submissions/{id}/retry` | `ZRA_MANAGE` | Reset a failed submission and transmit immediately |

**ZraSubmissionResponse:** `id`, `saleId`, `saleNumber`, `receiptId`, `refundId`, `refundNumber`, `productId`, `invoiceNo`, `submissionStatus`, `submissionType` (`SALE_INVOICE`, `CREDIT_NOTE`, `ITEM_REGISTRATION`), `retryCount`, `errorCode`, `errorMessage`, `lastAttemptAt`, `nextAttemptAt`, `completedAt`, `createdAt`, and – on the single-submission endpoint only – `requestPayload`, `responsePayload`.

---

### Support

WhatsApp help and onboarding guides.

| Method & path | Permission | Description |
|---|---|---|
| `GET /api/v1/support/whatsapp?topic=` | any authenticated user | WhatsApp chat link to support, pre-filled with the account id |
| `GET /api/v1/support/guides?topic=` | any authenticated user | Active guides, optionally by topic |
| `GET /api/v1/support/guides/{id}` | any authenticated user | One guide |
| `GET /api/v1/support/guides/all` | `SUPPORT_MANAGE` | All guides including inactive |
| `POST /api/v1/support/guides` | `SUPPORT_MANAGE` | Create guide → `201` |
| `PUT /api/v1/support/guides/{id}` | `SUPPORT_MANAGE` | Update guide |
| `DELETE /api/v1/support/guides/{id}` | `SUPPORT_MANAGE` | Delete guide → `204` |
| `POST /api/v1/support/guides/{id}/share?send=false` | `WHATSAPP_SEND` | Share a guide; body `{ "phone": "..." }`; `send=true` also sends via the provider |
| `GET /api/v1/support/whatsapp/messages` | `SUPPORT_MANAGE` | Recent messages sent through the provider (delivery log) |

**SupportGuideRequest:** `title`, `topic`, `description`, `mediaUrl`, `sortOrder`, `active`.
**SupportGuideResponse:** the same fields plus `id`, `requestViaWhatsAppUrl` (null if no support number is configured), `updatedAt`.
**WhatsAppHelpResponse:** `supportNumber`, `accountId`, `username`, `message`, `url` (`https://wa.me/...`).
**ShareResponse:** `text`, `shareUrl`, `sent`.

WhatsApp sending depends on `pos.whatsapp.provider` (`NONE` = click-to-chat links only, `TWILIO`, or `META`). Without a configured provider, `send=true` fails with `503`.

---

## Enumerations

| Enum | Values |
|---|---|
| Sale `saleStatus` | `ACTIVE`, `VOIDED`, `REFUNDED` |
| Sale `paymentStatus` | `PENDING`, `PAID`, `PARTIALLY_PAID`, `REFUNDED` |
| Sale `source` | `ONLINE`, `OFFLINE_SYNC` |
| Sale item `itemStatus` | `ACTIVE`, `REFUNDED` |
| Payment `paymentStatus` | `SUCCESS`, `FAILED`, `REFUNDED` |
| Payment method `type` | `CASH`, `MOBILE_MONEY`, `CARD`, `BANK_TRANSFER`, `OTHER` |
| Inventory `movementType` | `SALE`, `RESTOCK`, `ADJUSTMENT`, `RETURN`, `TRANSFER` |
| ZRA `submissionStatus` | `PENDING`, `RETRYING`, `SUCCESS`, `FAILED` |
| ZRA `submissionType` | `SALE_INVOICE`, `CREDIT_NOTE`, `ITEM_REGISTRATION` |
| Merchant `plan` | `MICRO`, `SME` |
| Reconciliation `status` | `BALANCED`, `SHORT`, `OVER` (`NOT_RECONCILED` in reports/dashboard when none submitted) |
| Import row `status` | `CREATED`, `UPDATED`, `WOULD_CREATE`, `WOULD_UPDATE`, `FAILED` |

**ZRA VAT tax types**

| Code | Label | Rate |
|---|---|---|
| `A` | Standard rated | 16% |
| `B` | Minimum taxable value (MTV) | 16% |
| `C1` | Exports (zero-rated) | 0% |
| `C2` | Zero-rated local purchase order | 0% |
| `C3` | Zero-rated by nature | 0% |
| `D` | Exempt | 0% |
| `E` | Disbursement | 0% |
| `RVAT` | Reverse VAT | 16% |

---

## Permissions & roles

Roles and permissions are seeded at startup. Default roles:

| Role | Permissions |
|---|---|
| **ADMIN** | All permissions |
| **MANAGER** | Product view/create/update/import · category view/create/update · user view · branch view/create/update · customer view/create/update · inventory view/adjust · sale view/create/void/refund · receipt view/reprint · report view · payment method view · ZRA view · sync device · device manage · reconciliation view/manage · WhatsApp send |
| **CASHIER** | Product view · category view · customer view/create/update · sale view/create · receipt view/reprint · inventory view · payment method view · sync device · WhatsApp send |

All permission names: `PRODUCT_VIEW|CREATE|UPDATE|DELETE|IMPORT`, `CATEGORY_VIEW|CREATE|UPDATE|DELETE`, `USER_VIEW|CREATE|UPDATE|DELETE`, `BRANCH_VIEW|CREATE|UPDATE|DELETE`, `CUSTOMER_VIEW|CREATE|UPDATE|DELETE`, `INVENTORY_VIEW|ADJUST`, `SALE_VIEW|CREATE|VOID|REFUND`, `PAYMENT_METHOD_VIEW|CREATE|UPDATE|DELETE`, `RECEIPT_VIEW|REPRINT`, `ZRA_VIEW|MANAGE`, `REPORT_VIEW`, `SYNC_DEVICE`, `DEVICE_MANAGE`, `RECONCILIATION_VIEW|MANAGE`, `MERCHANT_MANAGE`, `SUPPORT_MANAGE`, `WHATSAPP_SEND`.

---

## Plans (MICRO / SME)

The merchant plan (`GET /api/v1/merchant`) gates features. A restricted call returns `403` with a message like *"… is not available on the MICRO plan. Upgrade to SME."*

| Feature flag | MICRO | SME | Affects |
|---|---|---|---|
| `zra` | no | yes | ZRA Smart Invoice submission |
| `multiUser` | no (one user only) | yes | Registering additional users |
| `reports` | no | yes | `/reports/sales/*`, `/reports/products/top`, `/reports/inventory/value` |
| `reconciliation` | no | yes | Reconciliations and Z-report endpoints |

Available on every plan: sales, payments, receipts, products, categories, customers, offline sync, `GET /reports/today`, dashboard.

---

## Configuration reference

Selected properties (see `application.properties`; each can be overridden by the environment variable shown in the file):

| Property | Default | Purpose |
|---|---|---|
| `server.port` | `8081` | HTTP port |
| `pos.timezone` | `Africa/Lusaka` | Business time zone |
| `pos.low-stock-threshold` | `5` | Default low-stock threshold |
| `pos.jwt.secret` | dev value | JWT signing secret (≥ 32 bytes) – **override in every non-local environment** |
| `pos.jwt.expiration-minutes` | `1440` | Token lifetime |
| `pos.zra.enabled` | `false` | Enable ZRA VSDC integration |
| `pos.sync.max-batch-size` | `100` | Offline sales per upload |
| `pos.sync.max-offline-age-days` | `60` | Oldest accepted offline sale |
| `pos.sync.max-clock-skew-minutes` | `10` | Tolerated device clock skew |
| `pos.reconciliation.variance-tolerance` | `0.00` | Variance per tender before it is flagged |
| `pos.whatsapp.provider` | `NONE` | `NONE`, `TWILIO` or `META` |
