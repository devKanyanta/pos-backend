package com.devkanyanta.pos.sync;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.devkanyanta.pos.category.CategoryRepository;
import com.devkanyanta.pos.payment.PaymentMethodService;
import com.devkanyanta.pos.product.ProductEntity;
import com.devkanyanta.pos.product.ProductRepository;
import com.devkanyanta.pos.receipt.ReceiptRepository;
import com.devkanyanta.pos.sale.SaleRepository;
import com.devkanyanta.pos.shared.config.PosProperties;
import com.devkanyanta.pos.shared.exception.ApiException;
import com.devkanyanta.pos.shared.exception.BadRequestException;
import com.devkanyanta.pos.shared.exception.NotFoundException;
import com.devkanyanta.pos.shared.util.Texts;
import com.devkanyanta.pos.sync.dto.CatalogResponse;
import com.devkanyanta.pos.sync.dto.DeviceResponse;
import com.devkanyanta.pos.sync.dto.HeartbeatRequest;
import com.devkanyanta.pos.sync.dto.OfflineSaleRequest;
import com.devkanyanta.pos.sync.dto.RegisterDeviceRequest;
import com.devkanyanta.pos.sync.dto.SyncSaleResult;
import com.devkanyanta.pos.sync.dto.SyncSalesRequest;
import com.devkanyanta.pos.sync.dto.SyncSalesResponse;
import com.devkanyanta.pos.sync.dto.SyncStatusResponse;
import com.devkanyanta.pos.zra.ZraSubmissionEntity.SubmissionStatus;
import com.devkanyanta.pos.zra.ZraSubmissionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Server side of the offline-first sync engine.
 *
 * <ul>
 *   <li><b>Push</b> ({@link #syncSales}): the device uploads queued sales in batches. Every sale
 *       carries a unique {@code clientSaleId}; re-sending it returns {@code DUPLICATE} with the
 *       original sale, so retries after a dropped connection never double-count.</li>
 *   <li><b>Pull</b> ({@link #getCatalog}): products, categories and tenders changed since the
 *       last sync, for the device's local SQLite catalogue.</li>
 *   <li><b>Status / heartbeat</b>: queued-transaction counts and online state per device.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SyncService {

    private final PosDeviceRepository deviceRepository;

    private final SyncSaleProcessor syncSaleProcessor;

    private final SaleRepository saleRepository;

    private final ReceiptRepository receiptRepository;

    private final ProductRepository productRepository;

    private final CategoryRepository categoryRepository;

    private final PaymentMethodService paymentMethodService;

    private final ZraSubmissionRepository zraSubmissionRepository;

    private final PosProperties properties;

    private final Clock clock;

    @Transactional
    public DeviceResponse registerDevice(RegisterDeviceRequest request, String username) {

        if (request == null) {
            throw new BadRequestException("Device registration request is required");
        }

        String deviceId = validateDeviceId(request.getDeviceId());

        PosDeviceEntity device = deviceRepository.findByDeviceId(deviceId)
                .orElseGet(() -> PosDeviceEntity.builder()
                        .deviceId(deviceId)
                        .registeredBy(username)
                        .active(true)
                        .build());

        if (Boolean.FALSE.equals(device.getActive())) {
            throw new BadRequestException("Device " + deviceId + " has been deactivated by a manager");
        }

        if (request.getName() != null) {
            device.setName(Texts.truncate(Texts.clean(request.getName()), 255));
        }
        if (request.getModel() != null) {
            device.setModel(Texts.truncate(Texts.clean(request.getModel()), 255));
        }
        if (request.getAppVersion() != null) {
            device.setAppVersion(Texts.truncate(Texts.clean(request.getAppVersion()), 40));
        }
        if (device.getName() == null) {
            device.setName(deviceId);
        }

        device.setLastUser(username);
        device.setLastSeenAt(LocalDateTime.now(clock));

        return toResponse(deviceRepository.save(device));
    }

    @Transactional
    public DeviceResponse heartbeat(HeartbeatRequest request, String username) {

        if (request == null) {
            throw new BadRequestException("Heartbeat request is required");
        }

        PosDeviceEntity device = requireActiveDevice(request.getDeviceId(), username);

        if (request.getPendingQueueSize() != null) {
            device.setPendingQueueSize(Math.max(0, request.getPendingQueueSize()));
        }
        if (request.getOnline() != null) {
            device.setReportedOnline(request.getOnline());
        }
        if (request.getAppVersion() != null) {
            device.setAppVersion(Texts.truncate(Texts.clean(request.getAppVersion()), 40));
        }

        device.setLastUser(username);
        device.setLastSeenAt(LocalDateTime.now(clock));

        return toResponse(deviceRepository.save(device));
    }

    /**
     * Not transactional on purpose: each sale is processed in its own transaction by
     * {@link SyncSaleProcessor}.
     */
    public SyncSalesResponse syncSales(SyncSalesRequest request, String username) {

        if (request == null || request.getSales() == null || request.getSales().isEmpty()) {
            throw new BadRequestException("At least one sale is required");
        }

        if (request.getSales().size() > properties.getSync().getMaxBatchSize()) {
            throw new BadRequestException("A sync batch may contain at most "
                    + properties.getSync().getMaxBatchSize() + " sales");
        }

        String deviceId = touchDevice(request.getDeviceId(), username, null);

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime latestAllowed = now.plusMinutes(properties.getSync().getMaxClockSkewMinutes());
        LocalDateTime earliestAllowed = now.minusDays(properties.getSync().getMaxOfflineAgeDays());

        List<SyncSaleResult> results = new ArrayList<>();
        Set<String> seenInBatch = new HashSet<>();
        int created = 0;
        int duplicates = 0;
        int rejected = 0;

        for (OfflineSaleRequest sale : request.getSales()) {

            String clientSaleId = sale != null ? Texts.clean(sale.getClientSaleId()) : null;
            SyncSaleResult result;

            try {
                if (sale == null || clientSaleId == null) {
                    throw new BadRequestException("clientSaleId is required");
                }
                if (!seenInBatch.add(clientSaleId)) {
                    throw new BadRequestException("clientSaleId appears more than once in this batch");
                }
                if (sale.getSoldAt() == null) {
                    throw new BadRequestException("soldAt is required");
                }
                if (sale.getSoldAt().isAfter(latestAllowed)) {
                    throw new BadRequestException("soldAt is in the future; check the device clock");
                }
                if (sale.getSoldAt().isBefore(earliestAllowed)) {
                    throw new BadRequestException("soldAt is older than " + properties.getSync().getMaxOfflineAgeDays()
                            + " days; record it manually");
                }

                result = syncSaleProcessor.process(sale, deviceId, username);
            } catch (DataIntegrityViolationException ex) {
                // Lost a race with a concurrent upload of the same sale.
                result = existingResult(clientSaleId, ex);
            } catch (ApiException ex) {
                result = SyncSaleResult.builder()
                        .clientSaleId(clientSaleId)
                        .status("REJECTED")
                        .conflicts(List.of())
                        .error(ex.getMessage())
                        .build();
            }

            switch (result.getStatus()) {
                case "CREATED" -> created++;
                case "DUPLICATE" -> duplicates++;
                default -> rejected++;
            }
            results.add(result);
        }

        touchDevice(deviceId, username, request.getPendingQueueSize());

        log.info("Sync from device {} by {}: {} created, {} duplicates, {} rejected",
                deviceId, username, created, duplicates, rejected);

        return SyncSalesResponse.builder()
                .serverTime(LocalDateTime.now(clock))
                .created(created)
                .duplicates(duplicates)
                .rejected(rejected)
                .results(results)
                .build();
    }

    @Transactional(readOnly = true)
    public CatalogResponse getCatalog(LocalDateTime since) {

        LocalDateTime serverTime = LocalDateTime.now(clock);
        boolean full = since == null;

        List<ProductEntity> products = full
                ? productRepository.findAll()
                : productRepository.findByUpdatedAtAfterOrderByIdAsc(since.minusSeconds(1));

        return CatalogResponse.builder()
                .serverTime(serverTime)
                .full(full)
                .products(products.stream().map(this::toProductItem).toList())
                .categories(categoryRepository.findAll().stream()
                        .map(c -> CatalogResponse.CategoryItem.builder().id(c.getId()).name(c.getName()).build())
                        .toList())
                .paymentMethods(paymentMethodService.getAllPaymentMethods())
                .productIds(productRepository.findAllIds())
                .build();
    }

    @Transactional(readOnly = true)
    public SyncStatusResponse getStatus(String deviceId) {

        PosDeviceEntity device = StringUtils.hasText(deviceId)
                ? deviceRepository.findByDeviceId(deviceId.trim()).orElse(null)
                : null;

        return SyncStatusResponse.builder()
                .serverTime(LocalDateTime.now(clock))
                .deviceId(deviceId)
                .deviceRegistered(device != null)
                .deviceActive(device != null && !Boolean.FALSE.equals(device.getActive()))
                .lastSyncAt(device != null ? device.getLastSyncAt() : null)
                .pendingQueueSize(device != null ? device.getPendingQueueSize() : null)
                .zraPending(zraSubmissionRepository.countBySubmissionStatusIn(
                        List.of(SubmissionStatus.PENDING, SubmissionStatus.RETRYING)))
                .zraFailed(zraSubmissionRepository.countBySubmissionStatus(SubmissionStatus.FAILED))
                .salesNeedingReview(saleRepository.countByNeedsReviewTrue())
                .build();
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> getDevices() {

        return deviceRepository.findAllByOrderByLastSeenAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public DeviceResponse setDeviceActive(String deviceId, boolean active) {

        PosDeviceEntity device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new NotFoundException("Device not found"));
        device.setActive(active);
        return toResponse(deviceRepository.save(device));
    }

    private String touchDevice(String rawDeviceId, String username, Integer pendingQueueSize) {

        PosDeviceEntity device = requireActiveDevice(rawDeviceId, username);
        LocalDateTime now = LocalDateTime.now(clock);
        device.setLastSeenAt(now);
        device.setLastUser(username);
        if (pendingQueueSize != null) {
            device.setLastSyncAt(now);
            device.setPendingQueueSize(Math.max(0, pendingQueueSize));
        }
        deviceRepository.save(device);
        return device.getDeviceId();
    }

    /** Unknown devices are registered automatically on first contact. */
    private PosDeviceEntity requireActiveDevice(String rawDeviceId, String username) {

        String deviceId = validateDeviceId(rawDeviceId);

        PosDeviceEntity device = deviceRepository.findByDeviceId(deviceId)
                .orElseGet(() -> deviceRepository.save(PosDeviceEntity.builder()
                        .deviceId(deviceId)
                        .name(deviceId)
                        .registeredBy(username)
                        .active(true)
                        .build()));

        if (Boolean.FALSE.equals(device.getActive())) {
            throw new BadRequestException("Device " + deviceId + " has been deactivated by a manager");
        }

        return device;
    }

    private SyncSaleResult existingResult(String clientSaleId, DataIntegrityViolationException cause) {

        return saleRepository.findByClientSaleId(clientSaleId)
                .map(sale -> SyncSaleResult.builder()
                        .clientSaleId(clientSaleId)
                        .status("DUPLICATE")
                        .saleId(sale.getId())
                        .saleNumber(sale.getSaleNumber())
                        .receiptNumber(receiptRepository.findBySaleId(sale.getId())
                                .map(r -> r.getReceiptNumber()).orElse(null))
                        .totalAmount(sale.getTotalAmount())
                        .paymentStatus(sale.getPaymentStatus().name())
                        .conflicts(List.of())
                        .build())
                .orElseGet(() -> SyncSaleResult.builder()
                        .clientSaleId(clientSaleId)
                        .status("REJECTED")
                        .conflicts(List.of())
                        .error("Could not record sale: conflicting data (" + cause.getMostSpecificCause().getMessage() + ")")
                        .build());
    }

    private static String validateDeviceId(String raw) {

        String deviceId = Texts.clean(raw);
        if (deviceId == null || !deviceId.matches("[A-Za-z0-9._:\\-]{4,64}")) {
            throw new BadRequestException("deviceId must be 4-64 characters (letters, digits, . _ : -)");
        }
        return deviceId;
    }

    private CatalogResponse.ProductItem toProductItem(ProductEntity p) {

        return CatalogResponse.ProductItem.builder()
                .id(p.getId())
                .name(p.getName())
                .sku(p.getSku())
                .barcode(p.getBarcode())
                .price(p.getPrice())
                .vatRate(p.getVatRate())
                .zraTaxType(p.getZraTaxType() != null ? p.getZraTaxType().getCode() : null)
                .trackInventory(p.getTrackInventory())
                .stockQuantity(p.getStockQuantity())
                .active(!Boolean.FALSE.equals(p.getActive()))
                .categoryId(p.getCategory() != null ? p.getCategory().getId() : null)
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    private DeviceResponse toResponse(PosDeviceEntity device) {

        return DeviceResponse.builder()
                .id(device.getId())
                .deviceId(device.getDeviceId())
                .name(device.getName())
                .model(device.getModel())
                .appVersion(device.getAppVersion())
                .active(device.getActive())
                .registeredBy(device.getRegisteredBy())
                .lastUser(device.getLastUser())
                .lastSeenAt(device.getLastSeenAt())
                .lastSyncAt(device.getLastSyncAt())
                .pendingQueueSize(device.getPendingQueueSize())
                .reportedOnline(device.getReportedOnline())
                .createdAt(device.getCreatedAt())
                .build();
    }
}
