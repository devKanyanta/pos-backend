package com.devkanyanta.pos.zra;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.devkanyanta.pos.zra.dto.ZraSubmissionResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ZraSubmissionServiceImpl implements ZraSubmissionService {

    private final ZraSubmissionRepository zraSubmissionRepository;

    @Override
    public List<ZraSubmissionResponse> getAllSubmissions() {

        return zraSubmissionRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ZraSubmissionResponse getSubmissionById(Long id) {

        ZraSubmissionEntity submission = zraSubmissionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ZRA submission not found"));

        return toResponse(submission);
    }

    private ZraSubmissionResponse toResponse(ZraSubmissionEntity submission) {

        return ZraSubmissionResponse.builder()
                .id(submission.getId())
                .saleId(submission.getSale() != null ? submission.getSale().getId() : null)
                .saleNumber(submission.getSale() != null ? submission.getSale().getSaleNumber() : null)
                .receiptId(submission.getReceipt() != null ? submission.getReceipt().getId() : null)
                .submissionStatus(submission.getSubmissionStatus().name())
                .submissionType(submission.getSubmissionType() != null ? submission.getSubmissionType().name() : null)
                .retryCount(submission.getRetryCount())
                .lastAttemptAt(submission.getLastAttemptAt())
                .createdAt(submission.getCreatedAt())
                .build();
    }
}
