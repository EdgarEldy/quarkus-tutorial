package com.edgareldy.quarkustutorial.service;

import com.edgareldy.quarkustutorial.dto.common.PageResponse;
import com.edgareldy.quarkustutorial.dto.ecommerce.ProductRequest;
import com.edgareldy.quarkustutorial.dto.ecommerce.ProductResponse;

/**
 * Contract for product management.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
public interface ProductService {

    /** @return one page of products ordered by id, optionally filtered by category (null means all) */
    PageResponse<ProductResponse> list(int page, int size, Long categoryId);

    /** @return the product, or throws ResourceNotFoundException (404). Cached, see the implementation. */
    ProductResponse findById(Long id);

    /** @return the created product; BusinessRuleException (422) if the category does not exist */
    ProductResponse create(ProductRequest request);

    /** @return the updated product; 404 if missing, BusinessRuleException (422) if the category does not exist */
    ProductResponse update(Long id, ProductRequest request);

    /** Deletes a product; 404 if missing. */
    void delete(Long id);
}
