package com.sara.allmart.repository;

import com.sara.allmart.entity.Product;
import com.sara.allmart.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // ──────────────────────────────────────────────────────────────────────────
    // Global / admin product search (existing — unchanged)
    // ──────────────────────────────────────────────────────────────────────────

    @Query(value = "SELECT * FROM products p WHERE " +
            "(:name IS NULL OR CAST(p.name AS TEXT) ILIKE CAST('%' || :name || '%' AS TEXT)) AND " +
            "(:categoryId IS NULL OR p.category_id = :categoryId) AND " +
            "(:minPrice IS NULL OR p.price >= :minPrice) AND " +
            "(:maxPrice IS NULL OR p.price <= :maxPrice) AND " +
            "(:includeDeleted = TRUE OR p.is_deleted = FALSE)",
            nativeQuery = true)
    Page<Product> searchProducts(
            @Param("name") String name,
            @Param("categoryId") Long categoryId,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("includeDeleted") boolean includeDeleted,
            Pageable pageable
    );

    // ──────────────────────────────────────────────────────────────────────────
    // Seller-scoped product queries
    // Data isolation: every query below requires a seller parameter so sellers
    // can never read or count products they do not own.
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns all active (non-deleted) products owned by a specific seller,
     * with optional name and category filtering and full pagination support.
     */
    @Query("SELECT p FROM Product p " +
            "WHERE p.seller = :seller " +
            "AND p.deleted = false " +
            "AND (:name IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%'))) " +
            "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
            "AND (:minPrice IS NULL OR p.price >= :minPrice) " +
            "AND (:maxPrice IS NULL OR p.price <= :maxPrice)")
    Page<Product> findBySellerWithFilters(
            @Param("seller") User seller,
            @Param("name") String name,
            @Param("categoryId") Long categoryId,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable
    );

    /**
     * Returns all products owned by a specific seller regardless of deletion
     * status — used for admin-style seller dashboard views.
     */
    Page<Product> findBySeller(User seller, Pageable pageable);

    /**
     * Counts only active (non-deleted) products for a given seller.
     * Useful for the seller dashboard stats widget.
     */
    long countBySellerAndDeletedFalse(User seller);

    /**
     * Counts all products (including soft-deleted) for a given seller.
     * Useful for admin reporting.
     */
    long countBySeller(User seller);
}
