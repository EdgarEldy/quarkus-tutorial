package com.edgareldy.quarkustutorial.exception;

import io.quarkiverse.httpproblem.HttpProblem;
import java.net.URI;

/**
 * Thrown when authentication fails; rendered as a 401 Problem Details document.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// One generic message for unknown email and wrong password, so the response cannot be used to tell
// which emails are registered.
public class AuthenticationFailedException extends HttpProblem {

    /** Problem type URI identifying this class of error. */
    public static final URI TYPE = URI.create("https://api.example.com/problems/authentication-failed");

    /**
     * @param detail human readable explanation, identical for every credential failure
     */
    public AuthenticationFailedException(String detail) {
        super(builder()
                .withType(TYPE)
                .withTitle("Authentication Failed")
                .withStatus(401)
                .withDetail(detail));
    }
}
