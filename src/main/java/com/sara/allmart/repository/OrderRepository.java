package com.sara.allmart.repository;

import com.sara.allmart.entity.Order;
import com.sara.allmart.entity.OrderStatus;
import com.sara.allmart.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findOrdersByUser(User user);

    Page<Order> findByUser(User user, Pageable pageable);

    /**
     * Counts how many times a product has been delivered to a user.
     * Items now live under VendorOrder, so the join path is:
     * Order → vendorOrders → items (OrderItem)
     */
    @Query("SELECT COUNT(i) FROM Order o " +
            "JOIN o.vendorOrders vo " +
            "JOIN vo.items i " +
            "WHERE o.user = :user " +
            "AND i.product.id = :productId " +
            "AND vo.status = :status")
    long countDeliveredProductForUser(@Param("user") User user,
                                      @Param("productId") Long productId,
                                      @Param("status") OrderStatus status);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.user WHERE o.id = :orderId")
    Optional<Order> findByIdWithUser(@Param("orderId") Long orderId);

    /**
     * Marks an order as paid at the parent level.
     * isPaid is still on Order; status has moved to VendorOrder.
     * The Stripe webhook marks isPaid and separately transitions all
     * eligible VendorOrders to PAID.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Order o SET o.isPaid = true WHERE o.id = :orderId AND o.isPaid = false")
    int markPaidIfUnpaid(@Param("orderId") Long orderId);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.isPaid = true")
    BigDecimal sumPaidOrderRevenue();
}
