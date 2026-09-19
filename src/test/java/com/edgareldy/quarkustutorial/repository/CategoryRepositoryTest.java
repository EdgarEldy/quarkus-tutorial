package com.edgareldy.quarkustutorial.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.edgareldy.quarkustutorial.entity.Category;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Repository tests of CategoryRepository against the Dev Services PostgreSQL.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// @QuarkusTest boots the real datasource (Dev Services PostgreSQL, Flyway schema), so the native
// products count runs against the real table. Data is committed in QuarkusTransaction and removed afterwards.
@QuarkusTest
class CategoryRepositoryTest {

    @Inject
    CategoryRepository repository;

    @Inject
    ProductRepository productRepository;

    @Inject
    EntityManager em;

    private Long categoryId;

    @AfterEach
    void tearDown() {
        if (categoryId != null) {
            QuarkusTransaction.requiringNew().run(() -> {
                em.createNativeQuery("delete from products where category_id = ?1").setParameter(1, categoryId)
                        .executeUpdate();
                em.createNativeQuery("delete from categories where id = ?1").setParameter(1, categoryId)
                        .executeUpdate();
            });
        }
    }

    private Long persist(String name) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Category c = new Category();
            c.setCategoryName(name);
            repository.persist(c);
            return c.getId();
        });
    }

    @Test
    void persistAndFind() {
        categoryId = persist("Repo test");
        Category found = QuarkusTransaction.requiringNew().call(() -> repository.findById(categoryId));
        assertNotNull(found);
        assertEquals("Repo test", found.getCategoryName());
    }

    @Test
    void countProductsReflectsInsertedRows() {
        categoryId = persist("Repo count");
        assertEquals(0L, QuarkusTransaction.requiringNew().call(() -> productRepository.countByCategoryId(categoryId)));

        QuarkusTransaction.requiringNew().run(() -> {
            for (int i = 0; i < 3; i++) {
                em.createNativeQuery("insert into products (category_id, product_name, unit_price) "
                        + "values (?1, 'P', 1.00)").setParameter(1, categoryId).executeUpdate();
            }
        });

        assertEquals(3L, QuarkusTransaction.requiringNew().call(() -> productRepository.countByCategoryId(categoryId)));
    }
}
