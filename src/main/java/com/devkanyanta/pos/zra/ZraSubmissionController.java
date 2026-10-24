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

    private final ZraService zraService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.ZRA_VIEW + "')")
    public List<ZraSubmissionResponse> getAllSubmissions(
            @RequestParam(value = "status", required = false) String status
    ) {
        return status != null
                ? zraSubmissionService.getSubmissionsByStatus(status)
                : zraSubmissionService.getAllSubmissions();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ZRA_VIEW + "')")
    public ZraSubmissionResponse getSubmissionById(
            @PathVariable Long id
    ) {
        return zraSubmissionService.getSubmissionById(id);
    }

    /** Resets a failed submission and transmits it immediately. */
    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ZRA_MANAGE + "')")
    public ZraSubmissionResponse retry(@PathVariable Long id) {
        return zraService.retry(id);
    }
}
