package com.edgareldy.quarkustutorial.reactive;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Read-only mapping of the orders table for the reactive persistence unit, with plain id columns.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// Hibernate ORM (blocking) and Hibernate Reactive cannot share one persistence unit: a unit is either
// blocking (JDBC) or reactive (Vert.x client). This entity therefore lives in its own package, bound to
// the "reactive" unit only, and maps customer_id/product_id as plain columns so that Customer and
// Product (which belong to the blocking unit) never have to be dragged into the reactive one.
// It is never written: Flyway owns the schema and the blocking OrderServiceImpl owns the writes.
@Entity(name = "ReactiveOrder")
@Table(name = "orders")
public class ReactiveOrder {

    @Id
    private Long id;

    @Column(name = "customer_id", nullable = false, insertable = false, updatable = false)
    private Long customerId;

    @Column(name = "product_id", nullable = false, insertable = false, updatable = false)
    private Long productId;

    @Column(nullable = false, insertable = false, updatable = false)
    private Integer quantity;

    @Column(nullable = false, insertable = false, updatable = false)
    private BigDecimal total;

    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public Long getProductId() { return productId; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getTotal() { return total; }
}
