package com.sara.allmart.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "vendor_orders")
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class VendorOrder extends BaseEntity {

    @Id
    @SequenceGenerator(
            name = "vendor_order_id_seq",
            sequenceName = "vendor_order_id_seq",
            allocationSize = 1
    )
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "vendor_order_id_seq"
    )
    private Long id;

    // The global parent order placed by the buyer
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_vendor_orders_orders"))
    private Order order;

    // The seller who owns this sub-order
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_vendor_orders_seller"))
    private User seller;

    // Gross revenue for this seller before platform commission
    @NotNull
    @PositiveOrZero
    @Column(nullable = false)
    private BigDecimal vendorSubtotal = BigDecimal.ZERO;

    // Platform commission deducted from vendorSubtotal
    @NotNull
    @PositiveOrZero
    @Column(nullable = false)
    private BigDecimal platformFee = BigDecimal.ZERO;

    // Lifecycle status tracked at the seller level
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    // The individual line items belonging to this seller's sub-order
    @OneToMany(mappedBy = "vendorOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();
}
