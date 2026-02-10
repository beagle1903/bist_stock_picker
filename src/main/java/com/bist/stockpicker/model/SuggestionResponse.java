package com.bist.stockpicker.model;

import java.util.List;

public record SuggestionResponse(
        String strategy,
        List<StockSuggestion> suggestions
) {
}
