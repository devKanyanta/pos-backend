package com.devkanyanta.pos.inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devkanyanta.pos.inventory.InventoryDtos.AdjustmentGroup;
import com.devkanyanta.pos.inventory.InventoryDtos.AdjustmentReport;
import com.devkanyanta.pos.inventory.InventoryDtos.CogsGroup;
import com.devkanyanta.pos.inventory.InventoryDtos.CogsReport;
import com.devkanyanta.pos.inventory.InventoryDtos.DepletionItem;
import com.devkanyanta.pos.inventory.InventoryDtos.MoverItem;
import com.devkanyanta.pos.inventory.InventoryDtos.MoversReport;
import com.devkanyanta.pos.inventory.InventoryDtos.PurchaseGroup;
import com.devkanyanta.pos.inventory.InventoryDtos.PurchaseReport;
import com.devkanyanta.pos.inventory.InventoryDtos.ValuationGroup;
import com.devkanyanta.pos.inventory.InventoryDtos.ValuationReport;
import com.devkanyanta.pos.inventory.InventoryMovementEntity.MovementType;
import com.devkanyanta.pos.merchant.MerchantProfileService;
import com.devkanyanta.pos.sale.SaleEntity.SaleStatus;
import com.devkanyanta.pos.shared.exception.BadRequestException;
import com.devkanyanta.pos.stockcount.StockCountEntity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;

/**
 * Inventory reports. Every figure is aggregated by the database from the ledger, sale lines, receipts and
 * counts; nothing is cached or hard-coded. Stock is valued at weighted-average cost and the cost of goods
 * sold uses the average cost captured on each sale line when the sale was made.
 *
 * <p>Batch/expiry reporting is not provided: stock is tracked per product, not per batch.
 */
@Service
@RequiredArgsConstructor
public class InventoryReportService {

    public static final String METHOD = "WEIGHTED_AVERAGE";

    private static final int MAX_LIMIT = 500;

    private final EntityManager em;

    private final MerchantProfileService merchantProfileService;

    private void requirePlan() {
        merchantProfileService.requireFeature(MerchantProfileService.FEATURE_REPORTS, "Inventory reports");
    }

    // ---- Valuation ----

    @Transactional(readOnly = true)
    public ValuationReport valuation(Long categoryId) {
        requirePlan();

        String jpql = """
                SELECT c.id, c.name, COUNT(p), COALESCE(SUM(p.stockQuantity), 0),
                       COALESCE(SUM(p.stockQuantity * COALESCE(p.averageCost, 0)), 0),
                       COALESCE(SUM(p.stockQuantity * p.price), 0)
                FROM ProductEntity p LEFT JOIN p.category c
                WHERE p.trackInventory = true AND p.stockQuantity > 0
                """ + (categoryId != null ? " AND c.id = :cat" : "") + " GROUP BY c.id, c.name ORDER BY c.name";
        TypedQuery<Object[]> query = em.createQuery(jpql, Object[].class);
        if (categoryId != null) {
            query.setParameter("cat", categoryId);
        }

        List<ValuationGroup> groups = new ArrayList<>();
        long products = 0;
        long units = 0;
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal retail = BigDecimal.ZERO;
        for (Object[] row : query.getResultList()) {
            ValuationGroup g = new ValuationGroup((Long) row[0], row[1] != null ? (String) row[1] : "Uncategorised",
                    (Long) row[2], ((Number) row[3]).longValue(), money(row[4]), money(row[5]));
            groups.add(g);
            products += g.products();
            units += g.units();
            cost = cost.add(g.costValue());
            retail = retail.add(g.retailValue());
        }
        return new ValuationReport(METHOD, products, units, cost, retail, retail.subtract(cost), groups);
    }

    // ---- Cost of goods sold ----

    @Transactional(readOnly = true)
    public CogsReport cogs(LocalDate from, LocalDate to, Long categoryId) {
        requirePlan();
        Range range = range(from, to);

        String where = """
                WHERE s.saleStatus <> :voided AND s.soldAt >= :from AND s.soldAt < :to
                """ + (categoryId != null ? " AND p.category.id = :cat" : "");
        String measures = """
                COALESCE(SUM(i.quantity), 0), COALESCE(SUM(COALESCE(i.refundedQuantity, 0)), 0),
                COALESCE(SUM(COALESCE(i.taxableAmount, i.lineTotal)), 0),
                COALESCE(SUM(COALESCE(i.refundedAmount, 0) - COALESCE(i.refundedVatAmount, 0)), 0),
                COALESCE(SUM((i.quantity - COALESCE(i.refundedQuantity, 0)) * COALESCE(i.unitCost, 0)), 0)
                """;

        TypedQuery<Object[]> totals = em.createQuery("SELECT " + measures
                + ", COALESCE(SUM(CASE WHEN i.unitCost IS NULL THEN 1 ELSE 0 END), 0) "
                + "FROM SaleItemEntity i JOIN i.sale s LEFT JOIN i.product p " + where, Object[].class);
        bind(totals, range, categoryId);
        Object[] t = totals.getSingleResult();

        long sold = ((Number) t[0]).longValue();
        long refunded = ((Number) t[1]).longValue();
        BigDecimal revenue = money(t[2]).subtract(money(t[3]));
        BigDecimal cogs = money(t[4]);
        long withoutCost = ((Number) t[5]).longValue();

        TypedQuery<Object[]> grouped = em.createQuery("SELECT c.id, c.name, " + measures
                + "FROM SaleItemEntity i JOIN i.sale s LEFT JOIN i.product p LEFT JOIN p.category c " + where
                + " GROUP BY c.id, c.name ORDER BY c.name", Object[].class);
        bind(grouped, range, categoryId);
        List<CogsGroup> groups = new ArrayList<>();
        for (Object[] row : grouped.getResultList()) {
            BigDecimal groupRevenue = money(row[4]).subtract(money(row[5]));
            groups.add(new CogsGroup((Long) row[0], row[1] != null ? (String) row[1] : "Uncategorised",
                    ((Number) row[2]).longValue() - ((Number) row[3]).longValue(), groupRevenue, money(row[6]),
                    groupRevenue.subtract(money(row[6]))));
        }

        BigDecimal profit = revenue.subtract(cogs);
        BigDecimal margin = revenue.signum() == 0 ? BigDecimal.ZERO
                : profit.multiply(BigDecimal.valueOf(100)).divide(revenue, 2, RoundingMode.HALF_UP);
        return new CogsReport(from, to, METHOD, sold - refunded, refunded, revenue, cogs, profit, margin,
                withoutCost, groups);
    }

    // ---- Fast and slow movers ----

    @Transactional(readOnly = true)
    public MoversReport movers(LocalDate from, LocalDate to, String direction, Integer limit) {
        requirePlan();
        Range range = range(from, to);
        int max = limit == null ? 10 : limit;
        if (max < 1 || max > MAX_LIMIT) {
            throw new BadRequestException("limit must be between 1 and " + MAX_LIMIT);
        }
        String dir = direction == null ? "FAST" : direction.trim().toUpperCase(Locale.ROOT);
        if (!dir.equals("FAST") && !dir.equals("SLOW")) {
            throw new BadRequestException("direction must be FAST or SLOW");
        }

        TypedQuery<Object[]> query;
        if (dir.equals("FAST")) {
            query = em.createQuery("""
                    SELECT p.id, p.name, p.sku, p.stockQuantity,
                           SUM(i.quantity - COALESCE(i.refundedQuantity, 0)),
                           SUM(COALESCE(i.taxableAmount, i.lineTotal))
                    FROM SaleItemEntity i JOIN i.sale s JOIN i.product p
                    WHERE s.saleStatus <> :voided AND s.soldAt >= :from AND s.soldAt < :to
                    GROUP BY p.id, p.name, p.sku, p.stockQuantity
                    HAVING SUM(i.quantity - COALESCE(i.refundedQuantity, 0)) > 0
                    ORDER BY SUM(i.quantity - COALESCE(i.refundedQuantity, 0)) DESC, p.id
                    """, Object[].class);
        } else {
            // Products on the shelf that sold least (including nothing at all) in the period.
            query = em.createQuery("""
                    SELECT p.id, p.name, p.sku, p.stockQuantity,
                           (SELECT COALESCE(SUM(i.quantity - COALESCE(i.refundedQuantity, 0)), 0)
                              FROM SaleItemEntity i
                             WHERE i.product = p AND i.sale.saleStatus <> :voided
                               AND i.sale.soldAt >= :from AND i.sale.soldAt < :to),
                           (SELECT COALESCE(SUM(COALESCE(i.taxableAmount, i.lineTotal)), 0)
                              FROM SaleItemEntity i
                             WHERE i.product = p AND i.sale.saleStatus <> :voided
                               AND i.sale.soldAt >= :from AND i.sale.soldAt < :to)
                    FROM ProductEntity p
                    WHERE p.trackInventory = true AND (p.active IS NULL OR p.active = true) AND p.stockQuantity > 0
                    ORDER BY 5 ASC, p.stockQuantity DESC, p.id
                    """, Object[].class);
        }
        query.setParameter("voided", SaleStatus.VOIDED);
        query.setParameter("from", range.start());
        query.setParameter("to", range.end());
        query.setMaxResults(max);

        List<MoverItem> items = query.getResultList().stream().map(r -> new MoverItem((Long) r[0], (String) r[1],
                (String) r[2], ((Number) r[3]).intValue(), ((Number) r[4]).longValue(), money(r[5]))).toList();
        return new MoversReport(from, to, dir, items);
    }

    // ---- Adjustments and stock counts ----

    @Transactional(readOnly = true)
    public AdjustmentReport adjustments(LocalDate from, LocalDate to) {
        requirePlan();
        Range range = range(from, to);

        List<Object[]> rows = em.createQuery("""
                SELECT m.movementType, m.reason, COUNT(m),
                       COALESCE(SUM(CASE WHEN m.quantity > 0 THEN m.quantity ELSE 0 END), 0),
                       COALESCE(SUM(CASE WHEN m.quantity < 0 THEN -m.quantity ELSE 0 END), 0),
                       COALESCE(SUM(m.quantity * COALESCE(m.unitCost, 0)), 0)
                FROM InventoryMovementEntity m
                WHERE m.movementType IN :types AND m.createdAt >= :from AND m.createdAt < :to
                GROUP BY m.movementType, m.reason
                ORDER BY m.movementType, m.reason
                """, Object[].class)
                .setParameter("types", List.of(MovementType.ADJUSTMENT, MovementType.COUNT))
                .setParameter("from", range.start()).setParameter("to", range.end()).getResultList();

        List<AdjustmentGroup> groups = new ArrayList<>();
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal countNet = BigDecimal.ZERO;
        for (Object[] r : rows) {
            MovementType type = (MovementType) r[0];
            BigDecimal value = money(r[5]);
            groups.add(new AdjustmentGroup(type.name(), r[1] != null ? (String) r[1] : "UNSPECIFIED",
                    (Long) r[2], ((Number) r[3]).longValue(), ((Number) r[4]).longValue(), value));
            net = net.add(value);
            if (type == MovementType.COUNT) {
                countNet = countNet.add(value);
            }
        }
        Long counts = em.createQuery("""
                SELECT COUNT(c) FROM StockCountEntity c
                WHERE c.status = :status AND c.committedAt >= :from AND c.committedAt < :to
                """, Long.class).setParameter("status", StockCountEntity.Status.COMMITTED)
                .setParameter("from", range.start()).setParameter("to", range.end()).getSingleResult();
        return new AdjustmentReport(from, to, groups, net, counts, countNet);
    }

    // ---- Purchases ----

    @Transactional(readOnly = true)
    public PurchaseReport purchases(LocalDate from, LocalDate to, Long supplierId) {
        requirePlan();
        Range range = range(from, to);

        TypedQuery<Object[]> received = em.createQuery("""
                SELECT s.id, s.name, COUNT(DISTINCT r.id), COALESCE(SUM(l.quantity), 0),
                       COALESCE(SUM(l.quantity * l.unitCost), 0)
                FROM GoodsReceiptLineEntity l JOIN l.receipt r JOIN r.supplier s
                WHERE r.receivedAt >= :from AND r.receivedAt < :to
                """ + (supplierId != null ? " AND s.id = :sup" : "") + " GROUP BY s.id, s.name ORDER BY s.name", Object[].class);
        TypedQuery<Object[]> returned = em.createQuery("""
                SELECT s.id, s.name, COUNT(DISTINCT r.id), COALESCE(SUM(l.quantity * l.creditUnitCost), 0)
                FROM SupplierReturnLineEntity l JOIN l.supplierReturn r JOIN r.supplier s
                WHERE r.returnedAt >= :from AND r.returnedAt < :to
                """ + (supplierId != null ? " AND s.id = :sup" : "") + " GROUP BY s.id, s.name ORDER BY s.name", Object[].class);
        for (TypedQuery<Object[]> q : List.of(received, returned)) {
            q.setParameter("from", range.start()).setParameter("to", range.end());
            if (supplierId != null) {
                q.setParameter("sup", supplierId);
            }
        }

        Map<Long, long[]> counts = new LinkedHashMap<>();
        Map<Long, String> names = new LinkedHashMap<>();
        Map<Long, BigDecimal[]> amounts = new LinkedHashMap<>();
        for (Object[] r : received.getResultList()) {
            Long id = (Long) r[0];
            names.put(id, (String) r[1]);
            counts.put(id, new long[] {(Long) r[2], ((Number) r[3]).longValue(), 0});
            amounts.put(id, new BigDecimal[] {money(r[4]), BigDecimal.ZERO.setScale(2)});
        }
        for (Object[] r : returned.getResultList()) {
            Long id = (Long) r[0];
            names.putIfAbsent(id, (String) r[1]);
            counts.computeIfAbsent(id, k -> new long[3])[2] = (Long) r[2];
            amounts.computeIfAbsent(id, k -> new BigDecimal[] {BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2)})[1] = money(r[3]);
        }

        List<PurchaseGroup> groups = new ArrayList<>();
        long receipts = 0;
        long units = 0;
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (Long id : names.keySet()) {
            long[] c = counts.get(id);
            BigDecimal[] a = amounts.get(id);
            groups.add(new PurchaseGroup(id, names.get(id), c[0], c[1], a[0], c[2], a[1]));
            receipts += c[0];
            units += c[1];
            cost = cost.add(a[0]);
            credit = credit.add(a[1]);
        }
        groups.sort(Comparator.comparing(PurchaseGroup::supplierName));
        return new PurchaseReport(from, to, receipts, units, cost, credit, groups);
    }

    // ---- Sales-related depletion ----

    @Transactional(readOnly = true)
    public List<DepletionItem> depletion(LocalDate from, LocalDate to, Long categoryId, Integer limit) {
        requirePlan();
        Range range = range(from, to);
        int max = limit == null ? 50 : limit;
        if (max < 1 || max > MAX_LIMIT) {
            throw new BadRequestException("limit must be between 1 and " + MAX_LIMIT);
        }

        TypedQuery<Object[]> query = em.createQuery("""
                SELECT p.id, p.name, p.sku, p.stockQuantity,
                       COALESCE(SUM(CASE WHEN m.movementType = :sale THEN -m.quantity ELSE 0 END), 0),
                       COALESCE(SUM(CASE WHEN m.movementType = :voided THEN m.quantity ELSE 0 END), 0),
                       COALESCE(SUM(CASE WHEN m.movementType = :returned THEN m.quantity ELSE 0 END), 0)
                FROM InventoryMovementEntity m JOIN m.product p
                WHERE m.movementType IN :types AND m.createdAt >= :from AND m.createdAt < :to
                """ + (categoryId != null ? " AND p.category.id = :cat" : "")
                + " GROUP BY p.id, p.name, p.sku, p.stockQuantity", Object[].class);
        query.setParameter("sale", MovementType.SALE).setParameter("voided", MovementType.SALE_VOID)
                .setParameter("returned", MovementType.RETURN)
                .setParameter("types", List.of(MovementType.SALE, MovementType.SALE_VOID, MovementType.RETURN))
                .setParameter("from", range.start()).setParameter("to", range.end());
        if (categoryId != null) {
            query.setParameter("cat", categoryId);
        }

        return query.getResultList().stream()
                .map(r -> {
                    long sold = ((Number) r[4]).longValue();
                    long voided = ((Number) r[5]).longValue();
                    long returned = ((Number) r[6]).longValue();
                    return new DepletionItem((Long) r[0], (String) r[1], (String) r[2], sold, voided, returned,
                            sold - voided - returned, ((Number) r[3]).intValue());
                })
                .sorted(Comparator.comparingLong(DepletionItem::netDepleted).reversed()
                        .thenComparing(DepletionItem::productId))
                .limit(max)
                .toList();
    }

    // ---- helpers ----

    private record Range(LocalDateTime start, LocalDateTime end) {
    }

    private Range range(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BadRequestException("'from' and 'to' dates are required (yyyy-MM-dd)");
        }
        if (from.isAfter(to)) {
            throw new BadRequestException("'from' must not be after 'to'");
        }
        return new Range(from.atStartOfDay(), to.plusDays(1).atStartOfDay());
    }

    private void bind(TypedQuery<Object[]> query, Range range, Long categoryId) {
        query.setParameter("voided", SaleStatus.VOIDED);
        query.setParameter("from", range.start());
        query.setParameter("to", range.end());
        if (categoryId != null) {
            query.setParameter("cat", categoryId);
        }
    }

    private static BigDecimal money(Object value) {
        return InventoryQueryService.money(value);
    }
}
