package com.sara.allmart.mapper;

import com.sara.allmart.dto.request.ProductRequest;
import com.sara.allmart.dto.response.ProductImage;
import com.sara.allmart.dto.response.ProductResponse;
import com.sara.allmart.entity.Category;
import com.sara.allmart.entity.Product;
import com.sara.allmart.exception.ResourceNotFoundException;
import com.sara.allmart.repository.CategoryRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Component
public class ProductMapper {
    private final CategoryRepository categoryRepository;

    public ProductMapper(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public Product toEntity(ProductRequest request){
        Product product = new Product();
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());
        if (request.imageUrls() != null && !request.imageUrls().isEmpty()) {
            for (int i = 0; i < request.imageUrls().size(); i++) {
                com.sara.allmart.entity.ProductImage img = com.sara.allmart.entity.ProductImage.builder()
                        .imageUrl(request.imageUrls().get(i))
                        .position(i)
                        .build();
                product.addImage(img);
            }
        }
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        product.setCategory(category);

        return product;
    }

    public ProductResponse toResponse(Product product){
        Long id = product.getId();
        String name = product.getName();
        String description = product.getDescription();
        BigDecimal price = product.getPrice();
        Integer stock = product.getStockQuantity();
        String categoryName = "Uncategorized";
        List<ProductImage> imageResponses = product.getImages() != null ?
                product.getImages().stream()
                        .map(img -> new ProductImage(img.getId(), img.getImageUrl(), img.getPosition()))
                        .toList() : Collections.emptyList();
        int reviewCount = product.getReviewCount();
        double averageRating = product.getAverageRating();
        if(product.getCategory() != null) {
        categoryName = product.getCategory().getName();
        }
        Long sellerId = product.getSeller() != null ? product.getSeller().getId() : null;
        String sellerName = product.getSeller() != null
                ? product.getSeller().getFirstName() + " " + product.getSeller().getLastName()
                : "Unknown Seller";
        return new ProductResponse(id,name,description,price,stock,categoryName,imageResponses,reviewCount,averageRating,sellerId,sellerName);
    }
}
