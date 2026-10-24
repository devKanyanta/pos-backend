package com.devkanyanta.pos.shared.migration;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.devkanyanta.pos.shared.util.Barcodes;

import lombok.extern.slf4j.Slf4j;

/**
 * Versioned data/schema migrations that Hibernate's {@code ddl-auto=update} cannot perform.
 *
 * <p>Hibernate creates new tables and columns itself. This runner (executed right after the
 * schema update and before any seeders) back-fills new columns on rows created by earlier
 * versions. Migrations are versioned, run once and recorded in {@code schema_migrations}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class SchemaMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    private final DataSource dataSource;

    public SchemaMigrationRunner(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {

        String product;
        try (var connection = dataSource.getConnection()) {
            product = connection.getMetaData().getDatabaseProductName().toLowerCase();
        }

        if (!product.contains("postgresql")) {
            log.info("Skipping schema migrations for unsupported database '{}'", product);
            return;
        }

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS schema_migrations (
                    version VARCHAR(100) NOT NULL PRIMARY KEY,
                    applied_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                )""");

        for (Map.Entry<String, List<String>> migration : versionedMigrations().entrySet()) {
            applyOnce(migration.getKey(), migration.getValue());
        }
        applyOnce("2026.10.06-normalise-product-barcodes", this::normaliseProductBarcodes);
    }

    private Map<String, List<String>> versionedMigrations() {

        Map<String, List<String>> migrations = new LinkedHashMap<>();

        migrations.put("2026.10.01-backfill-sale-sold-at", List.of(
                "UPDATE sales SET sold_at = created_at WHERE sold_at IS NULL",
                "UPDATE sales SET refunded_amount = 0 WHERE refunded_amount IS NULL",
                "UPDATE sales SET needs_review = FALSE WHERE needs_review IS NULL",
                "UPDATE sales SET source = 'ONLINE' WHERE source IS NULL"
        ));

        migrations.put("2026.10.02-backfill-sale-item-amounts", List.of(
                "UPDATE sale_items SET sale_discount_share = 0 WHERE sale_discount_share IS NULL",
                "UPDATE sale_items SET taxable_amount = line_total WHERE taxable_amount IS NULL",
                "UPDATE sale_items SET refunded_amount = 0 WHERE refunded_amount IS NULL",
                "UPDATE sale_items SET gross_amount = unit_price * quantity WHERE gross_amount IS NULL"
        ));

        migrations.put("2026.10.03-backfill-payment-paid-at", List.of(
                "UPDATE payments SET paid_at = created_at WHERE paid_at IS NULL",
                "UPDATE payments SET refunded_amount = 0 WHERE refunded_amount IS NULL"
        ));

        migrations.put("2026.10.04-backfill-product-tax-type", List.of(
                "UPDATE products SET zra_tax_type = 'A' WHERE zra_tax_type IS NULL AND vat_rate = 16",
                "UPDATE products SET zra_tax_type = 'D' WHERE zra_tax_type IS NULL AND vat_rate = 0",
                "UPDATE products SET zra_registered = FALSE WHERE zra_registered IS NULL",
                "UPDATE products SET active = TRUE WHERE active IS NULL",
                "UPDATE products SET track_inventory = TRUE WHERE track_inventory IS NULL"
        ));

        migrations.put("2026.10.05-backfill-zra-submission-attempts", List.of(
                "UPDATE zra_submissions SET next_attempt_at = created_at WHERE next_attempt_at IS NULL"
        ));

        migrations.put("2026.10.07-inventory-costing", List.of(
                // Weighted-average cost starts from the last known purchase cost.
                "UPDATE products SET average_cost = COALESCE(cost_price, 0) WHERE average_cost IS NULL",
                // Historical sale lines have no cost snapshot; the current purchase cost is the best estimate.
                "UPDATE sale_items si SET unit_cost = COALESCE(p.cost_price, 0) FROM products p "
                        + "WHERE si.product_id = p.id AND si.unit_cost IS NULL",
                // Running balance for old ledger rows (assumes the ledger started from zero, as it always has).
                "UPDATE inventory_movements m SET balance_after = x.running FROM ("
                        + "SELECT id, SUM(quantity) OVER (PARTITION BY product_id ORDER BY created_at, id) AS running "
                        + "FROM inventory_movements) x WHERE m.id = x.id AND m.balance_after IS NULL"
        ));
        migrations.put("2026.10.08-inventory-constraints", List.of(
                addCheck("purchase_order_lines", "chk_po_line_quantities",
                        "ordered_quantity > 0 AND received_quantity >= 0 AND received_quantity <= ordered_quantity "
                                + "AND returned_quantity >= 0 AND returned_quantity <= received_quantity"),
                addCheck("goods_receipt_lines", "chk_grn_line_quantity", "quantity > 0"),
                addCheck("supplier_return_lines", "chk_srn_line_quantity", "quantity > 0"),
                addCheck("stock_count_lines", "chk_count_line_quantities",
                        "expected_quantity >= 0 AND (counted_quantity IS NULL OR counted_quantity >= 0)"),
                addCheck("inventory_movements", "chk_movement_quantity", "quantity <> 0")
        ));
        migrations.put("2026.10.24-movement-type-check", List.of(
                // Hibernate generated this CHECK from the enum when the table was first created and never
                // refreshes it, so newer types (e.g. OPENING) are rejected. The enum is the source of truth.
                "ALTER TABLE inventory_movements DROP CONSTRAINT IF EXISTS inventory_movements_movement_type_check"
        ));
        return migrations;
    }

    /** Idempotent ADD CONSTRAINT ... CHECK. Added NOT VALID so rows from before the rule never block startup. */
    private static String addCheck(String table, String name, String condition) {
        return "DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = '" + name + "') THEN "
                + "ALTER TABLE " + table + " ADD CONSTRAINT " + name + " CHECK (" + condition + ") NOT VALID; "
                + "END IF; END $$";
    }

    private void applyOnce(String version, List<String> statements) {

        applyOnce(version, () -> {
            for (String sql : statements) {
                int rows = jdbc.update(sql);
                log.debug("Migration {}: '{}' updated {} rows", version, sql, rows);
            }
        });
    }

    /**
     * Brings barcodes saved before validation existed into the canonical form (trimmed, upper-case,
     * blank -> NULL) so lookups by scanned value match them. Nothing is ever deleted: a value that
     * is not a valid barcode, or whose canonical form is already used by another product, is left
     * exactly as it is and reported in the log for a person to fix.
     */
    private void normaliseProductBarcodes() {

        Map<Long, String> stored = new LinkedHashMap<>();
        jdbc.query("SELECT id, barcode FROM products WHERE barcode IS NOT NULL ORDER BY id",
                rs -> {
                    stored.put(rs.getLong("id"), rs.getString("barcode"));
                });

        Set<String> taken = new HashSet<>(stored.values());
        int updated = 0;

        for (Map.Entry<Long, String> entry : stored.entrySet()) {
            String current = entry.getValue();
            String canonical = Barcodes.canonical(current);

            if (current.equals(canonical)) {
                continue;
            }
            if (canonical == null) {
                jdbc.update("UPDATE products SET barcode = NULL WHERE id = ?", entry.getKey());
                taken.remove(current);
                updated++;
            } else if (!Barcodes.isValid(canonical)) {
                log.warn("Product {} has barcode '{}' which is not a valid barcode; left unchanged. Please correct it.",
                        entry.getKey(), current);
            } else if (taken.contains(canonical)) {
                log.warn("Product {} barcode '{}' duplicates another product once normalised ('{}'); left unchanged. "
                        + "Please resolve the duplicate.", entry.getKey(), current, canonical);
            } else {
                jdbc.update("UPDATE products SET barcode = ? WHERE id = ?", canonical, entry.getKey());
                taken.remove(current);
                taken.add(canonical);
                updated++;
            }
        }

        log.info("Normalised {} product barcode(s)", updated);
    }

    private void applyOnce(String version, Runnable migration) {

        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM schema_migrations WHERE version = ?", Integer.class, version);

        if (applied != null && applied > 0) {
            return;
        }

        migration.run();

        jdbc.update("INSERT INTO schema_migrations (version) VALUES (?)", version);
        log.info("Applied schema migration {}", version);
    }
}
