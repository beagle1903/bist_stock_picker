package com.bist.stockpicker.controller;

import com.bist.stockpicker.model.SuggestionResponse;
import com.bist.stockpicker.service.PortfolioSuggestionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stocks")
public class StockSuggestionController {

    private final PortfolioSuggestionService portfolioSuggestionService;

    public StockSuggestionController(PortfolioSuggestionService portfolioSuggestionService) {
        this.portfolioSuggestionService = portfolioSuggestionService;
    }

    @GetMapping("/suggestions/high-risk")
    public SuggestionResponse suggestHighRiskPortfolio() {
        return portfolioSuggestionService.buildHighRiskPortfolio();
    }
}
