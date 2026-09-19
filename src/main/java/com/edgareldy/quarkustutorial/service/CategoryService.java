package com.edgareldy.quarkustutorial.service;

import com.edgareldy.quarkustutorial.dto.common.PageResponse;
import com.edgareldy.quarkustutorial.dto.ecommerce.CategoryRequest;
import com.edgareldy.quarkustutorial.dto.ecommerce.CategoryResponse;

/**
 * Contract for category management.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
public interface CategoryService {

    /** @return one page of categories ordered by id */
    PageResponse<CategoryResponse> list(int page, int size);

    /** @return the category, or throws ResourceNotFoundException (404) */
    CategoryResponse findById(Long id);

    /** @return the created category */
    CategoryResponse create(CategoryRequest request);

    /** @return the updated category, or throws ResourceNotFoundException (404) */
    CategoryResponse update(Long id, CategoryRequest request);

    /** Deletes a category; 404 if missing, BusinessRuleException (422) if it still has products. */
    void delete(Long id);
}
