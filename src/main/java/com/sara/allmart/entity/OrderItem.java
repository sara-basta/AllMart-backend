package com.sara.allmart.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class OrderItem {
    @Id
    @SequenceGenerator(
            name = "order_items_id_seq",
            sequenceName = "order_items_id_seq",
            allocationSize = 1
    )
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "order_items_id_seq"
    )
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "vendor_order_id",
            foreignKey = @ForeignKey(name = "fk_order_items_vendor_orders")
    )
    @JsonIgnore
    private VendorOrder vendorOrder;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name="product_id",
            foreignKey = @ForeignKey(name = "fk_order_items_products")
    )
    private Product product;

    @NotNull
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    @NotNull
    @Positive
    private BigDecimal priceAtPurchase;
}
