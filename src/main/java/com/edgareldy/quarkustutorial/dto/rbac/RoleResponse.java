package com.edgareldy.quarkustutorial.dto.rbac;

import java.util.List;

/**
 * View of a role with the permissions assigned to it.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
public record RoleResponse(Long id, String roleName, List<PermissionResponse> permissions) {
}
