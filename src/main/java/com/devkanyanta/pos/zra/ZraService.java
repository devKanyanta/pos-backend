package com.devkanyanta.pos.zra;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.devkanyanta.pos.merchant.MerchantProfileService;
import com.devkanyanta.pos.product.ProductEntity;
import com.devkanyanta.pos.product.ProductRepository;
import com.devkanyanta.pos.shared.config.PosProperties;
import com.devkanyanta.pos.shared.exception.BadRequestException;
import com.devkanyanta.pos.shared.exception.NotFoundException;
import com.devkanyanta.pos.shared.exception.ServiceUnavailableException;
import com.devkanyanta.pos.zra.ZraSubmissionEntity.SubmissionStatus;
import com.devkanyanta.pos.zra.ZraSubmissionEntity.SubmissionType;
import com.devkanyanta.pos.zra.dto.InitializeDeviceRequest;
import com.devkanyanta.pos.zra.dto.ZraDeviceResponse;
import com.devkanyanta.pos.zra.dto.ZraStatusResponse;
import com.devkanyanta.pos.zra.dto.ZraSubmissionResponse;
import com.devkanyanta.pos.zra.dto.ZraTaxTypeResponse;
import com.devkanyanta.pos.zra.vsdc.ZraPayloadBuilder;
import com.devkanyanta.pos.zra.vsdc.ZraVsdcClient;
import com.devkanyanta.pos.zra.vsdc.ZraVsdcException;
import com.devkanyanta.pos.zra.vsdc.ZraVsdcResponse;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/** VSDC device set-up, code lookups and manual submission control. */
@Service
@RequiredArgsConstructor
public class ZraService {

    private final ZraDeviceRepository deviceRepository;

    private final ZraSubmissionRepository submissionRepository;

    private final ProductRepository productRepository;

    private final ZraVsdcClient client;

    private final ZraPayloadBuilder payloadBuilder;

    private final ZraSubmissionWorker worker;

    private final ZraSubmissionProcessor processor;

    private final ZraSubmissionService submissionService;

    private final MerchantProfileService merchantProfileService;

    private final PosProperties properties;

    private final Clock clock;

    public List<ZraTaxTypeResponse> getTaxTypes() {

        return Arrays.stream(ZraTaxType.values())
                .map(type -> ZraTaxTypeResponse.builder()
                        .code(type.getCode())
                        .label(type.getLabel())
                        .rate(type.getRate())
                        .zeroRated(type.isZeroRated())
                        .exempt(type.isExempt())
                        .build())
                .toList();
    }

    /**
     * Initialises the VSDC device (initializer/selectInitInfo) and stores the returned SDC id and
     * MRC number. Re-initialising replaces the active device.
     */
    @Transactional
    public ZraDeviceResponse initializeDevice(InitializeDeviceRequest request) {

        requirePlan();

        if (request == null) {
            throw new BadRequestException("Device initialisation request is required");
        }

        String tpin = StringUtils.hasText(request.getTpin()) ? request.getTpin().trim() : null;
        String bhfId = StringUtils.hasText(request.getBhfId()) ? request.getBhfId().trim() : "000";
        String serial = StringUtils.hasText(request.getDeviceSerialNo()) ? request.getDeviceSerialNo().trim() : null;

        if (tpin == null || !tpin.matches("\\d{10}")) {
            throw new BadRequestException("TPIN must be a 10-digit numeric value");
        }
        if (!bhfId.matches("\\d{3}")) {
            throw new BadRequestException("Branch id (bhfId) must be 3 digits, e.g. 000");
        }
        if (serial == null || serial.length() > 100) {
            throw new BadRequestException("Device serial number is required (max 100 characters)");
        }

        ZraVsdcResponse response;
        try {
            response = client.post(ZraVsdcClient.PATH_INIT, payloadBuilder.initRequest(tpin, bhfId, serial));
        } catch (ZraVsdcException ex) {
            throw vsdcError("Device initialisation failed", ex);
        }

        JsonNode info = response.data() != null && response.data().has("info")
                ? response.data().get("info")
                : response.data();

        String sdcId = text(info, "sdcId");
        String mrcNo = text(info, "mrcNo");

        if (sdcId == null) {
            throw new ServiceUnavailableException("VSDC initialisation response did not contain an SDC id");
        }

        deviceRepository.findAll().forEach(existing -> {
            existing.setActive(false);
            deviceRepository.save(existing);
        });

        Long lastInvoiceNo = parseLong(firstNonNull(text(info, "lastSaleInvcNo"), text(info, "lastSalesInvcNo"),
                text(info, "lastInvcNo")));

        ZraDeviceEntity device = deviceRepository.save(ZraDeviceEntity.builder()
                .tpin(tpin)
                .bhfId(bhfId)
                .deviceSerialNo(serial)
                .sdcId(sdcId)
                .mrcNo(mrcNo)
                .taxpayerName(text(info, "taxprNm"))
                .branchName(text(info, "bhfNm"))
                .lastInvoiceNo(lastInvoiceNo != null ? lastInvoiceNo : 0L)
                .initializedAt(LocalDateTime.now(clock))
                .initResponse(response.rawBody())
                .active(true)
                .build());

        return toResponse(device);
    }

    @Transactional(readOnly = true)
    public ZraDeviceResponse getDevice() {

        return deviceRepository.findFirstByActiveTrueOrderByIdDesc()
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("ZRA VSDC device has not been initialised"));
    }

    @Transactional(readOnly = true)
    public ZraStatusResponse getStatus() {

        List<ProductEntity> products = productRepository.findAll();

        return ZraStatusResponse.builder()
                .enabled(properties.getZra().isEnabled())
                .planAllowsZra(merchantProfileService.isFeatureEnabled(MerchantProfileService.FEATURE_ZRA))
                .deviceInitialized(deviceRepository.findFirstByActiveTrueOrderByIdDesc().isPresent())
                .pending(submissionRepository.countBySubmissionStatus(SubmissionStatus.PENDING))
                .retrying(submissionRepository.countBySubmissionStatus(SubmissionStatus.RETRYING))
                .failed(submissionRepository.countBySubmissionStatus(SubmissionStatus.FAILED))
                .succeeded(submissionRepository.countBySubmissionStatus(SubmissionStatus.SUCCESS))
                .unmappedProducts(products.stream().filter(p -> !Boolean.FALSE.equals(p.getActive()) && !p.isZraMapped()).count())
                .unregisteredProducts(products.stream()
                        .filter(p -> !Boolean.FALSE.equals(p.getActive()) && !Boolean.TRUE.equals(p.getZraRegistered()))
                        .count())
                .build();
    }

    /** Code lists (units, payment types, countries...) from code/selectCodes. */
    public JsonNode getCodes(String lastRequestDate) {
        return lookup(ZraVsdcClient.PATH_CODES, lastRequestDate);
    }

    /** Item classification list from itemClass/selectItemsClass. */
    public JsonNode getItemClasses(String lastRequestDate) {
        return lookup(ZraVsdcClient.PATH_ITEM_CLASSES, lastRequestDate);
    }

    /** Registers one product with the VSDC immediately. Not transactional: the worker commits on its own. */
    public ZraSubmissionResponse registerProduct(Long productId) {

        requirePlan();

        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        if (!product.isZraMapped()) {
            throw new BadRequestException("Product is missing ZRA mapping (tax type, item class, item type, packaging and quantity units)");
        }

        requireDevice();

        ZraSubmissionEntity submission = submissionRepository.save(ZraSubmissionEntity.builder()
                .product(product)
                .submissionType(SubmissionType.ITEM_REGISTRATION)
                .submissionStatus(SubmissionStatus.PENDING)
                .build());

        worker.process(submission.getId());

        return submissionService.getSubmissionById(submission.getId());
    }

    /** Queues registration of every mapped, active, unregistered product. @return number queued */
    @Transactional
    public int queueUnregisteredProducts() {

        requirePlan();

        List<ProductEntity> products = productRepository.findByZraRegisteredFalseOrZraRegisteredIsNull().stream()
                .filter(p -> !Boolean.FALSE.equals(p.getActive()) && p.isZraMapped())
                .toList();

        products.forEach(product -> submissionRepository.save(ZraSubmissionEntity.builder()
                .product(product)
                .submissionType(SubmissionType.ITEM_REGISTRATION)
                .submissionStatus(SubmissionStatus.PENDING)
                .build()));

        return products.size();
    }

    /** Runs the processor now (ignores the enabled flag so it can be driven manually). */
    public int processNow() {

        requirePlan();
        return processor.processDue();
    }

    /** Resets a FAILED/RETRYING submission and transmits it immediately. */
    public ZraSubmissionResponse retry(Long submissionId) {

        requirePlan();
        submissionService.resetForRetry(submissionId);
        worker.process(submissionId);
        return submissionService.getSubmissionById(submissionId);
    }

    private JsonNode lookup(String path, String lastRequestDate) {

        requirePlan();
        ZraDeviceEntity device = requireDevice();

        String since = StringUtils.hasText(lastRequestDate) ? lastRequestDate.trim() : "20200101000000";
        if (!since.matches("\\d{14}")) {
            throw new BadRequestException("lastReqDt must be in yyyyMMddHHmmss format");
        }

        try {
            return client.post(path, payloadBuilder.codesRequest(device, since)).data();
        } catch (ZraVsdcException ex) {
            throw vsdcError("VSDC lookup failed", ex);
        }
    }

    private ZraDeviceEntity requireDevice() {

        return deviceRepository.findFirstByActiveTrueOrderByIdDesc()
                .orElseThrow(() -> new BadRequestException("Initialise the ZRA VSDC device first (POST /api/v1/zra/device/initialize)"));
    }

    private void requirePlan() {
        merchantProfileService.requireFeature(MerchantProfileService.FEATURE_ZRA, "ZRA Smart Invoice");
    }

    private static RuntimeException vsdcError(String prefix, ZraVsdcException ex) {

        if ("NETWORK".equals(ex.getCode()) || ex.getCode().startsWith("HTTP_5")) {
            return new ServiceUnavailableException(prefix + ": " + ex.getMessage(), ex);
        }
        return new BadRequestException(prefix + " (" + ex.getCode() + "): " + ex.getMessage());
    }

    private ZraDeviceResponse toResponse(ZraDeviceEntity device) {

        return ZraDeviceResponse.builder()
                .id(device.getId())
                .tpin(device.getTpin())
                .bhfId(device.getBhfId())
                .deviceSerialNo(device.getDeviceSerialNo())
                .sdcId(device.getSdcId())
                .mrcNo(device.getMrcNo())
                .taxpayerName(device.getTaxpayerName())
                .branchName(device.getBranchName())
                .lastInvoiceNo(device.getLastInvoiceNo())
                .initializedAt(device.getInitializedAt())
                .integrationEnabled(properties.getZra().isEnabled())
                .vsdcBaseUrl(properties.getZra().getBaseUrl())
                .build();
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static Long parseLong(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
