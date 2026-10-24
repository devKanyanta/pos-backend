# MVP Backend: ZRA-compliant, offline-first POS

This document describes the backend features added for the MVP specification
(ZRA VSDC integration, offline sync, multi-tender payments, inventory with ZRA item
mapping, end-of-day reconciliation, WhatsApp support and the MICRO tier). All endpoints are
under `/api/v1` and, except `auth/*`, need `Authorization: Bearer <token>`.

Errors are JSON `{timestamp, status, error, message, path}` with proper status codes:
`400` validation/business rule, `401` bad credentials, `403` missing permission or plan
feature, `404` not found, `409` duplicate/conflict, `503` external integration not
configured or unreachable.

## 1. ZRA Smart Invoice (VSDC)

Sales never wait for ZRA. When a sale becomes fully paid the receipt is issued and a
`SALE_INVOICE` submission is queued (outbox). A background job transmits queued
submissions to the VSDC with exponential back-off (`pos.zra.*`), registering any
unregistered products first (`items/saveItem`). The VSDC's signature data is stored on the
receipt and printed in the ZRA block. Each refund queues a `CREDIT_NOTE` (`rcptTyCd=R`)
that references the original invoice and is sent only after the invoice is accepted.

Set-up: `ZRA_ENABLED=true`, `ZRA_VSDC_BASE_URL=<sandbox or local VSDC>`, then call
`POST /zra/device/initialize`.

| Method | Path | Permission | Purpose |
|---|---|---|---|
| POST | `/zra/device/initialize` | ZRA_MANAGE | `{tpin, bhfId, deviceSerialNo}` → calls `initializer/selectInitInfo`, stores SDC id / MRC no |
| GET | `/zra/device` | ZRA_VIEW | Active VSDC device |
| GET | `/zra/status` | ZRA_VIEW | Queue counts, unmapped/unregistered products |
| GET | `/zra/tax-types` | PRODUCT_VIEW | VAT categories A, B, C1, C2, C3, D, E, RVAT with rates |
| GET | `/zra/codes?lastReqDt=` | ZRA_MANAGE | Proxy to `code/selectCodes` (units, payment types…) |
| GET | `/zra/item-classes?lastReqDt=` | ZRA_MANAGE | Proxy to `itemClass/selectItemsClass` |
| POST | `/zra/items/{productId}/register` | ZRA_MANAGE | Register one product now |
| POST | `/zra/items/register-all` | ZRA_MANAGE | Queue all mapped, unregistered products |
| POST | `/zra/submissions/process` | ZRA_MANAGE | Transmit everything due now |
| GET | `/zra-submissions?status=` | ZRA_VIEW | Submissions (PENDING, RETRYING, SUCCESS, FAILED) |
| GET | `/zra-submissions/{id}` | ZRA_VIEW | Includes request/response payloads and last error |
| POST | `/zra-submissions/{id}/retry` | ZRA_MANAGE | Reset a failed submission and send it now |

Tax types: products carry `zraTaxType`, which fixes `vatRate` (A/B/RVAT 16%, C1–C3
zero-rated, D exempt, E disbursement). A request with only `vatRate` maps 16 → A and
0 → D; send C1/C2/C3 explicitly for zero-rated goods. With ZRA enabled, online sales of
products missing ZRA mapping are refused.

> The VSDC field names follow the ZRA VSDC API (`trnsSales/saveSales`, `items/saveItem`,
> `initializer/selectInitInfo`). The integration is verified against a local test double;
> run it against the ZRA sandbox before accreditation (see "Remaining work" in the README).

## 2. Offline-first sync

The Android client keeps its own SQLite queue. Every sale gets a unique `clientSaleId`
on the device; uploads are idempotent, so retries after a dropped connection never
double-count.

| Method | Path | Permission | Purpose |
|---|---|---|---|
| POST | `/sync/devices/register` | SYNC_DEVICE | `{deviceId, name, model, appVersion}` |
| POST | `/sync/heartbeat` | SYNC_DEVICE | `{deviceId, pendingQueueSize, online}` |
| POST | `/sync/sales` | SYNC_DEVICE + SALE_CREATE | Batch upload (≤ `SYNC_MAX_BATCH_SIZE`) |
| GET | `/sync/catalog?since=` | SYNC_DEVICE | Products/categories/tenders changed since `since` (+ all product ids) |
| GET | `/sync/status?deviceId=` | SYNC_DEVICE | Last sync, queue size, ZRA pending/failed, sales to review |
| GET | `/sync/devices` | DEVICE_MANAGE | Device registry |
| POST | `/sync/devices/{deviceId}/deactivate` / `activate` | DEVICE_MANAGE | Block/unblock a till |
| GET | `/sales/needs-review` | DEVICE_MANAGE | Synced sales with conflicts |
| POST | `/sales/{id}/review` | DEVICE_MANAGE | Mark reviewed |

Each sale in a batch is processed in its own transaction and gets a result:
`CREATED`, `DUPLICATE` (already recorded – drop it from the device queue) or `REJECTED`
(with `error`). Conflict rules (the sale already happened at the till, so it is recorded
and flagged rather than refused):

| Conflict code | Rule |
|---|---|
| `NEGATIVE_STOCK` | Sold more than the server's stock: accepted, stock may go negative |
| `PRICE_MISMATCH` | Device price differs from the current price: device price is used |
| `TOTAL_MISMATCH` | Device total differs from the server calculation: server totals are used |
| `UNKNOWN_PRODUCT` / `INACTIVE_PRODUCT` | Recorded from the device snapshot (name, price, tax type) |
| `DUPLICATE_PAYMENT_REFERENCE`, `MISSING_PAYMENT_REFERENCE`, `OVERPAYMENT`, `INACTIVE_PAYMENT_METHOD` | Payment recorded and flagged |
| `UNKNOWN_CUSTOMER`, `UNPAID`, `ZRA_UNMAPPED_PRODUCT` | Recorded and flagged |

Sales are reported by `soldAt` (device time), so offline sales land on the right business
day. Rejected: missing/duplicate `clientSaleId`, `soldAt` in the future (beyond
`SYNC_MAX_CLOCK_SKEW_MINUTES`) or older than `SYNC_MAX_OFFLINE_AGE_DAYS`.

JSON, CSV and text responses over 1 KB are gzip-compressed for low-bandwidth clients.

## 3. Multi-tender payments

Seeded tenders: Cash (CASH), MTN Mobile Money (MTN), Airtel Money (AIRTEL), Zamtel Kwacha
(ZAMTEL), Card (CARD). Payment methods have a `type` (CASH, MOBILE_MONEY, CARD,
BANK_TRANSFER, OTHER), `requiresReference` and `zraPaymentTypeCode`.

* `POST /sales` accepts `payments: [...]` so a checkout is one atomic call (split tenders
  allowed), plus an optional `clientSaleId` for idempotency.
* `POST /sales/{saleId}/payments` records one tender.
* Cash may exceed the balance: the excess is returned as `changeAmount`. Other tenders may
  not exceed the balance.
* Mobile money requires the confirmation code (`transactionReference`); a code can be used
  only once per network (409 otherwise). `payerPhone` is optional.

Sale arithmetic (VAT-exclusive prices): `lineTotal = price × qty − line discount`;
the sale discount is allocated pro rata to lines; VAT is charged on the discounted amount;
`total = subtotal − sale discount + VAT`.

Refunds: `POST /sales/{id}/refund` with `saleItemIds` (whole lines), `items:[{saleItemId,
quantity}]` (partial quantities), `reason`, `zraReasonCode` and optional
`refundPaymentMethodId` (how the money was paid out). Each refund creates a `CRN-…` credit
note, returns stock, updates the refunded amount on the original payments and queues a ZRA
credit note. Only paid sales can be refunded; unpaid sales are voided.

## 4. Inventory with ZRA item mapping

Product fields added: `zraTaxType`, `zraItemCode` (generated on registration if empty),
`zraItemClassCode`, `zraItemTypeCode` (1 raw, 2 finished, 3 service),
`zraOriginCountryCode`, `zraPackagingUnitCode`, `zraQuantityUnitCode`, `reorderLevel`;
responses also include `zraMapped`, `zraRegistered` and `lowStock`.

| Method | Path | Permission | Purpose |
|---|---|---|---|
| POST | `/products/import?dryRun=` | PRODUCT_IMPORT | CSV upload (multipart `file`, or raw `text/csv` body) |
| GET | `/products/import/template` | PRODUCT_IMPORT | Header row with the recognised columns |
| GET | `/products/low-stock` | PRODUCT_VIEW | At or below reorder level (default `POS_LOW_STOCK_THRESHOLD`) |
| POST | `/inventory-movements/restock` | INVENTORY_ADJUST | `{productId, quantity, unitCost, notes}` |
| GET | `/dashboard` | SALE_VIEW | Today's totals, low-stock flag + items, ZRA queue, review count, device queues |

CSV import matches rows to existing products by SKU, then barcode; other rows create
products (categories are created by name). Invalid rows are reported with their line
number and skipped. Column names are flexible (`qty`, `cost`, `tax type`, `unit`…).
Every stock change (opening stock, import, product edit, sale, void, refund, restock,
adjustment) writes an inventory movement. Products with history are deactivated instead of
deleted.

## 5. End-of-day reconciliation (Z-report)

| Method | Path | Permission | Purpose |
|---|---|---|---|
| GET | `/reports/z-report?date=` | RECONCILIATION_VIEW | Sales, VAT by category, refunds, per-tender received/refunded/expected/declared/variance, ZRA status |
| GET | `/reports/z-report/csv?date=` | RECONCILIATION_VIEW | CSV export |
| GET | `/reports/z-report/pdf?date=` | RECONCILIATION_VIEW | PDF export |
| GET | `/reports/z-report/whatsapp?date=&phone=` | RECONCILIATION_VIEW | WhatsApp text + `wa.me` share link |
| POST | `/reports/z-report/whatsapp/send?date=` | RECONCILIATION_VIEW + WHATSAPP_SEND | Send through the WhatsApp provider |
| POST | `/reconciliations` | RECONCILIATION_MANAGE | Cash-up `{businessDate, openingFloat, declared:[{paymentMethodId, amount}], notes}` |
| GET | `/reconciliations?from=&to=` | RECONCILIATION_VIEW | History |
| GET | `/reconciliations/{date}` | RECONCILIATION_VIEW | One day |
| GET | `/reports/today` | SALE_VIEW | Today's total (every plan) |

Expected per tender = money received that day − refunds paid out that day (+ opening float
for cash). Status is `BALANCED` when every tender is within
`RECONCILIATION_VARIANCE_TOLERANCE`, otherwise `SHORT`/`OVER` (or `MISMATCH` when the net
is zero but individual tenders differ). Resubmitting replaces the earlier count.

## 6. WhatsApp support and sharing

| Method | Path | Permission | Purpose |
|---|---|---|---|
| GET | `/support/whatsapp?topic=` | any user | Help button: `wa.me` link to support pre-filled with the merchant account id |
| GET | `/support/guides?topic=` | any user | Setup/troubleshooting guides |
| POST/PUT/DELETE | `/support/guides[/{id}]` | SUPPORT_MANAGE | Manage guides |
| POST | `/support/guides/{id}/share?send=` | WHATSAPP_SEND | Send a guide to a merchant |
| GET | `/support/whatsapp/messages` | SUPPORT_MANAGE | Provider delivery log |
| GET | `/receipts/{id}/print?width=` | RECEIPT_VIEW | Thermal-printer text receipt with the ZRA block |
| GET | `/receipts/{id}/pdf` | RECEIPT_VIEW | PDF receipt |
| POST | `/receipts/{id}/share/whatsapp?send=` | RECEIPT_VIEW (+ WHATSAPP_SEND to send) | Customer receipt via WhatsApp |

Click-to-chat links work without any account. To send from the server, set
`WHATSAPP_PROVIDER=TWILIO` (`TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`,
`TWILIO_WHATSAPP_FROM`) or `WHATSAPP_PROVIDER=META` (`WHATSAPP_CLOUD_PHONE_NUMBER_ID`,
`WHATSAPP_CLOUD_ACCESS_TOKEN`). Note that WhatsApp only delivers free-form text inside a
24-hour customer-service window; outside it, approved templates are required.

## 7. Merchant profile and plans

`GET /merchant` (any user) returns the account id, receipt header and plan features;
`PUT /merchant` (MERCHANT_MANAGE) updates business name, TPIN, phone, address, receipt
footer and `plan`.

* `SME` (default): everything.
* `MICRO` (free tier): offline sales with cash and mobile money, stock deduction, WhatsApp
  receipt sharing and `/reports/today`. ZRA, additional users, reports and reconciliation
  return 403, and sales are not queued for ZRA.

The first account registered on a fresh installation becomes `ADMIN`; later
self-registrations are `CASHIER`. Admins create users with roles via `POST /users`
(`roleIds`).

## Permissions added

`ZRA_MANAGE`, `PRODUCT_IMPORT`, `SYNC_DEVICE`, `DEVICE_MANAGE`, `RECONCILIATION_VIEW`,
`RECONCILIATION_MANAGE`, `MERCHANT_MANAGE`, `SUPPORT_MANAGE`, `WHATSAPP_SEND`.
ADMIN has all. MANAGER additionally gets `ZRA_VIEW`, `PRODUCT_IMPORT`, `SYNC_DEVICE`,
`DEVICE_MANAGE`, `RECONCILIATION_VIEW`, `RECONCILIATION_MANAGE` and `WHATSAPP_SEND`.
CASHIER additionally gets `SYNC_DEVICE` and `WHATSAPP_SEND`. `POST/GET /users` now require
`USER_CREATE`/`USER_VIEW`.

## Database changes

New tables (created by Hibernate): `merchant_profile`, `pos_devices`, `sale_refunds`,
`sale_refund_items`, `sale_refund_payments`, `zra_device`, `reconciliations`,
`reconciliation_lines`, `support_guides`, `whatsapp_messages`, `schema_migrations`.
New columns on `products`, `sales`, `sale_items`, `payments`, `payment_methods`,
`receipts`, `zra_submissions`.

`SchemaMigrationRunner` runs on start-up after Hibernate's schema update: it converts native
`ENUM` columns to `VARCHAR` (so new enum values can be stored without `ALTER TABLE`) and
back-fills new columns on existing rows (recorded in `schema_migrations`).
