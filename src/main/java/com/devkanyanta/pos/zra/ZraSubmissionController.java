package com.devkanyanta.pos.zra;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.devkanyanta.pos.auth.security.PermissionConstants;
import com.devkanyanta.pos.zra.dto.ZraSubmissionResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/zra-submissions")
@RequiredArgsConstructor
public class ZraSubmissionController {

    private final ZraSubmissionService zraSubmissionService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.ZRA_VIEW + "')")
    public List<ZraSubmissionResponse> getAllSubmissions() {
        return zraSubmissionService.getAllSubmissions();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ZRA_VIEW + "')")
    public ZraSubmissionResponse getSubmissionById(
            @PathVariable Long id
    ) {
        return zraSubmissionService.getSubmissionById(id);
    }
}
