package com.sara.allmart.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String status,
        BigDecimal totalAmount,
        String shippingCity,
        LocalDateTime orderDate,
        List<OrderItemResponse> items
) {
}
