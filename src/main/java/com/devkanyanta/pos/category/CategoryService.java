package com.devkanyanta.pos.category;

import java.util.List;

import com.devkanyanta.pos.category.dto.CategoryResponse;
import com.devkanyanta.pos.category.dto.CreateCategoryRequest;

public interface CategoryService {

    CategoryResponse createCategory(CreateCategoryRequest request);

    List<CategoryResponse> getAllCategories();

    CategoryResponse getCategoryById(Long id);

    CategoryResponse updateCategory(Long id, CreateCategoryRequest request);

    void deleteCategory(Long id);
}
