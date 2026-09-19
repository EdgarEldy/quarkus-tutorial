package com.edgareldy.quarkustutorial.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.edgareldy.quarkustutorial.entity.Category;
import com.edgareldy.quarkustutorial.entity.Product;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Repository tests of ProductRepository against the Dev Services PostgreSQL.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// Real PostgreSQL through Dev Services (see CategoryRepositoryTest): numeric(12,2) and the foreign key are
// database behaviours that only a real instance can prove. Data is committed and cleaned up afterwards.
@QuarkusTest
class ProductRepositoryTest {

    @Inject
    ProductRepository repository;

    @Inject
    CategoryRepository categoryRepository;

    @Inject
    EntityManager em;

    private final List<Long> categories = new ArrayList<>();

    @AfterEach
    void tearDown() {
        QuarkusTransaction.requiringNew().run(() -> categories.forEach(id -> {
            em.createNativeQuery("delete from products where category_id = ?1").setParameter(1, id)
                    .executeUpdate();
            em.createNativeQuery("delete from categories where id = ?1").setParameter(1, id).executeUpdate();
        }));
        categories.clear();
    }

    private Long category() {
        Long id = QuarkusTransaction.requiringNew().call(() -> {
            Category c = new Category();
            c.setCategoryName("prod-repo-" + System.nanoTime());
            categoryRepository.persist(c);
            return c.getId();
        });
        categories.add(id);
        return id;
    }

    private Long product(Long categoryId, String name, String price) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Product p = new Product();
            p.setCategory(categoryRepository.findById(categoryId));
            p.setProductName(name);
            p.setUnitPrice(new BigDecimal(price));
            repository.persist(p);
            return p.getId();
        });
    }

    @Test
    void findByCategoryFiltersAndOrdersById() {
        Long catA = category();
        Long catB = category();
        Long a1 = product(catA, "A1", "1.00");
        Long b1 = product(catB, "B1", "2.00");
        Long a2 = product(catA, "A2", "3.00");

        List<Long> forA = QuarkusTransaction.requiringNew()
                .call(() -> repository.findByCategory(catA).list().stream().map(Product::getId).toList());
        List<Long> forB = QuarkusTransaction.requiringNew()
                .call(() -> repository.findByCategory(catB).list().stream().map(Product::getId).toList());
        List<Long> all = QuarkusTransaction.requiringNew()
                .call(() -> repository.findByCategory(null).list().stream().map(Product::getId).toList());

        assertEquals(List.of(a1, a2), forA);
        assertEquals(List.of(b1), forB);
        assertTrue(all.containsAll(List.of(a1, b1, a2)));
        assertEquals(all.stream().sorted().toList(), all);
    }

    @Test
    void countByCategoryIdCountsOnlyThatCategory() {
        Long catA = category();
        Long catB = category();
        product(catA, "A1", "1.00");
        product(catA, "A2", "1.00");
        product(catB, "B1", "1.00");

        assertEquals(2L, QuarkusTransaction.requiringNew().call(() -> repository.countByCategoryId(catA)));
        assertEquals(1L, QuarkusTransaction.requiringNew().call(() -> repository.countByCategoryId(catB)));
        assertEquals(0L, QuarkusTransaction.requiringNew().call(() -> repository.countByCategoryId(999999999L)));
    }

    @Test
    void unitPriceRoundTripsAsNumeric12Scale2() {
        Long cat = category();
        Long id = product(cat, "Precise", "79.99");
        Long big = product(cat, "Big", "9999999999.99");

        Product found = QuarkusTransaction.requiringNew().call(() -> repository.findById(id));
        Product foundBig = QuarkusTransaction.requiringNew().call(() -> repository.findById(big));

        assertEquals(new BigDecimal("79.99"), found.getUnitPrice());
        assertEquals(new BigDecimal("9999999999.99"), foundBig.getUnitPrice());
    }

    @Test
    void missingCategoryViolatesForeignKey() {
        assertThrows(RuntimeException.class, () -> QuarkusTransaction.requiringNew().run(() -> em
                .createNativeQuery("insert into products (category_id, product_name, unit_price) "
                        + "values (999999999, 'Orphan', 1.00)").executeUpdate()));
    }
}
