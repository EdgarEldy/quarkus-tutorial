package com.edgareldy.quarkustutorial.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.edgareldy.quarkustutorial.dto.common.PageResponse;
import com.edgareldy.quarkustutorial.dto.ecommerce.ProductRequest;
import com.edgareldy.quarkustutorial.dto.ecommerce.ProductResponse;
import com.edgareldy.quarkustutorial.entity.Category;
import com.edgareldy.quarkustutorial.entity.Product;
import com.edgareldy.quarkustutorial.exception.BusinessRuleException;
import com.edgareldy.quarkustutorial.exception.ResourceNotFoundException;
import com.edgareldy.quarkustutorial.repository.CategoryRepository;
import com.edgareldy.quarkustutorial.repository.OrderRepository;
import com.edgareldy.quarkustutorial.repository.ProductRepository;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Isolated unit tests of ProductServiceImpl with both repositories mocked.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// Plain Mockito, no container: caching annotations are not active here (no CDI interceptors), so only the
// business rules are exercised. Caching is covered by ProductCacheTest.
class ProductServiceImplTest {

    private ProductRepository productRepository;
    private CategoryRepository categoryRepository;
    private OrderRepository orderRepository;
    private ProductServiceImpl service;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        service = new ProductServiceImpl();
        service.productRepository = productRepository;
        service.categoryRepository = categoryRepository;
        orderRepository = mock(OrderRepository.class);
        service.orderRepository = orderRepository;
    }

    private static Category category(Long id) {
        Category c = new Category();
        c.setId(id);
        c.setCategoryName("C" + id);
        return c;
    }

    private static Product product(Long id, Category category, String name, String price) {
        Product p = new Product();
        p.setId(id);
        p.setCategory(category);
        p.setProductName(name);
        p.setUnitPrice(new BigDecimal(price));
        return p;
    }

    @Test
    void createWithUnknownCategoryIsRefused() {
        when(categoryRepository.findById(7L)).thenReturn(null);

        assertThrows(BusinessRuleException.class,
                () -> service.create(new ProductRequest(7L, "X", new BigDecimal("1.00"))));
        verify(productRepository, never()).persist(any(Product.class));
    }

    @Test
    void createScalesThePriceLikeTheDatabaseColumn() {
        when(categoryRepository.findById(1L)).thenReturn(category(1L));

        ProductResponse response = service.create(new ProductRequest(1L, "Pen", new BigDecimal("10.5")));

        assertEquals(new BigDecimal("10.50"), response.unitPrice());
    }

    @Test
    void createPersistsAndMapsResponse() {
        when(categoryRepository.findById(1L)).thenReturn(category(1L));

        ProductResponse response = service.create(new ProductRequest(1L, "Pen", new BigDecimal("2.50")));

        assertEquals(1L, response.categoryId());
        assertEquals("Pen", response.productName());
        assertEquals(new BigDecimal("2.50"), response.unitPrice());
        verify(productRepository).persist(any(Product.class));
    }

    @Test
    void updateWithUnknownCategoryIsRefused() {
        Product existing = product(3L, category(1L), "Old", "1.00");
        when(productRepository.findById(3L)).thenReturn(existing);
        when(categoryRepository.findById(7L)).thenReturn(null);

        assertThrows(BusinessRuleException.class,
                () -> service.update(3L, new ProductRequest(7L, "New", new BigDecimal("2.00"))));
        assertEquals("Old", existing.getProductName());
    }

    @Test
    void updateChangesFields() {
        Product existing = product(3L, category(1L), "Old", "1.00");
        when(productRepository.findById(3L)).thenReturn(existing);
        when(categoryRepository.findById(2L)).thenReturn(category(2L));

        ProductResponse response = service.update(3L, new ProductRequest(2L, "New", new BigDecimal("2.00")));

        assertEquals(2L, response.categoryId());
        assertEquals("New", existing.getProductName());
        assertEquals(new BigDecimal("2.00"), existing.getUnitPrice());
    }

    @Test
    void missingProductIsNotFoundOnFindUpdateDelete() {
        when(productRepository.findById(9L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> service.findById(9L));
        assertThrows(ResourceNotFoundException.class,
                () -> service.update(9L, new ProductRequest(1L, "x", new BigDecimal("1.00"))));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(9L));
        verify(productRepository, never()).delete(any(Product.class));
    }

    @Test
    void deleteRemovesProduct() {
        Product existing = product(3L, category(1L), "Old", "1.00");
        when(productRepository.findById(3L)).thenReturn(existing);

        service.delete(3L);

        verify(productRepository).delete(existing);
    }

    @Test
    void deleteIsRefusedWhileOrdersReferenceTheProduct() {
        Product existing = product(3L, category(1L), "Old", "1.00");
        when(productRepository.findById(3L)).thenReturn(existing);
        when(orderRepository.countByProductId(3L)).thenReturn(1L);

        assertThrows(BusinessRuleException.class, () -> service.delete(3L));
        verify(productRepository, never()).delete(any(Product.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listWithoutFilterUsesGlobalCount() {
        PanacheQuery<Product> query = mock(PanacheQuery.class);
        PanacheQuery<Product> paged = mock(PanacheQuery.class);
        when(productRepository.findByCategory(null)).thenReturn(query);
        when(query.page(any(Page.class))).thenReturn(paged);
        Category c = category(1L);
        when(paged.list()).thenReturn(List.of(product(1L, c, "A", "1.00"), product(2L, c, "B", "2.00")));
        when(productRepository.count()).thenReturn(5L);

        PageResponse<ProductResponse> page = service.list(1, 2, null);

        assertEquals(2, page.content().size());
        assertEquals("A", page.content().get(0).productName());
        assertEquals(1, page.page());
        assertEquals(2, page.size());
        assertEquals(5L, page.totalElements());
        assertEquals(3, page.totalPages());
        verify(productRepository, never()).countByCategoryId(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listWithCategoryFilterUsesCategoryCount() {
        PanacheQuery<Product> query = mock(PanacheQuery.class);
        PanacheQuery<Product> paged = mock(PanacheQuery.class);
        when(productRepository.findByCategory(4L)).thenReturn(query);
        when(query.page(any(Page.class))).thenReturn(paged);
        when(paged.list()).thenReturn(List.of(product(1L, category(4L), "A", "1.00")));
        when(productRepository.countByCategoryId(4L)).thenReturn(1L);

        PageResponse<ProductResponse> page = service.list(0, 20, 4L);

        assertEquals(1, page.content().size());
        assertEquals(4L, page.content().get(0).categoryId());
        assertEquals(1L, page.totalElements());
        assertEquals(1, page.totalPages());
        verify(productRepository, never()).count();
    }
}
