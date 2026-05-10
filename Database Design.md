# POS Database Design

## MVP Tables

```text
users
roles
user_roles

branches

categories
products
product_inventory
inventory_movements

customers

sales
sale_items

payment_methods
payments

receipts

zra_submissions
```

## Overview

This design supports a POS system that needs:

- user access control
- multi-branch operations
- inventory tracking
- sales and payment processing
- receipt generation
- ZRA compliance

## 1. Users, Roles, and User Roles

### `users`

Represents anyone who can log into the POS system.

Examples:

- cashier
- shop owner
- manager

Why it exists:

- control access
- enforce accountability
- support audit tracking

Key fields:

- `email` / `phone`: login identifiers
- `password_hash`: never store plain passwords
- `is_active`: disable staff without deleting them
- `branch_id`: the branch the user belongs to

Design note:

Every sale, refund, and stock change should be traceable to a user.

### `roles`

Defines permission groups.

Examples:

- `OWNER`: full control
- `MANAGER`: reports and inventory
- `CASHIER`: sales only

Why it exists:

- avoid hardcoding permissions in application code

### `user_roles`

Links users to roles through a many-to-many relationship.

Why it exists:

- one user can have multiple roles
- permissions stay flexible as the system grows

Examples:

- owner + accountant
- cashier + supervisor

Design rule:

This avoids redesigning the database later when role combinations expand.

## 2. Branches

### `branches`

Represents physical business locations.

Examples:

- town shop
- mall shop
- warehouse

Why it exists:

- supports multi-shop operations from the start
- avoids redesign if the business expands

Key fields:

- `name`: branch name
- `zra_tpin`: tax identifier required for branch-level submission
- `is_active`: close a branch without deleting its history

Design note:

Almost every transaction should include `branch_id`.

## 3. Categories

### `categories`

Groups products logically.

Examples:

- drinks
- snacks
- electronics

Why it exists:

- reporting
- product filtering
- stock organization

Key fields:

- `name`: category name
- `description`: optional explanation

Common mistake:

Do not overcomplicate categories for the MVP. Deep nesting is usually unnecessary.

## 4. Products

### `products`

Represents items being sold.

Examples:

- Coke 500ml
- Bread
- Rice 5kg

Why it exists:

- this is the central selling entity in the POS system

Key fields:

- `sku`: internal product code for staff use
- `barcode`: scanned by the POS scanner
- `cost_price`: purchase cost
- `selling_price`: default selling price
- `vat_rate`: tax calculation rate
- `track_inventory`: allows non-stocked items such as services

Important design decisions:

- never store stock quantity in `products`
- never store sales history in `products`

This keeps the table clean and scalable.

## 5. Product Inventory

### `product_inventory`

Stores the current stock snapshot per branch.

Why it exists:

- supports fast stock lookups

Example question it answers:

> How many Coca-Cola bottles are available right now?

Key fields:

- `product_id`
- `branch_id`
- `quantity`

Design note:

This is a current-state table, not a history table.

## 6. Inventory Movements

### `inventory_movements`

Stores the full history of stock changes.

Why it exists:

- provides a complete inventory audit trail

Movement types:

- `SALE`: stock decreases
- `RESTOCK`: stock increases from supplier or intake
- `ADJUSTMENT`: manual correction
- `RETURN`: customer return
- `TRANSFER`: branch-to-branch movement

Key fields:

- `reference_id`: links to the source record such as a sale or restock
- `reference_type`: identifies what caused the movement

Why this is critical:

If stock becomes inaccurate, the history can be reconstructed from this table.

## 7. Customers

### `customers`

Represents people who buy from the store.

Why it exists:

- loyalty systems
- credit sales or tab systems
- purchase history

Key fields:

- `phone`: often the most practical identifier in local retail flows
- `tpin`: needed for tax receipts when applicable
- `loyalty_points`: supports future rewards features

## 8. Sales

### `sales`

Represents one completed checkout session.

Example:

A customer buys:

- 2 Coke
- 1 Bread

That is one sale record.

Key fields:

- `sale_number`: human-readable receipt or transaction reference
- `subtotal`: amount before tax and discount
- `vat_amount`: tax portion for ZRA compliance
- `total_amount`: final payable amount
- `payment_status`: whether the money has been received
- `sale_status`: whether the sale is valid, voided, or otherwise adjusted

Important rule:

Sales must never be deleted. They should only be:

- voided
- refunded

## 9. Sale Items

### `sale_items`

Breaks each sale into individual product lines.

Why it exists:

- one sale contains many items
- product details must be stored historically

Key fields:

- `product_name`: snapshot of the name at time of sale
- `unit_price`: selling price at time of sale
- `vat_rate`: frozen tax rate at time of sale

Critical design principle:

Even if a product changes tomorrow, historical sales must not change.

## 10. Payment Methods

### `payment_methods`

Defines allowed payment types.

Examples:

- `CASH`
- `MTN Mobile Money`
- `Airtel Money`
- `CARD`

Why it exists:

- avoids hardcoding payment types in the backend

## 11. Payments

### `payments`

Stores actual money received for a sale.

Why it is separate from `sales`:

- split payments exist
- partial payments exist
- failed payments exist

Example:

One sale can be paid with:

- K50 cash
- K20 mobile money

That produces two payment rows.

Key fields:

- `transaction_reference`: mobile money or card reference
- `payment_status`: success or failure tracking

## 12. Receipts

### `receipts`

Represents printed or issued proof of purchase.

Why it is separate from `sales`:

- receipt formats can change
- reprints need tracking
- ZRA compliance requires receipt-level handling

Key fields:

- `receipt_number`: printed invoice number
- `printed_count`: tracks reprints and prevents abuse

Important design idea:

Receipt is the presentation layer. Sale is the business transaction. They are not the same thing.

## 13. ZRA Submissions

### `zra_submissions`

Tracks every invoice sent to the ZRA Smart Invoice system.

Why it exists:

- submissions must be auditable
- failures must be retryable
- integration status must be traceable

Key fields:

- `request_payload`: what was sent to ZRA
- `response_payload`: what ZRA returned
- `submission_status`: success or failure tracking
- `retry_count`: prevents infinite retry loops

Critical design rule:

Even if ZRA is unavailable:

- sales must still continue
- submission can happen later

## System Layers

This design can be understood as four layers:

### 1. Business Layer

- `sales`
- `customers`
- `products`

### 2. Stock Layer

- `inventory_movements`
- `product_inventory`

### 3. Payment Layer

- `payments`
- `payment_methods`

### 4. Compliance Layer

- `zra_submissions`
- `receipts`
