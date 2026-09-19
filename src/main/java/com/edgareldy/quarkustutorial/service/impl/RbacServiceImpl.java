package com.edgareldy.quarkustutorial.service.impl;

import com.edgareldy.quarkustutorial.dto.common.PageResponse;
import com.edgareldy.quarkustutorial.dto.rbac.PermissionRequest;
import com.edgareldy.quarkustutorial.dto.rbac.PermissionResponse;
import com.edgareldy.quarkustutorial.dto.rbac.RoleRequest;
import com.edgareldy.quarkustutorial.dto.rbac.RoleResponse;
import com.edgareldy.quarkustutorial.dto.rbac.UserDetailResponse;
import com.edgareldy.quarkustutorial.entity.Permission;
import com.edgareldy.quarkustutorial.entity.Role;
import com.edgareldy.quarkustutorial.entity.User;
import com.edgareldy.quarkustutorial.exception.BusinessRuleException;
import com.edgareldy.quarkustutorial.exception.ResourceNotFoundException;
import com.edgareldy.quarkustutorial.repository.PermissionRepository;
import com.edgareldy.quarkustutorial.repository.RoleRepository;
import com.edgareldy.quarkustutorial.repository.UserRepository;
import com.edgareldy.quarkustutorial.security.AuditLogger;
import com.edgareldy.quarkustutorial.service.RbacService;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.Comparator;
import java.util.List;

/**
 * Default RbacService: role, permission and assignment management with audit and last-admin protection.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
@ApplicationScoped
public class RbacServiceImpl implements RbacService {

    /** The permission whose last holder must never be removed (anti-lockout). */
    private static final String ADMIN_RESOURCE = "ROLE";
    private static final String ADMIN_ACTION = "WRITE";
    private static final String LAST_ADMIN = "This would leave no user holding ROLE:WRITE";

    @Inject
    UserRepository userRepository;

    @Inject
    RoleRepository roleRepository;

    @Inject
    PermissionRepository permissionRepository;

    @Inject
    AuditLogger auditLogger;

    @Inject
    EntityManager entityManager;

    // Arbitrary constant identifying the "last administrator" rule to PostgreSQL advisory locks.
    private static final long LAST_ADMIN_LOCK_KEY = 7_301_001L;

    // ---- users -------------------------------------------------------------------------------

    @Override
    @Transactional
    public PageResponse<UserDetailResponse> listUsers(int page, int size) {
        // Query 1 pages plain users (SQL limit/offset); query 2 fetch-joins their roles. Fetching
        // the collection in the paged query itself would force Hibernate to page in memory.
        List<Long> ids = userRepository.findAll(Sort.by("id")).page(Page.of(page, size)).list()
                .stream().map(User::getId).toList();
        List<UserDetailResponse> content = userRepository.findWithRolesByIds(ids).stream()
                .map(RbacServiceImpl::toResponse).toList();
        return PageResponse.of(content, page, size, userRepository.count());
    }

    @Override
    @Transactional
    public UserDetailResponse getUser(Long id) {
        return toResponse(findUser(id));
    }

    @Override
    @Transactional
    public UserDetailResponse assignRoleToUser(Long userId, Long roleId) {
        User user = findUser(userId);
        Role role = findRole(roleId);
        if (user.getRoles().add(role)) {
            auditLogger.log("ROLE_ASSIGNED_TO_USER", "USER", userId, "role " + role.getRoleName() + " (" + roleId + ")");
        }
        return toResponse(user);
    }

    @Override
    @Transactional
    public UserDetailResponse removeRoleFromUser(Long userId, Long roleId) {
        lockLastAdminRule();
        User user = findUser(userId);
        Role role = findRole(roleId);
        if (!user.getRoles().contains(role)) {
            return toResponse(user);
        }
        if (grants(role, ADMIN_RESOURCE, ADMIN_ACTION)
                && userRepository.countUserRolesGrantingExcluding(userId, ADMIN_RESOURCE, ADMIN_ACTION, roleId) == 0
                && userRepository.countHoldersExcludingUser(ADMIN_RESOURCE, ADMIN_ACTION, userId) == 0) {
            // Logged before throwing: the audit insert commits on its own (REQUIRES_NEW), so it
            // survives the rollback this exception triggers.
            auditLogger.logRejected("ROLE_UNASSIGN_REJECTED", "USER", userId,
                    "role " + role.getRoleName() + " (" + roleId + "): " + LAST_ADMIN);
            throw new BusinessRuleException(LAST_ADMIN);
        }
        user.getRoles().remove(role);
        auditLogger.log("ROLE_REMOVED_FROM_USER", "USER", userId, "role " + role.getRoleName() + " (" + roleId + ")");
        return toResponse(user);
    }

    // ---- roles -------------------------------------------------------------------------------

    @Override
    @Transactional
    public List<RoleResponse> listRoles() {
        return roleRepository.findAllWithPermissions().stream().map(RbacServiceImpl::toResponse).toList();
    }

    @Override
    @Transactional
    public RoleResponse createRole(RoleRequest request) {
        String name = request.roleName().trim();
        if (roleRepository.findByRoleName(name).isPresent()) {
            throw new BusinessRuleException("A role named " + name + " already exists");
        }
        Role role = new Role();
        role.setRoleName(name);
        roleRepository.persist(role);
        auditLogger.log("ROLE_CREATED", "ROLE", role.getId(), "name " + name);
        return toResponse(role);
    }

    @Override
    @Transactional
    public RoleResponse updateRole(Long id, RoleRequest request) {
        Role role = findRole(id);
        String name = request.roleName().trim();
        roleRepository.findByRoleName(name).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new BusinessRuleException("A role named " + name + " already exists");
        });
        String previous = role.getRoleName();
        role.setRoleName(name);
        auditLogger.log("ROLE_UPDATED", "ROLE", id, "name " + previous + " -> " + name);
        return toResponse(role);
    }

    @Override
    @Transactional
    public void deleteRole(Long id) {
        Role role = findRole(id);
        // Because a role still held by any user cannot be deleted, deleting one can never strip
        // ROLE:WRITE from the last holder: no separate last-admin check is needed here.
        long holders = userRepository.countByRoleId(id);
        if (holders > 0) {
            auditLogger.logRejected("ROLE_DELETE_REJECTED", "ROLE", id, "still assigned to " + holders + " user(s)");
            throw new BusinessRuleException("Role " + role.getRoleName() + " is still assigned to " + holders
                    + " user(s); remove it from them first");
        }
        roleRepository.delete(role);
        auditLogger.log("ROLE_DELETED", "ROLE", id, "name " + role.getRoleName());
    }

    @Override
    @Transactional
    public RoleResponse assignPermissionToRole(Long roleId, Long permissionId) {
        Role role = findRole(roleId);
        Permission permission = findPermission(permissionId);
        if (role.getPermissions().add(permission)) {
            auditLogger.log("PERMISSION_ASSIGNED_TO_ROLE", "ROLE", roleId,
                    "permission " + label(permission) + " (" + permissionId + ")");
        }
        return toResponse(role);
    }

    @Override
    @Transactional
    public RoleResponse removePermissionFromRole(Long roleId, Long permissionId) {
        lockLastAdminRule();
        Role role = findRole(roleId);
        Permission permission = findPermission(permissionId);
        if (!role.getPermissions().contains(permission)) {
            return toResponse(role);
        }
        // Rejected only when this role really has holders today and nobody would be left through
        // any other role: userRepository.countByRoleId > 0 and no holder via another role.
        if (ADMIN_RESOURCE.equals(permission.getResource()) && ADMIN_ACTION.equals(permission.getAction())
                && userRepository.countByRoleId(roleId) > 0
                && userRepository.countHoldersExcludingRole(ADMIN_RESOURCE, ADMIN_ACTION, roleId) == 0) {
            auditLogger.logRejected("PERMISSION_REMOVE_FROM_ROLE_REJECTED", "ROLE", roleId,
                    "permission " + label(permission) + ": " + LAST_ADMIN);
            throw new BusinessRuleException(LAST_ADMIN);
        }
        role.getPermissions().remove(permission);
        auditLogger.log("PERMISSION_REMOVED_FROM_ROLE", "ROLE", roleId,
                "permission " + label(permission) + " (" + permissionId + ")");
        return toResponse(role);
    }

    // ---- permissions -------------------------------------------------------------------------

    @Override
    @Transactional
    public List<PermissionResponse> listPermissions() {
        return permissionRepository.findAll(Sort.by("id")).list().stream()
                .map(RbacServiceImpl::toResponse).toList();
    }

    @Override
    @Transactional
    public PermissionResponse createPermission(PermissionRequest request) {
        String resource = normalize(request.resource());
        String action = normalize(request.action());
        if (permissionRepository.findByResourceAndAction(resource, action).isPresent()) {
            throw new BusinessRuleException("Permission " + resource + ":" + action + " already exists");
        }
        Permission permission = new Permission();
        permission.setResource(resource);
        permission.setAction(action);
        permissionRepository.persist(permission);
        auditLogger.log("PERMISSION_CREATED", "PERMISSION", permission.getId(), resource + ":" + action);
        return toResponse(permission);
    }

    @Override
    @Transactional
    public PermissionResponse updatePermission(Long id, PermissionRequest request) {
        lockLastAdminRule();
        Permission permission = findPermission(id);
        String resource = normalize(request.resource());
        String action = normalize(request.action());
        permissionRepository.findByResourceAndAction(resource, action)
                .filter(other -> !other.getId().equals(id)).ifPresent(other -> {
                    throw new BusinessRuleException("Permission " + resource + ":" + action + " already exists");
                });
        // Renaming ROLE:WRITE would silently strip it from everyone who holds it through a role.
        boolean wasAdmin = ADMIN_RESOURCE.equals(permission.getResource()) && ADMIN_ACTION.equals(permission.getAction());
        boolean stillAdmin = ADMIN_RESOURCE.equals(resource) && ADMIN_ACTION.equals(action);
        if (wasAdmin && !stillAdmin && userRepository.countHolders(ADMIN_RESOURCE, ADMIN_ACTION) > 0) {
            auditLogger.logRejected("PERMISSION_UPDATE_REJECTED", "PERMISSION", id, "renaming " + label(permission) + ": " + LAST_ADMIN);
            throw new BusinessRuleException(LAST_ADMIN);
        }
        String previous = label(permission);
        permission.setResource(resource);
        permission.setAction(action);
        auditLogger.log("PERMISSION_UPDATED", "PERMISSION", id, previous + " -> " + resource + ":" + action);
        return toResponse(permission);
    }

    @Override
    @Transactional
    public void deletePermission(Long id) {
        Permission permission = findPermission(id);
        // A permission still assigned to a role cannot be deleted, so this can never strip
        // ROLE:WRITE from the last holder either: no separate last-admin check is needed here.
        long roles = roleRepository.countByPermissionId(id);
        if (roles > 0) {
            auditLogger.logRejected("PERMISSION_DELETE_REJECTED", "PERMISSION", id, "still assigned to " + roles + " role(s)");
            throw new BusinessRuleException("Permission " + label(permission) + " is still assigned to " + roles
                    + " role(s); remove it from them first");
        }
        permissionRepository.delete(permission);
        auditLogger.log("PERMISSION_DELETED", "PERMISSION", id, label(permission));
    }

    // ---- helpers -----------------------------------------------------------------------------

    private User findUser(Long id) {
        return userRepository.findWithRolesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User " + id + " not found"));
    }

    private Role findRole(Long id) {
        Role role = roleRepository.findById(id);
        if (role == null) {
            throw new ResourceNotFoundException("Role " + id + " not found");
        }
        return role;
    }

    private Permission findPermission(Long id) {
        Permission permission = permissionRepository.findById(id);
        if (permission == null) {
            throw new ResourceNotFoundException("Permission " + id + " not found");
        }
        return permission;
    }

    private static boolean grants(Role role, String resource, String action) {
        return role.getPermissions().stream()
                .anyMatch(p -> resource.equals(p.getResource()) && action.equals(p.getAction()));
    }

    private static String normalize(String value) {
        return value.trim().toUpperCase();
    }

    private static String label(Permission permission) {
        return permission.getResource() + ":" + permission.getAction();
    }

    private static PermissionResponse toResponse(Permission p) {
        return new PermissionResponse(p.getId(), p.getResource(), p.getAction());
    }

    private static RoleResponse toResponse(Role r) {
        return new RoleResponse(r.getId(), r.getRoleName(), r.getPermissions().stream()
                .sorted(Comparator.comparing(Permission::getId)).map(RbacServiceImpl::toResponse).toList());
    }

    private static UserDetailResponse toResponse(User u) {
        return new UserDetailResponse(u.getId(), u.getFirstName(), u.getLastName(), u.getEmail(), u.isEnabled(),
                u.getRoles().stream().sorted(Comparator.comparing(Role::getId))
                        .map(r -> new UserDetailResponse.RoleSummary(r.getId(), r.getRoleName())).toList());
    }

    /**
     * Serialises the operations that can strip the last ROLE:WRITE holder. Each of them counts the
     * holders and then writes, and two concurrent transactions could each see the other holder still
     * in place and both commit. A transaction scoped advisory lock makes them queue: it is released
     * automatically at commit or rollback, so the second one counts after the first has committed.
     */
    private void lockLastAdminRule() {
        entityManager.createNativeQuery("select cast(pg_advisory_xact_lock(?1) as text)")
                .setParameter(1, LAST_ADMIN_LOCK_KEY)
                .getResultList();
    }
}
