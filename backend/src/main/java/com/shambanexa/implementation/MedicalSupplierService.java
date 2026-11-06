package com.shambanexa.implementation;

import com.shambanexa.common.ApiException;
import com.shambanexa.common.CurrentUser;
import com.shambanexa.dto.MedicalDtos;
import com.shambanexa.dto.MedicalDtos.Board;
import com.shambanexa.dto.MedicalDtos.MedicineBody;
import com.shambanexa.dto.MedicalDtos.MedicineView;
import com.shambanexa.dto.MedicalDtos.NoteBody;
import com.shambanexa.dto.MedicalDtos.OrderBody;
import com.shambanexa.dto.MedicalDtos.RequestView;
import com.shambanexa.dto.MedicalDtos.ShopBody;
import com.shambanexa.dto.MedicalDtos.SupplierView;
import com.shambanexa.entity.Farm;
import com.shambanexa.entity.MedicalSupplier;
import com.shambanexa.entity.MedicineRequest;
import com.shambanexa.entity.Species;
import com.shambanexa.entity.SupplierMedicine;
import com.shambanexa.entity.UserAccount;
import com.shambanexa.repository.FarmRepository;
import com.shambanexa.repository.MedicalSupplierRepository;
import com.shambanexa.repository.MedicineRequestRepository;
import com.shambanexa.repository.SpeciesRepository;
import com.shambanexa.repository.SupplierMedicineRepository;
import com.shambanexa.repository.UserAccountRepository;
import com.shambanexa.security.AccessGuard;
import com.shambanexa.service.AuditTrail;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class MedicalSupplierService {

    private static final Set<String> MARKS = Set.of("LEAVES", "PEAK", "CATTLE", "CROSS", "PAW");
    private static final Set<String> CATEGORIES = Set.of(
            "Dewormers", "Antibiotics", "Vaccines", "Vitamins and minerals", "Supplements", "Other");
    private static final Set<String> STOCK = Set.of("IN_STOCK", "LIMITED", "OUT");
    private static final Set<String> SHOP_STATES = Set.of("OPEN", "LIMITED", "CLOSED");
    private static final Set<String> CURRENCIES = Set.of("TZS", "USD");

    private final AccessGuard guard;
    private final AuditTrail audit;
    private final MedicalSupplierRepository suppliers;
    private final SupplierMedicineRepository medicines;
    private final MedicineRequestRepository requests;
    private final FarmRepository farms;
    private final SpeciesRepository species;
    private final UserAccountRepository users;

    public MedicalSupplierService(AccessGuard guard, AuditTrail audit, MedicalSupplierRepository suppliers,
                                  SupplierMedicineRepository medicines, MedicineRequestRepository requests,
                                  FarmRepository farms, SpeciesRepository species, UserAccountRepository users) {
        this.guard = guard;
        this.audit = audit;
        this.suppliers = suppliers;
        this.medicines = medicines;
        this.requests = requests;
        this.farms = farms;
        this.species = species;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Board board(String q, String animal, String category, String region, String district) {
        CurrentUser user = guard.current();
        UUID tenantId = user.tenantId();
        List<MedicalSupplier> shops = suppliers.findByTenantIdOrderByNameAsc(tenantId);
        Map<UUID, List<SupplierMedicine>> stock = medicines.findByTenantIdOrderByNameAsc(tenantId).stream()
                .collect(Collectors.groupingBy(SupplierMedicine::getSupplierId));
        String query = text(q).toLowerCase(Locale.ROOT);
        String animalFilter = text(animal);
        String categoryFilter = text(category);
        String regionFilter = text(region);
        String districtFilter = text(district);
        boolean narrowing = !query.isEmpty() || !animalFilter.isEmpty() || !categoryFilter.isEmpty();
        List<SupplierView> visible = new ArrayList<>();
        List<String> regions = new ArrayList<>();
        List<String> districts = new ArrayList<>();
        MedicalSupplier own = null;
        List<SupplierView> pending = new ArrayList<>();
        boolean admin = guard.canManageAccess();
        for (MedicalSupplier shop : shops) {
            if (shop.getOwnerId().equals(user.id())) {
                own = shop;
            }
            if (!"APPROVED".equals(shop.getApproval())) {
                if (admin) {
                    pending.add(view(shop, stock.getOrDefault(shop.getId(), List.of()), false));
                }
                continue;
            }
            regions.add(shop.getRegion());
            districts.add(shop.getDistrict());
            if (!regionFilter.isEmpty() && !shop.getRegion().equalsIgnoreCase(regionFilter)) {
                continue;
            }
            if (!districtFilter.isEmpty() && !shop.getDistrict().equalsIgnoreCase(districtFilter)) {
                continue;
            }
            List<SupplierMedicine> rows = stock.getOrDefault(shop.getId(), List.of());
            boolean shopHit = !query.isEmpty() && shop.getName().toLowerCase(Locale.ROOT).contains(query);
            List<SupplierMedicine> matched = rows.stream()
                    .filter(item -> !"OUT".equals(item.getStockState()))
                    .filter(item -> matches(item, shopHit ? "" : query, animalFilter, categoryFilter))
                    .toList();
            if (narrowing && matched.isEmpty()) {
                continue;
            }
            List<SupplierMedicine> listed = rows.stream().filter(item -> !"OUT".equals(item.getStockState())).toList();
            visible.add(view(shop, listed, shop.getOwnerId().equals(user.id())));
        }
        regions = regions.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        districts = districts.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        SupplierView ownView = own == null ? null : view(own, stock.getOrDefault(own.getId(), List.of()), true);
        return new Board(visible, regions, districts, ownView, pending, visibleRequests(user, shops), admin, user.canAdvise());
    }

    public SupplierView register(ShopBody body) {
        CurrentUser user = guard.current();
        if (suppliers.findByTenantIdAndOwnerId(user.tenantId(), user.id()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "SHOP_EXISTS", "This account already has a shop. Update that registration.");
        }
        MedicalSupplier shop = new MedicalSupplier();
        shop.setTenantId(user.tenantId());
        shop.setOwnerId(user.id());
        shop.setApproval("PENDING");
        applyShop(shop, body, true);
        suppliers.save(shop);
        audit.record("SUPPLIER_REGISTERED", "MEDICAL_SUPPLIER", shop.getId(), shop.getName() + " · " + shop.getRegion());
        audit.activity("MEDICAL", "Shop registered", shop.getName() + " · " + shop.getRegion(), null);
        return view(shop, List.of(), true);
    }

    public SupplierView update(UUID id, ShopBody body) {
        MedicalSupplier shop = owned(id);
        String previousLicense = shop.getLicenseNumber();
        applyShop(shop, body, false);
        if ("APPROVED".equals(shop.getApproval()) && !shop.getLicenseNumber().equals(previousLicense)) {
            shop.setApproval("PENDING");
        }
        if ("REJECTED".equals(shop.getApproval())) {
            shop.setApproval("PENDING");
        }
        suppliers.save(shop);
        audit.record("SUPPLIER_UPDATED", "MEDICAL_SUPPLIER", shop.getId(), shop.getName());
        audit.activity("MEDICAL", "Shop updated", shop.getName(), null);
        List<SupplierMedicine> rows = medicines.findByTenantIdAndSupplierIdOrderByNameAsc(shop.getTenantId(), shop.getId());
        return view(shop, rows, true);
    }

    public SupplierView verify(UUID id, NoteBody body) {
        if (!guard.canManageAccess()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "An administrator checks the shop license");
        }
        MedicalSupplier shop = requireShop(id);
        if (!"PENDING".equals(shop.getApproval())) {
            throw new ApiException(HttpStatus.CONFLICT, "SHOP_DECIDED", "This shop is already " + shop.getApproval().toLowerCase(Locale.ROOT));
        }
        boolean approve = body != null && Boolean.TRUE.equals(body.approve());
        shop.setApproval(approve ? "APPROVED" : "REJECTED");
        suppliers.save(shop);
        audit.record(approve ? "SUPPLIER_APPROVED" : "SUPPLIER_REJECTED", "MEDICAL_SUPPLIER", shop.getId(), shop.getName());
        audit.activity("MEDICAL", approve ? "Shop approved" : "Shop rejected", shop.getName(), null);
        return view(shop, medicines.findByTenantIdAndSupplierIdOrderByNameAsc(shop.getTenantId(), shop.getId()), false);
    }

    public MedicineView addMedicine(UUID supplierId, MedicineBody body) {
        MedicalSupplier shop = owned(supplierId);
        SupplierMedicine row = new SupplierMedicine();
        row.setTenantId(shop.getTenantId());
        row.setSupplierId(shop.getId());
        applyMedicine(row, body);
        medicines.save(row);
        audit.record("MEDICINE_LISTED", "SUPPLIER_MEDICINE", row.getId(), shop.getName() + " · " + row.getName());
        audit.activity("MEDICAL", "Medicine listed", shop.getName() + " · " + row.getName(), null);
        return medicineView(row);
    }

    public MedicineView updateMedicine(UUID supplierId, UUID medicineId, MedicineBody body) {
        MedicalSupplier shop = owned(supplierId);
        SupplierMedicine row = medicines.findByTenantIdAndId(shop.getTenantId(), medicineId)
                .filter(item -> item.getSupplierId().equals(shop.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEDICINE_NOT_FOUND", "Medicine not found"));
        applyMedicine(row, body);
        medicines.save(row);
        audit.record("MEDICINE_UPDATED", "SUPPLIER_MEDICINE", row.getId(), shop.getName() + " · " + row.getName());
        audit.activity("MEDICAL", "Medicine updated", shop.getName() + " · " + row.getName(), null);
        return medicineView(row);
    }

    public RequestView order(OrderBody body) {
        CurrentUser user = guard.current();
        if (body == null || body.supplierId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUPPLIER_REQUIRED", "Choose a supplier");
        }
        MedicalSupplier shop = requireShop(body.supplierId());
        if (!"APPROVED".equals(shop.getApproval())) {
            throw new ApiException(HttpStatus.CONFLICT, "SHOP_UNVERIFIED", "This shop is not approved for search yet");
        }
        if ("CLOSED".equals(shop.getShopState())) {
            throw new ApiException(HttpStatus.CONFLICT, "SHOP_CLOSED", "This shop is closed");
        }
        if (body.farmId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FARM_REQUIRED", "Choose the farm this request is for");
        }
        guard.checkFarm(body.farmId());
        BigDecimal quantity = positive(body.quantity(), "Enter a quantity greater than zero");
        String unit = required(body.unit(), 1, 40, "Enter the unit, such as bottle or dose");
        String fulfillment = text(body.fulfillment()).toUpperCase(Locale.ROOT);
        if (!fulfillment.equals("COLLECTION") && !fulfillment.equals("DELIVERY")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FULFILLMENT", "Choose collection or delivery");
        }
        SupplierMedicine medicine = null;
        String name;
        boolean prescription;
        if (body.medicineId() != null) {
            medicine = medicines.findByTenantIdAndId(user.tenantId(), body.medicineId())
                    .filter(item -> item.getSupplierId().equals(shop.getId()))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEDICINE_NOT_FOUND", "Medicine not found at this shop"));
            if ("OUT".equals(medicine.getStockState()) || medicine.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ApiException(HttpStatus.CONFLICT, "OUT_OF_STOCK", "This medicine is out of stock");
            }
            name = medicine.getName();
            unit = medicine.getUnitLabel();
            prescription = medicine.isPrescriptionRequired();
        } else {
            name = required(body.medicineName(), 2, 160, "Enter the medicine name");
            prescription = Boolean.TRUE.equals(body.prescriptionRequired());
        }
        MedicineRequest request = new MedicineRequest();
        request.setTenantId(user.tenantId());
        request.setSupplierId(shop.getId());
        request.setMedicineId(medicine == null ? null : medicine.getId());
        request.setRequesterId(user.id());
        request.setFarmId(body.farmId());
        request.setMedicineName(name);
        request.setQuantity(quantity);
        request.setUnitLabel(unit);
        request.setFulfillment(fulfillment);
        request.setPrescriptionRequired(prescription);
        request.setStatus(prescription ? "AWAITING_PRESCRIPTION" : "SUBMITTED");
        request.setStockDrawn(false);
        request.setRequesterNote(optional(body.note(), 400));
        request.setUpdatedAt(Instant.now());
        requests.save(request);
        audit.record("MEDICINE_REQUESTED", "MEDICINE_REQUEST", request.getId(), shop.getName() + " · " + name);
        audit.activity("MEDICAL", "Medicine requested", shop.getName() + " · " + name, request.getFarmId());
        return requestView(request, Map.of(shop.getId(), shop), names(), farmNames(), user);
    }

    public RequestView prescribe(UUID id, NoteBody body) {
        CurrentUser user = guard.current();
        if (!user.canAdvise()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "A Farm Adviser approves a prescription");
        }
        MedicineRequest request = requireRequest(id);
        MedicalSupplier shop = requireShop(request.getSupplierId());
        if (shop.getOwnerId().equals(user.id())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PRESCRIPTION_OWN_SHOP", "A Farm Adviser other than this shop approves the prescription");
        }
        if (!"AWAITING_PRESCRIPTION".equals(request.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "PRESCRIPTION_CLOSED", "This request is not waiting for a prescription");
        }
        boolean approve = body != null && Boolean.TRUE.equals(body.approve());
        request.setAdviserId(user.id());
        request.setAdviserNote(optional(body == null ? null : body.note(), 400));
        request.setStatus(approve ? "PRESCRIPTION_APPROVED" : "REJECTED");
        request.setUpdatedAt(Instant.now());
        requests.save(request);
        audit.record(approve ? "PRESCRIPTION_APPROVED" : "PRESCRIPTION_REJECTED", "MEDICINE_REQUEST", request.getId(), request.getMedicineName());
        audit.activity("MEDICAL", approve ? "Prescription approved" : "Prescription rejected", request.getMedicineName(), request.getFarmId());
        return requestView(request, Map.of(shop.getId(), shop), names(), farmNames(), user);
    }

    public RequestView decide(UUID id, NoteBody body) {
        MedicineRequest request = requireRequest(id);
        MedicalSupplier shop = owned(request.getSupplierId());
        String action = body == null ? "" : text(body.action()).toUpperCase(Locale.ROOT);
        String note = optional(body == null ? null : body.note(), 400);
        if (!note.isEmpty()) {
            request.setSupplierNote(note);
        }
        switch (action) {
            case "CONFIRM_COLLECTION", "CONFIRM_DELIVERY" -> confirm(request, action);
            case "REJECT" -> reject(request);
            case "MARK_READY" -> advance(request, "CONFIRMED_COLLECTION", "READY");
            case "MARK_COLLECTED" -> {
                advance(request, "READY", "COLLECTED");
                draw(request);
            }
            case "MARK_DISPATCHED" -> {
                advance(request, "CONFIRMED_DELIVERY", "OUT_FOR_DELIVERY");
                draw(request);
            }
            case "MARK_DELIVERED" -> advance(request, "OUT_FOR_DELIVERY", "DELIVERED");
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "ACTION", "Choose what to do with this request");
        }
        request.setUpdatedAt(Instant.now());
        requests.save(request);
        audit.record("MEDICINE_REQUEST_" + action, "MEDICINE_REQUEST", request.getId(), request.getMedicineName());
        audit.activity("MEDICAL", medicineTitle(action), request.getMedicineName(), request.getFarmId());
        return requestView(request, Map.of(shop.getId(), shop), names(), farmNames(), guard.current());
    }

    private String medicineTitle(String action) {
        return switch (action) {
            case "CONFIRM_COLLECTION", "CONFIRM_DELIVERY" -> "Medicine request confirmed";
            case "REJECT" -> "Medicine request rejected";
            case "MARK_READY" -> "Medicine marked ready";
            case "MARK_COLLECTED" -> "Medicine collected";
            case "MARK_DISPATCHED" -> "Medicine dispatched";
            case "MARK_DELIVERED" -> "Medicine delivered";
            default -> "Medicine request updated";
        };
    }

    private void confirm(MedicineRequest request, String action) {
        if (!"SUBMITTED".equals(request.getStatus()) && !"PRESCRIPTION_APPROVED".equals(request.getStatus())) {
            if ("AWAITING_PRESCRIPTION".equals(request.getStatus())) {
                throw new ApiException(HttpStatus.CONFLICT, "PRESCRIPTION_REQUIRED", "A Farm Adviser has to approve the prescription before this shop can supply it");
            }
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_CLOSED", "This request is already " + label(request.getStatus()).toLowerCase(Locale.ROOT));
        }
        if (request.getMedicineId() != null) {
            SupplierMedicine medicine = medicines.findByTenantIdAndId(request.getTenantId(), request.getMedicineId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEDICINE_NOT_FOUND", "Medicine not found"));
            if (medicine.getQuantity().compareTo(request.getQuantity()) < 0) {
                throw new ApiException(HttpStatus.CONFLICT, "STOCK_SHORT", "This shop does not have that quantity in stock");
            }
        }
        request.setStatus("CONFIRM_COLLECTION".equals(action) ? "CONFIRMED_COLLECTION" : "CONFIRMED_DELIVERY");
        request.setFulfillment("CONFIRM_COLLECTION".equals(action) ? "COLLECTION" : "DELIVERY");
    }

    private void reject(MedicineRequest request) {
        if (request.isStockDrawn() || "COLLECTED".equals(request.getStatus()) || "DELIVERED".equals(request.getStatus()) || "OUT_FOR_DELIVERY".equals(request.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_CLOSED", "This request has already left the shop");
        }
        if ("REJECTED".equals(request.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_CLOSED", "This request is already rejected");
        }
        request.setStatus("REJECTED");
    }

    private void advance(MedicineRequest request, String from, String to) {
        if (!from.equals(request.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_STEP", "This request is " + label(request.getStatus()).toLowerCase(Locale.ROOT));
        }
        request.setStatus(to);
    }

    private void draw(MedicineRequest request) {
        if (request.isStockDrawn() || request.getMedicineId() == null) {
            request.setStockDrawn(true);
            return;
        }
        SupplierMedicine medicine = medicines.findByTenantIdAndId(request.getTenantId(), request.getMedicineId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEDICINE_NOT_FOUND", "Medicine not found"));
        if (medicine.getQuantity().compareTo(request.getQuantity()) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "STOCK_SHORT", "Available stock cannot go negative");
        }
        BigDecimal left = medicine.getQuantity().subtract(request.getQuantity());
        medicine.setQuantity(left);
        if (left.compareTo(BigDecimal.ZERO) == 0) {
            medicine.setStockState("OUT");
        }
        medicines.save(medicine);
        request.setStockDrawn(true);
    }

    private List<RequestView> visibleRequests(CurrentUser user, List<MedicalSupplier> shops) {
        Map<UUID, MedicalSupplier> byId = shops.stream().collect(Collectors.toMap(MedicalSupplier::getId, Function.identity()));
        Set<UUID> owned = shops.stream().filter(shop -> shop.getOwnerId().equals(user.id())).map(MedicalSupplier::getId).collect(Collectors.toSet());
        Map<UUID, String> people = names();
        Map<UUID, String> farmNames = farmNames();
        boolean admin = guard.canManageAccess();
        return requests.findByTenantIdOrderByCreatedAtDesc(user.tenantId()).stream()
                .filter(request -> admin
                        || request.getRequesterId().equals(user.id())
                        || owned.contains(request.getSupplierId())
                        || (user.canAdvise() && ("AWAITING_PRESCRIPTION".equals(request.getStatus()) || user.id().equals(request.getAdviserId()))))
                .map(request -> requestView(request, byId, people, farmNames, user))
                .toList();
    }

    private RequestView requestView(MedicineRequest request, Map<UUID, MedicalSupplier> shops, Map<UUID, String> people,
                                    Map<UUID, String> farmNames, CurrentUser user) {
        MedicalSupplier shop = shops.get(request.getSupplierId());
        boolean ownedShop = shop != null && shop.getOwnerId().equals(user.id());
        return new RequestView(
                request.getId(),
                request.getSupplierId(),
                shop == null ? "Shop" : shop.getName(),
                request.getMedicineId(),
                request.getMedicineName(),
                request.getQuantity(),
                request.getUnitLabel(),
                request.getFulfillment(),
                request.getStatus(),
                request.isPrescriptionRequired(),
                people.getOrDefault(request.getRequesterId(), "Farmer"),
                request.getFarmId(),
                request.getFarmId() == null ? "" : farmNames.getOrDefault(request.getFarmId(), "Farm"),
                request.getRequesterNote() == null ? "" : request.getRequesterNote(),
                request.getAdviserId() == null ? "" : people.getOrDefault(request.getAdviserId(), "Farm Adviser"),
                request.getAdviserNote() == null ? "" : request.getAdviserNote(),
                request.getSupplierNote() == null ? "" : request.getSupplierNote(),
                ownedShop,
                request.getRequesterId().equals(user.id()),
                request.getCreatedAt(),
                request.getUpdatedAt()
        );
    }

    private SupplierView view(MedicalSupplier shop, List<SupplierMedicine> rows, boolean owned) {
        List<SupplierMedicine> ordered = rows.stream()
                .sorted(java.util.Comparator.comparing(SupplierMedicine::getCreatedAt))
                .toList();
        return new SupplierView(
                shop.getId(),
                shop.getName(),
                shop.getMark(),
                shop.getRegion(),
                shop.getDistrict(),
                shop.getAddress(),
                shop.getPhone(),
                shop.getOpenHours(),
                shop.getLatitude().doubleValue(),
                shop.getLongitude().doubleValue(),
                shop.getLicenseNumber(),
                shop.getLicenseAuthority() == null ? "" : shop.getLicenseAuthority(),
                shop.getLicenseExpiresOn(),
                shop.getProductsOffered() == null ? "" : shop.getProductsOffered(),
                shop.getSummary(),
                shop.getApproval(),
                shop.getShopState(),
                owned,
                ordered.stream().map(this::medicineView).toList()
        );
    }

    private MedicineView medicineView(SupplierMedicine row) {
        return new MedicineView(
                row.getId(),
                row.getName(),
                row.getAnimalTypes(),
                row.getCategory(),
                row.getPriceAmount(),
                row.getCurrencyCode(),
                row.getQuantity(),
                row.getUnitLabel(),
                row.getStockState(),
                row.isPrescriptionRequired(),
                row.getSummary() == null ? "" : row.getSummary()
        );
    }

    private void applyShop(MedicalSupplier shop, ShopBody body, boolean creating) {
        if (body == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SHOP", "Enter the shop details");
        }
        shop.setName(required(body.name(), 2, 160, "Enter the shop name"));
        String mark = text(body.mark()).toUpperCase(Locale.ROOT);
        if (!MARKS.contains(mark)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MARK", "Choose a shop mark");
        }
        shop.setMark(mark);
        shop.setRegion(required(body.region(), 2, 80, "Choose a region"));
        shop.setDistrict(required(body.district(), 2, 80, "Enter the district"));
        shop.setAddress(required(body.address(), 4, 240, "Enter the street address"));
        String phone = required(body.phone(), 7, 40, "Enter a phone number");
        if (phone.chars().noneMatch(Character::isDigit)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PHONE", "Enter a phone number");
        }
        shop.setPhone(phone);
        shop.setOpenHours(required(body.hours(), 2, 120, "Enter the opening hours"));
        if (body.latitude() == null || body.longitude() == null
                || body.latitude() < -12.2 || body.latitude() > -0.5
                || body.longitude() < 28.9 || body.longitude() > 40.9) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LOCATION", "Place the shop inside Tanzania");
        }
        shop.setLatitude(BigDecimal.valueOf(body.latitude()).setScale(6, RoundingMode.HALF_UP));
        shop.setLongitude(BigDecimal.valueOf(body.longitude()).setScale(6, RoundingMode.HALF_UP));
        shop.setLicenseNumber(required(body.licenseNumber(), 2, 80, "Enter the licence number"));
        shop.setLicenseAuthority(optional(body.licenseAuthority(), 160));
        shop.setLicenseExpiresOn(body.licenseExpiresOn());
        shop.setProductsOffered(optional(body.productsOffered(), 400));
        shop.setSummary(required(body.summary(), 2, 400, "Enter a short description of the shop"));
        if (creating || body.shopState() == null || body.shopState().isBlank()) {
            shop.setShopState(shop.getShopState() == null ? "OPEN" : shop.getShopState());
        } else {
            String state = body.shopState().trim().toUpperCase(Locale.ROOT);
            if (!SHOP_STATES.contains(state)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "SHOP_STATE", "Choose open, limited stock, or closed");
            }
            shop.setShopState(state);
        }
    }

    private void applyMedicine(SupplierMedicine row, MedicineBody body) {
        if (body == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MEDICINE", "Enter the medicine");
        }
        row.setName(required(body.name(), 2, 160, "Enter the medicine name"));
        row.setAnimalTypes(animals(body.animalTypes()));
        String category = text(body.category());
        if (!CATEGORIES.contains(category)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY", "Choose a treatment category");
        }
        row.setCategory(category);
        if (body.priceAmount() == null || body.priceAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRICE", "Enter the price in TZS or USD");
        }
        row.setPriceAmount(body.priceAmount().setScale(2, RoundingMode.HALF_UP));
        String currency = text(body.currency()).toUpperCase(Locale.ROOT);
        if (!CURRENCIES.contains(currency)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENCY", "Use TZS or USD");
        }
        row.setCurrencyCode(currency);
        if (body.quantity() == null || body.quantity().compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "QUANTITY", "Enter a quantity of zero or more");
        }
        row.setQuantity(body.quantity().setScale(2, RoundingMode.HALF_UP));
        row.setUnitLabel(required(body.unit(), 1, 40, "Enter the unit"));
        String stock = text(body.stock()).toUpperCase(Locale.ROOT);
        if (!STOCK.contains(stock)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "STOCK", "Choose in stock, limited stock, or out of stock");
        }
        if (row.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
            stock = "OUT";
        }
        row.setStockState(stock);
        row.setPrescriptionRequired(Boolean.TRUE.equals(body.prescriptionRequired()));
        if (body.summary() != null) {
            String summary = optional(body.summary(), 240);
            row.setSummary(summary.isEmpty() ? null : summary);
        }
    }

    private String animals(String raw) {
        Set<String> known = species.findByTenantIdOrderBySortOrderAsc(guard.tenantId()).stream()
                .map(Species::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        known.add("Any");
        LinkedHashSet<String> chosen = new LinkedHashSet<>();
        for (String part : text(raw).split(",")) {
            String name = part.trim();
            if (name.isEmpty()) {
                continue;
            }
            String match = known.stream().filter(item -> item.equalsIgnoreCase(name)).findFirst().orElse(null);
            if (match == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "ANIMAL", "Choose an animal type stored for this workspace");
            }
            chosen.add(match);
        }
        if (chosen.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ANIMAL", "Choose an animal type");
        }
        if (chosen.contains("Any")) {
            return "Any";
        }
        return String.join(", ", chosen);
    }

    private boolean matches(SupplierMedicine item, String query, String animal, String category) {
        if (!query.isEmpty() && !item.getName().toLowerCase(Locale.ROOT).contains(query)) {
            return false;
        }
        if (!animal.isEmpty() && !covers(item.getAnimalTypes(), animal)) {
            return false;
        }
        return category.isEmpty() || item.getCategory().equalsIgnoreCase(category);
    }

    private boolean covers(String types, String animal) {
        if ("Any".equalsIgnoreCase(types)) {
            return true;
        }
        for (String part : types.split(",")) {
            if (part.trim().equalsIgnoreCase(animal)) {
                return true;
            }
        }
        return false;
    }

    private MedicalSupplier owned(UUID id) {
        MedicalSupplier shop = requireShop(id);
        if (!shop.getOwnerId().equals(guard.current().id())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SHOP_FORBIDDEN", "Only this shop can update its stock and requests");
        }
        return shop;
    }

    private MedicalSupplier requireShop(UUID id) {
        return suppliers.findByTenantIdAndId(guard.tenantId(), id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "Shop not found"));
    }

    private MedicineRequest requireRequest(UUID id) {
        return requests.findByTenantIdAndId(guard.tenantId(), id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND", "Request not found"));
    }

    private Map<UUID, String> names() {
        return users.findByTenantIdOrderByFullNameAsc(guard.tenantId()).stream()
                .collect(Collectors.toMap(UserAccount::getId, UserAccount::getFullName, (left, right) -> left));
    }

    private Map<UUID, String> farmNames() {
        return farms.findByTenantIdOrderByNameAsc(guard.tenantId()).stream()
                .collect(Collectors.toMap(Farm::getId, Farm::getName, (left, right) -> left));
    }

    private BigDecimal positive(BigDecimal value, String message) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0 || value.compareTo(new BigDecimal("100000")) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "QUANTITY", message);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String required(String value, int min, int max, String message) {
        String trimmed = text(value);
        if (trimmed.length() < min || trimmed.length() > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FIELD", message);
        }
        return trimmed;
    }

    private String optional(String value, int max) {
        String trimmed = text(value);
        if (trimmed.length() > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOTE", "Keep the note to " + max + " characters");
        }
        return trimmed;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    static String label(String status) {
        return switch (status) {
            case "AWAITING_PRESCRIPTION" -> "Waiting for a Farm Adviser";
            case "PRESCRIPTION_APPROVED" -> "Prescription approved";
            case "SUBMITTED" -> "Submitted";
            case "CONFIRMED_COLLECTION" -> "Confirmed for collection";
            case "CONFIRMED_DELIVERY" -> "Confirmed for delivery";
            case "READY" -> "Ready";
            case "COLLECTED" -> "Collected";
            case "OUT_FOR_DELIVERY" -> "Out for delivery";
            case "DELIVERED" -> "Delivered";
            case "REJECTED" -> "Rejected";
            default -> status;
        };
    }
}
