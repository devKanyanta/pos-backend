package com.devkanyanta.pos.sale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devkanyanta.pos.receipt.ReceiptEntity;
import com.devkanyanta.pos.receipt.ReceiptRepository;
import com.devkanyanta.pos.zra.ZraQueueService;

import lombok.RequiredArgsConstructor;

/**
 * Runs once a sale becomes fully paid: issues the receipt and queues the ZRA Smart Invoice.
 */
@Service
@RequiredArgsConstructor
public class SaleCompletionService {

    private final ReceiptRepository receiptRepository;

    private final SaleNumberGenerator saleNumberGenerator;

    private final ZraQueueService zraQueueService;

    @Transactional
    public ReceiptEntity onFullyPaid(SaleEntity sale) {

        ReceiptEntity receipt = receiptRepository.findBySaleId(sale.getId())
                .orElseGet(() -> receiptRepository.save(ReceiptEntity.builder()
                        .sale(sale)
                        .receiptNumber(saleNumberGenerator.generateReceiptNumber())
                        .build()));

        zraQueueService.queueSaleInvoice(sale, receipt);

        return receipt;
    }
}
