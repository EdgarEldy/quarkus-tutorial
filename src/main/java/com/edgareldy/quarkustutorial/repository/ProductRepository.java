package com.edgareldy.quarkustutorial.repository;

import com.edgareldy.quarkustutorial.entity.Product;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Panache repository for products, with the category filter and count.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
@ApplicationScoped
public class ProductRepository implements PanacheRepository<Product> {

    /**
     * Products ordered by id, optionally restricted to one category.
     *
     * @param categoryId the category to filter on, or null for every product
     * @return a query the caller can page
     */
    public PanacheQuery<Product> findByCategory(Long categoryId) {
        if (categoryId == null) {
            return findAll(Sort.by("id"));
        }
        // Panache shortcut: a bare "category.id = ?1" is expanded into a full HQL query.
        return find("category.id = ?1", Sort.by("id"), categoryId);
    }

    /**
     * @param categoryId the category id
     * @return how many products reference this category
     */
    public long countByCategoryId(Long categoryId) {
        return count("category.id", categoryId);
    }
}
