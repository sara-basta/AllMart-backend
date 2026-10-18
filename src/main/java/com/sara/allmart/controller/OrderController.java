package com.sara.allmart.controller;

import com.sara.allmart.dto.request.OrderRequest;
import com.sara.allmart.dto.request.OrderStatusRequest;
import com.sara.allmart.dto.response.OrderResponse;
import com.sara.allmart.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ResponseEntity<OrderResponse> createOrder (@AuthenticationPrincipal UserDetails user,@Valid @RequestBody OrderRequest request){
        OrderResponse response = orderService.createOrder(user.getUsername(),request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PreAuthorize("hasAuthority('CUSTOMER')")
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getCustomerOrderById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        OrderResponse response = orderService.getCustomerOrderById(id, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("/admin/{id}")
    public ResponseEntity<OrderResponse> getAdminOrderById(@PathVariable Long id) {

        OrderResponse response = orderService.getAdminOrderById(id);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("/user/{id}")
    public ResponseEntity<List<OrderResponse>> getOrderByUser(@PathVariable Long id){
        List<OrderResponse> response = orderService.getOrderByUser(id);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateStatus(@PathVariable Long id, @Valid @RequestBody OrderStatusRequest request){
        OrderResponse response = orderService.updateVendorOrderStatus(id, request.status());
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAnyAuthority('CUSTOMER', 'ADMIN')")
    @GetMapping("/my-orders")
    public ResponseEntity<Page<OrderResponse>> getOrdersHistory(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ){
        return ResponseEntity.ok(orderService.getOrdersHistory(user.getUsername(), page, size));
    }

    @PreAuthorize("hasAuthority('CUSTOMER')")
    @PostMapping("{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@AuthenticationPrincipal UserDetails user,@PathVariable Long id){
        OrderResponse response = orderService.cancelOrder(user.getUsername(),id);
        return ResponseEntity.ok(response);
    }
}
