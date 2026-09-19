package com.edgareldy.quarkustutorial.dto.ecommerce;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload to create or update a category.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
public record CategoryRequest(@NotBlank @Size(max = 150) String categoryName) {
}
