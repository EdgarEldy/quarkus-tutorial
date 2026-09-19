package com.edgareldy.quarkustutorial.service.impl;

import com.edgareldy.quarkustutorial.dto.common.PageResponse;
import com.edgareldy.quarkustutorial.dto.ecommerce.CategoryRequest;
import com.edgareldy.quarkustutorial.dto.ecommerce.CategoryResponse;
import com.edgareldy.quarkustutorial.entity.Category;
import com.edgareldy.quarkustutorial.exception.BusinessRuleException;
import com.edgareldy.quarkustutorial.exception.ResourceNotFoundException;
import com.edgareldy.quarkustutorial.repository.CategoryRepository;
import com.edgareldy.quarkustutorial.repository.ProductRepository;
import com.edgareldy.quarkustutorial.service.CategoryService;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;

/**
 * Default {@link CategoryService} backed by Panache.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
@ApplicationScoped
public class CategoryServiceImpl implements CategoryService {

    @Inject
    CategoryRepository categoryRepository;

    @Inject
    ProductRepository productRepository;

    @Override
    @Transactional
    public PageResponse<CategoryResponse> list(int page, int size) {
        List<CategoryResponse> content = categoryRepository.findAll(Sort.by("id")).page(Page.of(page, size))
                .list().stream().map(CategoryServiceImpl::toResponse).toList();
        return PageResponse.of(content, page, size, categoryRepository.count());
    }

    @Override
    @Transactional
    public CategoryResponse findById(Long id) {
        return toResponse(find(id));
    }

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        Category category = new Category();
        category.setCategoryName(request.categoryName());
        categoryRepository.persist(category);
        return toResponse(category);
    }

    @Override
    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = find(id);
        category.setCategoryName(request.categoryName());
        return toResponse(category);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Category category = find(id);
        if (productRepository.countByCategoryId(id) > 0) {
            throw new BusinessRuleException("Category " + id + " still has products and cannot be deleted");
        }
        categoryRepository.delete(category);
    }

    private Category find(Long id) {
        Category category = categoryRepository.findById(id);
        if (category == null) {
            throw new ResourceNotFoundException("Category " + id + " not found");
        }
        return category;
    }

    private static CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getCategoryName());
    }
}
