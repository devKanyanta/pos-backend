package com.devkanyanta.pos.sale;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.devkanyanta.pos.customer.CustomerEntity;
import com.devkanyanta.pos.customer.CustomerRepository;
import com.devkanyanta.pos.inventory.InventoryMovementEntity.MovementType;
import com.devkanyanta.pos.inventory.StockLedger;
import com.devkanyanta.pos.merchant.MerchantProfileService;
import com.devkanyanta.pos.payment.PaymentService;
import com.devkanyanta.pos.payment.dto.CreatePaymentRequest;
import com.devkanyanta.pos.product.ProductEntity;
import com.devkanyanta.pos.product.ProductRepository;
import com.devkanyanta.pos.sale.SaleEntity.PaymentStatus;
import com.devkanyanta.pos.sale.SaleEntity.SaleSource;
import com.devkanyanta.pos.sale.SaleEntity.SaleStatus;
import com.devkanyanta.pos.sale.dto.CreateSaleRequest;
import com.devkanyanta.pos.sale.dto.RefundRequest;
import com.devkanyanta.pos.sale.dto.SaleItemRequest;
import com.devkanyanta.pos.sale.dto.SaleResponse;
import com.devkanyanta.pos.sale.refund.SaleRefundService;
import com.devkanyanta.pos.shared.config.PosProperties;
import com.devkanyanta.pos.shared.exception.BadRequestException;
import com.devkanyanta.pos.shared.exception.NotFoundException;
import com.devkanyanta.pos.shared.util.Money;
import com.devkanyanta.pos.shared.util.Texts;
import com.devkanyanta.pos.zra.ZraTaxType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SaleServiceImpl implements SaleService {

    public static final String CONFLICT_NEGATIVE_STOCK = "NEGATIVE_STOCK";
    public static final String CONFLICT_PRICE_MISMATCH = "PRICE_MISMATCH";
    public static final String CONFLICT_TOTAL_MISMATCH = "TOTAL_MISMATCH";
    public static final String CONFLICT_UNKNOWN_PRODUCT = "UNKNOWN_PRODUCT";
    public static final String CONFLICT_INACTIVE_PRODUCT = "INACTIVE_PRODUCT";
    public static final String CONFLICT_UNKNOWN_CUSTOMER = "UNKNOWN_CUSTOMER";
    public static final String CONFLICT_ZRA_UNMAPPED = "ZRA_UNMAPPED_PRODUCT";
    public static final String CONFLICT_UNPAID = "UNPAID";

    private static final BigDecimal TOTAL_TOLERANCE = new BigDecimal("0.01");

    private final SaleRepository saleRepository;

    private final ProductRepository productRepository;

    private final CustomerRepository customerRepository;

    private final StockLedger stockLedger;

    private final SaleNumberGenerator saleNumberGenerator;

    private final PaymentService paymentService;

    private final SaleRefundService saleRefundService;

    private final SaleMapper saleMapper;

    private final MerchantProfileService merchantProfileService;

    private final PosProperties properties;

    // ---- Sale creation ------------------------------------------------------------------

    /** Input for {@link #createFromDraft}; built from an online request or an offline sync item. */
    public record DraftItem(
            Long productId,
            int quantity,
            BigDecimal lineDiscount,
            BigDecimal clientUnitPrice,
            String clientProductName,
            BigDecimal clientVatRate,
            String clientTaxType
    ) {
    }

    public record SaleDraft(
            String clientSaleId,
            String deviceId,
            String clientReceiptNumber,
            Long customerId,
            String notes,
            BigDecimal discountAmount,
            List<DraftItem> items,
            List<CreatePaymentRequest> payments,
            LocalDateTime soldAt,
            BigDecimal expectedTotal
    ) {
    }

    public record SaleCreation(SaleEntity sale, List<String> conflicts, boolean duplicate) {
    }

    @Override
    @Transactional
    public SaleResponse createSale(CreateSaleRequest request, String username) {

        if (request == null) {
            throw new BadRequestException("Sale request is required");
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BadRequestException("Sale must have at least one item");
        }

        List<DraftItem> items = new ArrayList<>();
        for (SaleItemRequest item : request.getItems()) {
            if (item == null || item.getProductId() == null) {
                throw new BadRequestException("Each sale item needs a productId");
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BadRequestException("Item quantity must be positive");
            }
            items.add(new DraftItem(item.getProductId(), item.getQuantity(), item.getDiscountAmount(),
                    null, null, null, null));
        }

        SaleDraft draft = new SaleDraft(
                request.getClientSaleId(),
                request.getDeviceId(),
                null,
                request.getCustomerId(),
                request.getNotes(),
                request.getDiscountAmount(),
                items,
                request.getPayments(),
                null,
                null
        );

        return saleMapper.toResponse(createFromDraft(draft, username, false).sale());
    }

    /**
     * Creates a sale with its items, stock movements and (optionally) payments in the current
     * transaction.
     *
     * <p>Online ({@code offline=false}) every rule is enforced and violations reject the sale.
     * For offline sync ({@code offline=true}) the sale has already happened at the till, so
     * problems that cannot be undone are recorded as conflicts for review instead:
     * <ul>
     *   <li>insufficient stock: the sale is accepted and stock may go negative (NEGATIVE_STOCK);</li>
     *   <li>the price charged on the device wins over the current price (PRICE_MISMATCH);</li>
     *   <li>deleted/inactive products are recorded from the device snapshot (UNKNOWN_PRODUCT / INACTIVE_PRODUCT);</li>
     *   <li>server-calculated totals win when the device total differs (TOTAL_MISMATCH).</li>
     * </ul>
     */
    @Transactional
    public SaleCreation createFromDraft(SaleDraft draft, String username, boolean offline) {

        String clientSaleId = Texts.clean(draft.clientSaleId());

        if (clientSaleId != null) {
            if (!clientSaleId.matches("[A-Za-z0-9._:\\-]{8,64}")) {
                throw new BadRequestException("clientSaleId must be 8-64 characters (letters, digits, . _ : -)");
            }

            var existing = saleRepository.findByClientSaleId(clientSaleId);
            if (existing.isPresent()) {
                return new SaleCreation(existing.get(), List.of(), true);
            }
        } else if (offline) {
            throw new BadRequestException("clientSaleId is required for offline sales");
        }

        if (draft.items() == null || draft.items().isEmpty()) {
            throw new BadRequestException("Sale must have at least one item");
        }

        String cashier = username != null ? username : "system";
        Set<String> conflicts = new LinkedHashSet<>();
        boolean zraActive = properties.getZra().isEnabled()
                && merchantProfileService.isFeatureEnabled(MerchantProfileService.FEATURE_ZRA);

        // Lock all products in id order to serialise concurrent stock updates without deadlocks.
        Map<Long, ProductEntity> products = new HashMap<>();
        Set<Long> productIds = new TreeSet<>();
        draft.items().stream().map(DraftItem::productId).filter(java.util.Objects::nonNull).forEach(productIds::add);
        for (Long productId : productIds) {
            productRepository.findByIdForUpdate(productId).ifPresent(p -> products.put(productId, p));
        }

        SaleEntity sale = new SaleEntity();
        sale.setSaleNumber(saleNumberGenerator.generateSaleNumber());
        sale.setNotes(Texts.clean(draft.notes()));
        sale.setCreatedBy(cashier);
        sale.setClientSaleId(clientSaleId);
        sale.setDeviceId(Texts.truncate(Texts.clean(draft.deviceId()), 64));
        sale.setClientReceiptNumber(Texts.truncate(Texts.clean(draft.clientReceiptNumber()), 64));
        sale.setSource(offline ? SaleSource.OFFLINE_SYNC : SaleSource.ONLINE);
        sale.setSoldAt(draft.soldAt() != null ? draft.soldAt() : LocalDateTime.now());
        sale.setItems(new ArrayList<>());
        sale.setPayments(new ArrayList<>());

        if (draft.customerId() != null) {
            CustomerEntity customer = customerRepository.findById(draft.customerId()).orElse(null);
            if (customer == null) {
                if (!offline) {
                    throw new NotFoundException("Customer not found");
                }
                conflicts.add(CONFLICT_UNKNOWN_CUSTOMER);
            }
            sale.setCustomer(customer);
        }

        // Resolve each line: price, VAT and ZRA snapshot.
        List<SaleCalculator.LineInput> lineInputs = new ArrayList<>();
        List<SaleItemEntity> items = new ArrayList<>();
        Map<Long, Integer> requestedByProduct = new HashMap<>();

        for (DraftItem draftItem : draft.items()) {

            if (draftItem.quantity() <= 0) {
                throw new BadRequestException("Item quantity must be positive");
            }

            ProductEntity product = draftItem.productId() != null ? products.get(draftItem.productId()) : null;
            SaleItemEntity item = new SaleItemEntity();
            item.setSale(sale);
            item.setQuantity(draftItem.quantity());

            BigDecimal unitPrice;
            BigDecimal vatRate;
            ZraTaxType taxType;

            if (product == null) {
                if (!offline) {
                    throw new NotFoundException("Product not found: " + draftItem.productId());
                }
                String name = Texts.clean(draftItem.clientProductName());
                if (name == null || draftItem.clientUnitPrice() == null) {
                    throw new BadRequestException("Unknown product " + draftItem.productId()
                            + ": productName and unitPrice are required to record it");
                }
                conflicts.add(CONFLICT_UNKNOWN_PRODUCT);
                taxType = StringUtils.hasText(draftItem.clientTaxType())
                        ? ZraTaxType.fromCode(draftItem.clientTaxType())
                        : ZraTaxType.defaultForRate(draftItem.clientVatRate());
                vatRate = taxType != null ? taxType.getRate()
                        : (draftItem.clientVatRate() != null ? draftItem.clientVatRate() : new BigDecimal("16.00"));
                unitPrice = draftItem.clientUnitPrice();
                item.setProductName(Texts.truncate(name, 255));
            } else {
                if (Boolean.FALSE.equals(product.getActive())) {
                    if (!offline) {
                        throw new BadRequestException("Product is not active: " + product.getName());
                    }
                    conflicts.add(CONFLICT_INACTIVE_PRODUCT);
                }

                if (zraActive && !product.isZraMapped()) {
                    if (!offline) {
                        throw new BadRequestException("Product '" + product.getName()
                                + "' is not mapped to ZRA (tax type, item class, item type and units are required)");
                    }
                    conflicts.add(CONFLICT_ZRA_UNMAPPED);
                }

                unitPrice = product.getPrice();
                if (offline && draftItem.clientUnitPrice() != null) {
                    if (draftItem.clientUnitPrice().compareTo(product.getPrice()) != 0) {
                        conflicts.add(CONFLICT_PRICE_MISMATCH);
                    }
                    unitPrice = draftItem.clientUnitPrice();
                }

                taxType = product.getZraTaxType();
                vatRate = product.getVatRate() != null ? product.getVatRate() : BigDecimal.ZERO;

                item.setProduct(product);
                item.setProductName(product.getName());
                item.setZraItemCode(product.getZraItemCode());
                item.setZraItemClassCode(product.getZraItemClassCode());
                item.setZraPackagingUnitCode(product.getZraPackagingUnitCode());
                item.setZraQuantityUnitCode(product.getZraQuantityUnitCode());

                if (Boolean.TRUE.equals(product.getTrackInventory())) {
                    requestedByProduct.merge(product.getId(), draftItem.quantity(), Integer::sum);
                }
            }

            item.setUnitPrice(Money.of(unitPrice));
            item.setVatRate(vatRate);
            item.setZraTaxType(taxType);

            lineInputs.add(new SaleCalculator.LineInput(item.getProductName(), unitPrice, draftItem.quantity(),
                    draftItem.lineDiscount(), vatRate));
            items.add(item);
        }

        // Stock availability (online sales must not oversell).
        for (Map.Entry<Long, Integer> entry : requestedByProduct.entrySet()) {
            ProductEntity product = products.get(entry.getKey());
            int available = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
            if (available < entry.getValue()) {
                if (!offline) {
                    throw new BadRequestException("Insufficient stock for product: " + product.getName()
                            + ". Available: " + available
                            + ", requested: " + entry.getValue());
                }
                conflicts.add(CONFLICT_NEGATIVE_STOCK);
            }
        }

        SaleCalculator.Result totals = SaleCalculator.calculate(lineInputs, draft.discountAmount());

        for (int i = 0; i < items.size(); i++) {
            SaleItemEntity item = items.get(i);
            SaleCalculator.LineResult line = totals.lines().get(i);
            item.setGrossAmount(line.gross());
            item.setDiscountAmount(line.lineDiscount());
            item.setLineTotal(line.lineTotal());
            item.setSaleDiscountShare(line.saleDiscountShare());
            item.setTaxableAmount(line.taxable());
            item.setVatAmount(line.vat());
            sale.getItems().add(item);
        }

        sale.setSubtotal(totals.subtotal());
        sale.setDiscountAmount(totals.saleDiscount());
        sale.setVatAmount(totals.vat());
        sale.setTotalAmount(totals.total());

        if (draft.expectedTotal() != null
                && draft.expectedTotal().subtract(totals.total()).abs().compareTo(TOTAL_TOLERANCE) > 0) {
            conflicts.add(CONFLICT_TOTAL_MISMATCH);
        }

        saleRepository.save(sale);

        for (Map.Entry<Long, Integer> entry : requestedByProduct.entrySet()) {
            stockLedger.record(products.get(entry.getKey()), MovementType.SALE, -entry.getValue(),
                    sale.getId(), "SALE", "Sale: " + sale.getSaleNumber(), cashier);
        }

        if (draft.payments() != null) {
            for (CreatePaymentRequest payment : draft.payments()) {
                PaymentService.PaymentOutcome outcome = paymentService.applyPayment(
                        sale, payment, cashier, offline ? sale.getSoldAt() : null, offline);
                conflicts.addAll(outcome.conflicts());
            }
        }

        if (offline && sale.getPaymentStatus() != PaymentStatus.PAID && totals.total().signum() > 0) {
            conflicts.add(CONFLICT_UNPAID);
        }

        if (!conflicts.isEmpty()) {
            sale.setNeedsReview(true);
            sale.setSyncConflicts(Texts.truncate(String.join(",", conflicts), 500));
            saleRepository.save(sale);
        }

        return new SaleCreation(sale, new ArrayList<>(conflicts), false);
    }

    // ---- Queries ------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<SaleResponse> getAllSales() {

        return saleRepository.findAll()
                .stream()
                .map(saleMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SaleResponse getSaleById(Long id) {

        return saleMapper.toResponse(findSaleById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public SaleResponse getSaleByNumber(String saleNumber) {

        SaleEntity sale = saleRepository.findBySaleNumber(saleNumber)
                .orElseThrow(() -> new NotFoundException("Sale not found: " + saleNumber));

        return saleMapper.toResponse(sale);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaleResponse> getSalesByDateRange(LocalDate from, LocalDate to) {

        if (from.isAfter(to)) {
            throw new BadRequestException("'from' must not be after 'to'");
        }

        return saleRepository.findSoldBetween(from.atStartOfDay(), to.plusDays(1).atStartOfDay())
                .stream()
                .map(saleMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaleResponse> getSalesNeedingReview() {

        return saleRepository.findByNeedsReviewTrueOrderBySoldAtDesc()
                .stream()
                .map(saleMapper::toResponse)
                .toList();
    }

    // ---- Void / refund / review -----------------------------------------------------------

    @Override
    @Transactional
    public SaleResponse voidSale(Long id, String username) {

        SaleEntity sale = saleRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Sale not found"));

        if (sale.getSaleStatus() != SaleStatus.ACTIVE) {
            throw new BadRequestException("Cannot void a sale that is " + sale.getSaleStatus());
        }

        if (sale.getPaymentStatus() == PaymentStatus.PAID || sale.getPaymentStatus() == PaymentStatus.PARTIALLY_PAID) {
            throw new BadRequestException("Cannot void a sale that has payments. Use refund instead.");
        }

        sale.setSaleStatus(SaleStatus.VOIDED);

        // Reverse stock changes, one movement per product.
        Map<Long, Integer> quantities = new HashMap<>();
        for (SaleItemEntity item : sale.getItems()) {
            if (item.getProduct() != null && Boolean.TRUE.equals(item.getProduct().getTrackInventory())) {
                quantities.merge(item.getProduct().getId(), item.getQuantity(), Integer::sum);
            }
        }

        for (Long productId : new TreeSet<>(quantities.keySet())) {
            ProductEntity product = productRepository.findByIdForUpdate(productId)
                    .orElseThrow(() -> new NotFoundException("Product not found: " + productId));
            stockLedger.record(product, MovementType.ADJUSTMENT, quantities.get(productId),
                    sale.getId(), "SALE_VOID", "Voided sale: " + sale.getSaleNumber(), username);
        }

        return saleMapper.toResponse(saleRepository.save(sale));
    }

    @Override
    @Transactional
    public SaleResponse refundSale(Long id, RefundRequest request, String username) {

        SaleEntity sale = saleRefundService.refund(id, request, username);
        return saleMapper.toResponse(sale);
    }

    @Override
    @Transactional
    public SaleResponse markReviewed(Long id, String username) {

        SaleEntity sale = findSaleById(id);

        if (!Boolean.TRUE.equals(sale.getNeedsReview())) {
            throw new BadRequestException("Sale does not need review");
        }

        sale.setNeedsReview(false);
        sale.setReviewedBy(username);
        sale.setReviewedAt(LocalDateTime.now());

        return saleMapper.toResponse(saleRepository.save(sale));
    }

    private SaleEntity findSaleById(Long id) {

        return saleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Sale not found"));
    }
}
