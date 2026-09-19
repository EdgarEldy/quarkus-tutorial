package com.edgareldy.quarkustutorial.repository;

import com.edgareldy.quarkustutorial.entity.Order;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Panache repository for orders, with optional customer and product filters and counts.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
@ApplicationScoped
public class OrderRepository implements PanacheRepository<Order> {

    /**
     * Orders ordered by id, optionally restricted by customer and/or product (filters combine with AND).
     *
     * @param customerId the customer to filter on, or null
     * @param productId  the product to filter on, or null
     * @return a query the caller can page
     */
    public PanacheQuery<Order> search(Long customerId, Long productId) {
        if (customerId == null && productId == null) {
            return findAll(Sort.by("id"));
        }
        if (productId == null) {
            return find("customer.id = ?1", Sort.by("id"), customerId);
        }
        if (customerId == null) {
            return find("product.id = ?1", Sort.by("id"), productId);
        }
        return find("customer.id = ?1 and product.id = ?2", Sort.by("id"), customerId, productId);
    }

    /**
     * @param customerId the customer to filter on, or null
     * @param productId  the product to filter on, or null
     * @return how many orders match the same filters as {@link #search}
     */
    public long countFiltered(Long customerId, Long productId) {
        if (customerId == null && productId == null) {
            return count();
        }
        if (productId == null) {
            return countByCustomerId(customerId);
        }
        if (customerId == null) {
            return countByProductId(productId);
        }
        return count("customer.id = ?1 and product.id = ?2", customerId, productId);
    }

    /**
     * @param customerId the customer id
     * @return how many orders reference this customer
     */
    public long countByCustomerId(Long customerId) {
        return count("customer.id", customerId);
    }

    /**
     * @param productId the product id
     * @return how many orders reference this product
     */
    public long countByProductId(Long productId) {
        return count("product.id", productId);
    }
}
