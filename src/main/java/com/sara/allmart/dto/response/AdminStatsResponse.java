package com.sara.allmart.dto.response;

import java.math.BigDecimal;

public record AdminStatsResponse(
        long totalUsers,
        long totalOrders,
        long totalProducts,
        BigDecimal totalRevenue
) {
}
