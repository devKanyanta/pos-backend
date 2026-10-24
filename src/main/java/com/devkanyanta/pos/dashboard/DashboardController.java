package com.devkanyanta.pos.dashboard;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devkanyanta.pos.auth.security.PermissionConstants;
import com.devkanyanta.pos.merchant.MerchantProfileService;
import com.devkanyanta.pos.product.ProductService;
import com.devkanyanta.pos.product.dto.ProductResponse;
import com.devkanyanta.pos.reconciliation.ReconciliationRepository;
import com.devkanyanta.pos.report.ReportService;
import com.devkanyanta.pos.sale.SaleRepository;
import com.devkanyanta.pos.sync.PosDeviceRepository;
import com.devkanyanta.pos.zra.ZraSubmissionEntity.SubmissionStatus;
import com.devkanyanta.pos.zra.ZraSubmissionRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ReportService reportService;

    private final ProductService productService;

    private final ZraSubmissionRepository zraSubmissionRepository;

    private final SaleRepository saleRepository;

    private final PosDeviceRepository deviceRepository;

    private final ReconciliationRepository reconciliationRepository;

    private final MerchantProfileService merchantProfileService;

    private final Clock clock;

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.SALE_VIEW + "')")
    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {

        List<ProductResponse> lowStock = productService.getLowStockProducts();

        return DashboardResponse.builder()
                .plan(merchantProfileService.currentPlan().name())
                .today(reportService.getTodaySummary())
                .lowStockAlert(!lowStock.isEmpty())
                .lowStockCount(lowStock.size())
                .lowStockItems(lowStock.stream().limit(10).toList())
                .zraPending(zraSubmissionRepository.countBySubmissionStatusIn(
                        List.of(SubmissionStatus.PENDING, SubmissionStatus.RETRYING)))
                .zraFailed(zraSubmissionRepository.countBySubmissionStatus(SubmissionStatus.FAILED))
                .salesNeedingReview(saleRepository.countByNeedsReviewTrue())
                .deviceQueuedTransactions(deviceRepository.findAll().stream()
                        .filter(d -> !Boolean.FALSE.equals(d.getActive()))
                        .mapToLong(d -> d.getPendingQueueSize() != null ? d.getPendingQueueSize() : 0)
                        .sum())
                .reconciliationStatusToday(reconciliationRepository.findByBusinessDate(LocalDate.now(clock))
                        .map(r -> r.getStatus())
                        .orElse("NOT_RECONCILED"))
                .build();
    }
}
