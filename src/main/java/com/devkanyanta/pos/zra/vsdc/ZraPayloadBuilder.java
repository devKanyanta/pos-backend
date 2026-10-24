package com.devkanyanta.pos.zra.vsdc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.devkanyanta.pos.customer.CustomerEntity;
import com.devkanyanta.pos.payment.PaymentEntity;
import com.devkanyanta.pos.product.ProductEntity;
import com.devkanyanta.pos.receipt.ReceiptEntity;
import com.devkanyanta.pos.sale.SaleEntity;
import com.devkanyanta.pos.sale.SaleItemEntity;
import com.devkanyanta.pos.sale.refund.SaleRefundEntity;
import com.devkanyanta.pos.sale.refund.SaleRefundItemEntity;
import com.devkanyanta.pos.shared.config.PosProperties;
import com.devkanyanta.pos.shared.util.Money;
import com.devkanyanta.pos.zra.ZraDeviceEntity;
import com.devkanyanta.pos.zra.ZraTaxType;

import lombok.RequiredArgsConstructor;

/**
 * Builds VSDC request bodies (field names follow the ZRA Smart Invoice VSDC API).
 *
 * <p>Amount conventions: the POS stores VAT-exclusive prices. On the invoice
 * {@code prc}/{@code splyAmt} are VAT inclusive, {@code vatTaxblAmt} is the VAT-exclusive taxable
 * value, {@code vatAmt} the VAT and {@code totAmt} the line total including VAT. Header buckets
 * {@code taxblAmtX}/{@code taxAmtX} are the VAT-exclusive taxable value and VAT per category.
 */
@Component
@RequiredArgsConstructor
public class ZraPayloadBuilder {

    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final PosProperties properties;

    public Map<String, Object> initRequest(String tpin, String bhfId, String deviceSerialNo) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tpin", tpin);
        body.put("bhfId", bhfId);
        body.put("dvcSrlNo", deviceSerialNo);
        return body;
    }

    public Map<String, Object> codesRequest(ZraDeviceEntity device, String lastRequestDate) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tpin", device.getTpin());
        body.put("bhfId", device.getBhfId());
        body.put("lastReqDt", lastRequestDate);
        return body;
    }

    /** Generated item code: origin(2) + item type(1) + packaging unit + quantity unit + 7-digit id. */
    public String generateItemCode(ProductEntity product) {

        return product.getZraOriginCountryCode()
                + product.getZraItemTypeCode()
                + product.getZraPackagingUnitCode()
                + product.getZraQuantityUnitCode()
                + String.format("%07d", product.getId());
    }

    public Map<String, Object> itemRequest(ZraDeviceEntity device, ProductEntity product) {

        String registrant = properties.getZra().getRegistrantId();
        BigDecimal rate = product.getZraTaxType() != null ? product.getZraTaxType().getRate() : Money.nz(product.getVatRate());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tpin", device.getTpin());
        body.put("bhfId", device.getBhfId());
        body.put("itemCd", product.getZraItemCode());
        body.put("itemClsCd", product.getZraItemClassCode());
        body.put("itemTyCd", product.getZraItemTypeCode());
        body.put("itemNm", product.getName());
        body.put("itemStdNm", null);
        body.put("orgnNatCd", product.getZraOriginCountryCode());
        body.put("pkgUnitCd", product.getZraPackagingUnitCode());
        body.put("qtyUnitCd", product.getZraQuantityUnitCode());
        body.put("vatCatCd", product.getZraTaxType() != null ? product.getZraTaxType().getCode() : null);
        body.put("iplCatCd", null);
        body.put("tlCatCd", null);
        body.put("exciseTxCatCd", null);
        body.put("btchNo", null);
        body.put("bcd", product.getBarcode());
        body.put("dftPrc", inclusive(product.getPrice(), rate));
        body.put("manufacturerTpin", null);
        body.put("manufactureritemCd", null);
        body.put("rrp", inclusive(product.getPrice(), rate));
        body.put("svcChargeYn", "N");
        body.put("rentalYn", "N");
        body.put("addInfo", null);
        body.put("sftyQty", product.getReorderLevel());
        body.put("isrcAplcbYn", "N");
        body.put("useYn", Boolean.FALSE.equals(product.getActive()) ? "N" : "Y");
        body.put("regrNm", registrant);
        body.put("regrId", registrant);
        body.put("modrNm", registrant);
        body.put("modrId", registrant);
        return body;
    }

    /** Sales invoice (rcptTyCd = S) for a fully paid sale. */
    public Map<String, Object> saleInvoice(ZraDeviceEntity device, SaleEntity sale, ReceiptEntity receipt, long invoiceNo) {

        List<Line> lines = new ArrayList<>();
        for (SaleItemEntity item : sale.getItems()) {
            lines.add(new Line(item, item.getQuantity(), Money.nz(item.getTaxableAmount()), Money.nz(item.getVatAmount())));
        }

        Map<String, Object> body = header(device, sale, invoiceNo, lines);
        body.put("orgInvcNo", 0);
        body.put("cisInvcNo", receipt.getReceiptNumber());
        body.put("rcptTyCd", "S");
        body.put("rfdDt", null);
        body.put("rfdRsnCd", null);
        body.put("cfmDt", DATE_TIME.format(sale.getSoldAt() != null ? sale.getSoldAt() : LocalDateTime.now()));
        return body;
    }

    /** Credit note (rcptTyCd = R) for a refund; references the original invoice number. */
    public Map<String, Object> creditNote(
            ZraDeviceEntity device,
            SaleRefundEntity refund,
            long originalInvoiceNo,
            String originalSdcId,
            long invoiceNo
    ) {
        List<Line> lines = new ArrayList<>();
        for (SaleRefundItemEntity item : refund.getItems()) {
            lines.add(new Line(item.getSaleItem(), item.getQuantity(), item.getTaxableAmount(), item.getVatAmount()));
        }

        Map<String, Object> body = header(device, refund.getSale(), invoiceNo, lines);
        body.put("orgSdcId", originalSdcId);
        body.put("orgInvcNo", originalInvoiceNo);
        body.put("cisInvcNo", refund.getRefundNumber());
        body.put("rcptTyCd", "R");
        body.put("rfdDt", DATE_TIME.format(refund.getCreatedAt()));
        body.put("rfdRsnCd", refund.getZraReasonCode());
        body.put("cfmDt", DATE_TIME.format(refund.getCreatedAt()));
        body.put("salesDt", DATE.format(refund.getCreatedAt()));
        if (refund.getReason() != null) {
            body.put("remark", refund.getReason());
        }
        return body;
    }

    private Map<String, Object> header(ZraDeviceEntity device, SaleEntity sale, long invoiceNo, List<Line> lines) {

        String registrant = sale.getCreatedBy();
        CustomerEntity customer = sale.getCustomer();

        Map<ZraTaxType, BigDecimal> taxable = new EnumMap<>(ZraTaxType.class);
        Map<ZraTaxType, BigDecimal> tax = new EnumMap<>(ZraTaxType.class);
        for (ZraTaxType type : ZraTaxType.values()) {
            taxable.put(type, Money.ZERO);
            tax.put(type, Money.ZERO);
        }

        List<Map<String, Object>> itemList = new ArrayList<>();
        BigDecimal totalTaxable = Money.ZERO;
        BigDecimal totalTax = Money.ZERO;
        int seq = 1;

        for (Line line : lines) {
            SaleItemEntity item = line.item();
            ZraTaxType type = item.getZraTaxType() != null ? item.getZraTaxType() : ZraTaxType.A;

            taxable.merge(type, line.taxable(), BigDecimal::add);
            tax.merge(type, line.vat(), BigDecimal::add);
            totalTaxable = totalTaxable.add(line.taxable());
            totalTax = totalTax.add(line.vat());

            itemList.add(itemLine(seq++, item, line, type));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tpin", device.getTpin());
        body.put("bhfId", device.getBhfId());
        body.put("orgSdcId", null);
        body.put("orgInvcNo", 0);
        body.put("cisInvcNo", null);
        body.put("invcNo", invoiceNo);
        body.put("custTpin", customer != null ? customer.getTpin() : null);
        body.put("custNm", customer != null ? customerName(customer) : null);
        body.put("salesTyCd", "N");
        body.put("rcptTyCd", "S");
        body.put("pmtTyCd", paymentTypeCode(sale));
        body.put("salesSttsCd", "02");
        body.put("cfmDt", null);
        body.put("salesDt", DATE.format(sale.getSoldAt() != null ? sale.getSoldAt() : LocalDateTime.now()));
        body.put("stockRlsDt", null);
        body.put("cnclReqDt", null);
        body.put("cnclDt", null);
        body.put("rfdDt", null);
        body.put("rfdRsnCd", null);
        body.put("totItemCnt", itemList.size());

        for (ZraTaxType type : ZraTaxType.values()) {
            body.put("taxblAmt" + type.getPayloadSuffix(), taxable.get(type));
        }
        for (ZraTaxType type : ZraTaxType.values()) {
            body.put("taxRt" + type.getPayloadSuffix(), type.getRate());
        }
        for (ZraTaxType type : ZraTaxType.values()) {
            body.put("taxAmt" + type.getPayloadSuffix(), tax.get(type));
        }

        body.put("totTaxblAmt", totalTaxable);
        body.put("totTaxAmt", totalTax);
        body.put("totAmt", totalTaxable.add(totalTax));
        body.put("prchrAcptcYn", "N");
        body.put("remark", sale.getNotes());
        body.put("regrId", registrant);
        body.put("regrNm", registrant);
        body.put("modrId", registrant);
        body.put("modrNm", registrant);
        body.put("saleCtyCd", "1");
        body.put("lpoNumber", null);
        body.put("currencyTyCd", "ZMW");
        body.put("exchangeRt", "1");
        body.put("destnCountryCd", "");
        body.put("dbtRsnCd", "");
        body.put("invcAdjustReason", "");
        body.put("itemList", itemList);
        return body;
    }

    private Map<String, Object> itemLine(int seq, SaleItemEntity item, Line line, ZraTaxType type) {

        BigDecimal rate = item.getVatRate() != null ? item.getVatRate() : type.getRate();
        BigDecimal unitInclusive = inclusive(item.getUnitPrice(), rate);
        BigDecimal supply = Money.of(unitInclusive.multiply(BigDecimal.valueOf(line.quantity())));
        BigDecimal total = line.taxable().add(line.vat());
        BigDecimal discount = supply.subtract(total).max(Money.ZERO);
        BigDecimal discountRate = supply.signum() > 0
                ? discount.multiply(HUNDRED).divide(supply, 2, RoundingMode.HALF_UP)
                : Money.ZERO;

        ProductEntity product = item.getProduct();

        Map<String, Object> line1 = new LinkedHashMap<>();
        line1.put("itemSeq", seq);
        line1.put("itemCd", item.getZraItemCode() != null ? item.getZraItemCode()
                : product != null ? product.getZraItemCode() : null);
        line1.put("itemClsCd", item.getZraItemClassCode() != null ? item.getZraItemClassCode()
                : product != null ? product.getZraItemClassCode() : null);
        line1.put("itemNm", item.getProductName());
        line1.put("bcd", product != null ? product.getBarcode() : null);
        line1.put("pkgUnitCd", item.getZraPackagingUnitCode() != null ? item.getZraPackagingUnitCode()
                : properties.getZra().getDefaultPackagingUnitCode());
        line1.put("pkg", line.quantity());
        line1.put("qtyUnitCd", item.getZraQuantityUnitCode() != null ? item.getZraQuantityUnitCode()
                : properties.getZra().getDefaultQuantityUnitCode());
        line1.put("qty", line.quantity());
        line1.put("prc", unitInclusive);
        line1.put("splyAmt", supply);
        line1.put("dcRt", discountRate);
        line1.put("dcAmt", discount);
        line1.put("isrccCd", null);
        line1.put("isrccNm", null);
        line1.put("isrcRt", null);
        line1.put("isrcAmt", null);
        line1.put("vatCatCd", type.getCode());
        line1.put("exciseTxCatCd", null);
        line1.put("vatTaxblAmt", line.taxable());
        line1.put("exciseTaxblAmt", Money.ZERO);
        line1.put("vatAmt", line.vat());
        line1.put("exciseTxAmt", Money.ZERO);
        line1.put("totAmt", total);
        return line1;
    }

    /** pmtTyCd of the tender that covered most of the sale. */
    private String paymentTypeCode(SaleEntity sale) {

        return sale.getPayments().stream()
                .filter(p -> p.getPaymentStatus() != PaymentEntity.PaymentStatus.FAILED)
                .max(java.util.Comparator.comparing(PaymentEntity::getAmount))
                .map(p -> p.getPaymentMethod().getZraPaymentTypeCode())
                .filter(code -> code != null && !code.isBlank())
                .orElse("01");
    }

    private static BigDecimal inclusive(BigDecimal exclusive, BigDecimal ratePercent) {

        if (exclusive == null) {
            return Money.ZERO;
        }
        return Money.of(exclusive.add(Money.percentOf(exclusive, Money.nz(ratePercent))));
    }

    private static String customerName(CustomerEntity customer) {

        String name = ((customer.getFirstName() != null ? customer.getFirstName() + " " : "")
                + (customer.getLastName() != null ? customer.getLastName() : "")).trim();
        return name.isEmpty() ? null : name;
    }

    private record Line(SaleItemEntity item, int quantity, BigDecimal taxable, BigDecimal vat) {
    }
}
