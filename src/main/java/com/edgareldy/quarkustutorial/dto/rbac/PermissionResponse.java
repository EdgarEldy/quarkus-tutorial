package com.edgareldy.quarkustutorial.dto.rbac;

/**
 * View of a permission.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
public record PermissionResponse(Long id, String resource, String action) {
}
