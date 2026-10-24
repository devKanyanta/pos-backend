package com.devkanyanta.pos.stockcount;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devkanyanta.pos.inventory.InventoryMovementEntity.MovementType;
import com.devkanyanta.pos.inventory.StockLedger;
import com.devkanyanta.pos.product.ProductBarcodeLookup;
import com.devkanyanta.pos.product.ProductEntity;
import com.devkanyanta.pos.product.ProductRepository;
import com.devkanyanta.pos.sale.SaleNumberGenerator;
import com.devkanyanta.pos.shared.exception.BadRequestException;
import com.devkanyanta.pos.shared.exception.ConflictException;
import com.devkanyanta.pos.shared.exception.NotFoundException;
import com.devkanyanta.pos.shared.security.CurrentUser;
import com.devkanyanta.pos.shared.util.Texts;
import com.devkanyanta.pos.shared.web.PageResponse;

import lombok.RequiredArgsConstructor;

/**
 * Physical stock counts and discrepancy reconciliation.
 *
 * <p>Each line remembers the system quantity when it was added (the <i>expected</i> quantity). Committing books
 * {@code counted - expected} as a COUNT movement valued at the average cost, so sales made while the count
 * was open are preserved instead of being overwritten. Uncounted lines are ignored. A committed count is final
 * (committing twice is refused), and a count that would drive stock negative is rejected as a whole.
 */
@Service
@RequiredArgsConstructor
public class StockCountService {

    public record CountRequest(String notes, Long categoryId, Boolean allTrackedProducts) {
    }

    public record CountLineRequest(Long productId, String barcode, Integer countedQuantity) {
    }

    public record CountLinesRequest(List<CountLineRequest> lines) {
    }

    public record CountLineResponse(Long id, Long productId, String productName, String sku, String barcode,
                                    int expectedQuantity, Integer countedQuantity, Integer variance,
                                    BigDecimal unitCost, BigDecimal varianceValue) {
    }

    public record CountResponse(Long id, String countNumber, String status, String notes, String createdBy,
                                LocalDateTime createdAt, String committedBy, LocalDateTime committedAt,
                                int linesCounted, int linesUncounted, int totalOverage, int totalShortage,
                                BigDecimal netVarianceValue, List<CountLineResponse> lines) {
    }

    private static final Map<String, String> SORTS = Map.of("createdAt", "createdAt", "countNumber", "countNumber");

    private final StockCountRepository stockCountRepository;

    private final ProductRepository productRepository;

    private final ProductBarcodeLookup barcodeLookup;

    private final StockLedger stockLedger;

    private final SaleNumberGenerator numberGenerator;

    @Transactional
    public CountResponse create(CountRequest request) {

        StockCountEntity count = StockCountEntity.builder()
                .countNumber(numberGenerator.generateStockCountNumber())
                .notes(request != null ? Texts.truncate(Texts.clean(request.notes()), 1000) : null)
                .createdBy(CurrentUser.username())
                .build();

        if (request != null && (request.categoryId() != null || Boolean.TRUE.equals(request.allTrackedProducts()))) {
            productRepository.findAll().stream()
                    .filter(p -> Boolean.TRUE.equals(p.getTrackInventory()) && !Boolean.FALSE.equals(p.getActive()))
                    .filter(p -> request.categoryId() == null
                            || (p.getCategory() != null && p.getCategory().getId().equals(request.categoryId())))
                    .sorted(Comparator.comparing(ProductEntity::getName))
                    .forEach(p -> count.getLines().add(newLine(count, p)));
        }
        return toResponse(stockCountRepository.save(count));
    }

    /** Adds products to the count or changes their counted quantity. */
    @Transactional
    public CountResponse recordCounts(Long id, CountLinesRequest request) {

        StockCountEntity count = stockCountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Stock count not found"));
        requireOpen(count);

        if (request == null || request.lines() == null || request.lines().isEmpty()) {
            throw new BadRequestException("At least one line is required");
        }

        for (CountLineRequest line : request.lines()) {
            if (line == null || line.countedQuantity() == null || line.countedQuantity() < 0) {
                throw new BadRequestException("countedQuantity must be zero or greater");
            }
            ProductEntity product = resolveProduct(line);
            if (!Boolean.TRUE.equals(product.getTrackInventory())) {
                throw new BadRequestException("Stock is not tracked for product: " + product.getName());
            }
            StockCountLineEntity existing = count.getLines().stream()
                    .filter(l -> l.getProduct().getId().equals(product.getId())).findFirst().orElse(null);
            if (existing == null) {
                existing = newLine(count, product);
                count.getLines().add(existing);
            }
            existing.setCountedQuantity(line.countedQuantity());
        }
        return toResponse(stockCountRepository.save(count));
    }

    @Transactional
    public CountResponse commit(Long id) {

        StockCountEntity count = stockCountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Stock count not found"));
        requireOpen(count);

        List<StockCountLineEntity> counted = count.getLines().stream()
                .filter(l -> l.getCountedQuantity() != null)
                .sorted(Comparator.comparing(l -> l.getProduct().getId()))
                .toList();
        if (counted.isEmpty()) {
            throw new BadRequestException("Nothing has been counted yet");
        }

        String user = CurrentUser.username();
        for (StockCountLineEntity line : counted) {
            ProductEntity product = productRepository.findByIdForUpdate(line.getProduct().getId())
                    .orElseThrow(() -> new NotFoundException("Product not found"));
            int variance = line.getCountedQuantity() - line.getExpectedQuantity();
            line.setVariance(variance);
            if (variance != 0) {
                var movement = stockLedger.record(StockLedger.Change.builder()
                        .product(product).type(MovementType.COUNT).quantity(variance)
                        .referenceId(count.getId()).referenceType("STOCK_COUNT")
                        .reason("COUNT_VARIANCE")
                        .notes("Stock count " + count.getCountNumber() + ": counted " + line.getCountedQuantity()
                                + ", expected " + line.getExpectedQuantity())
                        .performedBy(user)
                        .build());
                line.setUnitCost(movement.getUnitCost());
            } else {
                line.setUnitCost(product.getAverageCost());
            }
        }
        count.setStatus(StockCountEntity.Status.COMMITTED);
        count.setCommittedBy(user);
        count.setCommittedAt(LocalDateTime.now());
        return toResponse(stockCountRepository.save(count));
    }

    @Transactional
    public CountResponse cancel(Long id) {
        StockCountEntity count = stockCountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Stock count not found"));
        requireOpen(count);
        count.setStatus(StockCountEntity.Status.CANCELLED);
        return toResponse(stockCountRepository.save(count));
    }

    @Transactional(readOnly = true)
    public CountResponse get(Long id) {
        return toResponse(stockCountRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Stock count not found")));
    }

    @Transactional(readOnly = true)
    public PageResponse<CountResponse> search(String status, Integer page, Integer size, String sort) {
        StockCountEntity.Status parsed = null;
        if (Texts.clean(status) != null) {
            try {
                parsed = StockCountEntity.Status.valueOf(status.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Unknown status '" + status + "'");
            }
        }
        StockCountEntity.Status wanted = parsed;
        Specification<StockCountEntity> spec = (root, query, cb) ->
                wanted == null ? cb.conjunction() : cb.equal(root.get("status"), wanted);
        return PageResponse.of(
                stockCountRepository.findAll(spec,
                        PageResponse.pageable(page, size, sort, SORTS, Sort.by(Sort.Direction.DESC, "id"))),
                this::toResponse);
    }

    private void requireOpen(StockCountEntity count) {
        if (count.getStatus() != StockCountEntity.Status.OPEN) {
            throw new ConflictException("Stock count " + count.getCountNumber() + " is already " + count.getStatus());
        }
    }

    private StockCountLineEntity newLine(StockCountEntity count, ProductEntity product) {
        return StockCountLineEntity.builder()
                .stockCount(count).product(product)
                .expectedQuantity(product.getStockQuantity() != null ? product.getStockQuantity() : 0)
                .build();
    }

    private ProductEntity resolveProduct(CountLineRequest line) {
        if (line.productId() != null) {
            return productRepository.findById(line.productId())
                    .orElseThrow(() -> new NotFoundException("Product not found: " + line.productId()));
        }
        if (Texts.clean(line.barcode()) != null) {
            return barcodeLookup.find(line.barcode())
                    .orElseThrow(() -> new NotFoundException("No product found for barcode " + line.barcode().trim()));
        }
        throw new BadRequestException("Each line needs a productId or barcode");
    }

    private CountResponse toResponse(StockCountEntity c) {
        List<CountLineResponse> lines = new ArrayList<>();
        int counted = 0;
        int uncounted = 0;
        int over = 0;
        int short_ = 0;
        BigDecimal net = BigDecimal.ZERO;
        for (StockCountLineEntity l : c.getLines()) {
            if (l.getCountedQuantity() == null) {
                uncounted++;
            } else {
                counted++;
            }
            if (l.getVariance() != null) {
                if (l.getVariance() > 0) {
                    over += l.getVariance();
                } else {
                    short_ += -l.getVariance();
                }
                if (l.getVarianceValue() != null) {
                    net = net.add(l.getVarianceValue());
                }
            }
            lines.add(new CountLineResponse(l.getId(), l.getProduct().getId(), l.getProduct().getName(),
                    l.getProduct().getSku(), l.getProduct().getBarcode(), l.getExpectedQuantity(),
                    l.getCountedQuantity(), l.getVariance(), l.getUnitCost(), l.getVarianceValue()));
        }
        return new CountResponse(c.getId(), c.getCountNumber(), c.getStatus().name(), c.getNotes(), c.getCreatedBy(),
                c.getCreatedAt(), c.getCommittedBy(), c.getCommittedAt(), counted, uncounted, over, short_,
                net.setScale(2, java.math.RoundingMode.HALF_UP), lines);
    }
}
