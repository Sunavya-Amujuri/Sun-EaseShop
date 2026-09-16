package com.telusko.controller;

import com.telusko.model.Category;
import com.telusko.service.CategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ecommerce")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Manage product categories")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/categories")
    public ResponseEntity<Page<Category>> getAllCategories(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int limit
    ) {
        return ResponseEntity.ok(categoryService.getAllCategories(page, limit));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/categories")
    public ResponseEntity<Category> createCategory(
            @RequestBody Category category
    ) {
        Category created = categoryService.createCategory(category);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/categories/{categoryId}")
    public ResponseEntity<Category> getCategoryById(
            @PathVariable Long categoryId
    ) {
        return ResponseEntity.ok(categoryService.getCategoryById(categoryId));
    }

    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/categories/{categoryId}")
    public ResponseEntity<Map<String, String>> deleteCategory(
            @PathVariable Long categoryId
    ) {
        categoryService.deleteCategory(categoryId);
        return ResponseEntity.ok(Map.of("message", "Category deleted successfully"));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/categories/{categoryId}")
    public ResponseEntity<Category> updateCategory(
            @PathVariable Long categoryId,
            @RequestBody Category category
    ) {
        Category updated = categoryService.updateCategory(categoryId, category);
        return ResponseEntity.ok(updated);
    }
}
