package com.edgareldy.quarkustutorial.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload of the registration endpoint.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
public record RegisterRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String password
) {

    /** Masks the password so it never reaches logs. */
    @Override
    public String toString() {
        return "RegisterRequest[firstName=" + firstName + ", lastName=" + lastName + ", email=" + email + ", password=***]";
    }
}
