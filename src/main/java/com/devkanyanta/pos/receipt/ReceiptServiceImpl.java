package com.devkanyanta.pos.receipt;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devkanyanta.pos.receipt.dto.ReceiptResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReceiptServiceImpl implements ReceiptService {

    private final ReceiptRepository receiptRepository;

    @Override
    public ReceiptResponse getReceiptById(Long id) {

        ReceiptEntity receipt = receiptRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Receipt not found"));

        return toResponse(receipt);
    }

    @Override
    public ReceiptResponse getReceiptBySaleId(Long saleId) {

        ReceiptEntity receipt = receiptRepository.findBySaleId(saleId)
                .orElseThrow(() -> new RuntimeException("Receipt not found for sale: " + saleId));

        return toResponse(receipt);
    }

    @Override
    @Transactional
    public ReceiptResponse reprintReceipt(Long id) {

        ReceiptEntity receipt = receiptRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Receipt not found"));

        receipt.setPrintedCount(receipt.getPrintedCount() + 1);
        receiptRepository.save(receipt);

        return toResponse(receipt);
    }

    private ReceiptResponse toResponse(ReceiptEntity receipt) {

        return ReceiptResponse.builder()
                .id(receipt.getId())
                .saleId(receipt.getSale().getId())
                .saleNumber(receipt.getSale().getSaleNumber())
                .receiptNumber(receipt.getReceiptNumber())
                .printedCount(receipt.getPrintedCount())
                .createdAt(receipt.getCreatedAt())
                .build();
    }
}
