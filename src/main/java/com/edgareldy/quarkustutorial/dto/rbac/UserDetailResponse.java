package com.edgareldy.quarkustutorial.dto.rbac;

import java.util.List;

/**
 * View of a user with the roles assigned to them (never exposes the password hash).
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
public record UserDetailResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        boolean enabled,
        List<RoleSummary> roles) {

    /**
     * Compact role reference inside a user view (id and name only).
     * <p>
     * Created edgar.muhamyangabo on 9/19/26
     * Author : edgar.muhamyangabo
     * Date : 9/19/26
     * Project : quarkus-tutorial
     */
    public record RoleSummary(Long id, String roleName) {
    }
}
