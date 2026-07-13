package com.devkanyanta.pos.branch;

import java.util.List;

import com.devkanyanta.pos.branch.dto.BranchResponse;
import com.devkanyanta.pos.branch.dto.CreateBranchRequest;
import com.devkanyanta.pos.branch.dto.UpdateBranchRequest;

public interface BranchService {

    BranchResponse createBranch(CreateBranchRequest request);

    List<BranchResponse> getAllBranches();

    BranchResponse getBranchById(Long id);

    BranchResponse updateBranch(Long id, UpdateBranchRequest request);

    void deleteBranch(Long id);
}
