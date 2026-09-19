package com.edgareldy.quarkustutorial.service;

import com.edgareldy.quarkustutorial.dto.common.PageResponse;
import com.edgareldy.quarkustutorial.dto.rbac.PermissionRequest;
import com.edgareldy.quarkustutorial.dto.rbac.PermissionResponse;
import com.edgareldy.quarkustutorial.dto.rbac.RoleRequest;
import com.edgareldy.quarkustutorial.dto.rbac.RoleResponse;
import com.edgareldy.quarkustutorial.dto.rbac.UserDetailResponse;
import java.util.List;

/**
 * Contract for administering roles, permissions and the role assignments of users.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
public interface RbacService {

    PageResponse<UserDetailResponse> listUsers(int page, int size);

    UserDetailResponse getUser(Long id);

    /** Idempotent: assigning a role the user already has changes nothing. */
    UserDetailResponse assignRoleToUser(Long userId, Long roleId);

    /** Rejected with a business rule error if it would leave no user holding ROLE:WRITE. */
    UserDetailResponse removeRoleFromUser(Long userId, Long roleId);

    List<RoleResponse> listRoles();

    RoleResponse createRole(RoleRequest request);

    RoleResponse updateRole(Long id, RoleRequest request);

    /** Rejected while any user still has the role. */
    void deleteRole(Long id);

    /** Idempotent: assigning a permission the role already has changes nothing. */
    RoleResponse assignPermissionToRole(Long roleId, Long permissionId);

    /** Rejected with a business rule error if it would leave no user holding ROLE:WRITE. */
    RoleResponse removePermissionFromRole(Long roleId, Long permissionId);

    List<PermissionResponse> listPermissions();

    PermissionResponse createPermission(PermissionRequest request);

    PermissionResponse updatePermission(Long id, PermissionRequest request);

    /** Rejected while any role still has the permission. */
    void deletePermission(Long id);
}
