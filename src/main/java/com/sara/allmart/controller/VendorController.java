package com.sara.allmart.controller;

import com.sara.allmart.dto.request.OrderStatusRequest;
import com.sara.allmart.dto.response.SellerStatsResponse;
import com.sara.allmart.dto.response.VendorOrderResponse;
import com.sara.allmart.entity.OrderStatus;
import com.sara.allmart.service.VendorOrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for the Seller Dashboard API.
 *
 * <p>All endpoints are restricted to the {@code SELLER} or {@code ADMIN} role via the class-level
 * {@code @PreAuthorize} annotation. Data isolation is additionally enforced inside
 * {@link VendorOrderService} — every query is scoped to the authenticated seller's
 * {@link com.sara.allmart.entity.User} entity, so a SELLER can never see or modify
 * another seller's data even if they guess an order ID.
 *
 * <p>Base path: {@code /api/vendor/orders} (covered by
 * {@code SecurityConfig.requestMatchers("/api/vendor/**").hasAnyAuthority("SELLER","ADMIN")}).
 */
@RestController
@RequestMapping("/api/vendor/orders")
@PreAuthorize("hasAnyAuthority('SELLER', 'ADMIN')")
public class VendorController {

    private final VendorOrderService vendorOrderService;

    public VendorController(VendorOrderService vendorOrderService) {
        this.vendorOrderService = vendorOrderService;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /api/vendor/orders
    // Returns the authenticated seller's paginated sub-order list.
    //
    // Optional query params:
    //   ?status=PENDING|CONFIRMED|SHIPPED|DELIVERED|CANCELLED
    //   ?page=0&size=10
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Lists the authenticated seller's vendor sub-orders, newest first.
     * Optionally filter by {@code status} query param.
     *
     * @param user   the authenticated seller (injected from security context)
     * @param status optional status filter (e.g. PENDING, SHIPPED)
     * @param page   zero-based page index (default 0)
     * @param size   page size (default 10)
     * @return paginated list of VendorOrderResponse
     */
    @GetMapping
    public ResponseEntity<Page<VendorOrderResponse>> getSellerOrders(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        String email = user.getUsername();
        Page<VendorOrderResponse> result = (status != null)
                ? vendorOrderService.getSellerOrdersByStatus(email, status, page, size)
                : vendorOrderService.getSellerOrders(email, page, size);

        return ResponseEntity.ok(result);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /api/vendor/orders/{id}
    // Returns a single vendor sub-order, only if it belongs to the seller.
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Fetches a specific vendor sub-order by ID.
     * Returns 403 if the order belongs to a different seller.
     *
     * @param id   the VendorOrder ID
     * @param user the authenticated seller
     * @return VendorOrderResponse for the requested sub-order
     */
    @GetMapping("/{id}")
    public ResponseEntity<VendorOrderResponse> getSellerOrderById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {

        VendorOrderResponse response = vendorOrderService.getSellerOrderById(id, user.getUsername());
        return ResponseEntity.ok(response);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PATCH /api/vendor/orders/{id}/status
    // Updates the lifecycle status of one of the seller's own sub-orders.
    // Sellers cannot set CANCELLED (buyer-only) or downgrade from DELIVERED.
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Updates the status of a vendor sub-order.
     *
     * <p>Allowed transitions (enforced in service):
     * <pre>
     *   PENDING → CONFIRMED → SHIPPED → DELIVERED
     * </pre>
     * CANCELLED and downgrading from DELIVERED are rejected.
     *
     * @param id      the VendorOrder ID
     * @param request body containing the new {@link OrderStatus}
     * @param user    the authenticated seller
     * @return updated VendorOrderResponse
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<VendorOrderResponse> updateOrderStatus(
            @PathVariable Long id,
            @Valid @RequestBody OrderStatusRequest request,
            @AuthenticationPrincipal UserDetails user) {

        VendorOrderResponse response = vendorOrderService.updateOrderStatus(
                id, user.getUsername(), request.status());
        return ResponseEntity.ok(response);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /api/vendor/orders/stats
    // Returns aggregate revenue and catalog figures for the seller dashboard.
    // NOTE: This mapping must appear BEFORE /{id} in source order so Spring
    // does not attempt to parse "stats" as a Long path variable.
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns the authenticated seller's dashboard statistics:
     * <ul>
     *   <li>grossRevenue — total vendorSubtotal across non-cancelled orders</li>
     *   <li>totalPlatformFees — AllMart's 10% commission portion</li>
     *   <li>netRevenue — what the seller actually earns</li>
     *   <li>activeProductCount — live (non-deleted) product listings</li>
     * </ul>
     *
     * @param user the authenticated seller
     * @return SellerStatsResponse
     */
    @GetMapping("/stats")
    public ResponseEntity<SellerStatsResponse> getSellerStats(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(required = false) Long userId) {

        SellerStatsResponse stats = userId == null
                ? vendorOrderService.getSellerStats(user.getUsername())
                : vendorOrderService.getSellerStats(userId);
        return ResponseEntity.ok(stats);
    }
}
