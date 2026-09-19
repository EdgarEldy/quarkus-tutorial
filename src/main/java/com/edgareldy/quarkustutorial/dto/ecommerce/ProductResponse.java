package com.edgareldy.quarkustutorial.dto.ecommerce;

import java.math.BigDecimal;

/**
 * Product as returned by the API (and the value held in the product cache).
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// Deliberately no categoryName: this DTO is cached by ProductService.findById, and a cached copy
// would keep serving the old name after the category is renamed (only product writes evict it).
public record ProductResponse(Long id, Long categoryId, String productName, BigDecimal unitPrice) {
}
