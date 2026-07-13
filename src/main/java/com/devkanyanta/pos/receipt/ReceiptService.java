package com.devkanyanta.pos.receipt;

import com.devkanyanta.pos.receipt.dto.ReceiptResponse;

public interface ReceiptService {

    ReceiptResponse getReceiptById(Long id);

    ReceiptResponse getReceiptBySaleId(Long saleId);

    ReceiptResponse reprintReceipt(Long id);
}
