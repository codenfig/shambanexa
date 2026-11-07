package com.shambanexa.common;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class RolePermissions {

    public static final String TENANT_ADMIN = "TENANT_ADMIN";
    public static final String FARM_MANAGER = "FARM_MANAGER";
    public static final String FARM_EXPERT = "FARM_EXPERT";
    public static final String AUDITOR = "AUDITOR";
    public static final String MEDICAL_EXPERT = "MEDICAL_EXPERT";
    public static final String MEDICAL_SELLER = "MEDICAL_SELLER";
    public static final String MARKET_BUYER = "MARKET_BUYER";
    public static final String MARKET_SELLER = "MARKET_SELLER";
    public static final String AGRICULTURAL_TRAINER = "AGRICULTURAL_TRAINER";

    private static final Map<String, List<String>> PERMISSIONS = Map.of(
            TENANT_ADMIN, List.of(
                    "FARM_READ", "FARM_WRITE",
                    "ANIMAL_READ", "ANIMAL_WRITE",
                    "INVENTORY_READ", "INVENTORY_WRITE",
                    "STAFF_READ", "STAFF_WRITE",
                    "REPORT_EXPORT"
            ),
            FARM_MANAGER, List.of(
                    "FARM_READ", "FARM_WRITE",
                    "ANIMAL_READ", "ANIMAL_WRITE",
                    "INVENTORY_READ", "INVENTORY_WRITE",
                    "STAFF_READ", "STAFF_WRITE",
                    "REPORT_EXPORT"
            ),
            FARM_EXPERT, List.of(
                    "FARM_READ",
                    "ANIMAL_READ",
                    "INVENTORY_READ",
                    "STAFF_READ",
                    "REPORT_EXPORT",
                    "ADVICE_WRITE"
            ),
            AUDITOR, List.of(
                    "FARM_READ",
                    "ANIMAL_READ",
                    "INVENTORY_READ",
                    "STAFF_READ",
                    "REPORT_EXPORT"
            ),
            MEDICAL_EXPERT, List.of(
                    "FARM_READ",
                    "ANIMAL_READ",
                    "INVENTORY_READ",
                    "STAFF_READ",
                    "REPORT_EXPORT",
                    "ADVICE_WRITE"
            ),
            MEDICAL_SELLER, List.of(),
            MARKET_BUYER, List.of(),
            MARKET_SELLER, List.of(),
            AGRICULTURAL_TRAINER, List.of()
    );

    public static final List<PermissionChoice> CHOICES = List.of(
            new PermissionChoice("FARM_READ", "View farms", "Open farm records."),
            new PermissionChoice("FARM_WRITE", "Update farms", "Edit farm records. Includes view."),
            new PermissionChoice("ANIMAL_READ", "View livestock and production", "Open animals, herds, and production records."),
            new PermissionChoice("ANIMAL_WRITE", "Update livestock and production", "Register, transfer, and record production. Includes view."),
            new PermissionChoice("INVENTORY_READ", "View inventory", "Open stock records."),
            new PermissionChoice("INVENTORY_WRITE", "Update inventory", "Record receipts, issues, and transfers. Includes view."),
            new PermissionChoice("STAFF_READ", "View staff", "Open staff records."),
            new PermissionChoice("STAFF_WRITE", "Update staff", "Add staff and workforce records. Includes view."),
            new PermissionChoice("REPORT_EXPORT", "Export reports", "Download the report CSV."),
            new PermissionChoice("ADVICE_WRITE", "Post farm advice", "Publish and remove the signed-in adviser's own advice.")
    );

    private static final Map<String, String> READ_FOR_WRITE = Map.of(
            "FARM_WRITE", "FARM_READ",
            "ANIMAL_WRITE", "ANIMAL_READ",
            "INVENTORY_WRITE", "INVENTORY_READ",
            "STAFF_WRITE", "STAFF_READ"
    );

    private RolePermissions() {
    }

    public static boolean isSystem(String roleCode) {
        return PERMISSIONS.containsKey(roleCode);
    }

    public static List<String> permissions(String roleCode) {
        return PERMISSIONS.getOrDefault(roleCode, List.of());
    }

    public static List<String> normalize(Collection<String> selected) {
        if (selected == null || selected.isEmpty()) {
            return List.of();
        }
        Set<String> known = CHOICES.stream().map(PermissionChoice::code).collect(Collectors.toSet());
        LinkedHashSet<String> held = new LinkedHashSet<>();
        for (String code : selected) {
            if (code == null || !known.contains(code)) {
                return null;
            }
            held.add(code);
            String read = READ_FOR_WRITE.get(code);
            if (read != null) {
                held.add(read);
            }
        }
        return List.copyOf(held);
    }

    public static String label(String roleCode) {
        return switch (roleCode) {
            case TENANT_ADMIN -> "Super Admin";
            case FARM_MANAGER -> "Farmer / Farm Manager";
            case FARM_EXPERT -> "Farm Adviser";
            case AUDITOR -> "Auditor";
            case MEDICAL_EXPERT -> "Veterinary Professional";
            case MEDICAL_SELLER -> "Veterinary Medicine Supplier";
            case MARKET_BUYER -> "Agricultural Buyer";
            case MARKET_SELLER -> "Agricultural Seller";
            case AGRICULTURAL_TRAINER -> "Agricultural Trainer";
            default -> roleCode;
        };
    }

    public static String description(String roleCode) {
        return switch (roleCode) {
            case TENANT_ADMIN -> "Full system access. Can manage users and assign Own Farm Only, Selected Farms, or All Farms.";
            case FARM_MANAGER -> "Runs the assigned farms: updates livestock, stock, staff, and farm records. Does not post adviser listings.";
            case FARM_EXPERT -> "Advises farmers, publishes recommendations, and can be found, rated, and recommended. Does not change livestock, stock, or staff records.";
            case AUDITOR -> "Read-only review of the farms in the assigned access scope. Operational records stay unchanged.";
            case MEDICAL_EXPERT -> "A veterinary professional who advises on animal health and can approve a prescription-required medicine request. Does not change livestock, stock, or staff records, and does not replace Farm Adviser.";
            case MEDICAL_SELLER -> "A veterinary medicine supplier. The shop stays hidden until an administrator approves the licence. Does not change farm records.";
            case MARKET_BUYER -> "An agricultural buyer who browses the marketplace and can post a buying request. Does not change farm records.";
            case MARKET_SELLER -> "An agricultural seller. Listings stay hidden until an administrator approves the seller. Does not change farm records.";
            case AGRICULTURAL_TRAINER -> "Offers agricultural training. Does not change livestock, stock, or staff records.";
            default -> "Access follows the grants stored for this role.";
        };
    }

    public static boolean canManageUsers(String roleCode) {
        return TENANT_ADMIN.equals(roleCode);
    }

    /**
     * Maps stored permission codes onto the modules the workspace actually gates.
     * Delete and approval are not separate permissions.
     */
    public static List<ModuleAccess> modules(String roleCode) {
        return modules(permissions(roleCode), TENANT_ADMIN.equals(roleCode));
    }

    public static List<ModuleAccess> modules(Collection<String> permissions, boolean admin) {
        Set<String> held = permissions == null ? Set.of() : Set.copyOf(permissions);
        boolean readFarm = held.contains("FARM_READ");
        boolean writeFarm = held.contains("FARM_WRITE");
        boolean readAnimal = held.contains("ANIMAL_READ");
        boolean writeAnimal = held.contains("ANIMAL_WRITE");
        boolean readStock = held.contains("INVENTORY_READ");
        boolean writeStock = held.contains("INVENTORY_WRITE");
        boolean readStaff = held.contains("STAFF_READ");
        boolean writeStaff = held.contains("STAFF_WRITE");
        boolean export = held.contains("REPORT_EXPORT");
        boolean advise = held.contains("ADVICE_WRITE");
        return List.of(
                module("dashboard", "Dashboard", true, false, false, false, false, export,
                        "Every signed-in role can open the dashboard. Export uses REPORT_EXPORT."),
                module("farms", "Farms", readFarm, writeFarm, writeFarm, false, false, false,
                        "View uses FARM_READ. Create and edit use FARM_WRITE."),
                module("livestock", "Livestock", readAnimal, writeAnimal, writeAnimal, false, false, false,
                        "View uses ANIMAL_READ. Registering and transferring use ANIMAL_WRITE."),
                module("production", "Production", readAnimal, writeAnimal, writeAnimal, false, false, false,
                        "Recording production uses ANIMAL_WRITE, limited to farms on the grant."),
                module("inventory", "Inventory", readStock, writeStock, writeStock, false, admin, false,
                        "View uses INVENTORY_READ. Receipts, issues, and transfers use INVENTORY_WRITE. A super admin approves adjustments before stock changes."),
                module("staff", "Staff & Tasks", readStaff, writeStaff, writeStaff, false, false, false,
                        "View uses STAFF_READ. Adding a staff record uses STAFF_WRITE."),
                module("reports", "Reports", true, false, false, false, false, export,
                        "Reports can be opened by a signed-in role. The CSV export uses REPORT_EXPORT."),
                module("advice", "Expert advice", true, advise, advise, false, false, false,
                        "Every signed-in role can read advice. Posting uses ADVICE_WRITE. Farm Adviser includes that permission."),
                module("medical", "Medical Suppliers", true, true, true, false, admin, false,
                        "Every signed-in role can search approved shops and request medicine. The shop owner updates that shop. A super admin verifies the licence before the shop appears in search. A Farm Adviser approves a prescription-required request."),
                module("market", "Agricultural Marketplace", true, true, true, false, admin, false,
                        "Every signed-in role can browse listings and post a buying request. A seller profile is verified by a super admin before that seller can list produce or send an offer. Accepting an offer reduces the listing quantity."),
                module("system", "System Administration", admin, false, false, false, false, false,
                        "Users & Roles is limited to the tenant administrator.")
        );
    }

    private static ModuleAccess module(String id, String label, boolean view, boolean create, boolean edit,
                                       boolean delete, boolean approve, boolean export, String basis) {
        return new ModuleAccess(id, label, view, create, edit, delete, approve, export, basis);
    }

    public record PermissionChoice(String code, String label, String detail) {
    }

    public record ModuleAccess(
            String id,
            String label,
            boolean view,
            boolean create,
            boolean edit,
            boolean delete,
            boolean approve,
            boolean export,
            String basis
    ) {
    }
}
