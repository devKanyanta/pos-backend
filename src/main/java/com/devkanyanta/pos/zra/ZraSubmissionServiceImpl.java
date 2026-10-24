package com.devkanyanta.pos.zra;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devkanyanta.pos.shared.exception.BadRequestException;
import com.devkanyanta.pos.shared.exception.NotFoundException;
import com.devkanyanta.pos.zra.ZraSubmissionEntity.SubmissionStatus;
import com.devkanyanta.pos.zra.dto.ZraSubmissionResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ZraSubmissionServiceImpl implements ZraSubmissionService {

    private final ZraSubmissionRepository zraSubmissionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ZraSubmissionResponse> getAllSubmissions() {

        return zraSubmissionRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(submission -> toResponse(submission, false))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ZraSubmissionResponse> getSubmissionsByStatus(String status) {

        SubmissionStatus parsed;
        try {
            parsed = SubmissionStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Status must be one of PENDING, RETRYING, SUCCESS, FAILED");
        }

        return zraSubmissionRepository.findBySubmissionStatusOrderByCreatedAtDesc(parsed)
                .stream()
                .map(submission -> toResponse(submission, false))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ZraSubmissionResponse getSubmissionById(Long id) {

        ZraSubmissionEntity submission = zraSubmissionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("ZRA submission not found"));

        return toResponse(submission, true);
    }

    @Override
    @Transactional
    public void resetForRetry(Long id) {

        ZraSubmissionEntity submission = zraSubmissionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("ZRA submission not found"));

        if (submission.getSubmissionStatus() == SubmissionStatus.SUCCESS) {
            throw new BadRequestException("Submission has already been accepted by ZRA");
        }

        submission.setSubmissionStatus(SubmissionStatus.PENDING);
        submission.setRetryCount(0);
        submission.setNextAttemptAt(LocalDateTime.now());
        zraSubmissionRepository.save(submission);
    }

    private ZraSubmissionResponse toResponse(ZraSubmissionEntity submission, boolean withPayloads) {

        return ZraSubmissionResponse.builder()
                .id(submission.getId())
                .saleId(submission.getSale() != null ? submission.getSale().getId() : null)
                .saleNumber(submission.getSale() != null ? submission.getSale().getSaleNumber() : null)
                .receiptId(submission.getReceipt() != null ? submission.getReceipt().getId() : null)
                .refundId(submission.getRefund() != null ? submission.getRefund().getId() : null)
                .refundNumber(submission.getRefund() != null ? submission.getRefund().getRefundNumber() : null)
                .productId(submission.getProduct() != null ? submission.getProduct().getId() : null)
                .invoiceNo(submission.getInvoiceNo())
                .submissionStatus(submission.getSubmissionStatus().name())
                .submissionType(submission.getSubmissionType() != null ? submission.getSubmissionType().name() : null)
                .retryCount(submission.getRetryCount())
                .errorCode(submission.getErrorCode())
                .errorMessage(submission.getErrorMessage())
                .lastAttemptAt(submission.getLastAttemptAt())
                .nextAttemptAt(submission.getNextAttemptAt())
                .completedAt(submission.getCompletedAt())
                .createdAt(submission.getCreatedAt())
                .requestPayload(withPayloads ? submission.getRequestPayload() : null)
                .responsePayload(withPayloads ? submission.getResponsePayload() : null)
                .build();
    }
}
