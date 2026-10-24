package com.devkanyanta.pos.receipt;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devkanyanta.pos.receipt.dto.ReceiptResponse;
import com.devkanyanta.pos.shared.exception.NotFoundException;
import com.devkanyanta.pos.zra.ZraSubmissionEntity.SubmissionType;
import com.devkanyanta.pos.zra.ZraSubmissionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReceiptServiceImpl implements ReceiptService {

    private final ReceiptRepository receiptRepository;

    private final ZraSubmissionRepository zraSubmissionRepository;

    @Override
    @Transactional(readOnly = true)
    public ReceiptResponse getReceiptById(Long id) {

        ReceiptEntity receipt = receiptRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Receipt not found"));

        return toResponse(receipt);
    }

    @Override
    @Transactional(readOnly = true)
    public ReceiptResponse getReceiptBySaleId(Long saleId) {

        ReceiptEntity receipt = receiptRepository.findBySaleId(saleId)
                .orElseThrow(() -> new NotFoundException("Receipt not found for sale: " + saleId));

        return toResponse(receipt);
    }

    @Override
    @Transactional
    public ReceiptResponse reprintReceipt(Long id) {

        ReceiptEntity receipt = receiptRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Receipt not found"));

        receipt.setPrintedCount(receipt.getPrintedCount() + 1);
        receiptRepository.save(receipt);

        return toResponse(receipt);
    }

    private ReceiptResponse toResponse(ReceiptEntity receipt) {

        String zraStatus = zraSubmissionRepository
                .findFirstBySaleIdAndSubmissionTypeOrderByIdDesc(receipt.getSale().getId(), SubmissionType.SALE_INVOICE)
                .map(submission -> submission.getSubmissionStatus().name())
                .orElse(null);

        return ReceiptResponse.builder()
                .id(receipt.getId())
                .saleId(receipt.getSale().getId())
                .saleNumber(receipt.getSale().getSaleNumber())
                .receiptNumber(receipt.getReceiptNumber())
                .printedCount(receipt.getPrintedCount())
                .createdAt(receipt.getCreatedAt())
                .totalAmount(receipt.getSale().getTotalAmount())
                .vatAmount(receipt.getSale().getVatAmount())
                .zraStatus(zraStatus)
                .zraVerified(receipt.isZraSigned())
                .zraInvoiceNo(receipt.getZraInvoiceNo())
                .zraReceiptNo(receipt.getZraReceiptNo())
                .zraInternalData(receipt.getZraInternalData())
                .zraReceiptSignature(receipt.getZraReceiptSignature())
                .zraSdcId(receipt.getZraSdcId())
                .zraMrcNo(receipt.getZraMrcNo())
                .zraQrCodeUrl(receipt.getZraQrCodeUrl())
                .zraReceiptDate(receipt.getZraReceiptDate())
                .build();
    }
}
