package com.sara.allmart.repository;

import com.sara.allmart.entity.OrderStatus;
import com.sara.allmart.entity.User;
import com.sara.allmart.entity.VendorOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface VendorOrderRepository extends JpaRepository<VendorOrder, Long> {

    /**
     * Returns all VendorOrders that belong to a specific seller, newest first.
     * Used for the seller dashboard's order list.
     */
    Page<VendorOrder> findBySellerOrderByCreatedAtDesc(User seller, Pageable pageable);

    /**
     * Returns all VendorOrders for a seller filtered by status.
     * Useful for a seller filtering their dashboard by PENDING / SHIPPED / etc.
     */
    Page<VendorOrder> findBySellerAndStatusOrderByCreatedAtDesc(
            User seller, OrderStatus status, Pageable pageable);

    /**
     * Calculates the total gross revenue (vendorSubtotal) earned by a seller
     * across all their non-cancelled sub-orders.
     * Returns 0 if the seller has no qualifying orders.
     */
    @Query("SELECT COALESCE(SUM(vo.vendorSubtotal), 0) FROM VendorOrder vo " +
            "WHERE vo.seller = :seller " +
            "AND vo.status != com.sara.allmart.entity.OrderStatus.CANCELLED")
    BigDecimal calculateTotalRevenueForSeller(@Param("seller") User seller);

    /**
     * Calculates the net revenue (vendorSubtotal minus platformFee) earned by a seller
     * across all non-cancelled sub-orders.
     */
    @Query("SELECT COALESCE(SUM(vo.vendorSubtotal - vo.platformFee), 0) FROM VendorOrder vo " +
            "WHERE vo.seller = :seller " +
            "AND vo.status != com.sara.allmart.entity.OrderStatus.CANCELLED")
    BigDecimal calculateNetRevenueForSeller(@Param("seller") User seller);

    /**
     * Finds a single VendorOrder by its ID only if it belongs to the given seller.
     * Prevents sellers from accessing other sellers' sub-orders.
     */
    Optional<VendorOrder> findByIdAndSeller(Long id, User seller);
}
