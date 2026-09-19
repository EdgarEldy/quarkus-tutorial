package com.edgareldy.quarkustutorial.dto.ecommerce;

/**
 * Customer as returned by the API.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
public record CustomerResponse(Long id, String firstName, String lastName, String telephone, String email,
        String address) {
}
