package com.edgareldy.quarkustutorial.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.edgareldy.quarkustutorial.entity.Category;
import com.edgareldy.quarkustutorial.entity.Customer;
import com.edgareldy.quarkustutorial.entity.Order;
import com.edgareldy.quarkustutorial.entity.Product;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Repository tests of OrderRepository against the Dev Services PostgreSQL, including the HQL entity name.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// Real PostgreSQL through Dev Services (see CategoryRepositoryTest). This is the runtime proof that the
// "CustomerOrder" entity name works: Panache generates HQL such as "FROM CustomerOrder" for count() and
// findAll(), which would not parse with the reserved word "Order".
@QuarkusTest
class OrderRepositoryTest {

    @Inject
    OrderRepository repository;

    @Inject
    CustomerRepository customerRepository;

    @Inject
    ProductRepository productRepository;

    @Inject
    CategoryRepository categoryRepository;

    @Inject
    EntityManager em;

    private Long categoryId;
    private Long customerA;
    private Long customerB;
    private Long productA;
    private Long productB;

    @BeforeEach
    void setUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            Category cat = new Category();
            cat.setCategoryName("order-repo-" + System.nanoTime());
            categoryRepository.persist(cat);
            categoryId = cat.getId();
            customerA = customer();
            customerB = customer();
            productA = product(cat);
            productB = product(cat);
        });
    }

    @AfterEach
    void tearDown() {
        QuarkusTransaction.requiringNew().run(() -> {
            for (Long id : List.of(customerA, customerB)) {
                em.createNativeQuery("delete from orders where customer_id = ?1").setParameter(1, id)
                        .executeUpdate();
                em.createNativeQuery("delete from customers where id = ?1").setParameter(1, id).executeUpdate();
            }
            em.createNativeQuery("delete from products where category_id = ?1").setParameter(1, categoryId)
                    .executeUpdate();
            em.createNativeQuery("delete from categories where id = ?1").setParameter(1, categoryId)
                    .executeUpdate();
        });
    }

    private Long customer() {
        Customer c = new Customer();
        c.setFirstName("Ada");
        c.setLastName("Lovelace");
        customerRepository.persist(c);
        return c.getId();
    }

    private Long product(Category category) {
        Product p = new Product();
        p.setCategory(category);
        p.setProductName("P" + System.nanoTime());
        p.setUnitPrice(new BigDecimal("2.50"));
        productRepository.persist(p);
        return p.getId();
    }

    private Long order(Long customerId, Long productId, int quantity, String total) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Order o = new Order();
            o.setCustomer(customerRepository.findById(customerId));
            o.setProduct(productRepository.findById(productId));
            o.setQuantity(quantity);
            o.setTotal(new BigDecimal(total));
            repository.persist(o);
            return o.getId();
        });
    }

    private List<Long> ids(Long customerId, Long productId) {
        return QuarkusTransaction.requiringNew()
                .call(() -> repository.search(customerId, productId).list().stream().map(Order::getId).toList());
    }

    @Test
    void persistAndFindRoundTripOnTheCustomerOrderEntity() {
        Long id = order(customerA, productA, 3, "7.50");

        Order found = QuarkusTransaction.requiringNew().call(() -> {
            Order o = repository.findById(id);
            // Touch the lazy associations inside the transaction.
            assertEquals(customerA, o.getCustomer().getId());
            assertEquals(productA, o.getProduct().getId());
            return o;
        });

        assertEquals(3, found.getQuantity());
        assertEquals(new BigDecimal("7.50"), found.getTotal());
    }

    @Test
    void searchAndCountWorkWithEveryFilterCombination() {
        Long o1 = order(customerA, productA, 1, "2.50");
        Long o2 = order(customerA, productB, 2, "5.00");
        Long o3 = order(customerB, productA, 3, "7.50");

        assertEquals(List.of(o1, o2), ids(customerA, null));
        assertEquals(List.of(o3), ids(customerB, null));
        assertEquals(List.of(o1, o3), ids(null, productA));
        assertEquals(List.of(o2), ids(customerA, productB));
        assertTrue(ids(customerB, productB).isEmpty());
        List<Long> all = ids(null, null);
        assertTrue(all.containsAll(List.of(o1, o2, o3)));
        assertEquals(all.stream().sorted().toList(), all);

        assertEquals(2L, QuarkusTransaction.requiringNew().call(() -> repository.countFiltered(customerA, null)));
        assertEquals(2L, QuarkusTransaction.requiringNew().call(() -> repository.countFiltered(null, productA)));
        assertEquals(1L,
                QuarkusTransaction.requiringNew().call(() -> repository.countFiltered(customerA, productB)));
        assertEquals(0L,
                QuarkusTransaction.requiringNew().call(() -> repository.countFiltered(customerB, productB)));
        assertTrue(QuarkusTransaction.requiringNew().call(() -> repository.countFiltered(null, null)) >= 3L);
    }

    @Test
    void countByCustomerAndProductCountOnlyReferencingOrders() {
        order(customerA, productA, 1, "2.50");
        order(customerA, productB, 1, "2.50");
        order(customerB, productA, 1, "2.50");

        assertEquals(2L, QuarkusTransaction.requiringNew().call(() -> repository.countByCustomerId(customerA)));
        assertEquals(1L, QuarkusTransaction.requiringNew().call(() -> repository.countByCustomerId(customerB)));
        assertEquals(2L, QuarkusTransaction.requiringNew().call(() -> repository.countByProductId(productA)));
        assertEquals(1L, QuarkusTransaction.requiringNew().call(() -> repository.countByProductId(productB)));
        assertEquals(0L, QuarkusTransaction.requiringNew().call(() -> repository.countByCustomerId(999999999L)));
        assertEquals(0L, QuarkusTransaction.requiringNew().call(() -> repository.countByProductId(999999999L)));
    }

    @Test
    void foreignKeysAndChecksAreEnforced() {
        assertThrows(RuntimeException.class, () -> QuarkusTransaction.requiringNew().run(() -> em
                .createNativeQuery("insert into orders (customer_id, product_id, quantity, total) "
                        + "values (999999999, " + productA + ", 1, 1.00)").executeUpdate()));
        assertThrows(RuntimeException.class, () -> QuarkusTransaction.requiringNew().run(() -> em
                .createNativeQuery("insert into orders (customer_id, product_id, quantity, total) "
                        + "values (" + customerA + ", " + productA + ", 0, 1.00)").executeUpdate()));
    }
}
