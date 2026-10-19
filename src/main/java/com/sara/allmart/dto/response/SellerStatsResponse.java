package com.sara.allmart.dto.response;

import java.math.BigDecimal;

/**
 * Response DTO for the seller dashboard statistics endpoint.
 * Aggregates revenue and product count figures for the authenticated seller.
 */
public record SellerStatsResponse(
        BigDecimal grossRevenue,
        BigDecimal totalPlatformFees,
        BigDecimal netRevenue,
        long activeProductCount
) {
}
