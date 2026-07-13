package com.devkanyanta.pos.branch;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.devkanyanta.pos.auth.security.PermissionConstants;
import com.devkanyanta.pos.branch.dto.BranchResponse;
import com.devkanyanta.pos.branch.dto.CreateBranchRequest;
import com.devkanyanta.pos.branch.dto.UpdateBranchRequest;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.BRANCH_CREATE + "')")
    @ResponseStatus(HttpStatus.CREATED)
    public BranchResponse createBranch(
            @RequestBody CreateBranchRequest request
    ) {
        return branchService.createBranch(request);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.BRANCH_VIEW + "')")
    public List<BranchResponse> getAllBranches() {
        return branchService.getAllBranches();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.BRANCH_VIEW + "')")
    public BranchResponse getBranchById(
            @PathVariable Long id
    ) {
        return branchService.getBranchById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.BRANCH_UPDATE + "')")
    public BranchResponse updateBranch(
            @PathVariable Long id,
            @RequestBody UpdateBranchRequest request
    ) {
        return branchService.updateBranch(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.BRANCH_DELETE + "')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBranch(
            @PathVariable Long id
    ) {
        branchService.deleteBranch(id);
    }
}
