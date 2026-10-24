package com.devkanyanta.pos.sync;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.devkanyanta.pos.receipt.ReceiptRepository;
import com.devkanyanta.pos.sale.SaleServiceImpl;
import com.devkanyanta.pos.sale.SaleServiceImpl.DraftItem;
import com.devkanyanta.pos.sale.SaleServiceImpl.SaleCreation;
import com.devkanyanta.pos.sale.SaleServiceImpl.SaleDraft;
import com.devkanyanta.pos.shared.exception.BadRequestException;
import com.devkanyanta.pos.sync.dto.OfflineSaleRequest;
import com.devkanyanta.pos.sync.dto.SyncSaleResult;

import lombok.RequiredArgsConstructor;

/**
 * Records one offline sale in its own transaction, so a single bad sale never blocks the rest
 * of the batch and a failure leaves no partial data.
 */
@Component
@RequiredArgsConstructor
public class SyncSaleProcessor {

    private final SaleServiceImpl saleService;

    private final ReceiptRepository receiptRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncSaleResult process(OfflineSaleRequest request, String deviceId, String username) {

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BadRequestException("Sale must have at least one item");
        }

        List<DraftItem> items = new ArrayList<>();
        for (OfflineSaleRequest.Item item : request.getItems()) {
            if (item == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BadRequestException("Each item needs a positive quantity");
            }
            if (item.getProductId() == null && item.getProductName() == null) {
                throw new BadRequestException("Each item needs a productId or productName");
            }
            items.add(new DraftItem(item.getProductId(), item.getQuantity(), item.getDiscountAmount(),
                    item.getUnitPrice(), item.getProductName(), item.getVatRate(), item.getZraTaxType()));
        }

        SaleDraft draft = new SaleDraft(
                request.getClientSaleId(),
                deviceId,
                request.getClientReceiptNumber(),
                request.getCustomerId(),
                request.getNotes(),
                request.getDiscountAmount(),
                items,
                request.getPayments(),
                request.getSoldAt(),
                request.getExpectedTotal()
        );

        SaleCreation creation = saleService.createFromDraft(draft, username, true);

        var receipt = receiptRepository.findBySaleId(creation.sale().getId()).orElse(null);

        return SyncSaleResult.builder()
                .clientSaleId(request.getClientSaleId())
                .status(creation.duplicate() ? "DUPLICATE" : "CREATED")
                .saleId(creation.sale().getId())
                .saleNumber(creation.sale().getSaleNumber())
                .receiptNumber(receipt != null ? receipt.getReceiptNumber() : null)
                .totalAmount(creation.sale().getTotalAmount())
                .paymentStatus(creation.sale().getPaymentStatus().name())
                .conflicts(creation.conflicts())
                .build();
    }
}
