package com.edgareldy.quarkustutorial.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edgareldy.quarkustutorial.dto.ecommerce.OrderRequest;
import com.edgareldy.quarkustutorial.dto.ecommerce.OrderResponse;
import com.edgareldy.quarkustutorial.entity.Category;
import com.edgareldy.quarkustutorial.entity.Customer;
import com.edgareldy.quarkustutorial.entity.Product;
import com.edgareldy.quarkustutorial.exception.BusinessRuleException;
import com.edgareldy.quarkustutorial.repository.CategoryRepository;
import com.edgareldy.quarkustutorial.repository.CustomerRepository;
import com.edgareldy.quarkustutorial.repository.ProductRepository;
import com.edgareldy.quarkustutorial.service.OrderService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle;
import io.smallrye.common.vertx.VertxContext;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the CDI to Mutiny bridge: committed orders reach every current subscriber, rolled back ones
 * reach nobody, and late subscribers get no replay (hot stream).
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// @QuarkusTest boots the real application (Dev Services PostgreSQL, blocking and reactive units). The
// AssertSubscriber from Mutiny's test helpers records the events of a Multi without blocking the test
// thread: awaitItems(n, timeout) polls with a deadline, so a missing event fails fast instead of hanging.
@QuarkusTest
class OrderEventBroadcasterTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Inject
    OrderEventBroadcaster broadcaster;

    @Inject
    OrderService orderService;

    @Inject
    CategoryRepository categoryRepository;

    @Inject
    CustomerRepository customerRepository;

    @Inject
    ProductRepository productRepository;

    @Inject
    EntityManager em;

    // The test thread has no Vert.x context, and stream() captures Vertx.currentContext() to hop back
    // onto it before the reactive DB lookup. Hibernate Reactive also insists on a duplicated context (the per-request child context Quarkus REST
    // creates), a plain root context fails with "not a duplicated context", and it must be flagged safe
    // (isolated, one request only) or Panache refuses it. Subscribing from inside one reproduces what
    // the SSE endpoint does on its event loop.
    @Inject
    Vertx vertx;

    private Long categoryId;
    private Long customerId;
    private Long productId;

    @BeforeEach
    void setUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            Category cat = new Category();
            cat.setCategoryName("stream-" + System.nanoTime());
            categoryRepository.persist(cat);
            categoryId = cat.getId();
            Customer c = new Customer();
            c.setFirstName("Ada");
            c.setLastName("Lovelace");
            customerRepository.persist(c);
            customerId = c.getId();
            Product p = new Product();
            p.setCategory(cat);
            p.setProductName("P" + System.nanoTime());
            p.setUnitPrice(new BigDecimal("2.50"));
            productRepository.persist(p);
            productId = p.getId();
        });
    }

    @AfterEach
    void tearDown() {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("delete from orders where customer_id = ?1").setParameter(1, customerId)
                    .executeUpdate();
            em.createNativeQuery("delete from customers where id = ?1").setParameter(1, customerId)
                    .executeUpdate();
            em.createNativeQuery("delete from products where id = ?1").setParameter(1, productId)
                    .executeUpdate();
            em.createNativeQuery("delete from categories where id = ?1").setParameter(1, categoryId)
                    .executeUpdate();
        });
    }

    private AssertSubscriber<OrderResponse> subscribe() {
        AssertSubscriber<OrderResponse> subscriber = AssertSubscriber.create(Long.MAX_VALUE);
        Context context = VertxContext.getOrCreateDuplicatedContext(vertx);
        VertxContextSafetyToggle.setContextSafe(context, true);
        context.runOnContext(ignored -> broadcaster.stream().subscribe().withSubscriber(subscriber));
        // Give the subscription time to reach the BroadcastProcessor before the order is created
        // (a hot stream drops anything emitted before the subscriber is attached).
        sleep(300);
        return subscriber;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private OrderResponse create() {
        return orderService.create(new OrderRequest(customerId, productId, 3));
    }

    private void assertMatches(OrderResponse expected, OrderResponse received) {
        assertEquals(expected.id(), received.id());
        assertEquals(customerId, received.customerId());
        assertEquals(productId, received.productId());
        assertEquals(3, received.quantity());
        assertEquals(0, new BigDecimal("7.50").compareTo(received.total()));
    }

    @Test
    void committedOrderIsEmittedToTheSubscriber() {
        AssertSubscriber<OrderResponse> subscriber = subscribe();
        OrderResponse created = create();

        subscriber.awaitItems(1, TIMEOUT);
        assertEquals(1, subscriber.getItems().size());
        assertMatches(created, subscriber.getItems().get(0));
        subscriber.cancel();
    }

    @Test
    void rolledBackCreationEmitsNothing() {
        AssertSubscriber<OrderResponse> subscriber = subscribe();

        assertThrows(BusinessRuleException.class,
                () -> orderService.create(new OrderRequest(customerId, 999_999_999L, 1)));

        sleep(1000);
        assertTrue(subscriber.getItems().isEmpty());
        subscriber.cancel();
    }

    @Test
    void everyCurrentSubscriberReceivesTheEvent() {
        AssertSubscriber<OrderResponse> first = subscribe();
        AssertSubscriber<OrderResponse> second = subscribe();
        OrderResponse created = create();

        first.awaitItems(1, TIMEOUT);
        second.awaitItems(1, TIMEOUT);
        assertMatches(created, first.getItems().get(0));
        assertMatches(created, second.getItems().get(0));
        first.cancel();
        second.cancel();
    }

    @Test
    void lateSubscriberDoesNotReceiveAnAlreadyCreatedOrder() {
        AssertSubscriber<OrderResponse> early = subscribe();
        create();
        early.awaitItems(1, TIMEOUT);

        AssertSubscriber<OrderResponse> late = subscribe();
        sleep(1000);
        assertTrue(late.getItems().isEmpty());

        // The late subscriber is live, though: it gets the next order.
        OrderResponse next = create();
        late.awaitItems(1, TIMEOUT);
        assertEquals(1, late.getItems().size());
        assertEquals(next.id(), late.getItems().get(0).id());
        early.cancel();
        late.cancel();
    }
}
