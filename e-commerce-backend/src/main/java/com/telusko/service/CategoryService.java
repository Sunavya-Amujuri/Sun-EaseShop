package com.telusko.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import com.telusko.model.Category;
import com.telusko.repository.CategoryRepository;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categories;
    private final AppVectorStoreService appVectors;


    public Page<Category> getAllCategories(int page, int limit) {
        if (page < 1) page = 1;
        if (limit < 1) limit = 5;

        Pageable pageable = PageRequest.of(page - 1, limit,
                Sort.by(Sort.Direction.ASC, "name"));

        return categories.findByActiveTrue(pageable);
    }

    public Category createCategory(Category request) {
        Category category = Category.builder()
                .name(request.getName())
                .description(request.getDescription())
                .active(true)
                .build();


        Category saved = categories.save(category);
        appVectors.indexCategory(saved);


        return saved;
    }

    public Category getCategoryById(Long categoryId) {
        return categories.findByIdAndActiveTrue(categoryId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Category not found with id: " + categoryId));
    }

    public void deleteCategory(Long categoryId) {
        Category category = categories.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Category not found with id: " + categoryId));
        category.setActive(false);
        Category saved = categories.save(category);
        appVectors.indexCategory(saved);

    }

    public Category updateCategory(Long categoryId, Category request) {
        Category category = categories.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Category not found with id: " + categoryId));

        if (request.getName() != null && !request.getName().isBlank()) {
            category.setName(request.getName());
        }
        if (request.getDescription() != null) {
            category.setDescription(request.getDescription());
        }
        if (request.getActive() != null) {
            category.setActive(request.getActive());
        }

        Category saved = categories.save(category);
        appVectors.indexCategory(saved);


        return saved;
    }
}
