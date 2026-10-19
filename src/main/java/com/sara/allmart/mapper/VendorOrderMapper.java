package com.sara.allmart.mapper;

import com.sara.allmart.dto.response.OrderItemResponse;
import com.sara.allmart.dto.response.VendorOrderResponse;
import com.sara.allmart.entity.Order;
import com.sara.allmart.entity.User;
import com.sara.allmart.entity.VendorOrder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Maps a {@link VendorOrder} entity to a {@link VendorOrderResponse} DTO.
 * Kept separate from {@link OrderMapper} because seller-facing and
 * buyer-facing projections have different fields and security scopes.
 */
@Component
public class VendorOrderMapper {

    public VendorOrderResponse toResponse(VendorOrder vendorOrder) {
        Order parentOrder = vendorOrder.getOrder();

        // Safely resolve buyer email: registered buyer or guest checkout
        String buyerEmail = resolveBuyerEmail(parentOrder);

        // Shipping city from the parent order's embedded address
        String shippingCity = (parentOrder.getShippingAddress() != null)
                ? parentOrder.getShippingAddress().getCity()
                : null;

        // Map only this seller's line items
        List<OrderItemResponse> items = vendorOrder.getItems() != null
                ? vendorOrder.getItems().stream()
                        .map(item -> {
                            BigDecimal unitPrice = item.getPriceAtPurchase();
                            int qty = item.getQuantity();
                            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));
                            return new OrderItemResponse(
                                    item.getId(),
                                    item.getProduct().getName(),
                                    unitPrice,
                                    qty,
                                    lineTotal
                            );
                        })
                        .toList()
                : Collections.emptyList();

        // Net revenue = what the seller actually earns after platform fee
        BigDecimal netRevenue = vendorOrder.getVendorSubtotal()
                .subtract(vendorOrder.getPlatformFee());

        return new VendorOrderResponse(
                vendorOrder.getId(),
                parentOrder.getId(),
                vendorOrder.getStatus(),
                vendorOrder.getVendorSubtotal(),
                vendorOrder.getPlatformFee(),
                netRevenue,
                buyerEmail,
                shippingCity,
                vendorOrder.getCreatedAt(),
                items
        );
    }

    private String resolveBuyerEmail(Order order) {
        User buyer = order.getUser();
        if (buyer != null && buyer.getEmail() != null && !buyer.getEmail().isBlank()) {
            return buyer.getEmail();
        }
        return order.getGuestEmail();
    }
}
