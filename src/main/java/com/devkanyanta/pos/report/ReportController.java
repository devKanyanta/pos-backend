package com.devkanyanta.pos.report;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.devkanyanta.pos.auth.security.PermissionConstants;
import com.devkanyanta.pos.merchant.MerchantProfileService;
import com.devkanyanta.pos.reconciliation.ZReportService;
import com.devkanyanta.pos.reconciliation.dto.ZReportResponse;
import com.devkanyanta.pos.report.dto.DailySalesReport;
import com.devkanyanta.pos.report.dto.InventoryValueReport;
import com.devkanyanta.pos.report.dto.ProductSummary;
import com.devkanyanta.pos.report.dto.SalesSummaryReport;
import com.devkanyanta.pos.report.dto.TodaySummary;
import com.devkanyanta.pos.support.dto.SendWhatsAppRequest;
import com.devkanyanta.pos.whatsapp.WhatsAppSendResult;
import com.devkanyanta.pos.whatsapp.WhatsAppService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    private final ZReportService zReportService;

    private final MerchantProfileService merchantProfileService;

    private final WhatsAppService whatsAppService;

    /** Today's total and money received per tender. Available on the MICRO plan. */
    @GetMapping("/today")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SALE_VIEW + "')")
    public TodaySummary getToday() {
        return reportService.getTodaySummary();
    }

    @GetMapping("/sales/daily")
    @PreAuthorize("hasAuthority('" + PermissionConstants.REPORT_VIEW + "')")
    public DailySalesReport getDailySalesReport(
            @RequestParam("date") LocalDate date
    ) {
        requireReports();
        return reportService.getDailySalesReport(date);
    }

    @GetMapping("/sales/summary")
    @PreAuthorize("hasAuthority('" + PermissionConstants.REPORT_VIEW + "')")
    public SalesSummaryReport getSalesSummary(
            @RequestParam("from") LocalDate from,
            @RequestParam("to") LocalDate to
    ) {
        requireReports();
        return reportService.getSalesSummary(from, to);
    }

    @GetMapping("/products/top")
    @PreAuthorize("hasAuthority('" + PermissionConstants.REPORT_VIEW + "')")
    public List<ProductSummary> getTopProducts(
            @RequestParam("from") LocalDate from,
            @RequestParam("to") LocalDate to,
            @RequestParam(value = "limit", defaultValue = "10") int limit
    ) {
        requireReports();
        return reportService.getTopProducts(from, to, limit);
    }

    @GetMapping("/inventory/value")
    @PreAuthorize("hasAuthority('" + PermissionConstants.REPORT_VIEW + "')")
    public InventoryValueReport getInventoryValue() {
        requireReports();
        return reportService.getInventoryValue();
    }

    // ---- End-of-day Z-report ---------------------------------------------------------------

    @GetMapping("/z-report")
    @PreAuthorize("hasAuthority('" + PermissionConstants.RECONCILIATION_VIEW + "')")
    public ZReportResponse getZReport(
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        requireReconciliation();
        return zReportService.generate(date);
    }

    @GetMapping(value = "/z-report/csv", produces = "text/csv")
    @PreAuthorize("hasAuthority('" + PermissionConstants.RECONCILIATION_VIEW + "')")
    public ResponseEntity<byte[]> getZReportCsv(
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        requireReconciliation();
        ZReportResponse report = zReportService.generate(date);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"z-report-" + report.getBusinessDate() + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(zReportService.toCsv(report).getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping(value = "/z-report/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAuthority('" + PermissionConstants.RECONCILIATION_VIEW + "')")
    public ResponseEntity<byte[]> getZReportPdf(
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        requireReconciliation();
        ZReportResponse report = zReportService.generate(date);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"z-report-" + report.getBusinessDate() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(zReportService.toPdf(report));
    }

    /** WhatsApp-ready summary text plus a click-to-chat share link. */
    @GetMapping("/z-report/whatsapp")
    @PreAuthorize("hasAuthority('" + PermissionConstants.RECONCILIATION_VIEW + "')")
    public Map<String, Object> getZReportWhatsApp(
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "phone", required = false) String phone
    ) {
        requireReconciliation();
        ZReportResponse report = zReportService.generate(date);
        String text = zReportService.toWhatsAppText(report);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("businessDate", report.getBusinessDate());
        response.put("text", text);
        response.put("shareUrl", whatsAppService.clickToChatLink(phone, text));
        response.put("canSendDirectly", whatsAppService.isSendingConfigured());
        return response;
    }

    /** Sends the Z-report summary through the configured WhatsApp Business provider. */
    @PostMapping("/z-report/whatsapp/send")
    @PreAuthorize("hasAuthority('" + PermissionConstants.RECONCILIATION_VIEW + "') and hasAuthority('"
            + PermissionConstants.WHATSAPP_SEND + "')")
    public WhatsAppSendResult sendZReportWhatsApp(
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody SendWhatsAppRequest request,
            Authentication authentication
    ) {
        requireReconciliation();
        ZReportResponse report = zReportService.generate(date);
        return whatsAppService.send(request.getPhone(), zReportService.toWhatsAppText(report),
                "Z_REPORT", null, authentication.getName());
    }

    private void requireReports() {
        merchantProfileService.requireFeature(MerchantProfileService.FEATURE_REPORTS, "Sales and inventory reports");
    }

    private void requireReconciliation() {
        merchantProfileService.requireFeature(MerchantProfileService.FEATURE_RECONCILIATION, "End-of-day reconciliation");
    }
}
