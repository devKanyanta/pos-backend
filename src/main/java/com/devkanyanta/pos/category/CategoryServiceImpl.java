package com.devkanyanta.pos.category;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.devkanyanta.pos.category.dto.CategoryResponse;
import com.devkanyanta.pos.category.dto.CreateCategoryRequest;
import com.devkanyanta.pos.product.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    private final ProductRepository productRepository;

    @Override
    public CategoryResponse createCategory(CreateCategoryRequest request) {

        validateRequest(request);
        validateCategoryName(request.getName(), null);

        CategoryEntity category = CategoryEntity.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();

        return toResponse(categoryRepository.save(category));
    }

    @Override
    public List<CategoryResponse> getAllCategories() {

        return categoryRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public CategoryResponse getCategoryById(Long id) {

        return toResponse(findCategoryById(id));
    }

    @Override
    public CategoryResponse updateCategory(Long id, CreateCategoryRequest request) {

        CategoryEntity category = findCategoryById(id);
        validateRequest(request);
        validateCategoryName(request.getName(), id);

        category.setName(request.getName());
        category.setDescription(request.getDescription());

        return toResponse(categoryRepository.save(category));
    }

    @Override
    public void deleteCategory(Long id) {

        CategoryEntity category = findCategoryById(id);

        if (productRepository.existsByCategoryId(id)) {
            throw new RuntimeException("Category cannot be deleted while products are assigned to it");
        }

        categoryRepository.delete(category);
    }

    private CategoryEntity findCategoryById(Long id) {

        return categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));
    }

    private void validateCategoryName(String name, Long categoryId) {

        categoryRepository.findByName(name)
                .filter(existingCategory -> !existingCategory.getId().equals(categoryId))
                .ifPresent(existingCategory -> {
                    throw new RuntimeException("Category name already exists");
                });
    }

    private void validateRequest(CreateCategoryRequest request) {

        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new RuntimeException("Category name is required");
        }
    }

    private CategoryResponse toResponse(CategoryEntity category) {

        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .build();
    }
}
