package com.devkanyanta.pos.sync;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.devkanyanta.pos.auth.security.PermissionConstants;
import com.devkanyanta.pos.sync.dto.CatalogResponse;
import com.devkanyanta.pos.sync.dto.DeviceResponse;
import com.devkanyanta.pos.sync.dto.HeartbeatRequest;
import com.devkanyanta.pos.sync.dto.RegisterDeviceRequest;
import com.devkanyanta.pos.sync.dto.SyncSalesRequest;
import com.devkanyanta.pos.sync.dto.SyncSalesResponse;
import com.devkanyanta.pos.sync.dto.SyncStatusResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;

    @PostMapping("/devices/register")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYNC_DEVICE + "')")
    public DeviceResponse registerDevice(
            @RequestBody RegisterDeviceRequest request,
            Authentication authentication
    ) {
        return syncService.registerDevice(request, authentication.getName());
    }

    /** Device reports connectivity and its local queue size. */
    @PostMapping("/heartbeat")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYNC_DEVICE + "')")
    public DeviceResponse heartbeat(
            @RequestBody HeartbeatRequest request,
            Authentication authentication
    ) {
        return syncService.heartbeat(request, authentication.getName());
    }

    /** Uploads a batch of offline sales; idempotent per clientSaleId. */
    @PostMapping("/sales")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYNC_DEVICE + "') and hasAuthority('"
            + PermissionConstants.SALE_CREATE + "')")
    public SyncSalesResponse syncSales(
            @RequestBody SyncSalesRequest request,
            Authentication authentication
    ) {
        return syncService.syncSales(request, authentication.getName());
    }

    /** Catalogue for the device's local database; pass the previous serverTime as 'since'. */
    @GetMapping("/catalog")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYNC_DEVICE + "')")
    public CatalogResponse getCatalog(
            @RequestParam(value = "since", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since
    ) {
        return syncService.getCatalog(since);
    }

    @GetMapping("/status")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYNC_DEVICE + "')")
    public SyncStatusResponse getStatus(
            @RequestParam(value = "deviceId", required = false) String deviceId
    ) {
        return syncService.getStatus(deviceId);
    }

    @GetMapping("/devices")
    @PreAuthorize("hasAuthority('" + PermissionConstants.DEVICE_MANAGE + "')")
    public List<DeviceResponse> getDevices() {
        return syncService.getDevices();
    }

    @PostMapping("/devices/{deviceId}/deactivate")
    @PreAuthorize("hasAuthority('" + PermissionConstants.DEVICE_MANAGE + "')")
    public DeviceResponse deactivateDevice(@PathVariable String deviceId) {
        return syncService.setDeviceActive(deviceId, false);
    }

    @PostMapping("/devices/{deviceId}/activate")
    @PreAuthorize("hasAuthority('" + PermissionConstants.DEVICE_MANAGE + "')")
    public DeviceResponse activateDevice(@PathVariable String deviceId) {
        return syncService.setDeviceActive(deviceId, true);
    }
}
