package com.edgareldy.quarkustutorial.exception;

import io.quarkiverse.httpproblem.HttpProblem;
import java.net.URI;

/**
 * Thrown by services when a business rule is violated; rendered as a 422 Problem Details document.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// Same HttpProblem mechanism as ResourceNotFoundException; 422 signals a well-formed request refused by a rule.
public class BusinessRuleException extends HttpProblem {

    /** Problem type URI identifying this class of error. */
    public static final URI TYPE = URI.create("https://api.example.com/problems/business-rule-violation");

    /**
     * @param detail human readable explanation of the violated rule
     */
    public BusinessRuleException(String detail) {
        super(builder()
                .withType(TYPE)
                .withTitle("Business Rule Violation")
                .withStatus(422)
                .withDetail(detail));
    }
}
