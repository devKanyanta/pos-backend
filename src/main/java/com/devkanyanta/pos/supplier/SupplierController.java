package com.devkanyanta.pos.supplier;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.SUPPLIER_MANAGE + "')")
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierService.SupplierResponse create(@RequestBody SupplierService.SupplierRequest request) {
        return supplierService.create(request);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.SUPPLIER_VIEW + "')")
    public PageResponse<SupplierService.SupplierResponse> search(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "active", required = false) Boolean active,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return supplierService.search(q, active, page, size, sort);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SUPPLIER_VIEW + "')")
    public SupplierService.SupplierResponse get(@PathVariable Long id) {
        return supplierService.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SUPPLIER_MANAGE + "')")
    public SupplierService.SupplierResponse update(@PathVariable Long id, @RequestBody SupplierService.SupplierRequest request) {
        return supplierService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SUPPLIER_MANAGE + "')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        supplierService.delete(id);
    }
}
