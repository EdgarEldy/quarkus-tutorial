package com.edgareldy.quarkustutorial.dto.rbac;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload to create or update a permission (stored upper-cased).
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
public record PermissionRequest(
        @NotBlank @Size(max = 100) String resource,
        @NotBlank @Size(max = 100) String action) {
}
