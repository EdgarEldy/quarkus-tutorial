package com.edgareldy.quarkustutorial.security;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edgareldy.quarkustutorial.rbac.RbacTestSupport;
import com.edgareldy.quarkustutorial.rbac.RbacTestSupport.TestUser;
import io.quarkus.security.StringPermission;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests that the augmentor resolves permissions from all of a user's roles and leaves anonymous identities alone.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// The augmentor is a real CDI bean backed by the Dev Services database, so it is injected and called directly
// with hand-built identities; a stub AuthenticationRequestContext runs the blocking supplier on the caller
// thread, which is fine here because the test thread is not an event loop.
@QuarkusTest
class PermissionSecurityIdentityAugmentorTest {

    @Inject
    PermissionSecurityIdentityAugmentor augmentor;

    @Inject
    RbacTestSupport support;

    private static final AuthenticationRequestContext DIRECT = new AuthenticationRequestContext() {
        @Override
        public Uni<SecurityIdentity> runBlocking(Supplier<SecurityIdentity> function) {
            return Uni.createFrom().item(function);
        }
    };

    @AfterEach
    void tearDown() {
        support.cleanup();
    }

    private SecurityIdentity augmentFor(Long userId) {
        SecurityIdentity input = QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal(String.valueOf(userId))).build();
        return augmentor.augment(input, DIRECT).await().indefinitely();
    }

    private static boolean has(SecurityIdentity identity, String resource, String action) {
        return identity.checkPermission(new StringPermission(resource, action)).await().indefinitely();
    }

    @Test
    void multiRoleUserGetsTheUnionOfPermissions() {
        support.createRole("aug-a", "USER:READ", "CATEGORY:READ");
        support.createRole("aug-b", "CATEGORY:READ", "ORDER:WRITE");
        TestUser user = support.createUser("aug-a", "aug-b");

        SecurityIdentity identity = augmentFor(user.id());
        assertTrue(has(identity, "USER", "READ"));
        assertTrue(has(identity, "CATEGORY", "READ"));
        assertTrue(has(identity, "ORDER", "WRITE"));
        assertFalse(has(identity, "ROLE", "WRITE"));
    }

    @Test
    void userWithoutRolesGetsNoPermission() {
        TestUser user = support.createUser();
        SecurityIdentity identity = augmentFor(user.id());
        assertFalse(has(identity, "USER", "READ"));
        assertFalse(has(identity, "ROLE", "WRITE"));
    }

    @Test
    void anonymousIdentityIsReturnedUnchanged() {
        SecurityIdentity anonymous = QuarkusSecurityIdentity.builder().setAnonymous(true).build();
        assertSame(anonymous, augmentor.augment(anonymous, DIRECT).await().indefinitely());
    }

    @Test
    void permissionsAllowedEndpointFollowsTheAugmentedPermissions() {
        support.createRole("aug-c", "PERMISSION:READ");
        String token = support.token(support.createUser("aug-c"));
        // PERMISSION:READ was resolved by the augmentor at request time: accepted here, rejected for USER:READ.
        given().auth().oauth2(token).when().get("/api/v1/permissions").then().statusCode(200);
        assertEquals(403, given().auth().oauth2(token).when().get("/api/v1/users").statusCode());
    }
}
