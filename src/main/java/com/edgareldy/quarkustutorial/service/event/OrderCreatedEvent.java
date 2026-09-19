package com.edgareldy.quarkustutorial.service.event;

/**
 * Plain CDI event fired by the blocking order service once a new order has been persisted.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// Only the id travels: the reactive side reloads the committed row itself, so it never publishes
// state that a rollback could still undo and never shares entity instances across persistence units.
public record OrderCreatedEvent(Long orderId) {
}
