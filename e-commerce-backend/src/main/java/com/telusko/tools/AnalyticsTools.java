package com.telusko.tools;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import com.telusko.dto.AnalyticsViews.CategoryRevenueView;
import com.telusko.dto.AnalyticsViews.LowStockView;
import com.telusko.dto.AnalyticsViews.SalesSummaryView;
import com.telusko.dto.AnalyticsViews.StatusCountView;
import com.telusko.dto.AnalyticsViews.TopProductView;
import com.telusko.enums.OrderStatus;
import com.telusko.model.Product;
import com.telusko.repository.OrderRepository;
import com.telusko.repository.ProductRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
@Component
@RequiredArgsConstructor
public class AnalyticsTools {

    private final OrderRepository orders;
    private final ProductRepository products;

    /** Cancelled and refunded orders are money that never landed, so they never count as revenue. */
    private static final List<OrderStatus> NON_REVENUE = List.of(
            OrderStatus.CANCELED, OrderStatus.RETURNED, OrderStatus.REFUNDED);

    @Tool(description = """
            Sales summary for a recent period: order count, total revenue and average order value.
            period must be one of: today, week, month, year, all.
            Revenue excludes canceled, returned and refunded orders.
            """)
    public SalesSummaryView salesSummary(String period) {
        LocalDateTime since = since(period);

        long orderCount = orders.countByPlacedAtAfter(since);
        BigDecimal revenue = orders.sumRevenueSince(since, NON_REVENUE);

        Double rawAverage = orders.averageOrderValueSince(since);
        BigDecimal average = BigDecimal.valueOf(rawAverage == null ? 0.0 : rawAverage)
                .setScale(2, RoundingMode.HALF_UP);

        return new SalesSummaryView(normalisePeriod(period), orderCount, revenue, average);
    }

    @Tool(description = """
            Best selling products for a period, ordered by units sold.
            period must be one of: today, week, month, year, all. limit caps how many rows come back.
            """)
    public List<TopProductView> topSellingProducts(String period, Integer limit) {
        int rows = (limit == null || limit <= 0) ? 5 : Math.min(limit, 25);

        return orders.topSellingSince(since(period), PageRequest.of(0, rows)).stream()
                .map(row -> new TopProductView(
                        (String) row[0],
                        ((Number) row[1]).longValue(),
                        (BigDecimal) row[2]))
                .toList();
    }

    @Tool(description = """
            Active products at or below a stock threshold, lowest stock first.
            Use this to answer restocking questions. threshold defaults to 5 when not given.
            """)
    public List<LowStockView> lowStockProducts(Integer threshold, Integer limit) {
        int cutoff = (threshold == null || threshold < 0) ? 5 : threshold;
        int rows = (limit == null || limit <= 0) ? 10 : Math.min(limit, 50);

        List<Product> lowStock = products.findLowStock(cutoff, PageRequest.of(0, rows));

        return lowStock.stream()
                .map(p -> new LowStockView(p.getId(), p.getName(), p.getStockQty()))
                .toList();
    }

    @Tool(description = """
            How many orders sit in each status (PENDING, CONFIRMED, PACKED, SHIPPED, DELIVERED,
            CANCELED, RETURN_REQUEST, RETURNED, REFUNDED). Useful for spotting a fulfilment backlog.
            """)
    public List<StatusCountView> ordersByStatus() {
        return orders.countGroupedByStatus().stream()
                .map(row -> new StatusCountView(
                        String.valueOf(row[0]),
                        ((Number) row[1]).longValue()))
                .toList();
    }

    @Tool(description = """
            Revenue broken down by product category for a period.
            period must be one of: today, week, month, year, all.
            """)
    public List<CategoryRevenueView> revenueByCategory(String period) {
        return orders.revenueByCategorySince(since(period)).stream()
                .map(row -> new CategoryRevenueView(
                        String.valueOf(row[0]),
                        (BigDecimal) row[1]))
                .toList();
    }

    /**
     * Turns the word the admin used into a cut-off date.
     * <p>
     * The model picks this word, so anything unrecognised falls back to the last 30 days rather
     * than throwing - a wrong-but-labelled window is far easier for the admin to spot in the
     * answer than a failed tool call.
     */
    private LocalDateTime since(String period) {
        LocalDateTime now = LocalDateTime.now();
        if (period == null) return now.minusDays(30);

        return switch (period.trim().toLowerCase()) {
            case "today" -> now.toLocalDate().atStartOfDay();
            case "week" -> now.minusDays(7);
            case "year" -> now.minusYears(1);
            case "all" -> LocalDateTime.of(1970, 1, 1, 0, 0);
            default -> now.minusDays(30);
        };
    }

    private String normalisePeriod(String period) {
        if (period == null) return "last 30 days";
        return switch (period.trim().toLowerCase()) {
            case "today" -> "today";
            case "week" -> "last 7 days";
            case "year" -> "last 12 months";
            case "all" -> "all time";
            default -> "last 30 days";
        };
    }
}
