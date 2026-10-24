package com.devkanyanta.pos.unit;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.devkanyanta.pos.auth.security.PermissionConstants;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/units")
@RequiredArgsConstructor
public class UnitController {

    private final UnitService unitService;

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.UNIT_MANAGE + "')")
    @ResponseStatus(HttpStatus.CREATED)
    public UnitService.UnitResponse create(@RequestBody UnitService.UnitRequest request) {
        return unitService.create(request);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.PRODUCT_VIEW + "')")
    public List<UnitService.UnitResponse> list() {
        return unitService.list();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.UNIT_MANAGE + "')")
    public UnitService.UnitResponse update(@PathVariable Long id, @RequestBody UnitService.UnitRequest request) {
        return unitService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.UNIT_MANAGE + "')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        unitService.delete(id);
    }
}
