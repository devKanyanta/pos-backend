package com.devkanyanta.pos.dashboard;

import java.util.List;

import com.devkanyanta.pos.product.dto.ProductResponse;
import com.devkanyanta.pos.report.dto.TodaySummary;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DashboardResponse {

    private String plan;

    private TodaySummary today;

    /** Low-stock alert flag and the most urgent items. */
    private boolean lowStockAlert;

    private long lowStockCount;

    private List<ProductResponse> lowStockItems;

    private long zraPending;

    private long zraFailed;

    private long salesNeedingReview;

    /** Transactions waiting on devices (from their last heartbeat/sync). */
    private long deviceQueuedTransactions;

    private String reconciliationStatusToday;
}
