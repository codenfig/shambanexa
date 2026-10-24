package com.shambanexa.implementation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shambanexa.common.ApiException;
import com.shambanexa.dto.FarmClimateResponse;
import com.shambanexa.entity.Farm;
import com.shambanexa.repository.FarmRepository;
import com.shambanexa.security.AccessGuard;
import com.shambanexa.service.ClimateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Service
public class ClimateServiceImpl implements ClimateService {

    private static final ZoneId DAR = ZoneId.of("Africa/Dar_es_Salaam");
    private static final Duration CACHE_FOR = Duration.ofMinutes(45);
    private static final DateTimeFormatter POWER_DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String SOURCE = "NASA POWER";
    private static final String ATTRIBUTION = "NASA POWER Daily API. Modelled climate for this location, not a gauge on the farm.";
    private static final String DAILY = "PRECTOTCORR,T2M_MAX,T2M_MIN,RH2M,T2MDEW,T2MWET,WS2M,ALLSKY_SFC_SW_DWN,ALLSKY_SFC_PAR_TOT,GWETTOP,GWETROOT,EVLAND";
    private static final String CLIMATOLOGY = "PRECTOTCORR,EVLAND,GWETTOP,GWETROOT,ALLSKY_SFC_PAR_TOT";

    private final FarmRepository farms;
    private final AccessGuard access;
    private final ObjectMapper json;
    private final RestClient nasa;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public ClimateServiceImpl(FarmRepository farms, AccessGuard access, ObjectMapper json) {
        this.farms = farms;
        this.access = access;
        this.json = json;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(8));
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.nasa = RestClient.builder()
                .baseUrl("https://power.larc.nasa.gov")
                .requestFactory(factory)
                .build();
    }

    @Override
    public FarmClimateResponse forFarm(UUID farmId) {
        access.checkFarm(farmId);
        Farm farm = farms.findByIdAndTenantId(farmId, access.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FARM_NOT_FOUND", "Farm not found"));
        LocalDate end = LocalDate.now(DAR).minusDays(3);
        LocalDate start = end.minusDays(29);
        String key = farm.getId() + ":" + start + ":" + end;
        Cached cached = cache.get(key);
        if (cached != null && cached.storedAt().plus(CACHE_FOR).isAfter(Instant.now())) {
            return cached.body().withCached(true);
        }
        FarmClimateResponse fresh = fetch(farm, start, end);
        if (fresh.available()) {
            cache.put(key, new Cached(fresh, Instant.now()));
        }
        return fresh;
    }

    private FarmClimateResponse fetch(Farm farm, LocalDate start, LocalDate end) {
        try {
            DailySlice daily = daily(farm, start, end);
            String climatology = null;
            try {
                climatology = request("/api/temporal/climatology/point", CLIMATOLOGY, farm, null, null);
            } catch (RestClientException ignored) {
                climatology = null;
            }
            return summarize(farm, start, end, daily, climatology);
        } catch (RestClientException | IllegalArgumentException ex) {
            return unavailable(farm, start, end, "NASA POWER did not return climate for this farm.");
        } catch (Exception ex) {
            return unavailable(farm, start, end, "NASA POWER did not return climate for this farm.");
        }
    }

    private DailySlice daily(Farm farm, LocalDate start, LocalDate end) throws Exception {
        ObjectNode merged = json.createObjectNode();
        double fill = -999;
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            LocalDate chunkEnd = cursor.plusDays(6);
            if (chunkEnd.isAfter(end)) {
                chunkEnd = end;
            }
            JsonNode root = json.readTree(request("/api/temporal/daily/point", DAILY, farm, cursor, chunkEnd));
            fill = root.path("header").path("fill_value").asDouble(fill);
            JsonNode parameter = root.path("properties").path("parameter");
            Iterator<Map.Entry<String, JsonNode>> fields = parameter.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                ObjectNode target = (ObjectNode) merged.get(field.getKey());
                if (target == null) {
                    target = merged.putObject(field.getKey());
                }
                Iterator<Map.Entry<String, JsonNode>> days = field.getValue().fields();
                while (days.hasNext()) {
                    Map.Entry<String, JsonNode> day = days.next();
                    target.set(day.getKey(), day.getValue());
                }
            }
            cursor = chunkEnd.plusDays(1);
        }
        return new DailySlice(merged, fill);
    }

    private String request(String path, String parameters, Farm farm, LocalDate start, LocalDate end) {
        Function<UriBuilder, URI> uri = builder -> {
            UriBuilder query = builder.path(path)
                    .queryParam("parameters", parameters)
                    .queryParam("community", "AG")
                    .queryParam("longitude", farm.getLongitude())
                    .queryParam("latitude", farm.getLatitude())
                    .queryParam("format", "JSON");
            if (start != null && end != null) {
                query.queryParam("start", POWER_DAY.format(start)).queryParam("end", POWER_DAY.format(end));
            }
            return query.build();
        };
        return nasa.get().uri(uri).retrieve().body(String.class);
    }

    private FarmClimateResponse summarize(Farm farm, LocalDate start, LocalDate end, DailySlice daily, String climatologyBody) {
        try {
            JsonNode parameter = daily.parameter();
            double fill = daily.fill();
            JsonNode climate = climatology(climatologyBody);
            PowerClimate.Assessment assessment = PowerClimate.assess(
                    start,
                    end,
                    series(parameter.path("PRECTOTCORR"), fill, false),
                    series(parameter.path("T2M_MAX"), fill, false),
                    series(parameter.path("T2M_MIN"), fill, false),
                    series(parameter.path("RH2M"), fill, false),
                    series(parameter.path("T2MDEW"), fill, false),
                    series(parameter.path("T2MWET"), fill, false),
                    series(parameter.path("WS2M"), fill, false),
                    series(parameter.path("ALLSKY_SFC_SW_DWN"), fill, false),
                    series(parameter.path("ALLSKY_SFC_PAR_TOT"), fill, false),
                    series(parameter.path("GWETTOP"), fill, false),
                    series(parameter.path("GWETROOT"), fill, false),
                    series(parameter.path("EVLAND"), fill, true),
                    months(climate, "PRECTOTCORR", fill),
                    months(climate, "EVLAND", fill),
                    months(climate, "GWETTOP", fill),
                    months(climate, "GWETROOT", fill),
                    months(climate, "ALLSKY_SFC_PAR_TOT", fill)
            );
            if (assessment.days() == 0) {
                return unavailable(farm, start, end, "NASA POWER has no climate values for these dates.");
            }
            return response(farm, start, end, null, assessment);
        } catch (Exception ex) {
            return unavailable(farm, start, end, "NASA POWER did not return climate for this farm.");
        }
    }

    private JsonNode climatology(String body) {
        if (body == null) {
            return null;
        }
        try {
            return json.readTree(body).path("properties").path("parameter");
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<LocalDate, Double> series(JsonNode node, double fill, boolean evaporation) {
        Map<LocalDate, Double> values = new HashMap<>();
        if (node == null || !node.isObject()) {
            return values;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (!field.getValue().isNumber()) {
                continue;
            }
            double number = field.getValue().asDouble();
            if (number <= fill + 1) {
                continue;
            }
            values.put(LocalDate.parse(field.getKey(), POWER_DAY), evaporation ? PowerClimate.evaporationMm(number) : number);
        }
        return values;
    }

    private Map<String, Double> months(JsonNode parameter, String name, double fill) {
        Map<String, Double> values = new HashMap<>();
        if (parameter == null || parameter.isMissingNode()) {
            return values;
        }
        JsonNode node = parameter.path(name);
        if (!node.isObject()) {
            return values;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if ("ANN".equals(field.getKey()) || !field.getValue().isNumber()) {
                continue;
            }
            double number = field.getValue().asDouble();
            if (number <= fill + 1) {
                continue;
            }
            values.put(field.getKey(), number);
        }
        return values;
    }

    private FarmClimateResponse response(Farm farm, LocalDate start, LocalDate end, String message, PowerClimate.Assessment climate) {
        return new FarmClimateResponse(
                farm.getId(),
                farm.getName(),
                true,
                message,
                SOURCE,
                ATTRIBUTION,
                Instant.now(),
                false,
                start,
                end,
                climate.days(),
                climate.rainfallMm(),
                climate.rainfallNormalMm(),
                climate.rainfallBalance(),
                climate.evapotranspirationMm(),
                climate.evapotranspirationNormalMm(),
                climate.evapotranspirationBalance(),
                climate.averageSurfaceWetness(),
                climate.averageRootWetness(),
                climate.rootWetnessNormal(),
                climate.rootWetnessBalance(),
                climate.averageMaxC(),
                climate.averageMinC(),
                climate.averageHumidity(),
                climate.averageDewPointC(),
                climate.averageWetBulbC(),
                climate.hotDays(),
                climate.coldNights(),
                climate.heatStressDays(),
                climate.growingDegreeDays(),
                climate.averageSunlightMj(),
                climate.sunlightDays(),
                climate.averageParMj(),
                climate.parNormalMj(),
                climate.averageWindMs(),
                climate.dryDays(),
                climate.longestDrySpell()
        );
    }

    private FarmClimateResponse unavailable(Farm farm, LocalDate start, LocalDate end, String message) {
        return new FarmClimateResponse(
                farm.getId(), farm.getName(), false, message, SOURCE, ATTRIBUTION,
                Instant.now(), false, start, end, 0,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null
        );
    }

    private record DailySlice(JsonNode parameter, double fill) {
    }

    private record Cached(FarmClimateResponse body, Instant storedAt) {
    }
}
