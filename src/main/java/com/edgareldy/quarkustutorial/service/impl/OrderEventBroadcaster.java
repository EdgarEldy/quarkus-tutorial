package com.edgareldy.quarkustutorial.service.impl;

import com.edgareldy.quarkustutorial.dto.ecommerce.OrderResponse;
import com.edgareldy.quarkustutorial.repository.OrderPanacheRepositoryReactive;
import com.edgareldy.quarkustutorial.service.event.OrderCreatedEvent;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import io.smallrye.mutiny.operators.multi.processors.SerializedProcessor;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

/**
 * Bridges the blocking order creation to the reactive world: turns committed-order CDI events into a
 * hot {@code Multi} of {@link OrderResponse} that any number of SSE clients can subscribe to.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
@ApplicationScoped
public class OrderEventBroadcaster {

    // Mutiny is Quarkus's reactive library: a Uni is an asynchronous result of 0 or 1 item, a Multi is
    // an asynchronous stream of 0..n items. A BroadcastProcessor is both a Multi and a sink: items
    // pushed with onNext are delivered to every current subscriber (a "hot" stream, nothing is
    // replayed to late subscribers). serialized() makes concurrent onNext calls from different
    // request threads safe.
    private final SerializedProcessor<Long, Long> processor = BroadcastProcessor.<Long>create().serialized();

    @Inject
    OrderPanacheRepositoryReactive orderRepository;

    /**
     * Receives the event only after the creating transaction committed successfully.
     *
     * @param event the order created event
     */
    // TransactionPhase.AFTER_SUCCESS defers this observer until the transaction that fired the event
    // commits, and skips it entirely on rollback: an order that was rolled back is never broadcast.
    void onOrderCreated(@Observes(during = TransactionPhase.AFTER_SUCCESS) OrderCreatedEvent event) {
        processor.onNext(event.orderId());
    }

    /**
     * @return a stream of the orders created from now on, one independent subscription per caller
     */
    public Multi<OrderResponse> stream() {
        // Hibernate Reactive needs a Vert.x context. The event is published from a blocking worker
        // thread, so each subscription remembers the context it was opened on (the event loop of the
        // SSE request) and hops back onto it before touching the database.
        Context context = Vertx.currentContext();
        Multi<Long> ids = Multi.createFrom().publisher(processor)
                // Per-subscriber buffer: a slow client fills its own buffer and is failed alone,
                // order creation (the publisher) never waits for anyone.
                .onOverflow().buffer(64);
        if (context != null) {
            ids = ids.emitOn(command -> context.runOnContext(ignored -> command.run()));
        }
        return ids.onItem().transformToUniAndConcatenate(this::load);
    }

    private Uni<OrderResponse> load(Long id) {
        // Opens a reactive session on the named "reactive" persistence unit for the duration of the
        // lookup (the no-argument variant would look for the default unit, which is blocking).
        return Panache.withSession("reactive", () -> orderRepository.findById(id))
                .map(order -> new OrderResponse(order.getId(), order.getCustomerId(), order.getProductId(),
                        order.getQuantity(), order.getTotal()));
    }
}
