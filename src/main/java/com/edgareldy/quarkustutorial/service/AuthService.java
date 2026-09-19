package com.edgareldy.quarkustutorial.service;

import com.edgareldy.quarkustutorial.dto.auth.AuthResponse;
import com.edgareldy.quarkustutorial.dto.auth.LoginRequest;
import com.edgareldy.quarkustutorial.dto.auth.RegisterRequest;
import com.edgareldy.quarkustutorial.dto.auth.UserResponse;
import java.time.Instant;

/**
 * Contract of the authentication use cases: registration, activation, login, logout and password reset.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
public interface AuthService {

    /** Creates a disabled account and its activation token; fails with 422 if the email is taken. */
    UserResponse register(RegisterRequest request);

    /** Enables the account owning the raw activation token; fails with 422 if invalid, expired or used. */
    void activateAccount(String rawToken);

    /** Verifies credentials and issues a JWT; fails with a generic 401 on any credential problem. */
    AuthResponse login(LoginRequest request);

    /** Blacklists the token identified by jti until its own expiry. */
    void logout(String jti, Long userId, Instant expiresAt);

    /** Returns the profile of the given user id. */
    UserResponse me(Long userId);

    /** Starts a password reset; behaves identically whether or not the email is registered. */
    void forgotPassword(String email);

    /** Consumes a raw reset token (single use) and sets the new password. */
    void resetPassword(String rawToken, String newPassword);
}
