package com.sara.allmart.controller;

import com.sara.allmart.dto.request.ProductCategoryRequest;
import com.sara.allmart.dto.request.ProductPriceRequest;
import com.sara.allmart.dto.request.ProductRequest;
import com.sara.allmart.dto.request.ProductStockRequest;
import com.sara.allmart.dto.response.ProductResponse;
import com.sara.allmart.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;


@RestController
@RequestMapping("/api/products")
@Validated
@Tag(name = "products", description = "Manage inventory, prices, and stock levels")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PreAuthorize("hasAnyAuthority('SELLER', 'ADMIN')")
    @Operation(summary = "Create a new product",
    description = "Adds a new product to the catalog. Requires a valid category ID. Caller must be a SELLER or ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Product created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input (e.g., negative price)"),
            @ApiResponse(responseCode = "403", description = "Access denied — caller is not a SELLER or ADMIN"),
            @ApiResponse(responseCode = "404", description = "Category not found")
    })
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request,
                                                         Authentication authentication){
        ProductResponse response = productService.createProduct(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<ProductResponse>> searchProducts(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @PositiveOrZero BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<ProductResponse> response = productService.searchProducts(name, categoryId, minPrice, maxPrice,false, page, size);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("/admin")
    public ResponseEntity<Page<ProductResponse>> searchProductsAdmin(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @PositiveOrZero BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "true") boolean includeDeleted,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<ProductResponse> response = productService.searchProducts(name, categoryId, minPrice, maxPrice,includeDeleted, page, size);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAnyAuthority('SELLER', 'ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ProductResponse> deleteProduct(@PathVariable Long id){
        ProductResponse response = productService.deleteProduct(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id) {
        ProductResponse response = productService.getProductById(id);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("/admin/{id}")
    public ResponseEntity<ProductResponse> getAdminProductById(@PathVariable Long id) {
        ProductResponse response = productService.getAdminProductById(id);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAnyAuthority('SELLER', 'ADMIN')")
    @Operation(summary = "Update an existing product",
            description = "Updates all fields of the product (Name, Description, Price, Stock, Category).")
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {
        ProductResponse response = productService.updateProduct(id, request);
        return ResponseEntity.ok(response);
    }
}
