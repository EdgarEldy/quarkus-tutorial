package com.edgareldy.quarkustutorial.dto.rbac;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload to create or rename a role.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
public record RoleRequest(@NotBlank @Size(max = 100) String roleName) {
}
