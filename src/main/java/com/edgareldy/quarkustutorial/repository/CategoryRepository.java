package com.edgareldy.quarkustutorial.repository;

import com.edgareldy.quarkustutorial.entity.Category;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Panache repository for categories.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
@ApplicationScoped
public class CategoryRepository implements PanacheRepository<Category> {

    /**
     * Counts the products of a category with a native query on the products table.
     * <p>
     * TODO(feature/products): there is no Product entity yet, hence the native SQL. Once it exists,
     * replace this with a ProductRepository count by category and remove this method.
     *
     * @param categoryId the category id
     * @return how many products reference this category
     */
    public long countProducts(Long categoryId) {
        Number count = (Number) getEntityManager()
                .createNativeQuery("select count(*) from products where category_id = :categoryId")
                .setParameter("categoryId", categoryId)
                .getSingleResult();
        return count.longValue();
    }
}
