package com.edgareldy.quarkustutorial.resource;

import com.edgareldy.quarkustutorial.dto.common.ApiResponse;
import com.edgareldy.quarkustutorial.dto.common.PageResponse;
import com.edgareldy.quarkustutorial.dto.rbac.UserDetailResponse;
import com.edgareldy.quarkustutorial.service.RbacService;
import io.quarkus.security.PermissionsAllowed;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

/**
 * Read access to users and management of the roles assigned to them (never creates a user).
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// @PermissionsAllowed("USER:READ") is enforced by Quarkus before the method runs, against the
// StringPermission objects PermissionSecurityIdentityAugmentor attached to the identity: no manual
// check in the body. Anonymous callers get 401, authenticated ones without the permission get 403.
@Path("/api/v1/users")
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "jwt")
public class UserResource {

    @Inject
    RbacService rbacService;

    @GET
    @PermissionsAllowed("USER:READ")
    public ApiResponse<PageResponse<UserDetailResponse>> list(
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(rbacService.listUsers(page, size), "Users");
    }

    @GET
    @Path("/{id}")
    @PermissionsAllowed("USER:READ")
    public ApiResponse<UserDetailResponse> get(@PathParam("id") Long id) {
        return ApiResponse.success(rbacService.getUser(id), "User");
    }

    @PATCH
    @Path("/{id}/roles/{roleId}")
    @PermissionsAllowed("USER:WRITE")
    public ApiResponse<UserDetailResponse> assignRole(@PathParam("id") Long id, @PathParam("roleId") Long roleId) {
        return ApiResponse.success(rbacService.assignRoleToUser(id, roleId), "Role assigned");
    }

    @DELETE
    @Path("/{id}/roles/{roleId}")
    @PermissionsAllowed("USER:WRITE")
    public ApiResponse<UserDetailResponse> removeRole(@PathParam("id") Long id, @PathParam("roleId") Long roleId) {
        return ApiResponse.success(rbacService.removeRoleFromUser(id, roleId), "Role removed");
    }
}
