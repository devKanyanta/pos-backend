package com.devkanyanta.pos.branch;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.devkanyanta.pos.branch.dto.BranchResponse;
import com.devkanyanta.pos.branch.dto.CreateBranchRequest;
import com.devkanyanta.pos.branch.dto.UpdateBranchRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BranchServiceImpl implements BranchService {

    private final BranchRepository branchRepository;

    @Override
    public BranchResponse createBranch(CreateBranchRequest request) {

        validateCreateRequest(request);

        if (branchRepository.existsByName(request.getName())) {
            throw new RuntimeException("Branch name already exists");
        }

        BranchEntity branch = BranchEntity.builder()
                .name(request.getName())
                .zraTpin(request.getZraTpin())
                .phone(request.getPhone())
                .email(request.getEmail())
                .address(request.getAddress())
                .build();

        return toResponse(branchRepository.save(branch));
    }

    @Override
    public List<BranchResponse> getAllBranches() {

        return branchRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public BranchResponse getBranchById(Long id) {

        return toResponse(findBranchById(id));
    }

    @Override
    public BranchResponse updateBranch(Long id, UpdateBranchRequest request) {

        BranchEntity branch = findBranchById(id);

        if (StringUtils.hasText(request.getName())) {
            if (!request.getName().equals(branch.getName())
                    && branchRepository.existsByName(request.getName())) {
                throw new RuntimeException("Branch name already exists");
            }
            branch.setName(request.getName());
        }

        if (StringUtils.hasText(request.getZraTpin())) {
            branch.setZraTpin(request.getZraTpin());
        }

        if (request.getPhone() != null) {
            branch.setPhone(request.getPhone());
        }

        if (request.getEmail() != null) {
            branch.setEmail(request.getEmail());
        }

        if (request.getAddress() != null) {
            branch.setAddress(request.getAddress());
        }

        if (request.getIsActive() != null) {
            branch.setIsActive(request.getIsActive());
        }

        return toResponse(branchRepository.save(branch));
    }

    @Override
    public void deleteBranch(Long id) {

        BranchEntity branch = findBranchById(id);
        branchRepository.delete(branch);
    }

    private BranchEntity findBranchById(Long id) {

        return branchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Branch not found"));
    }

    private void validateCreateRequest(CreateBranchRequest request) {

        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new RuntimeException("Branch name is required");
        }

        if (!StringUtils.hasText(request.getZraTpin())) {
            throw new RuntimeException("ZRA TPIN is required");
        }

        if (request.getZraTpin().length() != 10 || !request.getZraTpin().matches("\\d+")) {
            throw new RuntimeException("ZRA TPIN must be a 10-digit numeric value");
        }
    }

    private BranchResponse toResponse(BranchEntity branch) {

        return BranchResponse.builder()
                .id(branch.getId())
                .name(branch.getName())
                .zraTpin(branch.getZraTpin())
                .phone(branch.getPhone())
                .email(branch.getEmail())
                .address(branch.getAddress())
                .isActive(branch.getIsActive())
                .createdAt(branch.getCreatedAt())
                .build();
    }
}
