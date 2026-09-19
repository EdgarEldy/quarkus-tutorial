package com.edgareldy.quarkustutorial.service;

import com.edgareldy.quarkustutorial.dto.common.PageResponse;
import com.edgareldy.quarkustutorial.dto.ecommerce.OrderRequest;
import com.edgareldy.quarkustutorial.dto.ecommerce.OrderResponse;

/**
 * Contract for order management (read and create only: an order is never edited or deleted).
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
public interface OrderService {

    /** @return one page of orders ordered by id, optionally filtered by customer and/or product (null means any) */
    PageResponse<OrderResponse> list(int page, int size, Long customerId, Long productId);

    /** @return the order, or throws ResourceNotFoundException (404) */
    OrderResponse findById(Long id);

    /** @return the created order with its computed total; BusinessRuleException (422) if the customer or product does not exist */
    OrderResponse create(OrderRequest request);
}
