package com.edgareldy.quarkustutorial.dto.auth;

/**
 * Login result: the signed JWT, its scheme and its lifetime in seconds.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
public record AuthResponse(String token, String tokenType, long expiresIn) {

    /** Masks the token so it never reaches logs. */
    @Override
    public String toString() {
        return "AuthResponse[token=***, tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
    }
}
