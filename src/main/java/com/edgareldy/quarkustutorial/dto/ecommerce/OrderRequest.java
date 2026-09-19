package com.edgareldy.quarkustutorial.dto.ecommerce;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Payload to place an order (the total is computed by the server, never sent by the client).
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
public record OrderRequest(@NotNull Long customerId, @NotNull Long productId, @NotNull @Min(1) Integer quantity) {
}
