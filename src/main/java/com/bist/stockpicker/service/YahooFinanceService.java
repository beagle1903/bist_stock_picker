package com.bist.stockpicker.service;

import com.bist.stockpicker.model.StockPerformance;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class YahooFinanceService {

    private static final List<String> CANDIDATE_SYMBOLS = List.of(
            "THYAO.IS", "ASELS.IS", "TUPRS.IS", "KCHOL.IS", "BIMAS.IS",
            "EREGL.IS", "AKBNK.IS", "YKBNK.IS", "SASA.IS", "PETKM.IS",
            "SISE.IS", "TCELL.IS", "VESTL.IS", "HEKTS.IS", "KOZAL.IS",
            "PGSUS.IS", "GARAN.IS", "TOASO.IS", "ARCLK.IS", "ALARK.IS"
    );

    private static final Map<String, String> INDUSTRY_BY_SYMBOL = Map.ofEntries(
            Map.entry("THYAO.IS", "Air Transportation"),
            Map.entry("ASELS.IS", "Defense Electronics"),
            Map.entry("TUPRS.IS", "Oil & Gas Refining"),
            Map.entry("KCHOL.IS", "Holding Company"),
            Map.entry("BIMAS.IS", "Retail"),
            Map.entry("EREGL.IS", "Steel"),
            Map.entry("AKBNK.IS", "Banking"),
            Map.entry("YKBNK.IS", "Banking"),
            Map.entry("SASA.IS", "Chemicals"),
            Map.entry("PETKM.IS", "Petrochemicals"),
            Map.entry("SISE.IS", "Glass Manufacturing"),
            Map.entry("TCELL.IS", "Telecommunications"),
            Map.entry("VESTL.IS", "Consumer Electronics"),
            Map.entry("HEKTS.IS", "Agriculture Chemicals"),
            Map.entry("KOZAL.IS", "Gold Mining"),
            Map.entry("PGSUS.IS", "Air Transportation"),
            Map.entry("GARAN.IS", "Banking"),
            Map.entry("TOASO.IS", "Automotive"),
            Map.entry("ARCLK.IS", "Home Appliances"),
            Map.entry("ALARK.IS", "Construction & Energy")
    );

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public YahooFinanceService(ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0")
                .build();
        this.objectMapper = objectMapper;
    }

    public List<StockPerformance> fetchCandidatePerformances() {
        return CANDIDATE_SYMBOLS.stream()
                .map(this::fetchPerformance)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private Optional<StockPerformance> fetchPerformance(String symbol) {
        try {
            String chartBody = restClient.get()
                    .uri("https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?range=6mo&interval=1d", symbol)
                    .retrieve()
                    .body(String.class);

            JsonNode chartRoot = objectMapper.readTree(chartBody);
            JsonNode result = chartRoot.path("chart").path("result").path(0);
            if (result.isMissingNode()) {
                return Optional.empty();
            }

            JsonNode timestampsNode = result.path("timestamp");
            JsonNode closesNode = result.path("indicators").path("quote").path(0).path("close");
            List<Long> timestamps = new ArrayList<>();
            List<Double> closes = new ArrayList<>();

            for (int i = 0; i < Math.min(timestampsNode.size(), closesNode.size()); i++) {
                JsonNode closeNode = closesNode.get(i);
                if (closeNode != null && !closeNode.isNull()) {
                    timestamps.add(timestampsNode.get(i).asLong());
                    closes.add(closeNode.asDouble());
                }
            }

            if (closes.size() < 25) {
                return Optional.empty();
            }

            double latestClose = closes.get(closes.size() - 1);
            double close1m = closeNearestToMonthsAgo(timestamps, closes, 1);
            double close3m = closeNearestToMonthsAgo(timestamps, closes, 3);
            double close6m = closes.get(0);

            return Optional.of(new StockPerformance(
                    symbol,
                    INDUSTRY_BY_SYMBOL.getOrDefault(symbol, "Unknown"),
                    percentageChange(close1m, latestClose),
                    percentageChange(close3m, latestClose),
                    percentageChange(close6m, latestClose)
            ));
        } catch (RestClientException | IllegalArgumentException e) {
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private double closeNearestToMonthsAgo(List<Long> timestamps, List<Double> closes, int monthsAgo) {
        LocalDate targetDate = LocalDate.now(ZoneOffset.UTC).minusMonths(monthsAgo);
        long targetEpoch = targetDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC);

        int idx = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < timestamps.size(); i++) {
            long distance = Math.abs(timestamps.get(i) - targetEpoch);
            if (distance < bestDistance) {
                bestDistance = distance;
                idx = i;
            }
        }
        return closes.get(idx);
    }

    private double percentageChange(double oldPrice, double newPrice) {
        if (oldPrice == 0.0d) {
            return 0.0d;
        }
        return Double.parseDouble(String.format(Locale.US, "%.2f", ((newPrice - oldPrice) / oldPrice) * 100.0));
    }
}
