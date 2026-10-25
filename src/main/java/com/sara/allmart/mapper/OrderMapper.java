package com.sara.allmart.mapper;

import com.sara.allmart.dto.response.OrderItemResponse;
import com.sara.allmart.dto.response.OrderResponse;
import com.sara.allmart.entity.Order;
import com.sara.allmart.entity.OrderItem;
import com.sara.allmart.entity.VendorOrder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Component
public class OrderMapper {

    public OrderResponse toResponse(Order order) {
        Long id = order.getId();
        String status = order.getVendorOrders() != null
                ? order.getVendorOrders().stream()
                        .map(VendorOrder::getStatus)
                        .filter(java.util.Objects::nonNull)
                        .map(Enum::name)
                        .findFirst()
                        .orElse(null)
                : null;
        BigDecimal totalAmount = order.getTotalAmount();
        String shippingCity = order.getShippingAddress() != null
                ? order.getShippingAddress().getCity()
                : null;
        LocalDateTime orderDate = order.getCreatedAt();

        // Flatten all items across every VendorOrder belonging to this Order
        List<OrderItemResponse> items = order.getVendorOrders() != null
                ? order.getVendorOrders().stream()
                        .flatMap(vo -> vo.getItems() != null
                                ? vo.getItems().stream()
                                : java.util.stream.Stream.empty())
                        .map(this::toItemResponse)
                        .toList()
                : Collections.emptyList();

        return new OrderResponse(id, status, totalAmount, shippingCity, orderDate, items);
    }


    public OrderItemResponse toItemResponse(OrderItem orderItem) {
        Long id = orderItem.getId();
        String productName = orderItem.getProduct().getName();
        BigDecimal unitPrice = orderItem.getPriceAtPurchase();
        int quantity = orderItem.getQuantity();
        BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));

        return new OrderItemResponse(id, productName, unitPrice, quantity, totalPrice);
    }
}
