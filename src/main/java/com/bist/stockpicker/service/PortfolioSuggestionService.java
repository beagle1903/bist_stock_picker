package com.bist.stockpicker.service;

import com.bist.stockpicker.model.StockPerformance;
import com.bist.stockpicker.model.StockSuggestion;
import com.bist.stockpicker.model.SuggestionResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PortfolioSuggestionService {

    private final YahooFinanceService yahooFinanceService;
    private final GroqPortfolioService groqPortfolioService;

    public PortfolioSuggestionService(YahooFinanceService yahooFinanceService,
                                      GroqPortfolioService groqPortfolioService) {
        this.yahooFinanceService = yahooFinanceService;
        this.groqPortfolioService = groqPortfolioService;
    }

    public SuggestionResponse buildHighRiskPortfolio() {
        List<StockPerformance> performances = yahooFinanceService.fetchCandidatePerformances();
        if (performances.isEmpty()) {
            return new SuggestionResponse("No market data available", List.of());
        }

        List<StockSuggestion> suggestions = groqPortfolioService.suggestHighRiskPortfolio(performances, 10);

        return new SuggestionResponse(
                "High-risk BIST portfolio with cross-industry distribution",
                suggestions
        );
    }
}
