package com.edgareldy.quarkustutorial.resource;

import com.edgareldy.quarkustutorial.dto.auth.AuthResponse;
import com.edgareldy.quarkustutorial.dto.auth.ForgotPasswordRequest;
import com.edgareldy.quarkustutorial.dto.auth.LoginRequest;
import com.edgareldy.quarkustutorial.dto.auth.RegisterRequest;
import com.edgareldy.quarkustutorial.dto.auth.ResetPasswordRequest;
import com.edgareldy.quarkustutorial.dto.auth.UserResponse;
import com.edgareldy.quarkustutorial.dto.common.ApiResponse;
import com.edgareldy.quarkustutorial.service.AuthService;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.jboss.resteasy.reactive.ResponseStatus;
import io.quarkus.security.Authenticated;

/**
 * REST endpoints for registration, activation, login, logout, profile and password reset.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// Thin resource: Bean Validation on the body, delegate to AuthService, wrap in ApiResponse.
// @PermitAll / @Authenticated are declarative security annotations enforced by Quarkus before the
// method runs: public endpoints are opened explicitly, protected ones require a valid JWT.
@Path("/api/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

    // The validated JWT of the current request; the proxy is request scoped so a singleton can hold it.
    @Inject
    JsonWebToken jwt;

    @POST
    @Path("/register")
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    @ResponseStatus(201)
    public ApiResponse<UserResponse> register(@Valid RegisterRequest request) {
        return ApiResponse.success(authService.register(request), "Account created, activation required");
    }

    @GET
    @Path("/activate-account")
    @PermitAll
    public ApiResponse<Void> activateAccount(@QueryParam("token") @NotBlank String token) {
        authService.activateAccount(token);
        return ApiResponse.success(null, "Account activated");
    }

    @POST
    @Path("/login")
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    public ApiResponse<AuthResponse> login(@Valid LoginRequest request) {
        return ApiResponse.success(authService.login(request), "Login successful");
    }

    @POST
    @Path("/logout")
    @Authenticated
    @SecurityRequirement(name = "jwt")
    public ApiResponse<Void> logout() {
        authService.logout(jwt.getTokenID(), Long.valueOf(jwt.getSubject()),
                Instant.ofEpochSecond(jwt.getExpirationTime()));
        return ApiResponse.success(null, "Logged out");
    }

    @GET
    @Path("/me")
    @Authenticated
    @SecurityRequirement(name = "jwt")
    public ApiResponse<UserResponse> me() {
        return ApiResponse.success(authService.me(Long.valueOf(jwt.getSubject())), "Current user");
    }

    @POST
    @Path("/forgot-password")
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    public ApiResponse<Void> forgotPassword(@Valid ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        // Identical answer whether or not the email is registered.
        return ApiResponse.success(null, "If the email is registered, a reset token has been issued");
    }

    @POST
    @Path("/reset-password")
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    public ApiResponse<Void> resetPassword(@Valid ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ApiResponse.success(null, "Password updated");
    }
}
