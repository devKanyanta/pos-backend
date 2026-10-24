package com.devkanyanta.pos.stockcount;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.devkanyanta.pos.auth.security.PermissionConstants;
import com.devkanyanta.pos.shared.web.PageResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/stock-counts")
@RequiredArgsConstructor
public class StockCountController {

    private static final String MANAGE = "hasAuthority('" + PermissionConstants.STOCK_COUNT_MANAGE + "')";

    private final StockCountService stockCountService;

    @PostMapping
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    public StockCountService.CountResponse create(@RequestBody(required = false) StockCountService.CountRequest request) {
        return stockCountService.create(request);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.INVENTORY_VIEW + "')")
    public PageResponse<StockCountService.CountResponse> search(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return stockCountService.search(status, page, size, sort);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.INVENTORY_VIEW + "')")
    public StockCountService.CountResponse get(@PathVariable Long id) {
        return stockCountService.get(id);
    }

    @PutMapping("/{id}/lines")
    @PreAuthorize(MANAGE)
    public StockCountService.CountResponse recordCounts(@PathVariable Long id,
                                                        @RequestBody StockCountService.CountLinesRequest request) {
        return stockCountService.recordCounts(id, request);
    }

    @PostMapping("/{id}/commit")
    @PreAuthorize(MANAGE)
    public StockCountService.CountResponse commit(@PathVariable Long id) {
        return stockCountService.commit(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(MANAGE)
    public StockCountService.CountResponse cancel(@PathVariable Long id) {
        return stockCountService.cancel(id);
    }
}
