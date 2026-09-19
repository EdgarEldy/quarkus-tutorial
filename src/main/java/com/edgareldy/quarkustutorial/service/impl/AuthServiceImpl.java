package com.edgareldy.quarkustutorial.service.impl;

import com.edgareldy.quarkustutorial.dto.auth.AuthResponse;
import com.edgareldy.quarkustutorial.dto.auth.LoginRequest;
import com.edgareldy.quarkustutorial.dto.auth.RegisterRequest;
import com.edgareldy.quarkustutorial.dto.auth.UserResponse;
import com.edgareldy.quarkustutorial.entity.ActivationToken;
import com.edgareldy.quarkustutorial.entity.BlacklistedToken;
import com.edgareldy.quarkustutorial.entity.PasswordResetToken;
import com.edgareldy.quarkustutorial.entity.User;
import com.edgareldy.quarkustutorial.exception.AuthenticationFailedException;
import com.edgareldy.quarkustutorial.exception.BusinessRuleException;
import com.edgareldy.quarkustutorial.exception.ResourceNotFoundException;
import com.edgareldy.quarkustutorial.repository.ActivationTokenRepository;
import com.edgareldy.quarkustutorial.repository.BlacklistedTokenRepository;
import com.edgareldy.quarkustutorial.repository.PasswordResetTokenRepository;
import com.edgareldy.quarkustutorial.repository.UserRepository;
import com.edgareldy.quarkustutorial.security.JwtIssuer;
import com.edgareldy.quarkustutorial.service.AuthService;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.jboss.logging.Logger;

/**
 * Default AuthService: registration, activation, login, logout and password reset.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
@ApplicationScoped
public class AuthServiceImpl implements AuthService {

    private static final Logger LOG = Logger.getLogger(AuthServiceImpl.class);

    private static final Duration ACTIVATION_TTL = Duration.ofHours(24);
    private static final Duration RESET_TTL = Duration.ofMinutes(30);
    private static final String INVALID_CREDENTIALS = "Invalid email or password";
    private static final String INVALID_TOKEN = "The token is invalid, expired or already used";
    private static final SecureRandom RANDOM = new SecureRandom();
    // Computed once: verifying against it burns the same bcrypt time as a real check when the email is
    // unknown, so response timing does not reveal whether an account exists.
    private static final String DUMMY_HASH = BcryptUtil.bcryptHash("timing-equalizer");

    @Inject
    UserRepository userRepository;

    @Inject
    ActivationTokenRepository activationTokenRepository;

    @Inject
    PasswordResetTokenRepository passwordResetTokenRepository;

    @Inject
    BlacklistedTokenRepository blacklistedTokenRepository;

    @Inject
    JwtIssuer jwtIssuer;

    // @Transactional (Jakarta Transactions, on the implementation): the method runs in one database
    // transaction that commits on return and rolls back on a runtime exception, so a user and its
    // activation token are saved together or not at all. Later write methods just repeat it.
    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new BusinessRuleException("An account with this email already exists");
        }
        User user = new User();
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(request.email().toLowerCase());
        // BcryptUtil (quarkus-elytron-security-common) produces a salted, self-describing bcrypt hash.
        user.setPassword(BcryptUtil.bcryptHash(request.password()));
        user.setEnabled(false);
        user.setAccountLocked(false);
        userRepository.persist(user);

        String rawToken = newRawToken();
        ActivationToken token = new ActivationToken();
        token.setUser(user);
        token.setToken(sha256Hex(rawToken));
        token.setCreatedAt(Instant.now());
        token.setExpiresAt(Instant.now().plus(ACTIVATION_TTL));
        activationTokenRepository.persist(token);

        // This tutorial has no mail sender: the raw token is delivered by logging it. A real
        // application would email a link containing it instead. Only the hash is stored.
        LOG.infof("Activation token for %s (valid 24h): %s", user.getEmail(), rawToken);
        return toResponse(user);
    }

    @Override
    @Transactional
    public void activateAccount(String rawToken) {
        ActivationToken token = activationTokenRepository.findByToken(sha256Hex(rawToken))
                .filter(t -> t.getValidatedAt() == null && t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new BusinessRuleException(INVALID_TOKEN));
        token.setValidatedAt(Instant.now());
        token.getUser().setEnabled(true);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email()).orElse(null);
        // Password first, always: unknown email still pays one bcrypt verification against a dummy hash.
        boolean passwordOk;
        if (user != null) {
            passwordOk = BcryptUtil.matches(request.password(), user.getPassword());
        } else {
            BcryptUtil.matches(request.password(), DUMMY_HASH);
            passwordOk = false;
        }
        if (!passwordOk) {
            throw new AuthenticationFailedException(INVALID_CREDENTIALS);
        }
        // Only reached with the correct password, so this does not leak which emails exist.
        if (!user.isEnabled() || user.isAccountLocked()) {
            throw new AuthenticationFailedException("The account is not activated or is locked");
        }
        return jwtIssuer.issue(user);
    }

    @Override
    @Transactional
    public void logout(String jti, Long userId, Instant expiresAt) {
        if (blacklistedTokenRepository.existsByJti(jti)) {
            return;
        }
        User user = userRepository.findById(userId);
        if (user == null) {
            // The token is valid but its account was deleted since: treat it as an invalid credential.
            throw new AuthenticationFailedException("The token no longer matches an account");
        }
        BlacklistedToken entry = new BlacklistedToken();
        entry.setUser(user);
        entry.setToken(jti);
        entry.setJti(jti);
        entry.setBlacklistedAt(Instant.now());
        entry.setCreatedAt(Instant.now());
        entry.setExpiresAt(expiresAt);
        blacklistedTokenRepository.persist(entry);
    }

    @Override
    public UserResponse me(Long userId) {
        User user = userRepository.findById(userId);
        if (user == null) {
            throw new ResourceNotFoundException("User " + userId + " not found");
        }
        return toResponse(user);
    }

    @Override
    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        // The token is generated and hashed whether or not the account exists, so the answer does not
        // depend on it and the cryptographic work is identical. The database writes only happen for a
        // real account, which leaves a small residual timing difference that the specification accepts.
        String rawToken = newRawToken();
        String hash = sha256Hex(rawToken);
        if (user == null) {
            return;
        }
        passwordResetTokenRepository.delete("user", user);
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setToken(hash);
        token.setType(PasswordResetToken.TYPE_PASSWORD_RESET);
        token.setExpiryDate(Instant.now().plus(RESET_TTL));
        passwordResetTokenRepository.persist(token);
        // No mail sender in this tutorial: delivered by log line, as for activation tokens.
        LOG.infof("Password reset token for %s (valid 30 min, single use): %s", user.getEmail(), rawToken);
    }

    @Override
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = passwordResetTokenRepository.findByToken(sha256Hex(rawToken))
                .filter(t -> t.getExpiryDate().isAfter(Instant.now()))
                .orElseThrow(() -> new BusinessRuleException(INVALID_TOKEN));
        token.getUser().setPassword(BcryptUtil.bcryptHash(newPassword));
        // Single use: the token row is deleted once consumed.
        passwordResetTokenRepository.delete(token);
    }

    private static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(), user.isEnabled());
    }

    /** 256 bits from SecureRandom, URL-safe Base64 without padding. */
    private static String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
