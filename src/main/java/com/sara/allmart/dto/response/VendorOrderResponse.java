package com.sara.allmart.dto.response;

import com.sara.allmart.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for a seller's sub-order (VendorOrder).
 * Exposes seller-specific financial fields alongside parent order metadata.
 */
public record VendorOrderResponse(
        Long vendorOrderId,
        Long parentOrderId,
        OrderStatus status,
        BigDecimal vendorSubtotal,
        BigDecimal platformFee,
        BigDecimal netRevenue,
        String buyerEmail,
        String shippingCity,
        LocalDateTime createdAt,
        List<OrderItemResponse> items
) {
}
