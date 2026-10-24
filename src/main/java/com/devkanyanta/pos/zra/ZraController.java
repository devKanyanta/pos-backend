package com.devkanyanta.pos.zra;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.devkanyanta.pos.auth.security.PermissionConstants;
import com.devkanyanta.pos.zra.dto.InitializeDeviceRequest;
import com.devkanyanta.pos.zra.dto.ZraDeviceResponse;
import com.devkanyanta.pos.zra.dto.ZraStatusResponse;
import com.devkanyanta.pos.zra.dto.ZraSubmissionResponse;
import com.devkanyanta.pos.zra.dto.ZraTaxTypeResponse;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/zra")
@RequiredArgsConstructor
public class ZraController {

    private final ZraService zraService;

    /** VAT categories for the product form (available on every plan). */
    @GetMapping("/tax-types")
    @PreAuthorize("hasAuthority('" + PermissionConstants.PRODUCT_VIEW + "')")
    public List<ZraTaxTypeResponse> getTaxTypes() {
        return zraService.getTaxTypes();
    }

    @GetMapping("/status")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ZRA_VIEW + "')")
    public ZraStatusResponse getStatus() {
        return zraService.getStatus();
    }

    @PostMapping("/device/initialize")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ZRA_MANAGE + "')")
    public ZraDeviceResponse initializeDevice(@RequestBody InitializeDeviceRequest request) {
        return zraService.initializeDevice(request);
    }

    @GetMapping("/device")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ZRA_VIEW + "')")
    public ZraDeviceResponse getDevice() {
        return zraService.getDevice();
    }

    /** VSDC code lists; lastReqDt in yyyyMMddHHmmss (defaults to everything). */
    @GetMapping("/codes")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ZRA_MANAGE + "')")
    public JsonNode getCodes(@RequestParam(value = "lastReqDt", required = false) String lastReqDt) {
        return zraService.getCodes(lastReqDt);
    }

    @GetMapping("/item-classes")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ZRA_MANAGE + "')")
    public JsonNode getItemClasses(@RequestParam(value = "lastReqDt", required = false) String lastReqDt) {
        return zraService.getItemClasses(lastReqDt);
    }

    @PostMapping("/items/{productId}/register")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ZRA_MANAGE + "')")
    public ZraSubmissionResponse registerProduct(@PathVariable Long productId) {
        return zraService.registerProduct(productId);
    }

    @PostMapping("/items/register-all")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ZRA_MANAGE + "')")
    public Map<String, Integer> registerAll() {
        return Map.of("queued", zraService.queueUnregisteredProducts());
    }

    /** Transmits everything that is due now instead of waiting for the background job. */
    @PostMapping("/submissions/process")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ZRA_MANAGE + "')")
    public Map<String, Integer> processNow() {
        return Map.of("processed", zraService.processNow());
    }
}
