package com.devkanyanta.pos.reconciliation;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.devkanyanta.pos.auth.security.PermissionConstants;
import com.devkanyanta.pos.reconciliation.dto.ReconciliationRequest;
import com.devkanyanta.pos.reconciliation.dto.ReconciliationResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/reconciliations")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    /** Submits (or resubmits) the end-of-day cash-up for a business day. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + PermissionConstants.RECONCILIATION_MANAGE + "')")
    public ReconciliationResponse submit(
            @RequestBody ReconciliationRequest request,
            Authentication authentication
    ) {
        return reconciliationService.submit(request, authentication.getName());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.RECONCILIATION_VIEW + "')")
    public List<ReconciliationResponse> list(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return reconciliationService.list(from, to);
    }

    @GetMapping("/{date}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.RECONCILIATION_VIEW + "')")
    public ReconciliationResponse getByDate(
            @PathVariable("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return reconciliationService.getByDate(date);
    }
}
