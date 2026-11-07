package com.shambanexa.implementation;

import com.shambanexa.config.AppProperties;
import com.shambanexa.common.AccessScopes;
import com.shambanexa.common.ApiException;
import com.shambanexa.common.CurrentUser;
import com.shambanexa.common.RolePermissions;
import com.shambanexa.common.TenantContext;
import com.shambanexa.dto.AuthResponse;
import com.shambanexa.dto.FarmRequest;
import com.shambanexa.dto.MarketDtos.SellerBody;
import com.shambanexa.dto.MedicalDtos.ShopBody;
import com.shambanexa.dto.RegisterRequest;
import com.shambanexa.entity.ServiceProfile;
import com.shambanexa.entity.Tenant;
import com.shambanexa.entity.UserAccount;
import com.shambanexa.entity.UserRoleGrant;
import com.shambanexa.repository.ServiceProfileRepository;
import com.shambanexa.repository.TenantRepository;
import com.shambanexa.repository.UserAccountRepository;
import com.shambanexa.repository.UserRoleGrantRepository;
import com.shambanexa.service.AuditTrail;
import com.shambanexa.service.AuthService;
import com.shambanexa.service.FarmService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class OnboardingService {

    private static final String FARM = "FARM";
    private static final Set<String> KINDS = Set.of(
            FARM,
            RolePermissions.MEDICAL_EXPERT,
            RolePermissions.MEDICAL_SELLER,
            RolePermissions.MARKET_BUYER,
            RolePermissions.MARKET_SELLER,
            RolePermissions.AGRICULTURAL_TRAINER
    );

    private final TenantRepository tenants;
    private final UserAccountRepository users;
    private final UserRoleGrantRepository grants;
    private final ServiceProfileRepository profiles;
    private final FarmService farms;
    private final MedicalSupplierService medical;
    private final MarketplaceService market;
    private final AuditTrail audit;
    private final AuthService auth;
    private final PasswordEncoder passwords;
    private final AppProperties properties;

    public OnboardingService(TenantRepository tenants, UserAccountRepository users, UserRoleGrantRepository grants,
                             ServiceProfileRepository profiles, FarmService farms, MedicalSupplierService medical,
                             MarketplaceService market, AuditTrail audit, AuthService auth, PasswordEncoder passwords,
                             AppProperties properties) {
        this.tenants = tenants;
        this.users = users;
        this.grants = grants;
        this.profiles = profiles;
        this.farms = farms;
        this.medical = medical;
        this.market = market;
        this.audit = audit;
        this.auth = auth;
        this.passwords = passwords;
        this.properties = properties;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (!properties.isRegistrationEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REGISTRATION_CLOSED", "Registration is closed for this workspace.");
        }
        String kind = kind(request.accountKind());
        String email = request.email().trim().toLowerCase();
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "An account with that email already exists");
        }
        Tenant tenant = tenants.findAll().stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "TENANT_MISSING", "The workspace is not ready for registration"));
        String role = FARM.equals(kind) ? RolePermissions.FARM_MANAGER : kind;
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setTenantId(tenant.getId());
        user.setEmail(email);
        user.setPasswordHash(passwords.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setJobTitle(RolePermissions.label(role));
        user.setPhone(phone(request.contactPhone(), false));
        user.setStatus("ACTIVE");
        user.setAccessScope(AccessScopes.OWN_FARM);
        users.saveAndFlush(user);

        if (!FARM.equals(kind)) {
            UserRoleGrant grant = new UserRoleGrant();
            grant.setTenantId(tenant.getId());
            grant.setUserId(user.getId());
            grant.setRoleCode(role);
            grants.save(grant);
        }

        Set<String> authorities = new LinkedHashSet<>();
        authorities.add("ROLE_" + role);
        authorities.addAll(RolePermissions.permissions(role));
        CurrentUser current = new CurrentUser(
                user.getId(),
                tenant.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getJobTitle(),
                RolePermissions.label(role),
                false,
                Set.of(),
                authorities,
                AccessScopes.OWN_FARM,
                false,
                authorities.contains("ANIMAL_WRITE"),
                authorities.contains("ADVICE_WRITE")
        );
        TenantContext.set(current);
        try {
            if (FARM.equals(kind)) {
                requireText(request.farmName(), 2, 160, "Enter the farm name");
                requireText(request.farmCode(), 1, 40, "Enter a farm code");
                requireText(request.region(), 2, 80, "Choose a region");
                requireText(request.address(), 4, 300, "Enter the address");
                requirePoint(request);
                farms.create(new FarmRequest(
                        request.farmCode(),
                        request.farmName(),
                        request.region(),
                        request.latitude(),
                        request.longitude(),
                        request.address(),
                        request.fullName(),
                        request.contactPhone() == null ? "" : request.contactPhone(),
                        null,
                        request.boundary()
                ));
            } else if (RolePermissions.MEDICAL_SELLER.equals(kind)) {
                medical.register(shop(request));
            } else if (RolePermissions.MARKET_SELLER.equals(kind)) {
                market.registerSeller(seller(request));
            } else if (RolePermissions.MARKET_BUYER.equals(kind)) {
                requireText(request.region(), 2, 80, "Choose a region");
            } else {
                saveServiceProfile(user, kind, request);
            }
            audit.record("ACCOUNT_REGISTERED", "USER", user.getId(), RolePermissions.label(role));
        } finally {
            TenantContext.clear();
        }
        return auth.login(email, request.password());
    }

    private ShopBody shop(RegisterRequest request) {
        requirePoint(request);
        return new ShopBody(
                request.organizationName(),
                request.mark() == null || request.mark().isBlank() ? "LEAVES" : request.mark(),
                request.region(),
                request.district(),
                request.address(),
                request.contactPhone(),
                request.hours(),
                request.latitude() == null ? null : request.latitude().doubleValue(),
                request.longitude() == null ? null : request.longitude().doubleValue(),
                request.licenseNumber(),
                null,
                null,
                null,
                request.summary(),
                "OPEN"
        );
    }

    private SellerBody seller(RegisterRequest request) {
        requirePoint(request);
        return new SellerBody(
                request.organizationName(),
                request.region(),
                request.district(),
                request.contactPhone(),
                request.latitude() == null ? null : request.latitude().doubleValue(),
                request.longitude() == null ? null : request.longitude().doubleValue(),
                request.summary()
        );
    }

    private void saveServiceProfile(UserAccount user, String kind, RegisterRequest request) {
        ServiceProfile profile = new ServiceProfile();
        profile.setTenantId(user.getTenantId());
        profile.setUserId(user.getId());
        profile.setKind(kind);
        profile.setRegion(requireText(request.region(), 2, 80, "Choose a region"));
        profile.setDistrict(blank(request.district()));
        profile.setPhone(phone(request.contactPhone(), true));
        profile.setSummary(requireText(request.summary(), 2, 400, "Enter a short description"));
        profiles.save(profile);
    }

    private String kind(String value) {
        String next = value == null || value.isBlank() ? FARM : value.trim().toUpperCase();
        if (!KINDS.contains(next)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ACCOUNT_KIND", "Choose Farmer / Farm Manager, Veterinary Professional, Veterinary Medicine Supplier, Agricultural Buyer, Agricultural Seller, or Agricultural Trainer.");
        }
        return next;
    }

    private void requirePoint(RegisterRequest request) {
        if (request.latitude() == null || request.longitude() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LOCATION", "Place the location on the map");
        }
    }

    private String requireText(String value, int min, int max, String message) {
        String text = value == null ? "" : value.trim();
        if (text.length() < min || text.length() > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REGISTRATION", message);
        }
        return text;
    }

    private String phone(String value, boolean required) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            if (required) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PHONE", "Enter a phone number");
            }
            return null;
        }
        if (text.length() > 40 || text.chars().noneMatch(Character::isDigit)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PHONE", "Enter a phone number");
        }
        return text;
    }

    private String blank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        return text.length() > 80 ? text.substring(0, 80) : text;
    }
}
