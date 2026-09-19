package com.edgareldy.quarkustutorial.rbac;

import static io.restassured.RestAssured.given;

import com.edgareldy.quarkustutorial.entity.Permission;
import com.edgareldy.quarkustutorial.entity.Role;
import com.edgareldy.quarkustutorial.entity.User;
import com.edgareldy.quarkustutorial.repository.AuditLogRepository;
import com.edgareldy.quarkustutorial.repository.PermissionRepository;
import com.edgareldy.quarkustutorial.repository.RoleRepository;
import com.edgareldy.quarkustutorial.repository.UserRepository;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared fixture for the RBAC tests: creates users, roles and permissions directly in the database,
 * logs users in over HTTP, reads audit rows and normalises the global ROLE:WRITE holder state.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// A CDI bean living in src/test: @QuarkusTest indexes test classes too, so test classes can @Inject it. Data
// is written through the real repositories inside QuarkusTransaction.requiringNew(), because a test method
// runs on the JUnit thread with no ambient transaction. Every @QuarkusTest class shares one Dev Services
// database, so everything created here is tracked and removed by cleanup() (foreign keys cascade).
@ApplicationScoped
public class RbacTestSupport {

    /** Clear-text password of every user created by this helper. */
    public static final String PASSWORD = "Str0ngPassw0rd!";

    private static final String PASSWORD_HASH = BcryptUtil.bcryptHash(PASSWORD);

    /**
     * A user created by the helper.
     *
     * <p>
     * Created edgar.muhamyangabo on 9/19/26
     * Author : edgar.muhamyangabo
     * Date : 9/19/26
     * Project : quarkus-tutorial
     *
     * @param id    the user id (also the JWT subject)
     * @param email the login email
     */
    public record TestUser(Long id, String email) {
    }

    /**
     * Scalar snapshot of an audit log row.
     *
     * <p>
     * Created edgar.muhamyangabo on 9/19/26
     * Author : edgar.muhamyangabo
     * Date : 9/19/26
     * Project : quarkus-tutorial
     *
     * @param actorUserId the acting user id
     * @param action      the action code
     * @param entityType  the entity type
     * @param entityId    the entity id
     * @param details     the free-text details
     */
    public record AuditRow(Long actorUserId, String action, String entityType, Long entityId, String details) {
    }

    /**
     * A removed (user, role) link, kept so it can be restored.
     *
     * <p>
     * Created edgar.muhamyangabo on 9/19/26
     * Author : edgar.muhamyangabo
     * Date : 9/19/26
     * Project : quarkus-tutorial
     *
     * @param userId the user id
     * @param roleId the role id
     */
    public record Link(Long userId, Long roleId) {
    }

    @Inject
    EntityManager em;

    @Inject
    UserRepository userRepository;

    @Inject
    RoleRepository roleRepository;

    @Inject
    PermissionRepository permissionRepository;

    @Inject
    AuditLogRepository auditLogRepository;

    private final List<Long> users = new CopyOnWriteArrayList<>();
    private final List<Long> roles = new CopyOnWriteArrayList<>();
    private final List<Long> permissions = new CopyOnWriteArrayList<>();

    /**
     * @param prefix a readable prefix
     * @return a unique value built from the prefix
     */
    public static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Creates an enabled user holding the named roles.
     *
     * @param roleNames existing role names to assign
     * @return the created user
     */
    public TestUser createUser(String... roleNames) {
        String email = unique("rbac") + "@test.local";
        Long id = QuarkusTransaction.requiringNew().call(() -> {
            User user = new User();
            user.setFirstName("Rbac");
            user.setLastName("Tester");
            user.setEmail(email);
            user.setPassword(PASSWORD_HASH);
            user.setEnabled(true);
            user.setAccountLocked(false);
            for (String name : roleNames) {
                user.getRoles().add(roleRepository.findByRoleName(name).orElseThrow());
            }
            userRepository.persist(user);
            return user.getId();
        });
        users.add(id);
        return new TestUser(id, email);
    }

    /**
     * Creates a tracked role granting the given permissions.
     *
     * @param name        the role name (made unique by the caller if needed)
     * @param permissions permissions written as RESOURCE:ACTION
     * @return the role id
     */
    public Long createRole(String name, String... permissions) {
        Long id = QuarkusTransaction.requiringNew().call(() -> {
            Role role = new Role();
            role.setRoleName(name);
            for (String p : permissions) {
                String[] parts = p.split(":");
                role.getPermissions().add(permissionRepository.findByResourceAndAction(parts[0], parts[1]).orElseThrow());
            }
            roleRepository.persist(role);
            return role.getId();
        });
        roles.add(id);
        return id;
    }

    /**
     * Creates a tracked permission.
     *
     * @param resource the resource
     * @param action   the action
     * @return the permission id
     */
    public Long createPermission(String resource, String action) {
        Long id = QuarkusTransaction.requiringNew().call(() -> {
            Permission permission = new Permission();
            permission.setResource(resource);
            permission.setAction(action);
            permissionRepository.persist(permission);
            return permission.getId();
        });
        permissions.add(id);
        return id;
    }

    /**
     * Gives a user a role directly in the database.
     *
     * @param userId the user id
     * @param roleId the role id
     */
    public void grantRole(Long userId, Long roleId) {
        QuarkusTransaction.requiringNew().run(() -> userRepository.findById(userId).getRoles()
                .add(roleRepository.findById(roleId)));
    }

    /**
     * Makes sure a role created through the API is deleted by cleanup().
     *
     * @param roleId the role id
     */
    public void trackRole(Long roleId) {
        roles.add(roleId);
    }

    /**
     * Makes sure a permission created through the API is deleted by cleanup().
     *
     * @param permissionId the permission id
     */
    public void trackPermission(Long permissionId) {
        permissions.add(permissionId);
    }

    /**
     * @param name the role name
     * @return the id of the seeded or created role
     */
    public Long roleId(String name) {
        return QuarkusTransaction.requiringNew().call(() -> roleRepository.findByRoleName(name).orElseThrow().getId());
    }

    /**
     * @param resource the resource
     * @param action   the action
     * @return the id of the seeded or created permission
     */
    public Long permissionId(String resource, String action) {
        return QuarkusTransaction.requiringNew()
                .call(() -> permissionRepository.findByResourceAndAction(resource, action).orElseThrow().getId());
    }

    /**
     * Logs the user in through the real endpoint.
     *
     * @param user the user
     * @return the JWT
     */
    public String token(TestUser user) {
        return given().contentType("application/json")
                .body("{\"email\":\"" + user.email() + "\",\"password\":\"" + PASSWORD + "\"}")
                .when().post("/api/v1/auth/login")
                .then().statusCode(200).extract().path("data.token");
    }

    /**
     * @param action     the action code
     * @param entityType the entity type
     * @param entityId   the entity id
     * @return the matching audit rows, oldest first
     */
    public List<AuditRow> audit(String action, String entityType, Long entityId) {
        return QuarkusTransaction.requiringNew().call(() -> auditLogRepository
                .list("action = ?1 and entityType = ?2 and entityId = ?3 order by id", action, entityType, entityId)
                .stream()
                .map(a -> new AuditRow(a.getActorUserId(), a.getAction(), a.getEntityType(), a.getEntityId(),
                        a.getDetails()))
                .toList());
    }

    /**
     * Removes every link between a user other than {@code keepUserId} and a role granting ROLE:WRITE, so
     * that user becomes the only possible holder. Undo with {@link #restoreLinks(List)}.
     *
     * @param keepUserId the user to leave alone
     * @return the removed links
     */
    @SuppressWarnings("unchecked")
    public List<Link> isolateRoleWriteHolders(Long keepUserId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            List<Object[]> rows = em.createNativeQuery("select ru.user_id, ru.role_id from role_user ru "
                    + "where ru.user_id <> ?1 and ru.role_id in (select rp.role_id from role_permission rp "
                    + "join permissions p on p.id = rp.permission_id where p.resource = 'ROLE' and p.action = 'WRITE')")
                    .setParameter(1, keepUserId).getResultList();
            List<Link> removed = new ArrayList<>();
            for (Object[] row : rows) {
                Long userId = ((Number) row[0]).longValue();
                Long roleId = ((Number) row[1]).longValue();
                em.createNativeQuery("delete from role_user where user_id = ?1 and role_id = ?2")
                        .setParameter(1, userId).setParameter(2, roleId).executeUpdate();
                removed.add(new Link(userId, roleId));
            }
            return removed;
        });
    }

    /**
     * Puts back links removed by {@link #isolateRoleWriteHolders(Long)}, skipping rows whose user or role no
     * longer exists.
     *
     * @param links the links to restore
     */
    public void restoreLinks(List<Link> links) {
        QuarkusTransaction.requiringNew().run(() -> links.forEach(l -> em.createNativeQuery(
                "insert into role_user (user_id, role_id) select ?1, ?2 where exists "
                        + "(select 1 from users where id = ?1) and exists (select 1 from roles where id = ?2) "
                        + "on conflict do nothing").setParameter(1, l.userId()).setParameter(2, l.roleId())
                .executeUpdate()));
    }

    /**
     * Deletes every tracked user, role and permission (join rows cascade).
     */
    public void cleanup() {
        QuarkusTransaction.requiringNew().run(() -> {
            delete("users", users);
            delete("roles", roles);
            delete("permissions", permissions);
        });
        users.clear();
        roles.clear();
        permissions.clear();
    }

    private void delete(String table, List<Long> ids) {
        ids.forEach(id -> em.createNativeQuery("delete from " + table + " where id = ?1").setParameter(1, id)
                .executeUpdate());
    }
}
