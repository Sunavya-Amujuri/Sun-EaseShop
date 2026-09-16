package com.telusko.dto;

import java.math.BigDecimal;

public final class AnalyticsViews
{
    private AnalyticsViews() {
    }

    public record SalesSummaryView(
            String period,
            long orders,
            BigDecimal revenue,
            BigDecimal averageOrderValue
    ) {}

    public record TopProductView(
            String productName,
            long unitsSold,
            BigDecimal revenue
    ) {}

    public record LowStockView(
            Long productId,
            String productName,
            Integer stockQty
    ) {}

    public record StatusCountView(
            String status,
            long orders
    ) {}

    public record CategoryRevenueView(
            String category,
            BigDecimal revenue
    ) {}
}
