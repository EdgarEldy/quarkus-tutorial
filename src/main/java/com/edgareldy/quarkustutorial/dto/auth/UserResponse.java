package com.edgareldy.quarkustutorial.dto.auth;

/**
 * Public view of a user account (never exposes the password hash).
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
public record UserResponse(Long id, String firstName, String lastName, String email, boolean enabled) {
}
