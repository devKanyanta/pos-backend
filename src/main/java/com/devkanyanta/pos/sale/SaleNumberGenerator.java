package com.devkanyanta.pos.sale;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SaleNumberGenerator {

    private final EntityManager entityManager;

    public String generateSaleNumber() {
        return generateNumber("SALE");
    }

    public String generateReceiptNumber() {
        return generateNumber("RCT");
    }

    public String generateRefundNumber() {
        return generateNumber("CRN");
    }

    public String generatePurchaseOrderNumber() {
        return generateNumber("PO");
    }

    public String generateGoodsReceiptNumber() {
        return generateNumber("GRN");
    }

    public String generateSupplierReturnNumber() {
        return generateNumber("SRN");
    }

    public String generateStockCountNumber() {
        return generateNumber("CNT");
    }

    private String generateNumber(String prefix) {

        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // Use a pessimistic lock to prevent duplicate numbers under concurrent load.
        // We query a dedicated counter row stored in a number_sequences table.
        NumberSequence sequence = entityManager.find(
                NumberSequence.class,
                prefix + "_" + datePart,
                LockModeType.PESSIMISTIC_WRITE
        );

        int nextSeq;
        if (sequence == null) {
            sequence = new NumberSequence();
            sequence.setId(prefix + "_" + datePart);
            nextSeq = 1;
            sequence.setCurrentValue(nextSeq);
            entityManager.persist(sequence);
        } else {
            nextSeq = sequence.getCurrentValue() + 1;
            sequence.setCurrentValue(nextSeq);
            entityManager.merge(sequence);
        }

        return String.format("%s-%s-%04d", prefix, datePart, nextSeq);
    }
}
