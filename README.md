# POS Backend

Spring Boot REST API for a point-of-sale system built for Zambian retailers: ZRA Smart Invoice
(VSDC) integration, offline-first till sync, cash and mobile-money tenders, inventory, end-of-day
reconciliation, and WhatsApp receipt sharing.

- [Features](#features)
- [Tech stack](#tech-stack)
- [Quick start](#quick-start)
- [Demo data](#demo-data)
- [Using the API](#using-the-api)
- [Configuration](#configuration)
- [Database](#database)
- [Docker](#docker)
- [Testing](#testing)
- [Project layout](#project-layout)
- [Roles and permissions](#roles-and-permissions)
- [Plans](#plans)
- [Documentation](#documentation)
- [Troubleshooting](#troubleshooting)
- [Before going live](#before-going-live)

## Features

- **Sales and payments** – multi-line sales with discounts, VAT per line, split tenders, change
  for cash, mobile-money reference capture (duplicate references rejected), voids and
  full/partial refunds.
- **Receipts** – numbered receipts with plain-text (thermal printer), PDF and WhatsApp output.
- **Catalogue and stock** – products, categories, barcodes/SKUs, reorder levels, a full inventory
  movement ledger (sales, restocks, adjustments, returns), low-stock alerts and CSV bulk import.
- **ZRA Smart Invoice** – invoices and credit notes queued in an outbox and transmitted to the VSDC
  with exponential-backoff retries; item registration; per-product ZRA mapping.
- **Offline-first sync** – devices upload sales captured offline (idempotent per `clientSaleId`),
  pull catalogue deltas, and report heartbeats; conflicts are flagged for manager review.
- **End-of-day** – Z-report (JSON, CSV, PDF, WhatsApp) and cash-up reconciliation with variance flags.
- **Reports and dashboard** – today's totals, daily/range sales, top products, inventory value.
- **Users and security** – JWT authentication, role/permission-based authorization (ADMIN, MANAGER,
  CASHIER), MICRO/SME plans that gate features.
- **Support** – in-app WhatsApp help link and onboarding guides.

## Tech stack

| | |
|---|---|
| Language / runtime | Java 25 (Gradle provisions the toolchain automatically) |
| Framework | Spring Boot 4.0.6 (Web, Data JPA, Security, Validation) |
| Database | PostgreSQL (project-local instance, or Docker) |
| Auth | JWT (jjwt 0.11.5), HS256 |
| Build | Gradle 9.4.1 (wrapper included) |

## Quick start

### Prerequisites

- **PostgreSQL** installed so the `initdb`, `pg_ctl` and `psql` commands are available
  (Arch: `sudo pacman -S postgresql`; Debian/Ubuntu: `sudo apt install postgresql`).
  If those binaries are not on your `PATH` (e.g. Debian keeps them under
  `/usr/lib/postgresql/<version>/bin`), add that directory to `PATH`.
- Nothing else: the Gradle wrapper downloads Gradle and the Java 25 toolchain on first run.
- Alternatively, skip the local install and use the [Docker database](#docker).

### Run it

```bash
./gradlew bootRun
```

`bootRun` first starts the development database (data lives in `.dev/postgres`; no system service
and no Docker needed), then the API. It listens on **http://localhost:8081** (base path
`/api/v1`).

Check it is healthy:

```bash
curl http://localhost:8081/api/v1/health
# {"status":"UP","database":"UP","timestamp":"..."}
```

On first start the app creates its own schema and seeds roles, permissions and the default payment
methods (Cash, MTN Mobile Money, Airtel Money, Zamtel Kwacha, Card).

### Run it with sample data

```bash
POS_SEED_DEMO=true ./gradlew bootRun
```

See [Demo data](#demo-data).

## Demo data

A demo seeder can fill an **empty** database with a realistic data set, so you can explore the API
and UIs immediately. It is opt-in and off by default.

```bash
POS_SEED_DEMO=true ./gradlew bootRun
```

It only runs when the database has **no users** yet, so restarting never duplicates data, and it
does nothing on a database you have already used. To start over with fresh demo data:

```bash
./scripts/dev-db.sh reset                      # wipes ALL local data
POS_SEED_DEMO=true ./gradlew bootRun
```

### Logins

| Role | Username | Password |
|---|---|---|
| ADMIN | `admin` | `admin123` |
| MANAGER | `manager` | `manager123` |
| CASHIER | `cashier` | `cashier123` |

### What gets created

| Data | Contents |
|---|---|
| Merchant | "Mapalo General Dealers", TPIN `1002345678`, SME plan, receipt footer |
| Users | The three logins above |
| Branches | Head Office (Lusaka) and a Kitwe branch |
| Categories | Groceries, Beverages, Dairy & Bakery, Household, Personal Care, Services |
| Products | 21 products with ZRA mapping: standard-rated (16%) and exempt items, one non-stock service (phone charging), and one item below its reorder level so the low-stock alert fires |
| Customers | 5, including a business customer with a TPIN |
| Stock history | A supplier restock and a stock-take adjustment |
| Sales history | About 58 sales over the previous 6 days, uploaded through the offline-sync path from device `DEMO-TILL-01`, paid with cash, MTN, Airtel and card |
| Today's sales | Cash with change, mobile money, a split cash + card payment, a service sale, and a part-paid credit sale |
| Refund and void | One partially refunded sale and one voided sale |
| Reconciliation | Yesterday's cash-up with the cash drawer K10 short (status `SHORT`) |
| Support guides | 4 onboarding guides |

Historical baskets and times are generated from a fixed random seed, so a fresh database always
gets the same data.

> **Never enable `POS_SEED_DEMO` in production.** The demo logins have well-known passwords.

The seeder lives in `src/main/java/com/devkanyanta/pos/seed/DemoDataSeeder.java`. It goes through
the normal services, so stock, VAT, receipts and numbering behave exactly as for real traffic.

## Using the API

### Authenticate

```bash
# Log in (use the demo logins, or register -- see below)
curl -s -X POST http://localhost:8081/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'
# -> {"token":"eyJ...","username":"admin","roles":["ADMIN"]}

TOKEN=eyJ...   # paste the token
```

Without demo data, register the first account. On a fresh database the **first account becomes
ADMIN**; later self-registered accounts are CASHIERs (and need the SME plan):

```bash
curl -X POST http://localhost:8081/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Dev","lastName":"User","email":"dev@example.com","phoneNumber":"0977000000","username":"devuser","password":"devpass123"}'
```

Every other endpoint needs `Authorization: Bearer <token>`.

### Make a sale

```bash
# 1. Browse products
curl -s http://localhost:8081/api/v1/products -H "Authorization: Bearer $TOKEN"

# 2. Create a sale with payment in one call (use real product / payment-method ids)
curl -s -X POST http://localhost:8081/api/v1/sales \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
        "items": [{"productId": 1, "quantity": 2}],
        "payments": [{"paymentMethodId": 1, "amount": 400.00}]
      }'

# 3. Get the receipt as plain text for a thermal printer
curl -s "http://localhost:8081/api/v1/receipts/1/print?width=42" -H "Authorization: Bearer $TOKEN"
```

### More examples

```bash
# Dashboard
curl -s http://localhost:8081/api/v1/dashboard -H "Authorization: Bearer $TOKEN"

# Today's Z-report
curl -s http://localhost:8081/api/v1/reports/z-report -H "Authorization: Bearer $TOKEN"

# Low-stock products
curl -s http://localhost:8081/api/v1/products/low-stock -H "Authorization: Bearer $TOKEN"

# Download the CSV product import template
curl -s http://localhost:8081/api/v1/products/import/template -H "Authorization: Bearer $TOKEN" -o template.csv
```

The complete endpoint reference, with request and response shapes, is in
[docs/api-documentation.md](docs/api-documentation.md).

## Configuration

`src/main/resources/application.properties` reads these environment variables.

### Core

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/posdb` | JDBC URL |
| `DB_USERNAME` | `mapalo` | Database user |
| `DB_PASSWORD` | `mapalo` | Database password |
| `SERVER_PORT` | `8081` | HTTP port |
| `JWT_SECRET` | development value | HMAC signing secret, **at least 32 bytes**. Set your own outside local dev |
| `JWT_EXPIRATION_MINUTES` | `1440` | Token lifetime (24 h) |
| `POS_TIMEZONE` | `Africa/Lusaka` | Business day, receipts, ZRA timestamps |
| `POS_LOW_STOCK_THRESHOLD` | `5` | Used when a product has no reorder level |
| `JPA_SHOW_SQL` | `true` | Log SQL statements (set `false` to quiet the console) |
| `POS_SEED_DEMO` | `false` | Load [demo data](#demo-data) into an empty database |

### ZRA Smart Invoice

| Variable | Default |
|---|---|
| `ZRA_ENABLED` | `false` – set `true` to transmit invoices to the VSDC |
| `ZRA_VSDC_BASE_URL` | `https://api-sandbox.zra.org.zm/vsdc-api/v1` |
| `ZRA_CONNECT_TIMEOUT_SECONDS` / `ZRA_READ_TIMEOUT_SECONDS` | `10` / `30` |
| `ZRA_MAX_ATTEMPTS` | `10` |
| `ZRA_RETRY_BASE_DELAY_SECONDS` / `ZRA_RETRY_MAX_DELAY_SECONDS` | `60` / `3600` |
| `ZRA_BATCH_SIZE` | `20` |
| `ZRA_PROCESSOR_INTERVAL_MS` | `30000` |
| `ZRA_DEFAULT_REFUND_REASON_CODE` | `06` |

### Offline sync and reconciliation

| Variable | Default |
|---|---|
| `SYNC_MAX_BATCH_SIZE` | `100` sales per upload |
| `SYNC_MAX_OFFLINE_AGE_DAYS` | `60` |
| `SYNC_MAX_CLOCK_SKEW_MINUTES` | `10` |
| `RECONCILIATION_VARIANCE_TOLERANCE` | `0.00` per tender before it is flagged |

### WhatsApp

| Variable | Default |
|---|---|
| `WHATSAPP_SUPPORT_NUMBER` | empty – support line for the in-app Help button, e.g. `260971234567` |
| `WHATSAPP_PROVIDER` | `NONE` (click-to-chat links only), `TWILIO` or `META` |
| `WHATSAPP_DEFAULT_COUNTRY_CODE` | `260` |
| `TWILIO_ACCOUNT_SID` / `TWILIO_AUTH_TOKEN` / `TWILIO_WHATSAPP_FROM` | for `TWILIO` |
| `WHATSAPP_CLOUD_PHONE_NUMBER_ID` / `WHATSAPP_CLOUD_ACCESS_TOKEN` / `WHATSAPP_CLOUD_API_VERSION` | for `META` (`v21.0` by default) |

### Port already in use?

Pick another port per run:

```bash
SERVER_PORT=9090 ./gradlew bootRun
```

## Database

The local development database is a project-local PostgreSQL instance managed by
`scripts/dev-db.sh`. It binds to `127.0.0.1:5432`, with database `posdb`, user and password
`mapalo`/`mapalo` (what `application.properties` expects), plus a separate `posdb_test` for tests.
`.dev/` is git-ignored.

```bash
./scripts/dev-db.sh start    # initialise (first run) and start
./scripts/dev-db.sh status   # is it running?
./scripts/dev-db.sh stop     # shut it down
./scripts/dev-db.sh reset    # stop and DELETE all local data
```

`./gradlew bootRun` and `./gradlew test` run `start` for you. The script is idempotent. You can also
run it by hand, for example to use an IDE run configuration.

Superuser access is available over the local socket with no password:

```bash
psql -h .dev/postgres/run -U postgres posdb
# or as the app user
psql -h localhost -U mapalo posdb
```

The schema is managed by Hibernate (`spring.jpa.hibernate.ddl-auto=update`) plus small
idempotent adjustments in `SchemaMigrationRunner`. See [Database Design.md](Database%20Design.md)
for the model.

## Docker

`docker-compose.yml` starts only the PostgreSQL database (image `postgres:17`), published on
host port **5433**:

```bash
docker compose up -d
DB_URL=jdbc:postgresql://localhost:5433/posdb ./gradlew bootRun
```

The `Dockerfile` builds the API image (multi-stage, Java 25):

```bash
docker build -t pos-backend .
docker run --rm -p 8081:8081 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5433/posdb \
  -e JWT_SECRET='change-me-to-a-long-random-secret-of-32+bytes' \
  pos-backend
```

## Testing

```bash
./gradlew test
```

`test` starts the development database automatically (PostgreSQL must be installed). The
integration tests use a separate `posdb_test` database that is rebuilt on every run, and a local
stand-in for the ZRA VSDC, so they never touch your development data or the real ZRA.

| Test class | Covers |
|---|---|
| `CatalogInventoryIntegrationTest` | Products, categories, stock movements, CSV import |
| `SalesZraFlowIntegrationTest` | Sales, payments, receipts, refunds, ZRA outbox |
| `OfflineSyncIntegrationTest` | Device registration, offline upload, idempotency, conflicts |
| `ReconciliationIntegrationTest` | Cash-up, Z-report |
| `SecurityPlanSupportIntegrationTest` | Authorization, plan restrictions, support/WhatsApp |
| `PosApplicationTests` | Application context loads |

## Project layout

```
pos-backend/
├── build.gradle, settings.gradle     Gradle build (Java 25 toolchain)
├── docker-compose.yml, Dockerfile    Database container and API image
├── scripts/dev-db.sh                 Project-local PostgreSQL manager
├── docs/                             API docs, MVP spec, diagrams
└── src/
    ├── main/
    │   ├── java/com/devkanyanta/pos/
    │   │   ├── auth/                 Login/register, JWT, security config, permission seeding
    │   │   ├── user/, branch/        Users, roles, permissions; branches
    │   │   ├── merchant/             Merchant profile, plan and feature flags
    │   │   ├── category/, product/   Catalogue, ZRA mapping, CSV import
    │   │   ├── inventory/            Stock movement ledger, restock, adjustments
    │   │   ├── customer/             Customers
    │   │   ├── sale/, payment/       Sales, VAT maths, refunds, payments, payment methods
    │   │   ├── receipt/              Receipts, text/PDF rendering
    │   │   ├── zra/                  VSDC client, outbox, retry processor, device
    │   │   ├── sync/                 Offline devices, sales upload, catalogue delta
    │   │   ├── reconciliation/       Cash-up and Z-report
    │   │   ├── report/, dashboard/   Reports and dashboard
    │   │   ├── support/, whatsapp/   Help guides and WhatsApp messaging
    │   │   ├── health/               Health endpoint
    │   │   ├── seed/                 Opt-in demo data seeder
    │   │   └── shared/               Config, exceptions, utilities, PDF/CSV helpers
    │   └── resources/application.properties
    └── test/                         Integration tests (+ application-test.properties)
```

## Roles and permissions

Roles and permissions are seeded at startup (`AuthorizationDataSeeder`).

| Role | Can do |
|---|---|
| **ADMIN** | Everything |
| **MANAGER** | Catalogue (create/update, import), customers, inventory adjustments, sales incl. void and refund, receipts, reports, reconciliation, devices, ZRA read access; cannot delete users/products/payment methods or manage the merchant profile |
| **CASHIER** | View catalogue, create sales, take payments, view/reprint receipts, manage customers, sync a till, send WhatsApp receipts |

The full permission list is in [docs/api-documentation.md](docs/api-documentation.md#permissions--roles).

## Plans

| | MICRO (free) | SME (paid) |
|---|---|---|
| Sales, payments, receipts, catalogue, customers | yes | yes |
| Offline sync, today's totals, dashboard | yes | yes |
| ZRA Smart Invoice | no | yes |
| Multiple users | no (1 user) | yes |
| Sales / inventory reports | no | yes |
| Reconciliation and Z-report | no | yes |

A fresh install defaults to **SME**. Change it with `PUT /api/v1/merchant` (`MERCHANT_MANAGE`).
Calls to unavailable features return `403`.

## Documentation

| Document | Contents |
|---|---|
| [docs/api-documentation.md](docs/api-documentation.md) | Full API reference |
| [docs/mvp-backend.md](docs/mvp-backend.md) | MVP feature design: ZRA, offline sync, tenders, reconciliation, WhatsApp |
| [docs/pos-backend-spec.md](docs/pos-backend-spec.md) | Backend specification |
| [Database Design.md](Database%20Design.md) | Data model |
| `docs/domain_diagram.png` | Domain diagram |

## Troubleshooting

| Problem | Fix |
|---|---|
| `initdb not found` / `pg_ctl not found` | Install PostgreSQL and make sure its binaries are on `PATH` |
| `Port 8081 was already in use` | `SERVER_PORT=9090 ./gradlew bootRun` |
| Port 5432 already used by a system PostgreSQL | Stop it, or run the project database on another port: `DB_PORT=5434 ./scripts/dev-db.sh start` and `DB_URL=jdbc:postgresql://localhost:5434/posdb` |
| `Demo data not loaded: the database already has users` | Demo seeding only runs on an empty database. `./scripts/dev-db.sh reset` first (deletes all local data) |
| `403` on an endpoint | Your role lacks the permission, or the feature is not in your plan. Check `GET /api/v1/merchant` |
| `401` after a while | The token expired (default 24 h); log in again |
| `503` from `/health` | The database is unreachable; check `./scripts/dev-db.sh status` |
| ZRA submissions stuck `RETRYING` / `FAILED` | Check `GET /api/v1/zra-submissions/{id}` for the error; fix, then `POST /api/v1/zra-submissions/{id}/retry` |

## Before going live

- Run the VSDC integration against the ZRA sandbox (`ZRA_ENABLED=true`, `ZRA_VSDC_BASE_URL=...`),
  adjust field mappings to any validation errors, and confirm the payment-type and refund-reason
  codes from `GET /api/v1/zra/codes`.
- Set a strong `JWT_SECRET`, the WhatsApp support number and (optionally) a WhatsApp provider.
- Do **not** set `POS_SEED_DEMO`; remove or change the demo accounts if they were ever created.
- Tighten CORS: `SecurityConfig` currently allows all origins for testing.
- Use a managed PostgreSQL with backups, and set `spring.jpa.hibernate.ddl-auto` to `validate` with
  proper migrations once the schema is stable.
