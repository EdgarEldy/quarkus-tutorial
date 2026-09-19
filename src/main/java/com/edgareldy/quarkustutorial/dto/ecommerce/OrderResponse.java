package com.edgareldy.quarkustutorial.dto.ecommerce;

import java.math.BigDecimal;

/**
 * Order as returned by the API (flat: ids only, no customer or product names).
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
public record OrderResponse(Long id, Long customerId, Long productId, Integer quantity, BigDecimal total) {
}
