package com.shambanexa.common;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RolePermissions {

    public static final String TENANT_ADMIN = "TENANT_ADMIN";
    public static final String FARM_MANAGER = "FARM_MANAGER";
    public static final String FARM_EXPERT = "FARM_EXPERT";
    public static final String AUDITOR = "AUDITOR";

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
            )
    );

    private RolePermissions() {
    }

    public static List<String> permissions(String roleCode) {
        return PERMISSIONS.getOrDefault(roleCode, List.of());
    }

    public static String label(String roleCode) {
        return switch (roleCode) {
            case TENANT_ADMIN -> "Super Admin";
            case FARM_MANAGER -> "Farm Manager";
            case FARM_EXPERT -> "Farm Adviser";
            case AUDITOR -> "Auditor";
            default -> roleCode;
        };
    }

    public static String description(String roleCode) {
        return switch (roleCode) {
            case TENANT_ADMIN -> "Full system access. Can manage users and assign Own Farm Only, Selected Farms, or All Farms.";
            case FARM_MANAGER -> "Runs the assigned farms: updates livestock, stock, staff, and farm records. Does not post adviser listings.";
            case FARM_EXPERT -> "Advises farmers, publishes recommendations, and can be found, rated, and recommended. Does not change livestock, stock, or staff records.";
            case AUDITOR -> "Read-only review of the farms in the assigned access scope. Operational records stay unchanged.";
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
        Set<String> held = Set.copyOf(permissions(roleCode));
        boolean readFarm = held.contains("FARM_READ");
        boolean writeFarm = held.contains("FARM_WRITE");
        boolean readAnimal = held.contains("ANIMAL_READ");
        boolean writeAnimal = held.contains("ANIMAL_WRITE");
        boolean readStock = held.contains("INVENTORY_READ");
        boolean writeStock = held.contains("INVENTORY_WRITE");
        boolean readStaff = held.contains("STAFF_READ");
        boolean writeStaff = held.contains("STAFF_WRITE");
        boolean export = held.contains("REPORT_EXPORT");
        boolean admin = TENANT_ADMIN.equals(roleCode);
        boolean advise = FARM_EXPERT.equals(roleCode);
        return List.of(
                module("dashboard", "Dashboard", true, false, false, false, false, export,
                        "Every signed-in role can open the dashboard. Export uses REPORT_EXPORT."),
                module("farms", "Farms", readFarm, writeFarm, writeFarm, false, false, false,
                        "View uses FARM_READ. Create and edit use FARM_WRITE."),
                module("livestock", "Livestock", readAnimal, writeAnimal, writeAnimal, false, false, false,
                        "View uses ANIMAL_READ. Registering and transferring use ANIMAL_WRITE."),
                module("production", "Production", readAnimal, writeAnimal, writeAnimal, false, false, false,
                        "Recording production uses ANIMAL_WRITE, limited to farms on the grant."),
                module("inventory", "Inventory", readStock, writeStock, writeStock, false, false, false,
                        "View uses INVENTORY_READ. Creating and adjusting stock uses INVENTORY_WRITE."),
                module("staff", "Staff & Tasks", readStaff, writeStaff, writeStaff, false, false, false,
                        "View uses STAFF_READ. Adding a staff record uses STAFF_WRITE."),
                module("reports", "Reports", true, false, false, false, false, export,
                        "Reports can be opened by a signed-in role. The CSV export uses REPORT_EXPORT."),
                module("advice", "Expert advice", true, advise, advise, false, false, false,
                        "Every signed-in role can read advice. Only a Farm Adviser can post or remove their own advice."),
                module("system", "System Administration", admin, false, false, false, false, false,
                        "Users & Roles is limited to the tenant administrator. Role definitions are not edited here.")
        );
    }

    private static ModuleAccess module(String id, String label, boolean view, boolean create, boolean edit,
                                       boolean delete, boolean approve, boolean export, String basis) {
        return new ModuleAccess(id, label, view, create, edit, delete, approve, export, basis);
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
