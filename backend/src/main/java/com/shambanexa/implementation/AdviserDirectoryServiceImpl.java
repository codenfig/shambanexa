package com.shambanexa.implementation;

import com.shambanexa.common.AccessScopes;
import com.shambanexa.common.ApiException;
import com.shambanexa.common.RolePermissions;
import com.shambanexa.dto.AdviserEndorsement;
import com.shambanexa.dto.AdviserPlace;
import com.shambanexa.dto.AdviserProfileRequest;
import com.shambanexa.dto.AdviserRecommendationRequest;
import com.shambanexa.dto.AdviserRatingRequest;
import com.shambanexa.dto.AdviserResponse;
import com.shambanexa.entity.AdviserProfile;
import com.shambanexa.entity.AdviserRating;
import com.shambanexa.entity.AdviserRecommendation;
import com.shambanexa.entity.Alert;
import com.shambanexa.entity.Farm;
import com.shambanexa.entity.UserAccount;
import com.shambanexa.entity.UserRoleGrant;
import com.shambanexa.entity.WorkTask;
import com.shambanexa.repository.AdviserProfileRepository;
import com.shambanexa.repository.AdviserRatingRepository;
import com.shambanexa.repository.AdviserRecommendationRepository;
import com.shambanexa.repository.AlertRepository;
import com.shambanexa.repository.FarmRepository;
import com.shambanexa.repository.UserAccountRepository;
import com.shambanexa.repository.UserRoleGrantRepository;
import com.shambanexa.repository.WorkTaskRepository;
import com.shambanexa.security.AccessGuard;
import com.shambanexa.service.AdviserDirectoryService;
import com.shambanexa.service.AuditTrail;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdviserDirectoryServiceImpl implements AdviserDirectoryService {

    static final String AVAILABLE = "AVAILABLE";
    static final String LIMITED = "LIMITED";
    static final String UNAVAILABLE = "UNAVAILABLE";
    static final String UNSET = "UNSET";
    private static final Set<String> AVAILABILITY = Set.of(AVAILABLE, LIMITED, UNAVAILABLE);
    private static final Set<String> AVAILABILITY_FILTER = Set.of(AVAILABLE, LIMITED, UNAVAILABLE, UNSET);
    private static final Set<String> LOADS = Set.of("Light", "Steady", "Heavy");

    private final UserAccountRepository users;
    private final UserRoleGrantRepository grants;
    private final FarmRepository farms;
    private final AlertRepository alerts;
    private final WorkTaskRepository tasks;
    private final AdviserProfileRepository profiles;
    private final AdviserRatingRepository ratings;
    private final AdviserRecommendationRepository recommendations;
    private final AccessGuard access;
    private final AuditTrail audit;

    public AdviserDirectoryServiceImpl(
            UserAccountRepository users,
            UserRoleGrantRepository grants,
            FarmRepository farms,
            AlertRepository alerts,
            WorkTaskRepository tasks,
            AdviserProfileRepository profiles,
            AdviserRatingRepository ratings,
            AdviserRecommendationRepository recommendations,
            AccessGuard access,
            AuditTrail audit
    ) {
        this.users = users;
        this.grants = grants;
        this.farms = farms;
        this.alerts = alerts;
        this.tasks = tasks;
        this.profiles = profiles;
        this.ratings = ratings;
        this.recommendations = recommendations;
        this.access = access;
        this.audit = audit;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdviserResponse> list(String talent, String availability, String load) {
        String talentQuery = blank(talent);
        String availabilityQuery = blank(availability);
        String loadQuery = blank(load);
        if (availabilityQuery != null && !AVAILABILITY_FILTER.contains(availabilityQuery)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Choose available, limited, unavailable, or not set");
        }
        if (loadQuery != null && !LOADS.contains(loadQuery)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Choose a light, steady, or heavy load");
        }
        String talentNeedle = talentQuery == null ? null : talentQuery.toLowerCase(Locale.ROOT);
        return cards().stream()
                .filter(card -> talentNeedle == null || card.talents().stream().anyMatch(item -> item.toLowerCase(Locale.ROOT).contains(talentNeedle)))
                .filter(card -> availabilityQuery == null || availabilityQuery.equals(card.availability()))
                .filter(card -> loadQuery == null || loadQuery.equals(card.load()))
                .toList();
    }

    @Override
    @Transactional
    public AdviserResponse updateProfile(AdviserProfileRequest request) {
        access.requireAuthority("ADVICE_WRITE");
        UserAccount adviser = requireExpert(access.current().id());
        List<String> talents = cleanTalents(request == null ? null : request.talents());
        String availability = request == null ? null : blank(request.availability());
        if (availability == null || !AVAILABILITY.contains(availability)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Choose available, limited, or unavailable");
        }
        String note = clip(blank(request == null ? null : request.availabilityNote()), 240);
        AdviserProfile profile = profiles.findByTenantIdAndUserId(access.tenantId(), adviser.getId())
                .orElseGet(AdviserProfile::new);
        profile.setTenantId(access.tenantId());
        profile.setUserId(adviser.getId());
        profile.setTalents(String.join("\n", talents));
        profile.setAvailability(availability);
        profile.setAvailabilityNote(note);
        profiles.save(profile);
        audit.record("ADVISER_PROFILE_UPDATED", "ADVISER", adviser.getId(), adviser.getFullName());
        audit.activity("ADVISER", "Adviser profile updated", adviser.getFullName(), null);
        return card(adviser.getId());
    }

    @Override
    @Transactional
    public AdviserResponse rate(UUID adviserId, AdviserRatingRequest request) {
        UserAccount adviser = requireExpert(adviserId);
        if (adviser.getId().equals(access.current().id())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "You cannot rate your own listing");
        }
        int score = request == null ? 0 : request.score();
        if (score < 1 || score > 5) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Choose a rating from 1 to 5");
        }
        String note = clip(blank(request.note()), 400);
        AdviserRating rating = ratings.findByAdviserIdAndRaterId(adviser.getId(), access.current().id())
                .filter(row -> access.tenantId().equals(row.getTenantId()))
                .orElseGet(AdviserRating::new);
        rating.setTenantId(access.tenantId());
        rating.setAdviserId(adviser.getId());
        rating.setRaterId(access.current().id());
        rating.setScore(score);
        rating.setNote(note);
        rating.setCreatedAt(Instant.now());
        ratings.save(rating);
        audit.record("ADVISER_RATED", "ADVISER", adviser.getId(), adviser.getFullName() + " · " + score);
        audit.activity("ADVISER", "Adviser rated", adviser.getFullName() + " · " + score, null);
        return card(adviser.getId());
    }

    @Override
    @Transactional
    public AdviserResponse recommend(UUID adviserId, AdviserRecommendationRequest request) {
        UserAccount adviser = requireExpert(adviserId);
        if (adviser.getId().equals(access.current().id())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "You cannot recommend your own listing");
        }
        String note = clip(blank(request == null ? null : request.note()), 400);
        AdviserRecommendation row = recommendations.findByAdviserIdAndRecommenderId(adviser.getId(), access.current().id())
                .filter(item -> access.tenantId().equals(item.getTenantId()))
                .orElseGet(AdviserRecommendation::new);
        row.setTenantId(access.tenantId());
        row.setAdviserId(adviser.getId());
        row.setRecommenderId(access.current().id());
        row.setNote(note);
        if (row.getCreatedAt() == null) {
            row.setCreatedAt(Instant.now());
        }
        recommendations.save(row);
        audit.record("ADVISER_RECOMMENDED", "ADVISER", adviser.getId(), adviser.getFullName());
        audit.activity("ADVISER", "Adviser recommended", adviser.getFullName(), null);
        return card(adviser.getId());
    }

    @Override
    @Transactional
    public AdviserResponse withdrawRecommendation(UUID adviserId) {
        UserAccount adviser = requireExpert(adviserId);
        AdviserRecommendation row = recommendations.findByAdviserIdAndRecommenderId(adviser.getId(), access.current().id())
                .filter(item -> access.tenantId().equals(item.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RECOMMENDATION_NOT_FOUND", "You have not recommended this adviser"));
        recommendations.delete(row);
        audit.record("ADVISER_RECOMMENDATION_WITHDRAWN", "ADVISER", adviser.getId(), adviser.getFullName());
        audit.activity("ADVISER", "Recommendation withdrawn", adviser.getFullName(), null);
        return card(adviser.getId());
    }

    private List<AdviserResponse> cards() {
        UUID tenantId = access.tenantId();
        UUID me = access.current().id();
        Map<UUID, UserAccount> people = users.findByTenantIdOrderByFullNameAsc(tenantId).stream()
                .collect(Collectors.toMap(UserAccount::getId, user -> user, (left, right) -> left, LinkedHashMap::new));
        Map<UUID, List<UserRoleGrant>> expertGrants = grants.findByTenantId(tenantId).stream()
                .filter(grant -> RolePermissions.FARM_EXPERT.equals(grant.getRoleCode()))
                .collect(Collectors.groupingBy(UserRoleGrant::getUserId));
        Map<UUID, Farm> farmById = farms.findByTenantIdOrderByNameAsc(tenantId).stream()
                .collect(Collectors.toMap(Farm::getId, farm -> farm));
        List<Alert> openAlerts = alerts.findByTenantIdAndStatusOrderByOccurredAtDesc(tenantId, "OPEN");
        List<WorkTask> openTasks = tasks.findByTenantIdOrderByDueOnAsc(tenantId).stream()
                .filter(task -> "OPEN".equals(task.getStatus()))
                .toList();
        Map<UUID, AdviserProfile> profileByUser = profiles.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(AdviserProfile::getUserId, profile -> profile, (left, right) -> left));
        List<AdviserRating> tenantRatings = ratings.findByTenantId(tenantId);
        List<AdviserRecommendation> tenantRecommendations = recommendations.findByTenantId(tenantId);
        return expertGrants.keySet().stream()
                .map(people::get)
                .filter(user -> user != null && "ACTIVE".equals(user.getStatus()))
                .sorted(Comparator.comparing(UserAccount::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(user -> toCard(
                        user,
                        expertGrants.getOrDefault(user.getId(), List.of()),
                        farmById,
                        openAlerts,
                        openTasks,
                        profileByUser.get(user.getId()),
                        tenantRatings,
                        tenantRecommendations,
                        people,
                        me
                ))
                .toList();
    }

    private AdviserResponse card(UUID adviserId) {
        return cards().stream()
                .filter(item -> adviserId.equals(item.id()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ADVISER_NOT_FOUND", "That adviser was not found"));
    }

    private AdviserResponse toCard(
            UserAccount user,
            List<UserRoleGrant> roleGrants,
            Map<UUID, Farm> farmById,
            List<Alert> openAlerts,
            List<WorkTask> openTasks,
            AdviserProfile profile,
            List<AdviserRating> tenantRatings,
            List<AdviserRecommendation> tenantRecommendations,
            Map<UUID, UserAccount> people,
            UUID me
    ) {
        boolean allFarms = AccessScopes.ALL_FARMS.equals(user.getAccessScope());
        List<AdviserPlace> places = roleGrants.stream()
                .map(UserRoleGrant::getFarmId)
                .filter(Objects::nonNull)
                .distinct()
                .map(farmById::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Farm::getName, String.CASE_INSENSITIVE_ORDER))
                .map(farm -> new AdviserPlace(farm.getId(), farm.getName(), farm.getRegion(), farm.getLatitude(), farm.getLongitude()))
                .toList();
        Set<UUID> farmIds = places.stream().map(AdviserPlace::farmId).collect(Collectors.toSet());
        int alertCount = (int) openAlerts.stream()
                .filter(alert -> countsFor(allFarms, farmIds, alert.getFarmId()))
                .count();
        int taskCount = (int) openTasks.stream()
                .filter(task -> countsFor(allFarms, farmIds, task.getFarmId()))
                .count();
        List<AdviserRating> mineAndOthers = tenantRatings.stream()
                .filter(rating -> user.getId().equals(rating.getAdviserId()))
                .toList();
        int ratingCount = mineAndOthers.size();
        int ratingSum = mineAndOthers.stream().mapToInt(AdviserRating::getScore).sum();
        BigDecimal average = ratingCount == 0
                ? null
                : BigDecimal.valueOf(ratingSum).divide(BigDecimal.valueOf(ratingCount), 1, RoundingMode.HALF_UP);
        AdviserRating ownRating = mineAndOthers.stream()
                .filter(rating -> me.equals(rating.getRaterId()))
                .findFirst()
                .orElse(null);
        List<AdviserRecommendation> endorsements = tenantRecommendations.stream()
                .filter(row -> user.getId().equals(row.getAdviserId()))
                .sorted(Comparator.comparing(AdviserRecommendation::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        AdviserRecommendation ownRecommendation = endorsements.stream()
                .filter(row -> me.equals(row.getRecommenderId()))
                .findFirst()
                .orElse(null);
        List<AdviserEndorsement> visible = endorsements.stream()
                .map(row -> new AdviserEndorsement(nameOf(people.get(row.getRecommenderId())), row.getNote(), row.getCreatedAt()))
                .toList();
        String availability = profile == null || blank(profile.getAvailability()) == null ? UNSET : profile.getAvailability();
        return new AdviserResponse(
                user.getId(),
                user.getFullName(),
                user.getId().equals(me),
                allFarms,
                availability,
                profile == null ? null : profile.getAvailabilityNote(),
                splitTalents(profile == null ? null : profile.getTalents()),
                allFarms ? farmById.size() : places.size(),
                alertCount,
                taskCount,
                loadBand(alertCount, taskCount),
                average,
                ratingCount,
                ownRating == null ? null : ownRating.getScore(),
                ownRating == null ? null : ownRating.getNote(),
                endorsements.size(),
                ownRecommendation != null,
                ownRecommendation == null ? null : ownRecommendation.getNote(),
                visible,
                allFarms ? List.of() : places
        );
    }

    private boolean countsFor(boolean allFarms, Set<UUID> farmIds, UUID farmId) {
        if (allFarms) {
            return true;
        }
        return farmId != null && farmIds.contains(farmId);
    }

    private String loadBand(int openAlerts, int openTasks) {
        int open = openAlerts + openTasks;
        if (open == 0) {
            return "Light";
        }
        if (open <= 3) {
            return "Steady";
        }
        return "Heavy";
    }

    private UserAccount requireExpert(UUID adviserId) {
        if (adviserId == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ADVISER_NOT_FOUND", "That adviser was not found");
        }
        UserAccount user = users.findById(adviserId)
                .filter(account -> access.tenantId().equals(account.getTenantId()))
                .filter(account -> "ACTIVE".equals(account.getStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ADVISER_NOT_FOUND", "That adviser was not found"));
        boolean expert = grants.findByUserId(user.getId()).stream()
                .anyMatch(grant -> RolePermissions.FARM_EXPERT.equals(grant.getRoleCode()));
        if (!expert) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ADVISER_NOT_FOUND", "That adviser was not found");
        }
        return user;
    }

    private List<String> cleanTalents(List<String> talents) {
        List<String> clean = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (talents == null) {
            return clean;
        }
        for (String talent : talents) {
            String value = blank(talent);
            if (value == null) {
                continue;
            }
            value = value.replaceAll("\\s+", " ");
            if (value.length() > 40) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "A talent can be at most 40 characters");
            }
            if (seen.add(value.toLowerCase(Locale.ROOT))) {
                clean.add(value);
            }
        }
        if (clean.size() > 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Add at most 8 talents");
        }
        if (String.join("\n", clean).length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Add at most 8 talents");
        }
        return clean;
    }

    private List<String> splitTalents(String raw) {
        String value = blank(raw);
        if (value == null) {
            return List.of();
        }
        return Arrays.stream(value.split("\n")).map(String::trim).filter(item -> !item.isEmpty()).toList();
    }

    private String nameOf(UserAccount user) {
        return user == null || blank(user.getFullName()) == null ? "Farmer" : user.getFullName();
    }

    private String blank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String clip(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
