package com.sara.allmart.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Integer stockQuantity,
        String categoryName,
        List<ProductImage> images,
        int reviewCount,
        double averageRating,
        Long sellerId,
        String sellerName
){
}
