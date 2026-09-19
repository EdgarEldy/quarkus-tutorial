package com.edgareldy.quarkustutorial.exception;

import io.quarkiverse.httpproblem.HttpProblem;
import java.net.URI;

/**
 * Thrown by services when a requested entity does not exist; rendered as a 404 Problem Details document.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// HttpProblem (quarkus-http-problem) is a RuntimeException that the extension's built-in mapper turns
// into an RFC 9457 application/problem+json response. Extending it directly means services just throw
// it: no custom ExceptionMapper and no ApiResponse error shape are needed.
public class ResourceNotFoundException extends HttpProblem {

    /** Problem type URI identifying this class of error (constant to keep it simple). */
    public static final URI TYPE = URI.create("https://api.example.com/problems/resource-not-found");

    /**
     * @param detail human readable explanation of what was not found
     */
    public ResourceNotFoundException(String detail) {
        super(builder()
                .withType(TYPE)
                .withTitle("Resource Not Found")
                .withStatus(404)
                .withDetail(detail));
    }
}
