package com.edgareldy.quarkustutorial.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.edgareldy.quarkustutorial.dto.ecommerce.ProductRequest;
import com.edgareldy.quarkustutorial.dto.ecommerce.ProductResponse;
import com.edgareldy.quarkustutorial.entity.Category;
import com.edgareldy.quarkustutorial.exception.ResourceNotFoundException;
import com.edgareldy.quarkustutorial.repository.CategoryRepository;
import com.edgareldy.quarkustutorial.repository.ProductRepository;
import com.edgareldy.quarkustutorial.service.ProductService;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheName;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Verifies the product cache: reads are served from cache, writes evict the entry, misses are not cached.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// @QuarkusTest is needed because @CacheResult/@CacheInvalidate are CDI interceptors that only exist inside
// the container. @InjectSpy (quarkus-junit-mockito) wraps the real ProductRepository bean in a Mockito spy,
// so the real database is still used but the test can count how many times the service reached it.
@QuarkusTest
class ProductCacheTest {

    @Inject
    ProductService service;

    @InjectSpy
    ProductRepository productRepository;

    @Inject
    CategoryRepository categoryRepository;

    @Inject
    EntityManager em;

    @CacheName("product-cache")
    Cache cache;

    private final List<Long> products = new ArrayList<>();
    private Long categoryId;

    @BeforeEach
    void setUp() {
        // The cache lives for the whole application run and is shared by every test class: start empty so
        // one test can never be served an entry stored by another.
        cache.invalidateAll().await().indefinitely();
        categoryId = QuarkusTransaction.requiringNew().call(() -> {
            Category c = new Category();
            c.setCategoryName("cache-" + System.nanoTime());
            categoryRepository.persist(c);
            return c.getId();
        });
        Mockito.clearInvocations(productRepository);
    }

    @AfterEach
    void tearDown() {
        cache.invalidateAll().await().indefinitely();
        QuarkusTransaction.requiringNew().run(() -> {
            products.forEach(id -> em.createNativeQuery("delete from products where id = ?1").setParameter(1, id)
                    .executeUpdate());
            em.createNativeQuery("delete from categories where id = ?1").setParameter(1, categoryId)
                    .executeUpdate();
        });
        products.clear();
    }

    private ProductResponse create(String name, String price) {
        ProductResponse created = service.create(new ProductRequest(categoryId, name, new BigDecimal(price)));
        products.add(created.id());
        return created;
    }

    @Test
    void _01_ShouldServeFromCache_WhenProductReadTwice() {
        Long id = create("Cached", "5.00").id();
        Mockito.clearInvocations(productRepository);

        ProductResponse first = service.findById(id);
        ProductResponse second = service.findById(id);

        assertEquals(first, second);
        verify(productRepository, times(1)).findById(id);
    }

    @Test
    void _02_ShouldReloadFreshData_WhenProductUpdated() {
        Long id = create("Before", "5.00").id();
        assertEquals("Before", service.findById(id).productName());

        service.update(id, new ProductRequest(categoryId, "After", new BigDecimal("6.50")));
        Mockito.clearInvocations(productRepository);

        ProductResponse reloaded = service.findById(id);

        assertEquals("After", reloaded.productName());
        assertEquals(0, new BigDecimal("6.50").compareTo(reloaded.unitPrice()));
        verify(productRepository, times(1)).findById(id);
        service.findById(id);
        verify(productRepository, times(1)).findById(id);
    }

    @Test
    void _03_ShouldThrowNotFoundOnNextRead_WhenProductDeleted() {
        Long id = create("Doomed", "5.00").id();
        service.findById(id);

        service.delete(id);

        assertThrows(ResourceNotFoundException.class, () -> service.findById(id));
    }

    @Test
    void _04_ShouldNotCacheNotFound_WhenProductMissing() {
        assertThrows(ResourceNotFoundException.class, () -> service.findById(999999999L));
        assertThrows(ResourceNotFoundException.class, () -> service.findById(999999999L));

        verify(productRepository, times(2)).findById(999999999L);
    }

    @Test
    void _05_ShouldCacheProduct_WhenCreatedThenRead() {
        Long id = create("Fresh", "7.25").id();
        Mockito.clearInvocations(productRepository);

        assertEquals("Fresh", service.findById(id).productName());
        assertEquals("Fresh", service.findById(id).productName());

        verify(productRepository, times(1)).findById(id);
        verify(productRepository, never()).findById(Mockito.<Long>argThat(v -> v != null && v != id.longValue()));
    }
}
