package com.bist.stockpicker.service;

import com.bist.stockpicker.model.StockPerformance;
import com.bist.stockpicker.model.StockSuggestion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class GroqPortfolioService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String groqApiKey;
    private final String groqModel;

    public GroqPortfolioService(
            ObjectMapper objectMapper,
            @Value("${groq.api.key:}") String groqApiKey,
            @Value("${groq.model:llama-3.3-70b-versatile}") String groqModel
    ) {
        this.restClient = RestClient.builder().build();
        this.objectMapper = objectMapper;
        this.groqApiKey = groqApiKey;
        this.groqModel = groqModel;
    }

    public List<StockSuggestion> suggestHighRiskPortfolio(List<StockPerformance> performances, int limit) {
        if (groqApiKey == null || groqApiKey.isBlank()) {
            return fallbackWithoutGroq(performances, limit);
        }

        try {
            String prompt = buildPrompt(performances, limit);
            Map<String, Object> payload = Map.of(
                    "model", groqModel,
                    "temperature", 0.3,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", "You are a quant portfolio assistant for BIST. Return strict JSON only."),
                            Map.of("role", "user", "content", prompt)
                    )
            );

            String body = restClient.post()
                    .uri("https://api.groq.com/openai/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + groqApiKey)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            Optional<List<StockSuggestion>> parsed = parseGroqResponse(body, performances, limit);
            return parsed.orElseGet(() -> fallbackWithoutGroq(performances, limit));
        } catch (Exception e) {
            return fallbackWithoutGroq(performances, limit);
        }
    }

    private String buildPrompt(List<StockPerformance> performances, int limit) {
        String stockRows = performances.stream()
                .map(s -> String.format("%s | industry=%s | ret1m=%.2f%% | ret3m=%.2f%% | ret6m=%.2f%%",
                        s.symbol(), s.industry(), s.return1m(), s.return3m(), s.return6m()))
                .collect(Collectors.joining("\n"));

        return "Given BIST stock performance data below, create a HIGH-RISK portfolio with exactly " + limit + " symbols. " +
                "Prefer momentum and volatility candidates, but diversify industries to reduce overlap. " +
                "Output format: {\"strategy\":\"...\",\"picks\":[{\"symbol\":\"...\",\"rationale\":\"...\"}]}. " +
                "Choose symbols only from this list:\n" + stockRows;
    }

    private Optional<List<StockSuggestion>> parseGroqResponse(String completionBody,
                                                              List<StockPerformance> performances,
                                                              int limit) {
        try {
            JsonNode root = objectMapper.readTree(completionBody);
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        if (content.isBlank()) {
            return Optional.empty();
        }

            JsonNode json = objectMapper.readTree(content);
        JsonNode picks = json.path("picks");
        if (!picks.isArray()) {
            return Optional.empty();
        }

        Map<String, StockPerformance> bySymbol = performances.stream()
                .collect(Collectors.toMap(StockPerformance::symbol, s -> s));

        List<StockSuggestion> suggestions = new ArrayList<>();
        for (JsonNode pick : picks) {
            String symbol = pick.path("symbol").asText("").trim();
            String rationale = pick.path("rationale").asText("High-risk candidate");
            StockPerformance perf = bySymbol.get(symbol);
            if (perf != null) {
                suggestions.add(new StockSuggestion(
                        perf.symbol(),
                        perf.industry(),
                        perf.return1m(),
                        perf.return3m(),
                        perf.return6m(),
                        rationale
                ));
            }
            if (suggestions.size() == limit) {
                break;
            }
        }

        if (suggestions.isEmpty()) {
            return Optional.empty();
        }

        if (suggestions.size() < limit) {
            List<StockSuggestion> topups = fallbackWithoutGroq(performances, limit).stream()
                    .filter(s -> suggestions.stream().noneMatch(existing -> existing.symbol().equals(s.symbol())))
                    .limit(limit - suggestions.size())
                    .toList();
            suggestions.addAll(topups);
        }

            return Optional.of(suggestions);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private List<StockSuggestion> fallbackWithoutGroq(List<StockPerformance> performances, int limit) {
        List<StockPerformance> ranked = performances.stream()
                .sorted(Comparator.comparingDouble((StockPerformance s) -> s.return6m() + s.return3m()).reversed())
                .toList();

        Map<String, Integer> industrySeen = new HashMap<>();
        List<StockSuggestion> suggestions = new ArrayList<>();

        for (StockPerformance stock : ranked) {
            int count = industrySeen.getOrDefault(stock.industry(), 0);
            if (count <= 1 || suggestions.size() < limit / 2) {
                suggestions.add(toFallbackSuggestion(stock));
                industrySeen.put(stock.industry(), count + 1);
            }
            if (suggestions.size() == limit) {
                break;
            }
        }

        if (suggestions.size() < limit) {
            ranked.stream()
                    .filter(stock -> suggestions.stream().noneMatch(s -> s.symbol().equals(stock.symbol())))
                    .limit(limit - suggestions.size())
                    .map(this::toFallbackSuggestion)
                    .forEach(suggestions::add);
        }

        return suggestions;
    }

    private StockSuggestion toFallbackSuggestion(StockPerformance stock) {
        return new StockSuggestion(
                stock.symbol(),
                stock.industry(),
                stock.return1m(),
                stock.return3m(),
                stock.return6m(),
                "Momentum-based fallback pick with industry spread consideration"
        );
    }
}
