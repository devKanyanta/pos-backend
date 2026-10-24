package com.devkanyanta.pos.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devkanyanta.pos.payment.PaymentEntity;
import com.devkanyanta.pos.payment.PaymentRepository;
import com.devkanyanta.pos.product.ProductEntity;
import com.devkanyanta.pos.product.ProductRepository;
import com.devkanyanta.pos.report.dto.DailySalesReport;
import com.devkanyanta.pos.report.dto.InventoryValueReport;
import com.devkanyanta.pos.report.dto.LowStockItem;
import com.devkanyanta.pos.report.dto.ProductSummary;
import com.devkanyanta.pos.report.dto.SalesSummaryReport;
import com.devkanyanta.pos.report.dto.TodaySummary;
import com.devkanyanta.pos.sale.SaleEntity;
import com.devkanyanta.pos.sale.SaleEntity.SaleStatus;
import com.devkanyanta.pos.sale.SaleRepository;
import com.devkanyanta.pos.shared.config.PosProperties;
import com.devkanyanta.pos.shared.exception.BadRequestException;
import com.devkanyanta.pos.shared.util.Money;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;

/**
 * Sales and inventory reports. Sales are grouped by business time ({@code soldAt}), so offline
 * sales count on the day they happened, not the day they were synced. Voided sales are excluded.
 */
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final SaleRepository saleRepository;

    private final ProductRepository productRepository;

    private final PaymentRepository paymentRepository;

    private final EntityManager entityManager;

    private final PosProperties properties;

    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public DailySalesReport getDailySalesReport(LocalDate date) {

        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        List<SaleEntity> sales = saleRepository.findSoldBetween(start, end).stream()
                .filter(s -> s.getSaleStatus() != SaleStatus.VOIDED)
                .toList();

        BigDecimal totalRevenue = sales.stream()
                .map(SaleEntity::getTotalAmount)
                .reduce(Money.ZERO, BigDecimal::add);

        BigDecimal totalVat = sales.stream()
                .map(s -> Money.nz(s.getVatAmount()))
                .reduce(Money.ZERO, BigDecimal::add);

        // Money received per tender for the day's sales.
        Map<String, BigDecimal> paymentMethodBreakdown = new LinkedHashMap<>();
        sales.forEach(sale -> sale.getPayments().stream()
                .filter(p -> p.getPaymentStatus() != PaymentEntity.PaymentStatus.FAILED)
                .forEach(p -> paymentMethodBreakdown.merge(p.getPaymentMethod().getName(), p.getAmount(), BigDecimal::add)));

        return DailySalesReport.builder()
                .date(date.toString())
                .totalSales((long) sales.size())
                .totalRevenue(totalRevenue)
                .totalVat(totalVat)
                .paymentMethodBreakdown(paymentMethodBreakdown)
                .topProducts(getTopProducts(date, date, 10))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public SalesSummaryReport getSalesSummary(LocalDate from, LocalDate to) {

        validateRange(from, to);

        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        Long totalSales = saleRepository.countSalesBetween(start, end);
        BigDecimal totalRevenue = Money.of(saleRepository.totalRevenueBetween(start, end));
        BigDecimal totalVat = Money.of(saleRepository.totalVatBetween(start, end));
        BigDecimal avgOrderValue = totalSales > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalSales), 2, RoundingMode.HALF_UP)
                : Money.ZERO;

        return SalesSummaryReport.builder()
                .from(from)
                .to(to)
                .totalSales(totalSales)
                .totalRevenue(totalRevenue)
                .totalVat(totalVat)
                .averageOrderValue(avgOrderValue)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<ProductSummary> getTopProducts(LocalDate from, LocalDate to, int limit) {

        validateRange(from, to);

        if (limit <= 0 || limit > 100) {
            throw new BadRequestException("limit must be between 1 and 100");
        }

        Query query = entityManager.createQuery(
                "SELECT si.productName, SUM(si.quantity - si.refundedQuantity), "
                + "SUM(si.taxableAmount + si.vatAmount - si.refundedAmount) " +
                "FROM SaleItemEntity si " +
                "JOIN si.sale s " +
                "WHERE s.soldAt >= :start AND s.soldAt < :end " +
                "AND s.saleStatus <> :voided " +
                "GROUP BY si.productName " +
                "HAVING SUM(si.quantity - si.refundedQuantity) > 0 " +
                "ORDER BY SUM(si.quantity - si.refundedQuantity) DESC"
        );

        query.setParameter("start", from.atStartOfDay());
        query.setParameter("end", to.plusDays(1).atStartOfDay());
        query.setParameter("voided", SaleStatus.VOIDED);
        query.setMaxResults(limit);

        List<Object[]> results = query.getResultList();

        return results.stream()
                .map(row -> ProductSummary.builder()
                        .productName((String) row[0])
                        .totalQuantity(((Number) row[1]).longValue())
                        .totalRevenue(Money.of((BigDecimal) row[2]))
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryValueReport getInventoryValue() {

        List<ProductEntity> products = productRepository.findAll();

        long totalProducts = products.size();
        BigDecimal totalStockValue = Money.ZERO;
        BigDecimal totalSellingValue = Money.ZERO;
        List<LowStockItem> lowStockItems = new ArrayList<>();

        for (ProductEntity product : products) {
            if (Boolean.TRUE.equals(product.getTrackInventory()) && product.getStockQuantity() != null) {
                BigDecimal qty = BigDecimal.valueOf(Math.max(0, product.getStockQuantity()));

                if (product.getAverageCost() != null) {
                    totalStockValue = totalStockValue.add(product.getAverageCost().multiply(qty));
                }

                totalSellingValue = totalSellingValue.add(product.getPrice().multiply(qty));

                int threshold = product.getReorderLevel() != null
                        ? product.getReorderLevel()
                        : properties.getLowStockThreshold();

                if (!Boolean.FALSE.equals(product.getActive()) && product.getStockQuantity() <= threshold) {
                    lowStockItems.add(LowStockItem.builder()
                            .productId(product.getId())
                            .productName(product.getName())
                            .stockQuantity(product.getStockQuantity())
                            .reorderLevel(threshold)
                            .build());
                }
            }
        }

        return InventoryValueReport.builder()
                .totalProducts(totalProducts)
                .totalStockValue(Money.of(totalStockValue))
                .totalSellingValue(Money.of(totalSellingValue))
                .lowStockItems(lowStockItems)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public TodaySummary getTodaySummary() {

        LocalDate today = LocalDate.now(clock);
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        Map<String, BigDecimal> byMethod = new LinkedHashMap<>();
        paymentRepository.findReceivedBetween(start, end)
                .forEach(p -> byMethod.merge(p.getPaymentMethod().getName(), p.getAmount(), BigDecimal::add));

        return TodaySummary.builder()
                .date(today)
                .salesCount(saleRepository.countSalesBetween(start, end))
                .totalSales(Money.of(saleRepository.totalRevenueBetween(start, end)))
                .receivedByMethod(byMethod)
                .build();
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BadRequestException("'from' must not be after 'to'");
        }
    }
}
