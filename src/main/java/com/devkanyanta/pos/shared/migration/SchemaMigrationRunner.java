package com.devkanyanta.pos.shared.migration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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

        return migrations;
    }

    private void applyOnce(String version, List<String> statements) {

        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM schema_migrations WHERE version = ?", Integer.class, version);

        if (applied != null && applied > 0) {
            return;
        }

        for (String sql : statements) {
            int rows = jdbc.update(sql);
            log.debug("Migration {}: '{}' updated {} rows", version, sql, rows);
        }

        jdbc.update("INSERT INTO schema_migrations (version) VALUES (?)", version);
        log.info("Applied schema migration {}", version);
    }
}
