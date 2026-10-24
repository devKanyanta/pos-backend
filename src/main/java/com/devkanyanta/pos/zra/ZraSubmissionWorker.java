package com.devkanyanta.pos.zra;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.devkanyanta.pos.product.ProductEntity;
import com.devkanyanta.pos.product.ProductRepository;
import com.devkanyanta.pos.receipt.ReceiptEntity;
import com.devkanyanta.pos.receipt.ReceiptRepository;
import com.devkanyanta.pos.sale.SaleItemEntity;
import com.devkanyanta.pos.sale.refund.SaleRefundEntity;
import com.devkanyanta.pos.sale.refund.SaleRefundRepository;
import com.devkanyanta.pos.shared.config.PosProperties;
import com.devkanyanta.pos.shared.util.Texts;
import com.devkanyanta.pos.zra.ZraSubmissionEntity.SubmissionStatus;
import com.devkanyanta.pos.zra.ZraSubmissionEntity.SubmissionType;
import com.devkanyanta.pos.zra.vsdc.ZraPayloadBuilder;
import com.devkanyanta.pos.zra.vsdc.ZraVsdcClient;
import com.devkanyanta.pos.zra.vsdc.ZraVsdcException;
import com.devkanyanta.pos.zra.vsdc.ZraVsdcResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Transmits one queued submission to the VSDC in its own transaction and records the outcome.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ZraSubmissionWorker {

    static final String WAITING_FOR_DEVICE = "NOT_INITIALIZED";
    static final String WAITING_FOR_INVOICE = "WAITING_FOR_INVOICE";
    static final String UNMAPPED_ITEM = "UNMAPPED_ITEM";

    private final ZraSubmissionRepository submissionRepository;

    private final ZraDeviceRepository deviceRepository;

    private final ReceiptRepository receiptRepository;

    private final SaleRefundRepository refundRepository;

    private final ProductRepository productRepository;

    private final ZraVsdcClient client;

    private final ZraPayloadBuilder payloadBuilder;

    private final PosProperties properties;

    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SubmissionStatus process(Long submissionId) {

        ZraSubmissionEntity submission = submissionRepository.findByIdForUpdate(submissionId).orElse(null);

        if (submission == null
                || submission.getSubmissionStatus() == SubmissionStatus.SUCCESS
                || submission.getSubmissionStatus() == SubmissionStatus.FAILED) {
            return submission != null ? submission.getSubmissionStatus() : null;
        }

        LocalDateTime now = LocalDateTime.now(clock);

        ZraDeviceEntity device = deviceRepository.findFirstByActiveTrueOrderByIdDesc()
                .flatMap(d -> deviceRepository.findByIdForUpdate(d.getId()))
                .orElse(null);

        if (device == null) {
            postpone(submission, WAITING_FOR_DEVICE, "ZRA VSDC device has not been initialised", now);
            return submission.getSubmissionStatus();
        }

        try {
            switch (submission.getSubmissionType()) {
                case SALE_INVOICE -> sendInvoice(submission, device, now);
                case CREDIT_NOTE -> {
                    if (!sendCreditNote(submission, device, now)) {
                        return submission.getSubmissionStatus();
                    }
                }
                case ITEM_REGISTRATION -> registerItem(submission.getProduct(), device, submission, now);
            }

            submission.setSubmissionStatus(SubmissionStatus.SUCCESS);
            submission.setCompletedAt(now);
            submission.setErrorCode(null);
            submission.setErrorMessage(null);
            submission.setLastAttemptAt(now);
            submissionRepository.save(submission);
            return SubmissionStatus.SUCCESS;

        } catch (ZraVsdcException ex) {
            recordFailure(submission, ex.getCode(), ex.getMessage(), ex.getResponseBody(), now);
            return submission.getSubmissionStatus();
        } catch (UnmappedItemException ex) {
            submission.setLastAttemptAt(now);
            submission.setRetryCount(submission.getRetryCount() + 1);
            submission.setSubmissionStatus(SubmissionStatus.FAILED);
            submission.setErrorCode(UNMAPPED_ITEM);
            submission.setErrorMessage(Texts.truncate(ex.getMessage(), 1000));
            submissionRepository.save(submission);
            return SubmissionStatus.FAILED;
        }
    }

    private void sendInvoice(ZraSubmissionEntity submission, ZraDeviceEntity device, LocalDateTime now) {

        ReceiptEntity receipt = submission.getReceipt() != null
                ? submission.getReceipt()
                : receiptRepository.findBySaleId(submission.getSale().getId()).orElseThrow(
                        () -> new ZraVsdcException("NO_RECEIPT", "Sale has no receipt yet", null, null));

        ensureItemsRegistered(submission.getSale().getItems().stream().map(SaleItemEntity::getProduct).toList(),
                submission.getSale().getItems().stream().map(SaleItemEntity::getProductName).toList(),
                device, now);

        long invoiceNo = allocateInvoiceNo(submission, device);
        receipt.setZraInvoiceNo(invoiceNo);

        Map<String, Object> payload = payloadBuilder.saleInvoice(device, submission.getSale(), receipt, invoiceNo);
        String json = client.toJson(payload);
        submission.setRequestPayload(json);
        submission.setLastAttemptAt(now);

        ZraVsdcResponse response = client.post(ZraVsdcClient.PATH_SAVE_SALES, json);
        submission.setResponsePayload(response.rawBody());

        receipt.setZraReceiptNo(parseLong(response.dataText("rcptNo")));
        receipt.setZraInternalData(Texts.truncate(response.dataText("intrlData"), 200));
        receipt.setZraReceiptSignature(Texts.truncate(response.dataText("rcptSign"), 200));
        receipt.setZraReceiptDate(Texts.truncate(response.dataText("vsdcRcptPbctDate"), 20));
        receipt.setZraSdcId(Texts.truncate(firstNonNull(response.dataText("sdcId"), device.getSdcId()), 40));
        receipt.setZraMrcNo(Texts.truncate(firstNonNull(response.dataText("mrcNo"), device.getMrcNo()), 40));
        receipt.setZraQrCodeUrl(Texts.truncate(response.dataText("qrCodeUrl"), 500));
        receipt.setZraSignedAt(now);
        receiptRepository.save(receipt);
    }

    /** @return false when the credit note has to wait for its original invoice */
    private boolean sendCreditNote(ZraSubmissionEntity submission, ZraDeviceEntity device, LocalDateTime now) {

        SaleRefundEntity refund = submission.getRefund();

        ZraSubmissionEntity invoice = submissionRepository
                .findFirstBySaleIdAndSubmissionTypeOrderByIdDesc(refund.getSale().getId(), SubmissionType.SALE_INVOICE)
                .orElse(null);

        if (invoice == null || invoice.getSubmissionStatus() != SubmissionStatus.SUCCESS) {
            postpone(submission, WAITING_FOR_INVOICE, "Waiting for the original sales invoice to be accepted by ZRA", now);
            return false;
        }

        ReceiptEntity originalReceipt = receiptRepository.findBySaleId(refund.getSale().getId()).orElse(null);
        long originalInvoiceNo = invoice.getInvoiceNo() != null ? invoice.getInvoiceNo()
                : originalReceipt != null && originalReceipt.getZraInvoiceNo() != null ? originalReceipt.getZraInvoiceNo() : 0L;
        String originalSdcId = originalReceipt != null && originalReceipt.getZraSdcId() != null
                ? originalReceipt.getZraSdcId() : device.getSdcId();

        long invoiceNo = allocateInvoiceNo(submission, device);
        refund.setZraInvoiceNo(invoiceNo);

        Map<String, Object> payload = payloadBuilder.creditNote(device, refund, originalInvoiceNo, originalSdcId, invoiceNo);
        String json = client.toJson(payload);
        submission.setRequestPayload(json);
        submission.setLastAttemptAt(now);

        ZraVsdcResponse response = client.post(ZraVsdcClient.PATH_SAVE_SALES, json);
        submission.setResponsePayload(response.rawBody());

        refund.setZraReceiptNo(parseLong(response.dataText("rcptNo")));
        refund.setZraInternalData(Texts.truncate(response.dataText("intrlData"), 200));
        refund.setZraReceiptSignature(Texts.truncate(response.dataText("rcptSign"), 200));
        refund.setZraSignedAt(now);
        refundRepository.save(refund);
        return true;
    }

    private void ensureItemsRegistered(java.util.List<ProductEntity> products, java.util.List<String> names,
                                       ZraDeviceEntity device, LocalDateTime now) {

        Map<Long, ProductEntity> unique = new LinkedHashMap<>();
        for (int i = 0; i < products.size(); i++) {
            ProductEntity product = products.get(i);
            if (product == null) {
                throw new UnmappedItemException("Item '" + names.get(i)
                        + "' has no catalogue product and cannot be reported to ZRA");
            }
            unique.putIfAbsent(product.getId(), product);
        }

        for (ProductEntity product : unique.values()) {
            if (!Boolean.TRUE.equals(product.getZraRegistered())) {
                ZraSubmissionEntity log = submissionRepository.save(ZraSubmissionEntity.builder()
                        .product(product)
                        .submissionType(SubmissionType.ITEM_REGISTRATION)
                        .submissionStatus(SubmissionStatus.PENDING)
                        .build());
                try {
                    registerItem(product, device, log, now);
                    log.setSubmissionStatus(SubmissionStatus.SUCCESS);
                    log.setCompletedAt(now);
                } catch (ZraVsdcException ex) {
                    log.setSubmissionStatus(SubmissionStatus.FAILED);
                    log.setErrorCode(ex.getCode());
                    log.setErrorMessage(Texts.truncate(ex.getMessage(), 1000));
                    log.setResponsePayload(ex.getResponseBody());
                    submissionRepository.save(log);
                    throw ex;
                }
                submissionRepository.save(log);
            }
        }
    }

    /** Registers (or re-registers) a product with the VSDC. */
    void registerItem(ProductEntity product, ZraDeviceEntity device, ZraSubmissionEntity log, LocalDateTime now) {

        if (product == null) {
            throw new UnmappedItemException("Item registration has no product");
        }

        ProductEntity managed = productRepository.findById(product.getId()).orElse(product);

        if (!managed.isZraMapped()) {
            throw new UnmappedItemException("Product '" + managed.getName()
                    + "' is missing ZRA mapping (tax type, item class, item type, packaging and quantity units)");
        }

        if (managed.getZraItemCode() == null) {
            managed.setZraItemCode(payloadBuilder.generateItemCode(managed));
        }

        String json = client.toJson(payloadBuilder.itemRequest(device, managed));
        log.setRequestPayload(json);
        log.setLastAttemptAt(now);

        ZraVsdcResponse response = client.post(ZraVsdcClient.PATH_SAVE_ITEM, json);
        log.setResponsePayload(response.rawBody());

        managed.setZraRegistered(true);
        managed.setZraRegisteredAt(now);
        productRepository.save(managed);
    }

    private long allocateInvoiceNo(ZraSubmissionEntity submission, ZraDeviceEntity device) {

        if (submission.getInvoiceNo() != null) {
            return submission.getInvoiceNo();
        }

        long next = (device.getLastInvoiceNo() != null ? device.getLastInvoiceNo() : 0L) + 1;
        device.setLastInvoiceNo(next);
        deviceRepository.save(device);
        submission.setInvoiceNo(next);
        return next;
    }

    private void postpone(ZraSubmissionEntity submission, String code, String message, LocalDateTime now) {

        submission.setErrorCode(code);
        submission.setErrorMessage(message);
        submission.setNextAttemptAt(now.plusSeconds(properties.getZra().getRetryBaseDelaySeconds()));
        submissionRepository.save(submission);
    }

    private void recordFailure(ZraSubmissionEntity submission, String code, String message, String body, LocalDateTime now) {

        int attempts = submission.getRetryCount() + 1;
        submission.setRetryCount(attempts);
        submission.setLastAttemptAt(now);
        submission.setErrorCode(Texts.truncate(code, 40));
        submission.setErrorMessage(Texts.truncate(message, 1000));
        if (body != null) {
            submission.setResponsePayload(body);
        }

        if (attempts >= properties.getZra().getMaxAttempts()) {
            submission.setSubmissionStatus(SubmissionStatus.FAILED);
            log.warn("ZRA submission {} failed permanently after {} attempts: {} {}", submission.getId(), attempts, code, message);
        } else {
            long base = properties.getZra().getRetryBaseDelaySeconds();
            long delay = Math.min(properties.getZra().getRetryMaxDelaySeconds(), base * (1L << Math.min(attempts - 1, 20)));
            submission.setSubmissionStatus(SubmissionStatus.RETRYING);
            submission.setNextAttemptAt(now.plusSeconds(delay));
            log.info("ZRA submission {} attempt {} failed ({}), retrying in {}s", submission.getId(), attempts, code, delay);
        }

        submissionRepository.save(submission);
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    /** The document cannot be built until a product is mapped; needs manual action. */
    static class UnmappedItemException extends RuntimeException {
        UnmappedItemException(String message) {
            super(message);
        }
    }
}
