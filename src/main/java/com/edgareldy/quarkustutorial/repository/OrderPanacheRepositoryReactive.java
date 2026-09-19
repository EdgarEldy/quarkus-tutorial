package com.edgareldy.quarkustutorial.repository;

import com.edgareldy.quarkustutorial.reactive.ReactiveOrder;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Reactive Panache repository used only by the order streaming endpoint to reload a committed order.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// Same repository pattern as the blocking side, but from the reactive Panache variant: every method
// returns a Mutiny Uni (0 or 1 item, asynchronous) instead of a value, and runs on the Vert.x event
// loop through the "reactive" persistence unit. PanacheRepositoryBase is used (not PanacheRepository)
// because the id type is given explicitly.
@ApplicationScoped
public class OrderPanacheRepositoryReactive implements PanacheRepositoryBase<ReactiveOrder, Long> {
}
