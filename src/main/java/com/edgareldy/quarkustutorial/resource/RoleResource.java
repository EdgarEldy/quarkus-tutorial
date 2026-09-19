package com.edgareldy.quarkustutorial.resource;

import com.edgareldy.quarkustutorial.dto.common.ApiResponse;
import com.edgareldy.quarkustutorial.dto.rbac.RoleRequest;
import com.edgareldy.quarkustutorial.dto.rbac.RoleResponse;
import com.edgareldy.quarkustutorial.service.RbacService;
import io.quarkus.security.PermissionsAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.jboss.resteasy.reactive.ResponseStatus;

/**
 * Role administration, including which permissions each role holds (assigned onto the role only).
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
@Path("/api/v1/roles")
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "jwt")
public class RoleResource {

    @Inject
    RbacService rbacService;

    @GET
    @PermissionsAllowed("ROLE:READ")
    public ApiResponse<List<RoleResponse>> list() {
        return ApiResponse.success(rbacService.listRoles(), "Roles");
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @PermissionsAllowed("ROLE:WRITE")
    @ResponseStatus(201)
    public ApiResponse<RoleResponse> create(@Valid RoleRequest request) {
        return ApiResponse.success(rbacService.createRole(request), "Role created");
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @PermissionsAllowed("ROLE:WRITE")
    public ApiResponse<RoleResponse> update(@PathParam("id") Long id, @Valid RoleRequest request) {
        return ApiResponse.success(rbacService.updateRole(id, request), "Role updated");
    }

    @DELETE
    @Path("/{id}")
    @PermissionsAllowed("ROLE:WRITE")
    public ApiResponse<Void> delete(@PathParam("id") Long id) {
        rbacService.deleteRole(id);
        return ApiResponse.success(null, "Role deleted");
    }

    @POST
    @Path("/{id}/permissions/{permissionId}")
    @PermissionsAllowed("ROLE:WRITE")
    public ApiResponse<RoleResponse> assignPermission(@PathParam("id") Long id,
                                                      @PathParam("permissionId") Long permissionId) {
        return ApiResponse.success(rbacService.assignPermissionToRole(id, permissionId), "Permission assigned");
    }

    @DELETE
    @Path("/{id}/permissions/{permissionId}")
    @PermissionsAllowed("ROLE:WRITE")
    public ApiResponse<RoleResponse> removePermission(@PathParam("id") Long id,
                                                      @PathParam("permissionId") Long permissionId) {
        return ApiResponse.success(rbacService.removePermissionFromRole(id, permissionId), "Permission removed");
    }
}
