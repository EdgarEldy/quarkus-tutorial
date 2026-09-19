package com.edgareldy.quarkustutorial.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.edgareldy.quarkustutorial.dto.common.PageResponse;
import com.edgareldy.quarkustutorial.dto.ecommerce.CategoryRequest;
import com.edgareldy.quarkustutorial.dto.ecommerce.CategoryResponse;
import com.edgareldy.quarkustutorial.entity.Category;
import com.edgareldy.quarkustutorial.exception.BusinessRuleException;
import com.edgareldy.quarkustutorial.exception.ResourceNotFoundException;
import com.edgareldy.quarkustutorial.repository.CategoryRepository;
import com.edgareldy.quarkustutorial.repository.ProductRepository;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Isolated unit tests of CategoryServiceImpl with the repository mocked.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// Plain Mockito with the package-private injected field assigned directly (same approach as
// AuthServiceImplTest): no container is needed to test the business rules.
class CategoryServiceImplTest {

    private CategoryRepository repository;
    private ProductRepository productRepository;
    private CategoryServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(CategoryRepository.class);
        service = new CategoryServiceImpl();
        productRepository = mock(ProductRepository.class);
        service.categoryRepository = repository;
        service.productRepository = productRepository;
    }

    private static Category category(Long id, String name) {
        Category c = new Category();
        c.setId(id);
        c.setCategoryName(name);
        return c;
    }

    @Test
    void deleteIsRefusedWhenCategoryHasProducts() {
        Category c = category(1L, "Books");
        when(repository.findById(1L)).thenReturn(c);
        when(productRepository.countByCategoryId(1L)).thenReturn(2L);

        assertThrows(BusinessRuleException.class, () -> service.delete(1L));
        verify(repository, never()).delete(any(Category.class));
    }

    @Test
    void deleteRemovesCategoryWithoutProducts() {
        Category c = category(1L, "Books");
        when(repository.findById(1L)).thenReturn(c);
        when(productRepository.countByCategoryId(1L)).thenReturn(0L);

        service.delete(1L);

        verify(repository).delete(c);
    }

    @Test
    void deleteMissingCategoryIsNotFound() {
        when(repository.findById(9L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> service.delete(9L));
        verify(repository, never()).delete(any(Category.class));
    }

    @Test
    void updateMissingCategoryIsNotFound() {
        when(repository.findById(9L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> service.update(9L, new CategoryRequest("x")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listBuildsPageResponse() {
        PanacheQuery<Category> all = mock(PanacheQuery.class);
        PanacheQuery<Category> paged = mock(PanacheQuery.class);
        when(repository.findAll(any(Sort.class))).thenReturn(all);
        when(all.page(any(Page.class))).thenReturn(paged);
        when(paged.list()).thenReturn(List.of(category(1L, "A"), category(2L, "B")));
        when(repository.count()).thenReturn(5L);

        PageResponse<CategoryResponse> page = service.list(1, 2);

        assertEquals(2, page.content().size());
        assertEquals("A", page.content().get(0).categoryName());
        assertEquals(1, page.page());
        assertEquals(2, page.size());
        assertEquals(5L, page.totalElements());
        assertEquals(3, page.totalPages());
        ArgumentCaptor<Page> requested = ArgumentCaptor.forClass(Page.class);
        verify(all).page(requested.capture());
        assertEquals(1, requested.getValue().index);
        assertEquals(2, requested.getValue().size);
    }
}
