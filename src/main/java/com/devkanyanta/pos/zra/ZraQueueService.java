package com.devkanyanta.pos.zra;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devkanyanta.pos.merchant.MerchantProfileService;
import com.devkanyanta.pos.receipt.ReceiptEntity;
import com.devkanyanta.pos.sale.SaleEntity;
import com.devkanyanta.pos.sale.refund.SaleRefundEntity;
import com.devkanyanta.pos.zra.ZraSubmissionEntity.SubmissionStatus;
import com.devkanyanta.pos.zra.ZraSubmissionEntity.SubmissionType;

import lombok.RequiredArgsConstructor;

/**
 * Queues documents for transmission to the ZRA VSDC. Queueing is part of the business
 * transaction (outbox pattern); transmission happens asynchronously in
 * {@link ZraSubmissionProcessor}.
 */
@Service
@RequiredArgsConstructor
public class ZraQueueService {

    private final ZraSubmissionRepository repository;

    private final MerchantProfileService merchantProfileService;

    /** Queues the sales invoice for a fully paid sale (once per sale). */
    @Transactional
    public void queueSaleInvoice(SaleEntity sale, ReceiptEntity receipt) {

        if (!merchantProfileService.isFeatureEnabled(MerchantProfileService.FEATURE_ZRA)) {
            return;
        }

        if (repository.findFirstBySaleIdAndSubmissionTypeOrderByIdDesc(sale.getId(), SubmissionType.SALE_INVOICE).isPresent()) {
            return;
        }

        repository.save(ZraSubmissionEntity.builder()
                .sale(sale)
                .receipt(receipt)
                .submissionType(SubmissionType.SALE_INVOICE)
                .submissionStatus(SubmissionStatus.PENDING)
                .build());
    }

    /** Queues a credit note for a refund, if the original sale was reported to ZRA. */
    @Transactional
    public void queueCreditNote(SaleRefundEntity refund) {

        boolean invoiceQueued = repository
                .findFirstBySaleIdAndSubmissionTypeOrderByIdDesc(refund.getSale().getId(), SubmissionType.SALE_INVOICE)
                .isPresent();

        if (!invoiceQueued) {
            return;
        }

        repository.save(ZraSubmissionEntity.builder()
                .sale(refund.getSale())
                .refund(refund)
                .submissionType(SubmissionType.CREDIT_NOTE)
                .submissionStatus(SubmissionStatus.PENDING)
                .build());
    }
}
