package com.shambanexa.implementation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shambanexa.common.ApiException;
import com.shambanexa.dto.FarmForecastResponse;
import com.shambanexa.dto.FarmForecastResponse.Day;
import com.shambanexa.dto.FarmForecastResponse.ReportedIllness;
import com.shambanexa.entity.Farm;
import com.shambanexa.entity.HealthCase;
import com.shambanexa.repository.AlertRepository;
import com.shambanexa.repository.FarmRepository;
import com.shambanexa.repository.HealthCaseRepository;
import com.shambanexa.security.AccessGuard;
import com.shambanexa.service.ForecastService;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ForecastServiceImpl implements ForecastService {

    private static final ZoneId DAR = ZoneId.of("Africa/Dar_es_Salaam");
    private static final Duration CACHE_FOR = Duration.ofMinutes(45);
    private static final String SOURCE = "Open-Meteo";
    private static final String ATTRIBUTION = "Open-Meteo forecast, CC BY 4.0. A model for this location, not a guarantee on the farm. Weather does not diagnose a disease or choose a medicine.";
    private static final String DAILY = "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max,relative_humidity_2m_mean,wind_speed_10m_max";

    private final FarmRepository farms;
    private final AlertRepository alerts;
    private final HealthCaseRepository healthCases;
    private final AccessGuard access;
    private final ObjectMapper json;
    private final RestClient meteo;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public ForecastServiceImpl(
            FarmRepository farms,
            AlertRepository alerts,
            HealthCaseRepository healthCases,
            AccessGuard access,
            ObjectMapper json
    ) {
        this.farms = farms;
        this.alerts = alerts;
        this.healthCases = healthCases;
        this.access = access;
        this.json = json;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(8));
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.meteo = RestClient.builder()
                .baseUrl("https://api.open-meteo.com")
                .requestFactory(factory)
                .build();
    }

    @Override
    public FarmForecastResponse forFarm(UUID farmId) {
        access.checkFarm(farmId);
        Farm farm = farms.findByIdAndTenantId(farmId, access.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FARM_NOT_FOUND", "Farm not found"));
        LocalDate today = LocalDate.now(DAR);
        String key = farm.getId() + ":" + today;
        Cached cached = cache.get(key);
        if (cached != null && cached.storedAt().plus(CACHE_FOR).isAfter(Instant.now())) {
            return finish(cached.body().withCached(true), farm);
        }
        FarmForecastResponse fresh = fetch(farm);
        if (fresh.available()) {
            cache.put(key, new Cached(fresh, Instant.now()));
        }
        return finish(fresh, farm);
    }

    private FarmForecastResponse fetch(Farm farm) {
        try {
            String body = meteo.get()
                    .uri(uri -> uri.path("/v1/forecast")
                            .queryParam("latitude", farm.getLatitude())
                            .queryParam("longitude", farm.getLongitude())
                            .queryParam("current", "temperature_2m,relative_humidity_2m,precipitation,wind_speed_10m")
                            .queryParam("daily", DAILY)
                            .queryParam("timezone", "Africa/Dar_es_Salaam")
                            .queryParam("forecast_days", 7)
                            .queryParam("wind_speed_unit", "ms")
                            .build())
                    .retrieve()
                    .body(String.class);
            return summarize(farm, body, List.of());
        } catch (RestClientException | IllegalArgumentException ex) {
            return unavailable(farm, List.of(), "Open-Meteo did not return a forecast for this farm.");
        }
    }

    private FarmForecastResponse finish(FarmForecastResponse weather, Farm farm) {
        List<ReportedIllness> rows = reported(farm);
        List<ForecastWatch.Reading> readings = weather.days().stream()
                .map(day -> new ForecastWatch.Reading(
                        day.rainChance(),
                        day.humidity() == null ? null : day.humidity().doubleValue(),
                        day.highC() == null ? null : day.highC().doubleValue(),
                        day.lowC() == null ? null : day.lowC().doubleValue()
                ))
                .toList();
        return new FarmForecastResponse(
                weather.farmId(),
                weather.farmName(),
                weather.available(),
                weather.message(),
                weather.source(),
                weather.attribution(),
                weather.retrievedAt(),
                weather.cached(),
                weather.currentTemperatureC(),
                weather.currentHumidity(),
                weather.currentRainMm(),
                weather.currentWindMs(),
                weather.days(),
                rows,
                ForecastWatch.checks(readings, !rows.isEmpty())
        );
    }

    private FarmForecastResponse summarize(Farm farm, String body, List<ReportedIllness> reported) {
        try {
            JsonNode root = json.readTree(body);
            JsonNode daily = root.path("daily");
            JsonNode dates = daily.path("time");
            if (!dates.isArray() || dates.isEmpty()) {
                return unavailable(farm, reported, "Open-Meteo has no forecast days for this farm.");
            }
            List<Day> days = new ArrayList<>();
            List<ForecastWatch.Reading> readings = new ArrayList<>();
            for (int i = 0; i < dates.size(); i++) {
                Double high = number(daily.path("temperature_2m_max"), i);
                Double low = number(daily.path("temperature_2m_min"), i);
                Double rain = number(daily.path("precipitation_sum"), i);
                Double chance = number(daily.path("precipitation_probability_max"), i);
                Double humidity = number(daily.path("relative_humidity_2m_mean"), i);
                Double wind = number(daily.path("wind_speed_10m_max"), i);
                Double code = number(daily.path("weather_code"), i);
                Integer rainChance = chance == null ? null : (int) Math.round(chance);
                days.add(new Day(
                        LocalDate.parse(dates.get(i).asText()),
                        code == null ? "Forecast" : ForecastWatch.sky(code.intValue()),
                        round(high),
                        round(low),
                        round(rain),
                        rainChance,
                        round(humidity),
                        round(wind)
                ));
                readings.add(new ForecastWatch.Reading(rainChance, humidity, high, low));
            }
            JsonNode current = root.path("current");
            return new FarmForecastResponse(
                    farm.getId(),
                    farm.getName(),
                    true,
                    null,
                    SOURCE,
                    ATTRIBUTION,
                    Instant.now(),
                    false,
                    round(current.path("temperature_2m").isNumber() ? current.path("temperature_2m").asDouble() : null),
                    round(current.path("relative_humidity_2m").isNumber() ? current.path("relative_humidity_2m").asDouble() : null),
                    round(current.path("precipitation").isNumber() ? current.path("precipitation").asDouble() : null),
                    round(current.path("wind_speed_10m").isNumber() ? current.path("wind_speed_10m").asDouble() : null),
                    days,
                    reported,
                    ForecastWatch.checks(readings)
            );
        } catch (Exception ex) {
            return unavailable(farm, reported, "Open-Meteo did not return a forecast for this farm.");
        }
    }

    private List<ReportedIllness> reported(Farm farm) {
        Map<UUID, Farm> known = new HashMap<>();
        for (Farm candidate : farms.findByTenantIdOrderByNameAsc(access.tenantId())) {
            known.put(candidate.getId(), candidate);
        }
        List<ReportedIllness> rows = new ArrayList<>();
        alerts.findByTenantIdAndStatusOrderByOccurredAtDesc(access.tenantId(), "OPEN").stream()
                .filter(alert -> visible(farm, alert.getFarmId(), known))
                .filter(alert -> "DISEASE".equals(alert.getSourceType()) || "SICK".equals(alert.getSourceType()))
                .forEach(alert -> rows.add(new ReportedIllness(
                        alert.getId(),
                        alert.getTitle(),
                        alert.getSourceType(),
                        name(known, alert.getFarmId()),
                        alert.getBody(),
                        null
                )));
        for (HealthCase health : healthCases.findByTenantIdOrderByOpenedOnDesc(access.tenantId())) {
            if (!"OPEN".equals(health.getCaseStatus()) || !visible(farm, health.getFarmId(), known)) {
                continue;
            }
            rows.add(new ReportedIllness(
                    health.getId(),
                    health.getTitle(),
                    "CASE",
                    name(known, health.getFarmId()),
                    health.getNotes(),
                    health.getOpenedOn()
            ));
        }
        return rows;
    }

    private boolean visible(Farm current, UUID otherId, Map<UUID, Farm> known) {
        if (!access.canReadFarm(otherId)) {
            return false;
        }
        if (current.getId().equals(otherId)) {
            return true;
        }
        Farm other = known.get(otherId);
        return other != null && current.getRegion() != null && current.getRegion().equals(other.getRegion());
    }

    private String name(Map<UUID, Farm> known, UUID farmId) {
        Farm farm = known.get(farmId);
        return farm == null ? null : farm.getName();
    }

    private Double number(JsonNode series, int index) {
        if (!series.isArray() || index >= series.size() || series.get(index).isNull() || !series.get(index).isNumber()) {
            return null;
        }
        return series.get(index).asDouble();
    }

    private FarmForecastResponse unavailable(Farm farm, List<ReportedIllness> reported, String message) {
        return new FarmForecastResponse(
                farm.getId(), farm.getName(), false, message, SOURCE, ATTRIBUTION,
                Instant.now(), false, null, null, null, null, List.of(), reported, List.of()
        );
    }

    private BigDecimal round(Double value) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }

    private record Cached(FarmForecastResponse body, Instant storedAt) {
    }
}
