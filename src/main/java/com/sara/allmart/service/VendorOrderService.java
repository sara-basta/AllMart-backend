package com.sara.allmart.service;

import com.sara.allmart.dto.response.SellerStatsResponse;
import com.sara.allmart.dto.response.VendorOrderResponse;
import com.sara.allmart.entity.OrderStatus;
import com.sara.allmart.entity.User;
import com.sara.allmart.entity.VendorOrder;
import com.sara.allmart.event.OrderStatusEvent;
import com.sara.allmart.exception.ResourceNotFoundException;
import com.sara.allmart.mapper.VendorOrderMapper;
import com.sara.allmart.repository.ProductRepository;
import com.sara.allmart.repository.UserRepository;
import com.sara.allmart.repository.VendorOrderRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Slf4j
public class VendorOrderService {

    private final VendorOrderRepository vendorOrderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final VendorOrderMapper vendorOrderMapper;
    private final ApplicationEventPublisher eventPublisher;

    public VendorOrderService(
            VendorOrderRepository vendorOrderRepository,
            UserRepository userRepository,
            ProductRepository productRepository,
            VendorOrderMapper vendorOrderMapper,
            ApplicationEventPublisher eventPublisher) {
        this.vendorOrderRepository = vendorOrderRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.vendorOrderMapper = vendorOrderMapper;
        this.eventPublisher = eventPublisher;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Paginated order list for the seller dashboard
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns a paginated list of vendor sub-orders belonging to the
     * authenticated seller, sorted newest-first.
     *
     * @param email the authenticated seller's email (from JWT principal)
     * @param page  zero-based page index
     * @param size  number of records per page
     * @return page of VendorOrderResponse DTOs scoped to this seller
     */
    public Page<VendorOrderResponse> getSellerOrders(String email, int page, int size) {
        User seller = resolveSellerByEmail(email);
        Pageable pageable = PageRequest.of(page, size);

        return vendorOrderRepository
                .findBySellerOrderByCreatedAtDesc(seller, pageable)
                .map(vendorOrderMapper::toResponse);
    }

    /**
     * Returns a paginated list of vendor sub-orders filtered by status.
     * Seller can only see their own orders regardless of what status is requested.
     *
     * @param email  the authenticated seller's email
     * @param status the OrderStatus to filter by
     * @param page   zero-based page index
     * @param size   number of records per page
     * @return page of VendorOrderResponse DTOs filtered by status
     */
    public Page<VendorOrderResponse> getSellerOrdersByStatus(
            String email, OrderStatus status, int page, int size) {
        User seller = resolveSellerByEmail(email);
        Pageable pageable = PageRequest.of(page, size);

        return vendorOrderRepository
                .findBySellerAndStatusOrderByCreatedAtDesc(seller, status, pageable)
                .map(vendorOrderMapper::toResponse);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Single order detail with ownership enforcement
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Fetches a single vendor sub-order by ID, verifying it actually belongs
     * to the requesting seller. Throws {@link AccessDeniedException} if the
     * vendorOrder exists but belongs to a different seller.
     *
     * @param vendorOrderId the ID of the VendorOrder to fetch
     * @param email         the authenticated seller's email
     * @return VendorOrderResponse for the requested sub-order
     */
    public VendorOrderResponse getSellerOrderById(Long vendorOrderId, String email) {
        User seller = resolveSellerByEmail(email);

        VendorOrder vendorOrder = vendorOrderRepository.findByIdAndSeller(vendorOrderId, seller)
                .orElseThrow(() -> {
                    // Check if the order exists at all to give a meaningful error message
                    boolean exists = vendorOrderRepository.existsById(vendorOrderId);
                    if (exists) {
                        log.warn("Seller {} attempted to access VendorOrder {} belonging to another seller",
                                email, vendorOrderId);
                        return new AccessDeniedException(
                                "Access Denied: This order does not belong to your account.");
                    }
                    return new ResourceNotFoundException(
                            "VendorOrder not found with ID: " + vendorOrderId);
                });

        return vendorOrderMapper.toResponse(vendorOrder);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Status update with ownership + lifecycle enforcement
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Updates the status of a vendor sub-order.
     * <ul>
     *   <li>Only the owning seller may update their own sub-order.</li>
     *   <li>DELIVERED orders cannot be changed.</li>
     *   <li>Sellers cannot cancel their own sub-orders via this endpoint
     *       (cancellation is buyer-initiated and goes through OrderService).</li>
     * </ul>
     *
     * @param vendorOrderId the ID of the VendorOrder to update
     * @param email         the authenticated seller's email
     * @param newStatus     the desired new OrderStatus
     * @return updated VendorOrderResponse
     */
    @Transactional
    public VendorOrderResponse updateOrderStatus(Long vendorOrderId, String email, OrderStatus newStatus) {
        User seller = resolveSellerByEmail(email);

        VendorOrder vendorOrder = vendorOrderRepository.findByIdAndSeller(vendorOrderId, seller)
                .orElseThrow(() -> {
                    boolean exists = vendorOrderRepository.existsById(vendorOrderId);
                    if (exists) {
                        log.warn("Seller {} attempted to update VendorOrder {} belonging to another seller",
                                email, vendorOrderId);
                        return new AccessDeniedException(
                                "Access Denied: You can only update your own orders.");
                    }
                    return new ResourceNotFoundException(
                            "VendorOrder not found with ID: " + vendorOrderId);
                });

        if (vendorOrder.getStatus() == OrderStatus.DELIVERED) {
            throw new IllegalStateException(
                    "You cannot change the status of a delivered order.");
        }

        if (newStatus == OrderStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Sellers cannot cancel orders directly. Cancellation is buyer-initiated.");
        }

        log.info("Seller {} updating VendorOrder {} from {} to {}",
                email, vendorOrderId, vendorOrder.getStatus(), newStatus);

        vendorOrder.setStatus(newStatus);
        VendorOrder saved = vendorOrderRepository.save(vendorOrder);

        // Publish the event so email notifications fire (e.g., buyer notified of shipment)
        eventPublisher.publishEvent(new OrderStatusEvent(this, saved.getOrder(), newStatus.name()));

        return vendorOrderMapper.toResponse(saved);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Revenue statistics for the dashboard stats widget
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Fetches the seller's aggregated revenue figures and active product count.
     * <ul>
     *   <li>grossRevenue: sum of vendorSubtotal across non-cancelled sub-orders</li>
     *   <li>totalPlatformFees: grossRevenue × 10% (already stored on each VendorOrder)</li>
     *   <li>netRevenue: grossRevenue minus totalPlatformFees</li>
     *   <li>activeProductCount: number of live (non-deleted) products listed by this seller</li>
     * </ul>
     *
     * @param email the authenticated seller's email
     * @return SellerStatsResponse with aggregated financial and catalog data
     */
    public SellerStatsResponse getSellerStats(String email) {
        User seller = resolveSellerByEmail(email);

        BigDecimal grossRevenue = vendorOrderRepository.calculateTotalRevenueForSeller(seller);
        BigDecimal netRevenue   = vendorOrderRepository.calculateNetRevenueForSeller(seller);

        // Platform fee = difference between gross and net
        BigDecimal totalPlatformFees = grossRevenue.subtract(netRevenue);

        long activeProductCount = productRepository.countBySellerAndDeletedFalse(seller);

        log.info("Stats fetched for seller {}: gross={}, net={}, products={}",
                email, grossRevenue, netRevenue, activeProductCount);

        return new SellerStatsResponse(grossRevenue, totalPlatformFees, netRevenue, activeProductCount);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    private User resolveSellerByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Seller account not found for email: " + email));
    }
}
