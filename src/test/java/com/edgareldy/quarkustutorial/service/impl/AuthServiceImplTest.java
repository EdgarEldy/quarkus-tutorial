package com.edgareldy.quarkustutorial.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.edgareldy.quarkustutorial.dto.auth.LoginRequest;
import com.edgareldy.quarkustutorial.dto.auth.RegisterRequest;
import com.edgareldy.quarkustutorial.entity.ActivationToken;
import com.edgareldy.quarkustutorial.entity.BlacklistedToken;
import com.edgareldy.quarkustutorial.entity.PasswordResetToken;
import com.edgareldy.quarkustutorial.entity.User;
import com.edgareldy.quarkustutorial.exception.AuthenticationFailedException;
import com.edgareldy.quarkustutorial.exception.BusinessRuleException;
import com.edgareldy.quarkustutorial.repository.ActivationTokenRepository;
import com.edgareldy.quarkustutorial.repository.BlacklistedTokenRepository;
import com.edgareldy.quarkustutorial.repository.PasswordResetTokenRepository;
import com.edgareldy.quarkustutorial.repository.UserRepository;
import com.edgareldy.quarkustutorial.security.JwtIssuer;
import io.quarkus.elytron.security.common.BcryptUtil;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Isolated unit tests of AuthServiceImpl with every collaborator mocked.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// Plain Mockito rather than @QuarkusTest: no container or database is needed to test the service logic,
// so the suite stays fast. The injected fields are package-private and this test lives in the same
// package, so they are assigned directly, without reflection or CDI.
class AuthServiceImplTest {

    private UserRepository users;
    private ActivationTokenRepository activations;
    private PasswordResetTokenRepository resets;
    private BlacklistedTokenRepository blacklist;
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        activations = mock(ActivationTokenRepository.class);
        resets = mock(PasswordResetTokenRepository.class);
        blacklist = mock(BlacklistedTokenRepository.class);
        service = new AuthServiceImpl();
        service.userRepository = users;
        service.activationTokenRepository = activations;
        service.passwordResetTokenRepository = resets;
        service.blacklistedTokenRepository = blacklist;
        service.jwtIssuer = mock(JwtIssuer.class);
    }

    @Test
    void loginUnknownEmailFailsWithSameMessageAsWrongPassword() {
        User user = new User();
        user.setPassword(BcryptUtil.bcryptHash("Correct-horse-1"));
        user.setEnabled(true);
        when(users.findByEmail("known@example.com")).thenReturn(Optional.of(user));
        when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        AuthenticationFailedException wrong = assertThrows(AuthenticationFailedException.class,
                () -> service.login(new LoginRequest("known@example.com", "bad-password")));
        AuthenticationFailedException unknown = assertThrows(AuthenticationFailedException.class,
                () -> service.login(new LoginRequest("ghost@example.com", "bad-password")));

        assertEquals(wrong.getMessage(), unknown.getMessage());
        assertEquals(wrong.getStatusCode(), unknown.getStatusCode());
        assertEquals(401, unknown.getStatusCode());
    }

    @Test
    void registerDuplicateThrowsBusinessRule() {
        when(users.findByEmail("dup@example.com")).thenReturn(Optional.of(new User()));
        assertThrows(BusinessRuleException.class,
                () -> service.register(new RegisterRequest("A", "B", "dup@example.com", "Str0ngPassw0rd!")));
        verify(users, never()).persist(any(User.class));
    }

    @Test
    void activateAccountWithExpiredTokenIsRejected() {
        ActivationToken token = new ActivationToken();
        User user = new User();
        token.setUser(user);
        token.setExpiresAt(Instant.now().minusSeconds(60));
        when(activations.findByToken(any())).thenReturn(Optional.of(token));

        assertThrows(BusinessRuleException.class, () -> service.activateAccount("raw"));
        assertFalse(user.isEnabled());
        assertNull(token.getValidatedAt());
    }

    @Test
    void logoutIsIdempotent() {
        when(blacklist.existsByJti("jti-1")).thenReturn(false, true);
        when(users.findById(7L)).thenReturn(new User());

        service.logout("jti-1", 7L, Instant.now().plusSeconds(600));
        service.logout("jti-1", 7L, Instant.now().plusSeconds(600));

        verify(blacklist, times(1)).persist(any(BlacklistedToken.class));
    }

    @Test
    void resetPasswordWithExpiredTokenIsRejected() {
        PasswordResetToken token = new PasswordResetToken();
        User user = new User();
        user.setPassword("old-hash");
        token.setUser(user);
        token.setExpiryDate(Instant.now().minusSeconds(60));
        when(resets.findByToken(any())).thenReturn(Optional.of(token));

        assertThrows(BusinessRuleException.class, () -> service.resetPassword("raw", "N3wPassw0rd!!"));
        assertEquals("old-hash", user.getPassword());
        verify(resets, never()).delete(any(PasswordResetToken.class));
    }
}
