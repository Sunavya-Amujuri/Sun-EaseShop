package com.telusko.controller;

import com.telusko.dto.ApiMessage;
import com.telusko.dto.CreateProductRequest;
import com.telusko.dto.ProductResponseDto;
import com.telusko.dto.UpdateProductRequest;
import com.telusko.service.ProductService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ecommerce")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Browse products catalog")
public class ProductController {

    private final ProductService productService;


    @GetMapping("/products")
    public ResponseEntity<Page<ProductResponseDto>> getAllProducts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit
    ) {
        Page<ProductResponseDto> products = productService.getAllProducts(page, limit);
        return ResponseEntity.ok(products);
    }

    @PostMapping(value = "/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ProductResponseDto> createProduct(
            @ModelAttribute CreateProductRequest request,
            @RequestPart("mainImage") MultipartFile mainImage,
            @RequestPart(value = "subImages", required = false)
            List<MultipartFile> subImages
    ) {
        ProductResponseDto created =
                productService.createProduct(request, mainImage, subImages);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/products/{productId}")
    public ResponseEntity<ProductResponseDto> getProductById(
            @PathVariable Long productId) {
        return ResponseEntity.ok(productService.getProductById(productId));
    }

    @DeleteMapping("/products/{productId}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiMessage> deleteProduct(@PathVariable Long productId) {
        productService.deleteProduct(productId);
        return ResponseEntity.ok(new ApiMessage("Product deleted successfully"));
    }

    @PatchMapping(value = "/products/{productId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ProductResponseDto> updateProduct(
            @PathVariable Long productId,
            @ModelAttribute UpdateProductRequest request,
            @RequestPart(value = "mainImage", required = false)
            MultipartFile mainImage,
            @RequestPart(value = "subImages", required = false)
            List<MultipartFile> subImages
    ) {
        ProductResponseDto updated =
                productService.updateProduct(productId, request, mainImage, subImages);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/products/category/{categoryId}")
    public ResponseEntity<Page<ProductResponseDto>> getProductsByCategory(
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int limit
    ) {
        return ResponseEntity.ok(
                productService.getProductsByCategory(categoryId, page, limit));
    }

    @GetMapping("/products/search")
    public ResponseEntity<List<ProductResponseDto>> searchProducts(@RequestParam String query) {
        List<ProductResponseDto> products = productService.searchProducts(query);
        return new ResponseEntity<>(products, HttpStatus.OK);
    }

    @GetMapping("/products/smart-search")
    public ResponseEntity<List<ProductResponseDto>> smartSearchProducts(@RequestParam String query) {
        List<ProductResponseDto> products = productService.semanticSearchProducts(query);
        return new ResponseEntity<>(products, HttpStatus.OK);
    }
    
    @PostMapping("/product/generate-description")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String,String>> generateDescription(@RequestParam String name,
                                                                  @RequestParam String category,
                                                                  @RequestParam String brand) {
        String aiDescription = productService.generateDescription(name, category, brand);
        return new ResponseEntity<>(Map.of("description", aiDescription), HttpStatus.OK);
    }

    @PostMapping("/product/generate-image")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String,byte[]>> generateImage(@RequestParam String name,
                                                            @RequestParam String category,
                                                            @RequestParam String description,
                                                            @RequestParam String brand) {
        byte[] aiImage = productService.generateImage(name, category, description, brand);

        // generateImage returns null when the model call fails, and Map.of rejects null values,
        // so the failure used to surface as an unrelated NullPointerException.
        if (aiImage == null) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
        return ResponseEntity.ok(Map.of("image", aiImage));
    }
}
