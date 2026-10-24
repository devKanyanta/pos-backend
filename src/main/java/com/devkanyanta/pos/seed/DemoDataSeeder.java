package com.devkanyanta.pos.seed;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.devkanyanta.pos.branch.BranchService;
import com.devkanyanta.pos.branch.dto.CreateBranchRequest;
import com.devkanyanta.pos.category.CategoryService;
import com.devkanyanta.pos.category.dto.CategoryResponse;
import com.devkanyanta.pos.category.dto.CreateCategoryRequest;
import com.devkanyanta.pos.customer.CustomerService;
import com.devkanyanta.pos.customer.dto.CreateCustomerRequest;
import com.devkanyanta.pos.customer.dto.CustomerResponse;
import com.devkanyanta.pos.inventory.InventoryMovementService;
import com.devkanyanta.pos.inventory.dto.AdjustmentRequest;
import com.devkanyanta.pos.inventory.dto.RestockRequest;
import com.devkanyanta.pos.merchant.MerchantProfileService;
import com.devkanyanta.pos.merchant.dto.UpdateMerchantProfileRequest;
import com.devkanyanta.pos.payment.PaymentMethodEntity;
import com.devkanyanta.pos.payment.PaymentMethodRepository;
import com.devkanyanta.pos.payment.PaymentService;
import com.devkanyanta.pos.payment.dto.CreatePaymentRequest;
import com.devkanyanta.pos.product.ProductService;
import com.devkanyanta.pos.product.dto.CreateProductRequest;
import com.devkanyanta.pos.product.dto.ProductResponse;
import com.devkanyanta.pos.reconciliation.ReconciliationService;
import com.devkanyanta.pos.reconciliation.dto.ReconciliationRequest;
import com.devkanyanta.pos.sale.SaleCalculator;
import com.devkanyanta.pos.sale.SaleService;
import com.devkanyanta.pos.sale.dto.CreateSaleRequest;
import com.devkanyanta.pos.sale.dto.RefundRequest;
import com.devkanyanta.pos.sale.dto.SaleItemRequest;
import com.devkanyanta.pos.sale.dto.SaleResponse;
import com.devkanyanta.pos.shared.util.Money;
import com.devkanyanta.pos.support.SupportService;
import com.devkanyanta.pos.support.dto.SupportGuideRequest;
import com.devkanyanta.pos.sync.SyncService;
import com.devkanyanta.pos.sync.dto.OfflineSaleRequest;
import com.devkanyanta.pos.sync.dto.RegisterDeviceRequest;
import com.devkanyanta.pos.sync.dto.SyncSalesRequest;
import com.devkanyanta.pos.user.UserRepository;
import com.devkanyanta.pos.user.UserService;
import com.devkanyanta.pos.user.dto.CreateUserRequest;
import com.devkanyanta.pos.user.role.RoleEntity;
import com.devkanyanta.pos.user.role.RoleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Loads a realistic demo data set (users, catalogue, customers, a week of sales, a refund, a void
 * and an end-of-day cash-up) so a fresh database is immediately explorable.
 *
 * <p>Opt-in: enable with {@code pos.seed.demo=true} (env {@code POS_SEED_DEMO=true}). It only runs
 * on an empty database (no users yet) and goes through the normal services, so stock, VAT,
 * receipts and numbering behave exactly as they do for real traffic. Never enable in production.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "pos.seed", name = "demo", havingValue = "true")
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    /** Demo logins; documented in the README. */
    static final String ADMIN_USERNAME = "admin";
    static final String MANAGER_USERNAME = "manager";
    static final String CASHIER_USERNAME = "cashier";

    private static final String DEVICE_ID = "DEMO-TILL-01";
    private static final String ZRA_CLASS_CODE = "50000000";
    private static final int HISTORY_DAYS = 6;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserService userService;
    private final MerchantProfileService merchantProfileService;
    private final BranchService branchService;
    private final CategoryService categoryService;
    private final ProductService productService;
    private final CustomerService customerService;
    private final InventoryMovementService inventoryMovementService;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentService paymentService;
    private final SaleService saleService;
    private final SyncService syncService;
    private final ReconciliationService reconciliationService;
    private final SupportService supportService;
    private final Clock clock;

    /** Runs after every CommandLineRunner (roles, permissions, payment methods) has finished. */
    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (userRepository.count() > 0) {
            log.info("Demo data not loaded: the database already has users.");
            return;
        }

        log.info("Loading demo data ...");

        seedMerchant();
        seedUsers();
        seedBranches();
        Map<String, CategoryResponse> categories = seedCategories();
        Map<String, ProductResponse> products = seedProducts(categories);
        List<CustomerResponse> customers = seedCustomers();
        seedStockMovements(products);
        seedSupportGuides();

        seedSalesHistory(products);
        seedTodaysSales(products, customers);
        seedRefundAndVoid(products);
        seedReconciliation();

        log.info("Demo data loaded. Log in as admin/admin123, manager/manager123 or cashier/cashier123.");
    }

    // ---- Master data ------------------------------------------------------------------------

    private void seedMerchant() {
        UpdateMerchantProfileRequest request = new UpdateMerchantProfileRequest();
        request.setBusinessName("Mapalo General Dealers");
        request.setTpin("1002345678");
        request.setPhone("0977123456");
        request.setEmail("shop@mapalo-dealers.example");
        request.setAddress("Plot 14, Cairo Road, Lusaka");
        request.setReceiptFooter("Thank you for shopping with us! Goods once sold are not returnable without a receipt.");
        request.setPlan("SME");
        merchantProfileService.updateProfile(request);
    }

    private void seedUsers() {
        createUser("Admin", "User", "admin@example.com", "0977000001", ADMIN_USERNAME, "admin123", "ADMIN");
        createUser("Mary", "Banda", "manager@example.com", "0977000002", MANAGER_USERNAME, "manager123", "MANAGER");
        createUser("Joseph", "Phiri", "cashier@example.com", "0977000003", CASHIER_USERNAME, "cashier123", "CASHIER");
    }

    private void createUser(String first, String last, String email, String phone, String username,
                            String password, String roleName) {
        RoleEntity role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role not seeded yet: " + roleName));
        CreateUserRequest request = new CreateUserRequest();
        request.setFirstName(first);
        request.setLastName(last);
        request.setEmail(email);
        request.setPhoneNumber(phone);
        request.setUsername(username);
        request.setPassword(password);
        request.setRoleIds(Set.of(role.getId()));
        userService.createUser(request);
    }

    private void seedBranches() {
        createBranch("Head Office - Cairo Road", "1002345678", "0977123456", "shop@mapalo-dealers.example",
                "Plot 14, Cairo Road, Lusaka");
        createBranch("Kitwe Branch", "1002345678", "0966123456", "kitwe@mapalo-dealers.example",
                "12 Obote Avenue, Kitwe");
    }

    private void createBranch(String name, String tpin, String phone, String email, String address) {
        CreateBranchRequest request = new CreateBranchRequest();
        request.setName(name);
        request.setZraTpin(tpin);
        request.setPhone(phone);
        request.setEmail(email);
        request.setAddress(address);
        branchService.createBranch(request);
    }

    private Map<String, CategoryResponse> seedCategories() {
        Map<String, CategoryResponse> categories = new LinkedHashMap<>();
        String[][] data = {
                {"Groceries", "Staple foods and pantry items"},
                {"Beverages", "Soft drinks, juices and water"},
                {"Dairy & Bakery", "Milk, eggs and bread"},
                {"Household", "Cleaning and household supplies"},
                {"Personal Care", "Toiletries and hygiene"},
                {"Services", "Non-stock services"},
        };
        for (String[] row : data) {
            CreateCategoryRequest request = new CreateCategoryRequest();
            request.setName(row[0]);
            request.setDescription(row[1]);
            categories.put(row[0], categoryService.createCategory(request));
        }
        return categories;
    }

    /** SKU -> product. {@code taxType} A = 16% standard rated, D = exempt. */
    private Map<String, ProductResponse> seedProducts(Map<String, CategoryResponse> categories) {
        Map<String, ProductResponse> products = new LinkedHashMap<>();
        // sku, name, category, price (VAT exclusive), cost, stock, reorder level, tax type
        Object[][] data = {
                {"MM-25", "Mealie Meal 25kg", "Groceries", "185.00", "160.00", 60, 10, "D"},
                {"MM-10", "Mealie Meal 10kg", "Groceries", "78.00", "66.00", 80, 15, "D"},
                {"OIL-2L", "Cooking Oil 2L", "Groceries", "62.00", "52.00", 70, 12, "A"},
                {"SUG-2", "Sugar 2kg", "Groceries", "54.00", "46.00", 90, 15, "A"},
                {"SALT-1", "Salt 1kg", "Groceries", "12.00", "8.50", 120, 20, "A"},
                {"RICE-2", "Rice 2kg", "Groceries", "68.00", "57.00", 45, 10, "A"},
                {"BEAN-1", "Kidney Beans 1kg", "Groceries", "38.00", "30.00", 50, 10, "D"},
                {"BREAD-W", "White Bread 700g", "Dairy & Bakery", "18.00", "13.50", 40, 10, "D"},
                {"EGG-30", "Eggs Tray (30)", "Dairy & Bakery", "85.00", "72.00", 25, 5, "D"},
                {"MILK-1", "Fresh Milk 1L", "Dairy & Bakery", "28.00", "22.00", 60, 12, "A"},
                {"COKE-500", "Coca-Cola 500ml", "Beverages", "12.00", "8.00", 150, 24, "A"},
                {"FANTA-500", "Fanta Orange 500ml", "Beverages", "12.00", "8.00", 140, 24, "A"},
                {"WATER-1.5", "Bottled Water 1.5L", "Beverages", "10.00", "6.50", 100, 20, "A"},
                {"JUICE-1", "Mango Juice 1L", "Beverages", "32.00", "25.00", 36, 10, "A"},
                {"SOAP-BAR", "Laundry Soap Bar", "Household", "15.00", "10.50", 100, 20, "A"},
                {"DETERG-1", "Washing Powder 1kg", "Household", "48.00", "38.00", 40, 8, "A"},
                {"BLEACH-750", "Bleach 750ml", "Household", "26.00", "19.00", 30, 8, "A"},
                {"TOOTHP", "Toothpaste 100ml", "Personal Care", "28.00", "20.00", 55, 10, "A"},
                {"LOTION-400", "Body Lotion 400ml", "Personal Care", "55.00", "41.00", 25, 6, "A"},
                {"TISSUE-4", "Toilet Paper 4-pack", "Household", "34.00", "26.00", 3, 8, "A"},
                {"CHARGE", "Phone Charging", "Services", "5.00", "0.00", 0, null, "A"},
        };
        for (Object[] row : data) {
            CreateProductRequest request = new CreateProductRequest();
            String sku = (String) row[0];
            request.setSku(sku);
            request.setName((String) row[1]);
            request.setCategoryId(categories.get((String) row[2]).getId());
            request.setPrice(new BigDecimal((String) row[3]));
            request.setCostPrice(new BigDecimal((String) row[4]));
            request.setStockQuantity((Integer) row[5]);
            request.setReorderLevel((Integer) row[6]);
            request.setZraTaxType((String) row[7]);
            request.setBarcode("600" + String.format("%010d", Math.abs(sku.hashCode()) % 1_000_000_000L));
            boolean service = "Services".equals(row[2]);
            request.setTrackInventory(!service);
            request.setZraItemClassCode(ZRA_CLASS_CODE);
            request.setZraItemTypeCode(service ? "3" : "2");
            request.setZraOriginCountryCode("ZM");
            request.setZraPackagingUnitCode("NT");
            request.setZraQuantityUnitCode("U");
            products.put(sku, productService.createProduct(request));
        }
        return products;
    }

    private List<CustomerResponse> seedCustomers() {
        List<CustomerResponse> customers = new ArrayList<>();
        String[][] data = {
                {"Chanda", "Mulenga", "0971234501", "chanda.mulenga@example.com", null, "Kabulonga, Lusaka"},
                {"Natasha", "Zulu", "0961234502", "natasha.zulu@example.com", null, "Woodlands, Lusaka"},
                {"Bwalya", "Chileshe", "0951234503", null, null, "Chilenje, Lusaka"},
                {"Tendai", "Moyo", "0977234504", "tendai.moyo@example.com", null, "Rhodes Park, Lusaka"},
                {"Lusaka Primary School", "Tuck Shop", "0211234505", "tuckshop@lps.example.com", "1009876543",
                        "Independence Avenue, Lusaka"},
        };
        for (String[] row : data) {
            CreateCustomerRequest request = new CreateCustomerRequest();
            request.setFirstName(row[0]);
            request.setLastName(row[1]);
            request.setPhone(row[2]);
            request.setEmail(row[3]);
            request.setTpin(row[4]);
            request.setAddress(row[5]);
            customers.add(customerService.createCustomer(request));
        }
        return customers;
    }

    /** A supplier delivery and a stock-take correction, so the movement ledger has history. */
    private void seedStockMovements(Map<String, ProductResponse> products) {
        RestockRequest restock = new RestockRequest();
        restock.setProductId(products.get("OIL-2L").getId());
        restock.setQuantity(24);
        restock.setUnitCost(new BigDecimal("53.00"));
        restock.setNotes("Supplier delivery - Trade Kings");
        inventoryMovementService.restock(restock, ADMIN_USERNAME);

        AdjustmentRequest adjustment = new AdjustmentRequest();
        adjustment.setProductId(products.get("EGG-30").getId());
        adjustment.setQuantity(-2);
        adjustment.setReason("Broken trays found during stock take");
        inventoryMovementService.adjustStock(adjustment, MANAGER_USERNAME);
    }

    private void seedSupportGuides() {
        String[][] data = {
                {"Making your first sale", "getting-started",
                        "Search or scan a product, set the quantity, pick a payment method and tap Complete Sale."},
                {"Taking mobile money payments", "payments",
                        "Choose MTN, Airtel or Zamtel, enter the customer's confirmation code as the reference, then confirm."},
                {"Selling when the internet is down", "offline",
                        "Keep selling normally. Sales queue on the device and upload automatically when you are back online."},
                {"Closing the day (cash-up)", "end-of-day",
                        "Count each tender, enter the amounts under Reconciliation and review the Z-report."},
        };
        int order = 1;
        for (String[] row : data) {
            SupportGuideRequest request = new SupportGuideRequest();
            request.setTitle(row[0]);
            request.setTopic(row[1]);
            request.setDescription(row[2]);
            request.setSortOrder(order++);
            request.setActive(true);
            supportService.createGuide(request);
        }
    }

    // ---- Transactions -----------------------------------------------------------------------

    /**
     * The past week, uploaded the way a till would after working offline (so the sales carry a past
     * {@code soldAt}). Gives the reports, Z-report and dashboard something to show.
     */
    private void seedSalesHistory(Map<String, ProductResponse> products) {
        RegisterDeviceRequest device = new RegisterDeviceRequest();
        device.setDeviceId(DEVICE_ID);
        device.setName("Front Counter Till");
        device.setModel("Android POS");
        device.setAppVersion("1.0.0");
        syncService.registerDevice(device, CASHIER_USERNAME);

        PaymentMethodEntity cash = paymentMethod("CASH");
        PaymentMethodEntity mtn = paymentMethod("MTN");
        PaymentMethodEntity airtel = paymentMethod("AIRTEL");
        PaymentMethodEntity card = paymentMethod("CARD");
        List<PaymentMethodEntity> tenders = List.of(cash, cash, cash, mtn, mtn, airtel, card);

        List<ProductResponse> stocked = products.values().stream()
                .filter(p -> Boolean.TRUE.equals(p.getTrackInventory()))
                .toList();

        Random random = new Random(42);
        LocalDate today = LocalDate.now(clock);

        for (int daysAgo = HISTORY_DAYS; daysAgo >= 1; daysAgo--) {
            List<OfflineSaleRequest> batch = new ArrayList<>();
            int salesThatDay = 6 + random.nextInt(6);

            for (int i = 0; i < salesThatDay; i++) {
                OfflineSaleRequest sale = new OfflineSaleRequest();
                sale.setClientSaleId("demo-d" + daysAgo + "-s" + i + "-" + DEVICE_ID);
                sale.setSoldAt(LocalDateTime.of(today.minusDays(daysAgo),
                        LocalTime.of(8 + random.nextInt(10), random.nextInt(60))));

                List<OfflineSaleRequest.Item> items = new ArrayList<>();
                List<SaleCalculator.LineInput> lines = new ArrayList<>();
                int lineCount = 1 + random.nextInt(4);
                for (int l = 0; l < lineCount; l++) {
                    ProductResponse product = stocked.get(random.nextInt(stocked.size()));
                    // Keep the near-empty product low for the dashboard's low-stock alert.
                    if ("TISSUE-4".equals(product.getSku())) {
                        continue;
                    }
                    int quantity = 1 + random.nextInt(3);

                    OfflineSaleRequest.Item item = new OfflineSaleRequest.Item();
                    item.setProductId(product.getId());
                    item.setProductName(product.getName());
                    item.setQuantity(quantity);
                    item.setUnitPrice(product.getPrice());
                    item.setVatRate(product.getVatRate());
                    item.setZraTaxType(product.getZraTaxType());
                    items.add(item);
                    lines.add(new SaleCalculator.LineInput(product.getName(), product.getPrice(), quantity,
                            null, product.getVatRate()));
                }
                if (items.isEmpty()) {
                    continue;
                }

                BigDecimal total = SaleCalculator.calculate(lines, null).total();
                PaymentMethodEntity tender = tenders.get(random.nextInt(tenders.size()));
                CreatePaymentRequest payment = payment(tender, total, "history-" + daysAgo + "-" + i);

                sale.setItems(items);
                sale.setExpectedTotal(total);
                sale.setPayments(List.of(payment));
                batch.add(sale);
            }

            SyncSalesRequest request = new SyncSalesRequest();
            request.setDeviceId(DEVICE_ID);
            request.setPendingQueueSize(0);
            request.setSales(batch);
            syncService.syncSales(request, CASHIER_USERNAME);
        }
    }

    /** A handful of live sales for today: cash with change, mobile money, split tender, a credit sale. */
    private void seedTodaysSales(Map<String, ProductResponse> products, List<CustomerResponse> customers) {
        PaymentMethodEntity cash = paymentMethod("CASH");
        PaymentMethodEntity mtn = paymentMethod("MTN");
        PaymentMethodEntity card = paymentMethod("CARD");

        // Cash with change.
        SaleSummary cashSale = sell(null, List.of(line(products, "MM-25", 1), line(products, "OIL-2L", 2)));
        pay(cashSale, cash, roundUp(cashSale.total(), 50), null);

        // Mobile money.
        SaleSummary momoSale = sell(customers.get(0).getId(),
                List.of(line(products, "COKE-500", 6), line(products, "BREAD-W", 2)));
        pay(momoSale, mtn, momoSale.total(), "MTN24100001");

        // Split tender: part cash, remainder on card.
        SaleSummary splitSale = sell(customers.get(1).getId(),
                List.of(line(products, "DETERG-1", 2), line(products, "TOOTHP", 3), line(products, "LOTION-400", 1)));
        BigDecimal cashPart = new BigDecimal("100.00");
        pay(splitSale, cash, cashPart, null);
        pay(splitSale, card, splitSale.total().subtract(cashPart), "CARD-APPR-7731");

        // Service item (no stock tracking).
        SaleSummary service = sell(null, List.of(line(products, "CHARGE", 2), line(products, "WATER-1.5", 1)));
        pay(service, cash, service.total(), null);

        // Credit sale: the school tuck shop pays a deposit and owes the balance.
        SaleSummary credit = sell(customers.get(4).getId(),
                List.of(line(products, "SUG-2", 10), line(products, "RICE-2", 5), line(products, "MM-10", 4)));
        pay(credit, mtn, new BigDecimal("500.00"), "MTN24100002");
    }

    /** One refunded sale and one voided (unpaid) sale, so those flows show up in reports. */
    private void seedRefundAndVoid(Map<String, ProductResponse> products) {
        PaymentMethodEntity cash = paymentMethod("CASH");

        SaleSummary refunded = sell(null, List.of(line(products, "BLEACH-750", 2), line(products, "SOAP-BAR", 4)));
        pay(refunded, cash, refunded.total(), null);

        SaleResponse refundedSale = saleService.getSaleById(refunded.id());
        RefundRequest refund = new RefundRequest();
        refund.setSaleItemIds(List.of(refundedSale.getItems().get(0).getId()));
        refund.setReason("Customer returned damaged bottles");
        refund.setRefundPaymentMethodId(cash.getId());
        saleService.refundSale(refunded.id(), refund, MANAGER_USERNAME);

        SaleSummary voided = sell(null, List.of(line(products, "FANTA-500", 3)));
        saleService.voidSale(voided.id(), MANAGER_USERNAME);
    }

    /** Yesterday's cash-up with a small cash shortage, giving the reconciliation screen a flagged line. */
    private void seedReconciliation() {
        LocalDate yesterday = LocalDate.now(clock).minusDays(1);
        // First pass declares nothing, only to learn what was expected per tender.
        List<ReconciliationRequest.Declared> declared = new ArrayList<>();
        for (PaymentMethodEntity method : paymentMethodRepository.findAll()) {
            ReconciliationRequest.Declared line = new ReconciliationRequest.Declared();
            line.setPaymentMethodId(method.getId());
            line.setAmount(BigDecimal.ZERO);
            declared.add(line);
        }

        // The second pass declares exactly the expected amounts, except cash which is 10.00 short.
        ReconciliationRequest probe = new ReconciliationRequest();
        probe.setBusinessDate(yesterday);
        probe.setOpeningFloat(new BigDecimal("200.00"));
        probe.setDeclared(declared);
        var expected = reconciliationService.submit(probe, MANAGER_USERNAME);

        List<ReconciliationRequest.Declared> counted = new ArrayList<>();
        for (var line : expected.getLines()) {
            ReconciliationRequest.Declared entry = new ReconciliationRequest.Declared();
            entry.setPaymentMethodId(line.getPaymentMethodId());
            BigDecimal amount = line.getExpectedAmount();
            if ("Cash".equals(line.getPaymentMethodName()) && amount.compareTo(BigDecimal.TEN) > 0) {
                amount = amount.subtract(BigDecimal.TEN);
            }
            entry.setAmount(amount);
            counted.add(entry);
        }

        ReconciliationRequest request = new ReconciliationRequest();
        request.setBusinessDate(yesterday);
        request.setOpeningFloat(new BigDecimal("200.00"));
        request.setDeclared(counted);
        request.setNotes("Demo cash-up - cash drawer is K10 short.");
        reconciliationService.submit(request, MANAGER_USERNAME);
    }

    // ---- Helpers ----------------------------------------------------------------------------

    private record SaleLine(Long productId, int quantity) {
    }

    private record SaleSummary(Long id, BigDecimal total) {
    }

    private SaleLine line(Map<String, ProductResponse> products, String sku, int quantity) {
        return new SaleLine(products.get(sku).getId(), quantity);
    }

    private SaleSummary sell(Long customerId, List<SaleLine> lines) {
        CreateSaleRequest request = new CreateSaleRequest();
        request.setCustomerId(customerId);
        request.setItems(lines.stream().map(l -> {
            SaleItemRequest item = new SaleItemRequest();
            item.setProductId(l.productId());
            item.setQuantity(l.quantity());
            return item;
        }).toList());
        SaleResponse sale = saleService.createSale(request, CASHIER_USERNAME);
        return new SaleSummary(sale.getId(), sale.getTotalAmount());
    }

    private void pay(SaleSummary sale, PaymentMethodEntity method, BigDecimal amount, String reference) {
        paymentService.createPayment(sale.id(), payment(method, amount, reference));
    }

    private CreatePaymentRequest payment(PaymentMethodEntity method, BigDecimal amount, String reference) {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setPaymentMethodId(method.getId());
        request.setAmount(amount);
        if (Boolean.TRUE.equals(method.getRequiresReference())) {
            // Unique per sale; mobile money requires a 3-64 char reference.
            request.setTransactionReference(reference != null ? "MM" + reference : null);
            request.setPayerPhone("0971234501");
        } else if (reference != null && "CARD".equals(method.getCode())) {
            request.setTransactionReference(reference);
        }
        return request;
    }

    private PaymentMethodEntity paymentMethod(String code) {
        return paymentMethodRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Payment method not seeded: " + code));
    }

    private static BigDecimal roundUp(BigDecimal amount, int multiple) {
        BigDecimal step = BigDecimal.valueOf(multiple);
        return Money.of(amount.divide(step, 0, java.math.RoundingMode.CEILING).multiply(step));
    }
}
