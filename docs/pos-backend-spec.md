# POS Backend — Specification Document

> Generated: July 13, 2026
> Based on codebase analysis and stakeholder interviews

---

## 1. Table of Contents

1. [Project Overview](#2-project-overview)
2. [Technology Stack](#3-technology-stack)
3. [Existing Implementation Summary](#4-existing-implementation-summary)
4. [New Modules to Build](#5-new-modules-to-build)
5. [Detailed Module Specifications](#6-detailed-module-specifications)
6. [Permissions & Roles Matrix](#7-permissions--roles-matrix)
7. [API Endpoints Summary](#8-api-endpoints-summary)
8. [Data Model Changes](#9-data-model-changes)
9. [Business Logic Rules](#10-business-logic-rules)
10. [Reports Specification](#11-reports-specification)
11. [Out of Scope](#12-out-of-scope)

---

## 2. Project Overview

A backend REST API for a Point of Sale (POS) system serving Zambian retail businesses. Built with Spring Boot and MariaDB, the system handles user authentication & authorization, product/category management, sales transactions, payment processing, inventory tracking, customer management, receipt generation, and ZRA (Zambia Revenue Authority) Smart Invoice compliance.

---

## 3. Technology Stack

| Technology | Version | Purpose |
|---|---|---|
| Java | 25 | Language runtime |
| Spring Boot | 4.0.6 | Application framework |
| Spring Data JPA | — | ORM / database access |
| Spring Security | — | Authentication & authorization |
| Spring Validation | — | Request validation |
| MariaDB | 12 | Database |
| JWT (jjwt) | 0.11.5 | Token-based authentication |
| Lombok | Latest | Boilerplate reduction |
| Gradle | 9.4.1 | Build tool |

---

## 4. Existing Implementation Summary

### Already built and operational:

| Module | Status | Key Entities |
|---|---|---|
| **Auth** | ✅ Complete | Login, Register with JWT tokens |
| **Users** | ✅ Partial | Create, Get All, Get By ID. **Missing:** Update, Delete |
| **Roles & Permissions** | ✅ Complete | Seeder creates ADMIN, MANAGER, CASHIER with permissions. **Missing:** Permission constants for new modules |
| **Categories** | ✅ Complete | Full CRUD with unique name and delete-protection if products exist |
| **Products** | ✅ Complete | Full CRUD with barcode/SKU uniqueness, category association, basic validation |
| **Global Exception Handler** | ✅ Complete | Handles auth errors, access denied, runtime exceptions with structured JSON responses |
| **Base Entity** | ✅ Complete | createdAt/updatedAt audit fields |

### Product entity currently has:
- `stockQuantity` (single-stock model — no per-branch inventory yet)
- **Missing:** `vatRate`, `trackInventory` fields

### Existing permissions (to be extended):
- `PRODUCT_VIEW`, `PRODUCT_CREATE`, `PRODUCT_UPDATE`, `PRODUCT_DELETE`
- `CATEGORY_VIEW`, `CATEGORY_CREATE`, `CATEGORY_UPDATE`, `CATEGORY_DELETE`

---

## 5. New Modules to Build

All modules should be built in this implementation sweep:

| # | Module | Priority | Description |
|---|---|---|---|
| 1 | **Branches** | High | Physical store locations (CRUD) |
| 2 | **Inventory Movements** | High | Full audit trail for stock changes |
| 3 | **Customers** | High | People who buy from the store (CRUD) |
| 4 | **Payment Methods** | High | Pre-seeded payment types with admin CRUD |
| 5 | **Sales & Sale Items** | Critical | Core transaction engine |
| 6 | **Payments** | Critical | Money received (supports split payments) |
| 7 | **Receipts** | High | Proof of purchase records |
| 8 | **ZRA Submissions** | Medium | ZRA Smart Invoice data model (API integration deferred) |
| 9 | **Basic Reports** | Medium | Daily sales, top products, inventory value |

---

## 6. Detailed Module Specifications

### 6.1 Branches

**Purpose:** Represent physical business locations for multi-shop operations.

**Entity: `BranchEntity`**
```
id: Long (PK, auto-increment)
name: String (required, unique)
zraTpin: String (Zambia tax identifier, required)
phone: String (optional)
email: String (optional)
address: String (optional)
isActive: Boolean (default: true, soft-delete support)
createdAt: LocalDateTime (audit)
updatedAt: LocalDateTime (audit)
```

**API Endpoints:**
| Method | Path | Auth | Permission |
|---|---|---|---|
| POST | `/api/v1/branches` | JWT | BRANCH_CREATE |
| GET | `/api/v1/branches` | JWT | BRANCH_VIEW |
| GET | `/api/v1/branches/{id}` | JWT | BRANCH_VIEW |
| PUT | `/api/v1/branches/{id}` | JWT | BRANCH_UPDATE |
| DELETE | `/api/v1/branches/{id}` | JWT | BRANCH_DELETE |

**Validation Rules:**
- Name is required and must be unique
- ZRA TPIN is required (Zambia tax ID format: 10-digit numeric)
- Cannot delete a branch that has active inventory or sales

---

### 6.2 Inventory Movements

**Purpose:** Full history of stock changes for audit trail.

**Entity: `InventoryMovementEntity`**
```
id: Long (PK, auto-increment)
product: ProductEntity (ManyToOne, required)
movementType: Enum (SALE, RESTOCK, ADJUSTMENT, RETURN, TRANSFER)
quantity: Integer (positive for increase, negative for decrease)
referenceId: Long (ID of source record — sale_id, etc.)
referenceType: String (type of source — "SALE", "RESTOCK", etc.)
notes: String (optional)
performedBy: String (username who performed the action)
createdAt: LocalDateTime (audit, immutable)
```

**Business Rules:**
- Every stock change MUST create an inventory movement (immutable — never deleted)
- Movement types:
  - `SALE`: quantity = -n (stock decrease from sale)
  - `RESTOCK`: quantity = +n (stock increase from intake)
  - `ADJUSTMENT`: ±n (manual corrections)
  - `RETURN`: quantity = +n (customer return)
  - `TRANSFER`: branch-to-branch movement (for future use)
- `product.stockQuantity` is the current snapshot; `InventoryMovement` is the transaction log
- When a sale is completed, create movements automatically
- When a sale is refunded, create reverse movements

**API Endpoints:**
| Method | Path | Auth | Permission |
|---|---|---|---|
| GET | `/api/v1/inventory-movements` | JWT | INVENTORY_VIEW |
| GET | `/api/v1/inventory-movements/product/{productId}` | JWT | INVENTORY_VIEW |
| POST | `/api/v1/inventory-movements/adjustment` | JWT | INVENTORY_ADJUST |

---

### 6.3 Customers

**Purpose:** People who buy from the store.

**Entity: `CustomerEntity`**
```
id: Long (PK, auto-increment)
firstName: String (optional)
lastName: String (optional)
phone: String (optional, unique if provided)
email: String (optional, unique if provided)
tpin: String (optional — ZRA tax ID for receipt)
address: String (optional)
isActive: Boolean (default: true)
createdAt: LocalDateTime (audit)
updatedAt: LocalDateTime (audit)
```

**API Endpoints:**
| Method | Path | Auth | Permission |
|---|---|---|---|
| POST | `/api/v1/customers` | JWT | CUSTOMER_CREATE |
| GET | `/api/v1/customers` | JWT | CUSTOMER_VIEW |
| GET | `/api/v1/customers/{id}` | JWT | CUSTOMER_VIEW |
| PUT | `/api/v1/customers/{id}` | JWT | CUSTOMER_UPDATE |
| DELETE | `/api/v1/customers/{id}` | JWT | CUSTOMER_DELETE |
| GET | `/api/v1/customers/search?phone=X` | JWT | CUSTOMER_VIEW |

**Validation Rules:**
- At least one of phone or email must be present
- Phone (if provided) must be unique
- Email (if provided) must be unique
- Soft-delete via isActive; hard delete only if customer has no sales history

---

### 6.4 Payment Methods

**Purpose:** Define allowed payment types.

**Entity: `PaymentMethodEntity`**
```
id: Long (PK, auto-increment)
name: String (required, unique — e.g., "CASH", "MTN Mobile Money", "Airtel Money", "CARD")
code: String (required, unique — e.g., "CASH", "MTN", "AIRTEL", "CARD")
isActive: Boolean (default: true)
```

**Pre-seeded Data:**
| Name | Code |
|---|---|
| Cash | CASH |
| MTN Mobile Money | MTN |
| Airtel Money | AIRTEL |
| Card | CARD |

**API Endpoints:**
| Method | Path | Auth | Permission |
|---|---|---|---|
| POST | `/api/v1/payment-methods` | JWT | PAYMENT_METHOD_CREATE |
| GET | `/api/v1/payment-methods` | JWT | PAYMENT_METHOD_VIEW |
| GET | `/api/v1/payment-methods/{id}` | JWT | PAYMENT_METHOD_VIEW |
| PUT | `/api/v1/payment-methods/{id}` | JWT | PAYMENT_METHOD_UPDATE |
| DELETE | `/api/v1/payment-methods/{id}` | JWT | PAYMENT_METHOD_DELETE |

---

### 6.5 Sales & Sale Items

**Purpose:** Core transaction engine — one sale = one checkout session.

**Entity: `SaleEntity`**
```
id: Long (PK, auto-increment)
saleNumber: String (unique, formatted: "SALE-YYYYMMDD-XXXX" — auto-generated)
customer: CustomerEntity (ManyToOne, optional — walk-in customers allowed)
subtotal: BigDecimal (sum of line items before tax/discount)
discountAmount: BigDecimal (sale-level discount, default 0)
vatAmount: BigDecimal (total VAT, auto-calculated)
totalAmount: BigDecimal (subtotal - discount + vat)
paymentStatus: Enum (PENDING, PAID, PARTIALLY_PAID, REFUNDED)
saleStatus: Enum (ACTIVE, VOIDED, REFUNDED)
notes: String (optional)
createdBy: String (username of cashier who processed the sale)
createdAt: LocalDateTime (audit)
updatedAt: LocalDateTime (audit)
```

**Entity: `SaleItemEntity`**
```
id: Long (PK, auto-increment)
sale: SaleEntity (ManyToOne, required)
product: ProductEntity (ManyToOne, nullable — in case product is deleted later)
productName: String (snapshot at time of sale — denormalized)
unitPrice: BigDecimal (snapshot at time of sale)
quantity: Integer (positive)
lineTotal: BigDecimal (unitPrice * quantity)
vatRate: BigDecimal (snapshot at time of sale — e.g., 0.00 or 16.00)
vatAmount: BigDecimal (lineTotal * vatRate / 100)
discountAmount: BigDecimal (line-item discount, default 0)
itemStatus: Enum (ACTIVE, REFUNDED)
refundedQuantity: Integer (default 0, tracks partial refunds)
```

**Sale Number Generation:**
- Format: `SALE-YYYYMMDD-XXXX`
- Where XXXX is a zero-padded daily sequential number (resets daily)
- Example: `SALE-20260713-0001`
- Implementation: Use a counter table or query for the last sale number of the day and increment

**Sale Business Rules:**
1. Sales must NEVER be deleted — only voided or refunded
2. Creating a sale transactionally:
   - Calculate each line: `lineTotal = unitPrice * quantity`
   - Calculate line VAT: `vatAmount = lineTotal * (product.vatRate / 100)`
   - Calculate subtotal: sum of all lineTotals
   - Apply sale-level discount: subtract from subtotal
   - Calculate total VAT: sum of all line vatAmounts
   - Calculate total: `subtotal - discountAmount + vatAmount`
   - Decrease product stock: `product.stockQuantity -= quantity`
   - Create inventory movement for each item (type=SALE, quantity=-qty)
3. Refunding (partial):
   - Update SaleItem: `itemStatus = REFUNDED`, set `refundedQuantity`
   - If ALL items are refunded, mark sale `saleStatus = REFUNDED`
   - Increase product stock: `product.stockQuantity += refundedQuantity`
   - Create inventory movement (type=RETURN, quantity=+refundedQty)
4. Voiding:
   - Set `saleStatus = VOIDED`
   - Reverse all stock changes with inventory movements (type=ADJUSTMENT)
   - Only possible if no payments have been processed yet

**API Endpoints:**
| Method | Path | Auth | Permission |
|---|---|---|---|
| POST | `/api/v1/sales` | JWT | SALE_CREATE |
| GET | `/api/v1/sales` | JWT | SALE_VIEW |
| GET | `/api/v1/sales/{id}` | JWT | SALE_VIEW |
| GET | `/api/v1/sales/number/{saleNumber}` | JWT | SALE_VIEW |
| POST | `/api/v1/sales/{id}/void` | JWT | SALE_VOID |
| POST | `/api/v1/sales/{id}/refund` | JWT | SALE_REFUND |
| GET | `/api/v1/sales/date-range?from=X&to=Y` | JWT | SALE_VIEW |

**Request/Response DTOs:**

**CreateSaleRequest:**
```json
{
  "customerId": 1,
  "notes": "Walk-in customer",
  "discountAmount": 5.00,
  "items": [
    {
      "productId": 1,
      "quantity": 2,
      "discountAmount": 0
    },
    {
      "productId": 3,
      "quantity": 1,
      "discountAmount": 2.50
    }
  ]
}
```

**SaleResponse:**
```json
{
  "id": 1,
  "saleNumber": "SALE-20260713-0001",
  "customer": { "id": 1, "name": "John Doe", "phone": "0977123456" },
  "subtotal": 100.00,
  "discountAmount": 5.00,
  "vatAmount": 15.20,
  "totalAmount": 110.20,
  "paymentStatus": "PENDING",
  "saleStatus": "ACTIVE",
  "items": [
    {
      "productId": 1,
      "productName": "Coke 500ml",
      "quantity": 2,
      "unitPrice": 15.00,
      "lineTotal": 30.00,
      "vatRate": 16.00,
      "vatAmount": 4.80,
      "discountAmount": 0,
      "itemStatus": "ACTIVE"
    }
  ],
  "createdBy": "cashier1",
  "createdAt": "2026-07-13T10:30:00"
}
```

**Void/Refund Request:**
```json
{
  "saleItemIds": [1, 3],
  "reason": "Customer returned damaged items"
}
```

---

### 6.6 Payments

**Purpose:** Track money received per sale. Supports split payments.

**Entity: `PaymentEntity`**
```
id: Long (PK, auto-increment)
sale: SaleEntity (ManyToOne, required)
paymentMethod: PaymentMethodEntity (ManyToOne, required)
amount: BigDecimal (amount paid with this method)
transactionReference: String (optional — mobile money ref, card ref)
paymentStatus: Enum (SUCCESS, FAILED, REFUNDED)
refundedAmount: BigDecimal (default 0)
createdAt: LocalDateTime (audit)
updatedAt: LocalDateTime (audit)
```

**Business Rules:**
- A sale can have multiple payments summing to the total (split payments)
- Payment status is set based on external service response if applicable
- When refunding a sale, payments can be marked as `REFUNDED`
- After all payments for a sale are recorded: `sale.paymentStatus = PAID` or `PARTIALLY_PAID`

**API Endpoints:**
| Method | Path | Auth | Permission |
|---|---|---|---|
| POST | `/api/v1/sales/{saleId}/payments` | JWT | SALE_CREATE |
| GET | `/api/v1/sales/{saleId}/payments` | JWT | SALE_VIEW |

---

### 6.7 Receipts

**Purpose:** Keep records of issued receipts (for reprint tracking and ZRA compliance).

**Entity: `ReceiptEntity`**
```
id: Long (PK, auto-increment)
sale: SaleEntity (ManyToOne, required)
receiptNumber: String (unique, formatted: "RCT-YYYYMMDD-XXXX")
printedCount: Integer (default 1, increments on reprint)
createdAt: LocalDateTime (audit)
```

**Receipt Number Generation:**
- Same format as sale number: `RCT-YYYYMMDD-XXXX`
- Separate daily counter from sales

**Business Rules:**
- A receipt is auto-created when payments for a sale are completed
- Reprinting increments `printedCount`
- ZRA submission reference can be linked here (future)

**API Endpoints:**
| Method | Path | Auth | Permission |
|---|---|---|---|
| GET | `/api/v1/receipts/{id}` | JWT | RECEIPT_VIEW |
| GET | `/api/v1/receipts/sale/{saleId}` | JWT | RECEIPT_VIEW |
| POST | `/api/v1/receipts/{id}/reprint` | JWT | RECEIPT_REPRINT |

---

### 6.8 ZRA Submissions

**Purpose:** Data model for ZRA Smart Invoice integration (actual API integration deferred).

**Entity: `ZraSubmissionEntity`**
```
id: Long (PK, auto-increment)
sale: SaleEntity (ManyToOne, optional — null if submission is not sale-related)
receipt: ReceiptEntity (ManyToOne, optional)
requestPayload: TEXT (what was/would be sent to ZRA)
responsePayload: TEXT (what ZRA returned, if integrated)
submissionStatus: Enum (PENDING, SUCCESS, FAILED, RETRYING)
submissionType: Enum (SALE_INVOICE, CREDIT_NOTE, etc.)
retryCount: Integer (default 0)
lastAttemptAt: LocalDateTime
createdAt: LocalDateTime (audit)
updatedAt: LocalDateTime (audit)
```

**API Endpoints (minimal — model only):**
| Method | Path | Auth | Permission |
|---|---|---|---|
| GET | `/api/v1/zra-submissions` | JWT | ZRA_VIEW |
| GET | `/api/v1/zra-submissions/{id}` | JWT | ZRA_VIEW |

---

### 6.9 Product Entity Updates

Add the following fields to `ProductEntity`:

```
vatRate: BigDecimal (default: 16.00 — Zambia standard VAT rate)
trackInventory: Boolean (default: true — false for services/non-stocked items)
```

Update validation accordingly:
- `vatRate` must be >= 0 (0 means VAT-exempt)
- `trackInventory = false` means stock changes are not tracked (services)
- `stockQuantity` can be null when `trackInventory = false`
- `vatRate` should be seeded with common values: 0, 16 (standard), etc.

---

## 7. Permissions & Roles Matrix

### New Permission Constants to Add:

```java
// Branches
"BRANCH_VIEW", "BRANCH_CREATE", "BRANCH_UPDATE", "BRANCH_DELETE"

// Customers
"CUSTOMER_VIEW", "CUSTOMER_CREATE", "CUSTOMER_UPDATE", "CUSTOMER_DELETE"

// Inventory
"INVENTORY_VIEW", "INVENTORY_ADJUST"

// Sales
"SALE_VIEW", "SALE_CREATE", "SALE_VOID", "SALE_REFUND"

// Payment Methods
"PAYMENT_METHOD_VIEW", "PAYMENT_METHOD_CREATE", "PAYMENT_METHOD_UPDATE", "PAYMENT_METHOD_DELETE"

// Receipts
"RECEIPT_VIEW", "RECEIPT_REPRINT"

// ZRA
"ZRA_VIEW"

// Reports
"REPORT_VIEW"
```

### Role-Permission Assignment (Updated `AuthorizationDataSeeder`):

| Role | Permissions |
|---|---|
| **ADMIN** | ALL permissions |
| **MANAGER** | All VIEW + CREATE + UPDATE for: Products, Categories, Branches, Customers, Inventory Adjust, SALE_VIEW, SALE_CREATE, SALE_VOID, SALE_REFUND, RECEIPT_VIEW, RECEIPT_REPRINT, REPORT_VIEW, PAYMENT_METHOD_VIEW |
| **CASHIER** | PRODUCT_VIEW, CATEGORY_VIEW, CUSTOMER_CREATE, CUSTOMER_VIEW, CUSTOMER_UPDATE, SALE_CREATE, SALE_VIEW, RECEIPT_VIEW, RECEIPT_REPRINT, PAYMENT_METHOD_VIEW, INVENTORY_VIEW |

---

## 8. API Endpoints Summary

### Auth (`/api/v1/auth`)
- `POST /register` — Public
- `POST /login` — Public

### Users (`/api/v1/users`)
- `POST /` — Create user (add role assignment)
- `GET /` — List users
- `GET /{id}` — Get user by ID
- **NEW:** `PUT /{id}` — Update user
- **NEW:** `DELETE /{id}` — Delete user

### Categories (`/api/v1/categories`)
- (All existing — no changes)

### Products (`/api/v1/products`)
- (All existing — extend DTOs with vatRate, trackInventory)

### Branches (`/api/v1/branches`)
- Full CRUD (NEW module)

### Customers (`/api/v1/customers`)
- Full CRUD (NEW module)
- `GET /search?phone=X` — Search by phone

### Inventory Movements (`/api/v1/inventory-movements`)
- `GET /` — List all
- `GET /product/{productId}` — Movements for a product
- `POST /adjustment` — Manual stock adjustment

### Payment Methods (`/api/v1/payment-methods`)
- Full CRUD (NEW module)

### Sales (`/api/v1/sales`)
- `POST /` — Create sale
- `GET /` — List sales
- `GET /{id}` — Get sale by ID
- `GET /number/{saleNumber}` — Get by sale number
- `POST /{id}/void` — Void sale
- `POST /{id}/refund` — Partial/full refund
- `GET /date-range` — Sales within date range

### Payments (`/api/v1/sales/{saleId}/payments`)
- `POST /` — Record payment
- `GET /` — List payments for a sale

### Receipts (`/api/v1/receipts`)
- `GET /{id}` — Get receipt
- `GET /sale/{saleId}` — Get receipt for a sale
- `POST /{id}/reprint` — Increment reprint count

### ZRA Submissions (`/api/v1/zra-submissions`)
- `GET /` — List submissions
- `GET /{id}` — Get submission details

### Reports (`/api/v1/reports`)
- `GET /sales/daily?date=YYYY-MM-DD` — Daily sales summary
- `GET /sales/summary?from=X&to=Y` — Sales summary for date range
- `GET /products/top?from=X&to=Y&limit=N` — Top selling products
- `GET /inventory/value` — Current inventory valuation

---

## 9. Data Model Changes

### Entity Lifecycle & DDL Notes

- All entities extend `BaseEntity` (createdAt, updatedAt) unless specified otherwise
- Hibernate `ddl-auto=update` handles schema changes — no manual DDL needed
- InventoryMovement is immutable — `updatable = false` on createdAt
- Sales records are never deleted — only soft-status changes

### Key Relationships Summary

```
UserEntity M:N RoleEntity (via user_roles)
RoleEntity M:N PermissionEntity (via role_permissions)
ProductEntity M:1 CategoryEntity
SaleEntity M:1 CustomerEntity (optional)
SaleEntity 1:N SaleItemEntity
SaleItemEntity M:1 ProductEntity
SaleEntity 1:N PaymentEntity
SaleEntity 1:1 ReceiptEntity (optional)
SaleEntity 1:N InventoryMovementEntity
SaleEntity 1:N ZraSubmissionEntity
PaymentEntity M:1 PaymentMethodEntity
```

---

## 10. Business Logic Rules

### Sale Creation Flow (Transaction Required — `@Transactional`):

> **Note:** The entire flow below must run within a single `@Transactional` block. If any step fails (e.g., stock insufficient, validation error), the entire transaction rolls back — no partial stock deductions or orphaned records.

> **Concurrency note for sale number generation:** The daily counter (`SALE-YYYYMMDD-XXXX`) must use database locking (e.g., `SELECT ... FOR UPDATE` on a dedicated counter table) or a database sequence to prevent duplicate numbers under concurrent load.

1. **Validate Request:**
   - All product IDs exist and are active
   - Each item quantity > 0
   - Stock quantity >= requested quantity (if `trackInventory = true`)
   - Sale-level discount >= 0 and <= subtotal

2. **Snapshot & Calculate:**
   - For each item: copy `productName`, `unitPrice`, `vatRate` from `ProductEntity`
   - Calculate `lineTotal`, `vatAmount`, and item-level discount
   - Calculate `subtotal`, `sale-level discount`, `vatTotal`, `totalAmount`

3. **Update Stock:**
   - For each item with `trackInventory = true`: decrement `product.stockQuantity`
   - Create `InventoryMovement` for each item (type = SALE, quantity = -qty)

4. **Generate Sale Number:**
   - Query latest sale number for today
   - Increment daily sequence
   - Format: `SALE-YYYYMMDD-XXXX`

5. **Persist:**
   - Save `SaleEntity`
   - Save all `SaleItemEntity` records
   - Return `SaleResponse` with `paymentStatus = PENDING`

### Payment Recording Flow:

1. Validate sale exists and is `ACTIVE`
2. Validate payment method exists and is active
3. Sum existing payments + new payment amount <= `sale.totalAmount`
4. Create `PaymentEntity`
5. Update `sale.paymentStatus`:
   - If total paid < sale.totalAmount → `PARTIALLY_PAID`
   - If total paid >= sale.totalAmount → `PAID`
6. Auto-create `ReceiptEntity` when sale becomes `PAID`

### Refund Flow:

1. Validate sale exists and is `PAID`
2. Validate `saleStatus` is `ACTIVE` (cannot refund an already refunded/voided sale)
3. If specific `saleItemIds` provided → partial refund:
   - Mark each SaleItem as `REFUNDED`
   - Increase stock for refunded items (InventoryMovement type = RETURN)
4. If no `saleItemIds` → full refund:
   - Mark all SaleItems as `REFUNDED`
   - Set `sale.saleStatus = REFUNDED`
   - Return all stock (InventoryMovement type = RETURN)
5. Refund attached payments (mark as `REFUNDED`)

---

## 11. Reports Specification

### Daily Sales Summary
```
Endpoint: GET /api/v1/reports/sales/daily?date=2026-07-13
Response:
{
  "date": "2026-07-13",
  "totalSales": 25,
  "totalRevenue": 4250.00,
  "totalVat": 560.00,
  "paymentMethodBreakdown": {
    "CASH": 2000.00,
    "MTN": 1500.00,
    "AIRTEL": 750.00
  },
  "topProducts": [
    { "productName": "Coke 500ml", "quantity": 45, "revenue": 675.00 },
    ...
  ]
}
```

### Sales Summary (Date Range)
```
Endpoint: GET /api/v1/reports/sales/summary?from=2026-07-01&to=2026-07-13
Response: { "from", "to", "totalSales", "totalRevenue", "totalVat", "averageOrderValue" }
```

### Top Selling Products
```
Endpoint: GET /api/v1/reports/products/top?from=2026-07-01&to=2026-07-13&limit=10
Response: [ { "productName", "totalQuantity", "totalRevenue" }, ... ]
```

### Inventory Valuation
```
Endpoint: GET /api/v1/reports/inventory/value
Response:
{
  "totalProducts": 150,
  "totalStockValue": 25000.00,
  "totalSellingValue": 45000.00,
  "lowStockItems": [
    { "productName": "Bread", "stockQuantity": 3 }
  ]
  // Note: If reorder levels are needed in future, add a reorderLevel field to ProductEntity.
  // For now, low-stock means stockQuantity <= 5 (configurable threshold).
}
```

**Report Permissions:** All report endpoints require `REPORT_VIEW` authority.

**Exception handling:** All new modules should follow the existing `GlobalExceptionHandler` pattern — throw `RuntimeException` (or custom subclasses) for business logic failures. The handler already returns structured JSON responses. No new exception handling infrastructure is needed.

---

## 12. Out of Scope (This Phase)

The following are explicitly excluded from this implementation:

- ❌ ZRA API integration (actual HTTP calls to ZRA Smart Invoice). Data model only.
- ❌ Loyalty points system (fields excluded from Customer entity)
- ❌ Per-branch inventory management (single-stock model retained)
- ❌ Product categories with deep nesting (flat category structure)
- ❌ Email notifications or SMS alerts
- ❌ Barcode scanning or image upload endpoints
- ❌ Websocket/real-time updates
- ❌ Pagination — all list endpoints return full results (can be added later). Note: Customer search by phone is expected to return a limited result set (< 10 matches), not a full list.
- ❌ Export to CSV/Excel (can be added as a separate endpoint)
- ❌ Audit logging beyond InventoryMovements (can be extended via Spring Data Envers later)
- ❌ Multi-currency support
- ❌ Debtor/credit tracking (Customer tab system)

---

## 13. Implementation Order

The recommended build order (each step should compile and be testable):

1. **Extend existing entities** — Add vatRate, trackInventory to ProductEntity; add update/delete endpoints to UserController
2. **Permission constants & seeder** — Add new permissions to PermissionConstants, update AuthorizationDataSeeder
3. **Branches module** — Full CRUD (independent of other new modules)
4. **Customers module** — Full CRUD (independent)
5. **Payment Methods module** — CRUD + seeder (independent)
6. **Inventory Movements module** — CRUD + adjustment endpoint
7. **Sales module** — The largest piece: SaleEntity, SaleItemEntity, sale creation, void, refund
8. **Payments module** — Payment recording, auto-receipt generation
9. **Receipts module** — Receipt lookup, reprint tracking
10. **ZRA Submissions module** — Basic model only
11. **Reports module** — Summary endpoints
12. **Integration testing** — Verify the full flow: create product → record sale → record payment → generate receipt

---

*End of Specification Document*
