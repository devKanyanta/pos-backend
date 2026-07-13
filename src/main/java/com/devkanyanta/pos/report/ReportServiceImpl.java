package com.devkanyanta.pos.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.devkanyanta.pos.payment.PaymentEntity;
import com.devkanyanta.pos.payment.PaymentRepository;
import com.devkanyanta.pos.product.ProductRepository;
import com.devkanyanta.pos.report.dto.DailySalesReport;
import com.devkanyanta.pos.report.dto.InventoryValueReport;
import com.devkanyanta.pos.report.dto.LowStockItem;
import com.devkanyanta.pos.report.dto.ProductSummary;
import com.devkanyanta.pos.report.dto.SalesSummaryReport;
import com.devkanyanta.pos.sale.SaleEntity;
import com.devkanyanta.pos.sale.SaleItemEntity;
import com.devkanyanta.pos.sale.SaleRepository;
import com.devkanyanta.pos.sale.SaleEntity.SaleStatus;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final SaleRepository saleRepository;

    private final ProductRepository productRepository;

    private final PaymentRepository paymentRepository;

    private final EntityManager entityManager;

    @Override
    public DailySalesReport getDailySalesReport(LocalDate date) {

        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(LocalTime.MAX);

        List<SaleEntity> sales = saleRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);

        long totalSales = sales.stream()
                .filter(s -> s.getSaleStatus() == SaleStatus.ACTIVE)
 .count();

        BigDecimal totalRevenue = sales.stream()
                .filter(s -> s.getSaleStatus() == SaleStatus.ACTIVE)
                .map(SaleEntity::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalVat = sales.stream()
                .filter(s -> s.getSaleStatus() == SaleStatus.ACTIVE)
                .map(SaleEntity::getVatAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Payment method breakdown from payments of these sales
        Map<String, BigDecimal> paymentMethodBreakdown = new LinkedHashMap<>();
        sales.stream()
                .filter(s -> s.getSaleStatus() == SaleStatus.ACTIVE)
                .forEach(sale -> {
                    List<PaymentEntity> payments = paymentRepository.findBySaleId(sale.getId());
                    payments.stream()
                            .filter(p -> p.getPaymentStatus() == PaymentEntity.PaymentStatus.SUCCESS)
                            .forEach(p -> {
                                String methodName = p.getPaymentMethod().getName();
                                paymentMethodBreakdown.merge(methodName, p.getAmount(), BigDecimal::add);
                            });
                });

        // Top products for the day
        List<ProductSummary> topProducts = getTopProducts(date, date, 10);

        return DailySalesReport.builder()
                .date(date.toString())
                .totalSales(totalSales)
                .totalRevenue(totalRevenue)
                .totalVat(totalVat)
                .paymentMethodBreakdown(paymentMethodBreakdown)
                .topProducts(topProducts)
                .build();
    }

    @Override
    public SalesSummaryReport getSalesSummary(LocalDate from, LocalDate to) {

        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.atTime(LocalTime.MAX);

        Long totalSales = saleRepository.countSalesBetween(start, end);
        BigDecimal totalRevenue = saleRepository.totalRevenueBetween(start, end);
        BigDecimal totalVat = saleRepository.totalVatBetween(start, end);
        BigDecimal avgOrderValue = totalSales > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalSales), 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

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
    @SuppressWarnings("unchecked")
    public List<ProductSummary> getTopProducts(LocalDate from, LocalDate to, int limit) {

        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.atTime(LocalTime.MAX);

        Query query = entityManager.createQuery(
                "SELECT si.productName, SUM(si.quantity), SUM(si.lineTotal) " +
                "FROM SaleItemEntity si " +
                "JOIN si.sale s " +
                "WHERE s.createdAt BETWEEN :start AND :end " +
                "AND s.saleStatus = :status " +
                "AND si.itemStatus = 'ACTIVE' " +
                "GROUP BY si.productName " +
                "ORDER BY SUM(si.quantity) DESC"
        );

        query.setParameter("start", start);
        query.setParameter("end", end);
        query.setParameter("status", SaleStatus.ACTIVE);
        query.setMaxResults(limit);

        List<Object[]> results = query.getResultList();

        return results.stream()
                .map(row -> ProductSummary.builder()
                        .productName((String) row[0])
                        .totalQuantity(((Number) row[1]).longValue())
                        .totalRevenue((BigDecimal) row[2])
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public InventoryValueReport getInventoryValue() {

        List<com.devkanyanta.pos.product.ProductEntity> products = productRepository.findAll();

        long totalProducts = products.size();
        BigDecimal totalStockValue = BigDecimal.ZERO;
        BigDecimal totalSellingValue = BigDecimal.ZERO;
        List<LowStockItem> lowStockItems = new java.util.ArrayList<>();

        for (com.devkanyanta.pos.product.ProductEntity product : products) {
            if (product.getTrackInventory() && product.getStockQuantity() != null) {
                BigDecimal qty = BigDecimal.valueOf(product.getStockQuantity());

                if (product.getCostPrice() != null) {
                    totalStockValue = totalStockValue.add(
                            product.getCostPrice().multiply(qty)
                    );
                }

                totalSellingValue = totalSellingValue.add(
                        product.getPrice().multiply(qty)
                );

                if (product.getStockQuantity() <= 5) {
                    lowStockItems.add(LowStockItem.builder()
                            .productName(product.getName())
                            .stockQuantity(product.getStockQuantity())
                            .build());
                }
            }
        }

        return InventoryValueReport.builder()
                .totalProducts(totalProducts)
                .totalStockValue(totalStockValue)
                .totalSellingValue(totalSellingValue)
                .lowStockItems(lowStockItems)
                .build();
    }
}
