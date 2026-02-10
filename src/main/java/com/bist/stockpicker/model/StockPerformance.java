package com.bist.stockpicker.model;

public record StockPerformance(
        String symbol,
        String industry,
        double return1m,
        double return3m,
        double return6m
) {
}
